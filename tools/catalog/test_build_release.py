"""Real accepted QC -> SQLite candidate, including failure and reproducibility gates."""

from contextlib import closing
import copy
import json
from pathlib import Path
import shutil
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import build_release as builder


class ReleaseBuildTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workspace = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.workspace.cleanup)
        cls.root = Path(cls.workspace.name)
        shutil.copytree(builder.ROOT / builder.accept_editorial.BASE,
                        cls.root / builder.accept_editorial.BASE)
        schema = cls.root / builder.SCHEMA_PATH
        schema.parent.mkdir(parents=True)
        shutil.copyfile(builder.ROOT / builder.SCHEMA_PATH, schema)
        cls.snapshot = cls.root / builder.accept_editorial.ACCEPTED
        cls.qc = cls.root / 'build/vocabulary-quality/accepted'
        ingestion = cls.root / 'build/vocabulary-ingestion/accepted'
        config = cls.snapshot / 'ingestion-config.json'
        if builder.ingest.run(config, ingestion, root=cls.root) != 0:
            raise AssertionError('Production ingestion failed')
        if builder.quality.run(ingestion, config, cls.snapshot / 'reviews.json',
                               cls.qc, root=cls.root) != 0:
            raise AssertionError('Production QC failed')
        cls.data, cls.policy = builder.accept_editorial.load_handoff(cls.qc, cls.snapshot)

    def setUp(self):
        self.output = self.root / 'build/catalog-release' / self.id().rsplit('.', 1)[-1]

    def test_build_preserves_150_senses_companions_and_real_sql_counts(self):
        metadata = builder.build(self.qc, self.output, root=self.root)
        self.assertEqual(150, metadata['uniqueVocabularyCount'])
        self.assertEqual(150, metadata['membershipCount'])
        self.assertEqual(builder.ingest.contract.PACK_MINIMUMS, metadata['packCounts'])
        self.assertEqual(150, metadata['examplePairCount'])
        self.assertEqual(150, metadata['nullableIpaCount'])
        self.assertEqual(150, metadata['ftsMappedRows'])
        self.assertEqual(300, metadata['ftsSearchChecks'])
        self.assertEqual(metadata, builder.verify(self.qc, self.output, root=self.root))
        policy = builder.read_json(self.output / 'quiz-policy.json')
        self.assertEqual(self.policy['forbiddenDistractorPairs'], policy['forbiddenDistractorPairs'])
        self.assertEqual(builder.build_quiz_policy.projection_digest(self.data['entries']),
                         policy['projectionSha256'])
        with closing(sqlite3.connect(self.output / 'catalog.db')) as db:
            self.assertEqual({entry['id'] for entry in self.data['entries']},
                             {row[0] for row in db.execute('SELECT id FROM VocabularyEntry')})

    def test_independent_builds_have_identical_bytes_and_logical_dump(self):
        first = self.output / 'first'
        second = self.output / 'second'
        builder.build(self.qc, first, root=self.root)
        builder.build(self.qc, second, root=self.root)
        for path in first.iterdir():
            self.assertEqual(path.read_bytes(), (second / path.name).read_bytes(), path.name)

    def test_existing_output_and_packaged_asset_paths_are_refused(self):
        self.output.mkdir(parents=True)
        marker = self.output / 'keep.txt'
        marker.write_bytes(b'preserve')
        for target in (self.output, self.root / 'app/src/main/assets/database'):
            with self.subTest(target=target), self.assertRaisesRegex(ValueError, 'fresh directory'):
                builder.build(self.qc, target, root=self.root)
        self.assertEqual(b'preserve', marker.read_bytes())

    def test_sql_or_publication_failure_leaves_no_candidate(self):
        for method in ('create_database', 'inspect_database', 'write_json', 'os.rename'):
            with self.subTest(method=method):
                with patch('build_release.' + method, side_effect=OSError('injected failure')):
                    with self.assertRaises(OSError):
                        builder.build(self.qc, self.output, root=self.root)
                self.assertFalse(self.output.exists())
                self.assertEqual([], list(self.output.parent.glob('.catalog-stage-*')))

    def test_missing_tampered_and_fixture_qc_never_publish(self):
        for mode in ('manifest', 'canonical', 'fixture', 'pending'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as folder:
                altered = Path(folder) / 'qc'
                shutil.copytree(self.qc, altered)
                if mode == 'manifest':
                    (altered / 'manifest.json').unlink()
                elif mode == 'canonical':
                    with (altered / 'canonical-catalog.json').open('ab') as stream:
                        stream.write(b' ')
                else:
                    manifest = builder.read_json(altered / 'manifest.json')
                    manifest['fixtureOnly'] = mode == 'fixture'
                    manifest['status'] = 'fixture-pass' if mode == 'fixture' else 'pending'
                    builder.write_json(altered / 'manifest.json', manifest)
                with self.assertRaises((ValueError, OSError)):
                    builder.build(altered, self.output, root=self.root)
                self.assertFalse(self.output.exists())

    def test_missing_stale_and_changed_policy_are_refused(self):
        path = self.snapshot / 'quiz-policy.json'
        original = path.read_bytes()
        self.addCleanup(path.write_bytes, original)
        for mode in ('missing', 'dataset', 'pairs'):
            with self.subTest(mode=mode):
                path.write_bytes(original)
                if mode == 'missing':
                    path.unlink()
                else:
                    policy = json.loads(original)
                    if mode == 'dataset':
                        policy['datasetSha256'] = '0' * 64
                    else:
                        policy['forbiddenDistractorPairs'] = []
                    builder.write_json(path, policy)
                with self.assertRaises((ValueError, OSError)):
                    builder.build(self.qc, self.output, root=self.root)
                self.assertFalse(self.output.exists())

    def test_source_hold_pending_review_and_revoked_acceptance_are_refused(self):
        for filename, mutate in (
            ('source-registry.json', lambda d: d['sources'][0].update(decision='hold')),
            ('source-registry.json', lambda d: d['sources'][0]['review'].update(decision='pending')),
            ('acceptance.json', lambda d: d.update(decision='pending')),
            ('reviews.json', lambda d: d['records'][0].update(decision='pending')),
        ):
            with self.subTest(filename=filename):
                path = self.snapshot / filename
                original = path.read_bytes()
                try:
                    document = json.loads(original)
                    mutate(document)
                    builder.write_json(path, document)
                    with self.assertRaises(ValueError):
                        builder.build(self.qc, self.output, root=self.root)
                    self.assertFalse(self.output.exists())
                finally:
                    path.write_bytes(original)

    def test_rehashed_invalid_canonical_still_fails_release_or_frozen_gate(self):
        for mode in ('149-words', 'missing-pack', 'blank-meaning', 'blank-example',
                     'duplicate-id', 'duplicate-order', 'rewritten-text', 'changed-license'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as folder:
                altered = Path(folder) / 'qc'
                shutil.copytree(self.qc, altered)
                data = copy.deepcopy(self.data)
                if mode == '149-words':
                    data['entries'].pop()
                elif mode == 'missing-pack':
                    data['packs'].pop()
                elif mode == 'blank-meaning':
                    data['entries'][0]['meaningVi'] = ''
                elif mode == 'blank-example':
                    data['entries'][0]['exampleVi'] = ''
                elif mode == 'duplicate-id':
                    data['entries'][1]['id'] = data['entries'][0]['id']
                elif mode == 'duplicate-order':
                    data['packWords'][1]['orderNo'] = data['packWords'][0]['orderNo']
                    data['packWords'][1]['packId'] = data['packWords'][0]['packId']
                elif mode == 'rewritten-text':
                    data['entries'][0]['exampleEn'] += ' Changed.'
                else:
                    data['sources'][0]['licenseCode'] = 'CC0-1.0'
                builder.write_json(altered / 'canonical-catalog.json', data)
                manifest = builder.read_json(altered / 'manifest.json')
                manifest['outputHashes']['canonical-catalog.json'] = builder.checksum(
                    altered / 'canonical-catalog.json')
                builder.write_json(altered / 'manifest.json', manifest)
                with self.assertRaises(ValueError):
                    builder.build(altered, self.output, root=self.root)
                self.assertFalse(self.output.exists())


if __name__ == '__main__':
    unittest.main()
