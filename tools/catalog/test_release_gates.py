"""Candidate bundles stay bound together and technical success never grants rights."""

from contextlib import closing
from pathlib import Path
import shutil
import sqlite3
import tempfile
import unittest

import build_release as builder
import test_build_release


class ReleaseGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Reuse the real ingestion setup without inheriting its test methods.
        cls.workspace = None
        test_build_release.ReleaseBuildTests.setUpClass.__func__(cls)
        cls.candidate = cls.root / 'build/catalog-release/gate-baseline'
        builder.build(cls.qc, cls.candidate, root=cls.root)

    def test_candidate_credits_are_honest_and_distribution_remains_blocked(self):
        report = builder.read_json(self.candidate / 'release-gate-report.json')
        self.assertEqual('pass', report['technicalStatus'])
        self.assertEqual('blocked', report['releaseStatus'])
        self.assertFalse(report['releaseAuthorized'])
        self.assertFalse(report['credits']['licenseApprovalVerified'])
        self.assertFalse(report['credits']['releaseCreditComplete'])
        credits = builder.read_json(self.candidate / 'content-credits.json')
        pairs = {(source['id'], source['snapshotDate']) for source in self.data['sources']}
        with self.assertRaisesRegex(ValueError, 'missing'):
            builder.check_credits.validate(credits, pairs, release=True)
        self.assertEqual([], credits['sources'][0]['authors'])
        self.assertIn('not an external license grant', credits['sources'][0]['creditText'])

    def test_tampering_any_companion_is_detected(self):
        for name in sorted(builder.OUTPUTS):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                candidate = Path(folder) / 'candidate'
                shutil.copytree(self.candidate, candidate)
                with (candidate / name).open('ab') as stream:
                    stream.write(b' ')
                with self.assertRaisesRegex(ValueError, 'checksum'):
                    builder.verify(self.qc, candidate, root=self.root)

    def test_rehashed_stale_policy_credits_report_and_metadata_are_detected(self):
        mutations = {
            'quiz-policy.json': lambda d: d.update(projectionSha256='0' * 64),
            'content-credits.json': lambda d: d['sources'][0].update(snapshotDate='2000-01-01'),
            'catalog.metadata.json': lambda d: d.update(uniqueVocabularyCount=999),
            'release-gate-report.json': lambda d: d.update(releaseAuthorized=True, releaseStatus='pass'),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                candidate = Path(folder) / 'candidate'
                shutil.copytree(self.candidate, candidate)
                document = builder.read_json(candidate / name)
                mutate(document)
                builder.write_json(candidate / name, document)
                manifest = builder.read_json(candidate / 'manifest.json')
                manifest['outputHashes'][name] = builder.checksum(candidate / name)
                builder.write_json(candidate / 'manifest.json', manifest)
                with self.assertRaises(ValueError):
                    builder.verify(self.qc, candidate, root=self.root)

    def test_fts_rowid_corruption_is_detected_even_after_rehash(self):
        with tempfile.TemporaryDirectory() as folder:
            candidate = Path(folder) / 'candidate'
            shutil.copytree(self.candidate, candidate)
            with closing(sqlite3.connect(candidate / 'catalog.db')) as db, db:
                db.execute('DELETE FROM VocabularyFts WHERE rowid=1')
                db.execute(
                    'INSERT INTO VocabularyFts(rowid,lemma,normalizedLemma,meaningVi) '
                    'SELECT rowid+1000,lemma,normalizedLemma,meaningVi FROM VocabularyEntry WHERE rowid=1'
                )
            manifest = builder.read_json(candidate / 'manifest.json')
            manifest['outputHashes']['catalog.db'] = builder.checksum(candidate / 'catalog.db')
            builder.write_json(candidate / 'manifest.json', manifest)
            with self.assertRaisesRegex(ValueError, 'FTS rowid'):
                builder.verify(self.qc, candidate, root=self.root)

    def test_missing_or_extra_artifacts_have_no_valid_completion_marker(self):
        for mode in ('missing', 'extra', 'manifest'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as folder:
                candidate = Path(folder) / 'candidate'
                shutil.copytree(self.candidate, candidate)
                if mode == 'missing':
                    (candidate / 'quiz-policy.json').unlink()
                elif mode == 'extra':
                    (candidate / 'stale.db').write_bytes(b'stale')
                else:
                    (candidate / 'manifest.json').unlink()
                with self.assertRaises((ValueError, OSError)):
                    builder.verify(self.qc, candidate, root=self.root)


if __name__ == '__main__':
    unittest.main()
