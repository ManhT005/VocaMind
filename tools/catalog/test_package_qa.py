"""Regression gates for the isolated QA bundle and scoped rights records."""
import copy
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

from audit_provenance import AUDIT, NGSL, NGSL_HASH, RIGHTS, SOURCE_REGISTRY, audit
import build_release as builder
import catalog_snapshot
import content_rights
import package_qa


class QaPackagingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        workspace = tempfile.TemporaryDirectory()
        cls.addClassCleanup(workspace.cleanup)
        cls.root = Path(workspace.name)
        shutil.copytree(builder.ROOT / builder.accept_editorial.BASE,
                        cls.root / builder.accept_editorial.BASE)
        for path in (builder.SCHEMA_PATH, AUDIT, NGSL, RIGHTS, SOURCE_REGISTRY):
            target = cls.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(builder.ROOT / path, target)
        cls.bundle = package_qa.prepare(root=cls.root)
        cls.snapshot = cls.root / catalog_snapshot.QA_PATH
        cls.assets = cls.bundle / 'assets/database'

    def test_preparation_reuses_byte_identical_bundle_and_preserves_v1(self):
        baseline = package_qa.file_hashes(self.root / builder.accept_editorial.ACCEPTED)
        hashes = package_qa.file_hashes(self.bundle)
        package_qa.prepare(root=self.root)
        self.assertEqual(hashes, package_qa.file_hashes(self.bundle))
        self.assertEqual(baseline, package_qa.file_hashes(self.root / builder.accept_editorial.ACCEPTED))
        v1 = builder.read_json(self.root / builder.accept_editorial.ACCEPTED / 'canonical-preview.json')
        v2 = builder.read_json(self.snapshot / 'canonical-preview.json')
        self.assertEqual(v1['packWords'], v2['packWords'])
        self.assertEqual(v1['identityRegistry'], v2['identityRegistry'])
        self.assertEqual(v1['entries'], [dict(e, contentVersion=v1['contentVersion']) for e in v2['entries']])
        self.assertEqual('1.0.1', v2['contentVersion'])
        self.assertTrue(all('Bản nháp' not in pack['description'] for pack in v2['packs']))

    def test_historical_audit_rejects_altered_archive(self):
        path = self.root / NGSL
        original = path.read_bytes()
        self.addCleanup(path.write_bytes, original)
        path.write_bytes(original + b'\n')
        with self.assertRaisesRegex(ValueError, 'archived CSV checksum'):
            audit(self.root)

    def test_snapshot_rejects_changed_source_and_reviews_even_if_rehashed(self):
        for name in ('canonical-preview.json', 'reviews.json'):
            with self.subTest(name=name):
                path = self.snapshot / name
                manifest_path = self.snapshot / 'snapshot-manifest.json'
                original, manifest_bytes = path.read_bytes(), manifest_path.read_bytes()
                try:
                    record = builder.read_json(path)
                    if name == 'canonical-preview.json':
                        record['entries'][0]['sourceId'] = 'tatoeba-text'
                    else:
                        record['records'][0]['reviewer'] = 'unverified-reviewer'
                    builder.write_json(path, record)
                    manifest = builder.read_json(manifest_path)
                    manifest['snapshotHashes'][name] = builder.checksum(path)
                    builder.write_json(manifest_path, manifest)
                    with self.assertRaisesRegex(ValueError, 'QA snapshot changed'):
                        catalog_snapshot.load(self.root, self.snapshot)
                finally:
                    path.write_bytes(original)
                    manifest_path.write_bytes(manifest_bytes)

    def test_apk_verifier_checks_extracted_sql_and_rejects_wrong_or_tampered_bundle(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'test.apk'
            for tampered in (None, 'content-credits.json', 'catalog.db', 'missing'):
                with self.subTest(tampered=tampered):
                    with ZipFile(apk, 'w') as archive:
                        for path in sorted(self.assets.iterdir()):
                            if tampered == 'missing' and path.name == 'catalog.db':
                                continue
                            raw = path.read_bytes()
                            archive.writestr(package_qa.ASSET_PREFIX + path.name,
                                             raw + (b'x' if tampered == path.name else b''))
                    if tampered is None:
                        result = package_qa.verify_apk(apk, root=self.root)
                        self.assertEqual(150, result['sqlQa']['uniqueVocabularyCount'])
                        self.assertEqual(300, result['sqlQa']['ftsSearchChecks'])
                        self.assertFalse(result['releaseAuthorized'])
                    else:
                        with self.assertRaisesRegex(ValueError, 'APK .* mismatch'):
                            package_qa.verify_apk(apk, root=self.root)

    def test_failed_publication_leaves_existing_bundle_intact_and_no_new_marker(self):
        with tempfile.TemporaryDirectory() as temporary:
            stage = Path(temporary) / 'stage'
            stage.mkdir()
            (stage / 'manifest.json').write_text('different bundle', encoding='utf-8')
            before = package_qa.file_hashes(self.bundle)
            with self.assertRaisesRegex(ValueError, 'Existing output differs'):
                package_qa.publish(stage, self.bundle)
            self.assertEqual(before, package_qa.file_hashes(self.bundle))
            target = Path(temporary) / 'new'
            with patch('package_qa.os.rename', side_effect=OSError('injected disk failure')):
                with self.assertRaises(OSError):
                    package_qa.publish(stage, target)
            self.assertFalse(target.exists())
            self.assertEqual(before, package_qa.file_hashes(self.bundle))

    def test_scoped_approval_record_and_negative_rights_gates(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data = builder.read_json(self.snapshot / 'canonical-preview.json')
            source = data['sources'][0]
            record = {
                'schemaVersion': 1, 'decision': 'approved', 'role': 'Content Lead',
                'approvedBy': 'Synthetic test approver', 'approvedAt': '2026-11-07',
                'contentReview': 'approved', 'outstandingConditions': [],
                'channels': ['internal-qa'], 'snapshotId': 'editorial-qa-v2',
                'snapshotManifestSha256': builder.checksum(self.snapshot / 'snapshot-manifest.json'),
                'datasetSha256': builder.build_quiz_policy.dataset_digest(data),
                'components': {name: {'sourceId': source['id'], 'status': 'authorized',
                                      'sourceSha256': source['snapshotSha256'],
                                      'permissionBasis': 'Synthetic test permission'}
                               for name in content_rights.EDITORIAL},
                'sourceCredits': [{'sourceId': source['id'], 'snapshotDate': source['snapshotDate'],
                                   'authors': [], 'creditMode': 'responsible-publisher',
                                   'responsiblePublisher': 'Synthetic test publisher',
                                   'creditText': 'Synthetic test credit'}],
            }
            record['components']['ngsl-reference'] = {
                'sourceId': 'ngsl', 'sourceSha256': NGSL_HASH, 'status': 'history-only', 'permissionBasis': 'Synthetic scope decision',
            }
            for field in ('signedDecision', 'reviewedNotice'):
                evidence = base / (field + '.txt')
                evidence.write_text('Synthetic test evidence', encoding='utf-8')
                record[field] = {'file': evidence.name, 'sha256': builder.checksum(evidence)}
            approval = base / 'approval.json'
            builder.write_json(approval, record)
            result = content_rights.verify(self.snapshot, approval, 'internal-qa', root=self.root)
            self.assertEqual('verified', result['recordStatus'])
            self.assertFalse(result['releaseAuthorized'])
            with self.assertRaisesRegex(ValueError, 'scope'):
                content_rights.verify(self.snapshot, approval, 'public-apk', root=self.root)

            mutations = (
                (lambda d: d.update(decision='HOLD', approvedBy=None), 'approval record'),
                (lambda d: d.update(datasetSha256='0' * 64), 'different snapshot'),
                (lambda d: d['signedDecision'].update(sha256='0' * 64), 'evidence mismatch'),
                (lambda d: d['components']['ngsl-reference'].update(status='redistributed'), 'NGSL attribution'),
                (lambda d: d['components']['editorial-meaning'].update(status='pending'), 'editorial permission'),
                (lambda d: d['sourceCredits'][0].update(snapshotDate='2020-01-01'), 'mismatched snapshot'),
            )
            for mutate, expected in mutations:
                with self.subTest(expected=expected):
                    changed = copy.deepcopy(record)
                    mutate(changed)
                    builder.write_json(approval, changed)
                    with self.assertRaisesRegex(ValueError, expected):
                        content_rights.verify(self.snapshot, approval, 'internal-qa', root=self.root)


if __name__ == '__main__':
    unittest.main()
