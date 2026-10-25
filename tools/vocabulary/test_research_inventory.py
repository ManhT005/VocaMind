"""Offline checks of actual captured inputs; no network or approval changes."""
import csv
import json
import unittest

from source_gate import ROOT, audit_eligible, authorize, verified_artifact


class ResearchInventoryTest(unittest.TestCase):
    def setUp(self):
        self.document = json.loads((ROOT / 'docs/data/source-registry.json').read_text('utf-8'))

    def test_all_archived_artifacts_match_capture_hashes(self):
        capture = json.loads((ROOT / self.document['evidenceCapture']).read_text('utf-8'))
        self.assertEqual([], capture['errors'])
        self.assertEqual(12, sum(map(len, capture['sources'].values())))
        for artifacts in capture['sources'].values():
            for artifact in artifacts:
                verified_artifact(artifact, ROOT)
        audit_eligible(self.document)

    def test_raw_wordlists_match_registered_counts(self):
        for source in self.document['sources']:
            if source['id'] not in ('ngsl', 'tsl'):
                continue
            snapshot = source['snapshot']
            data = next(a for a in snapshot['artifacts'] if a['role'] == 'data')
            with (ROOT / data['path']).open(encoding=snapshot['textEncoding'], newline='') as stream:
                rows = list(csv.DictReader(stream))
            expected = 2809 if source['id'] == 'ngsl' else 1250
            self.assertEqual(expected, len(rows))
            self.assertEqual(expected, snapshot['recordCount'])

    def test_every_actual_source_remains_blocked(self):
        for source in self.document['sources']:
            self.assertFalse(source['pipelineEligible'])
            with self.subTest(source=source['id']), self.assertRaises(ValueError):
                authorize(self.document, source['id'])


if __name__ == '__main__':
    unittest.main()
