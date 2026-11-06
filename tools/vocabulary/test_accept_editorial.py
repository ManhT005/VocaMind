"""Acceptance-route tests; never substitute fixture decisions for owner evidence."""
import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest

import accept_editorial as accepted
import ingest
import quality
from source_gate import authorize, validate_registry


class AcceptedEditorialTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        source = ingest.ROOT / accepted.BASE
        shutil.copytree(source, self.root / accepted.BASE)
        self.files = accepted.compile_accepted(self.root)
        target = self.root / accepted.ACCEPTED
        target.mkdir(parents=True, exist_ok=True)
        for name, raw in self.files.items():
            (target / name).write_bytes(raw)
        self.registry = json.loads(self.files['source-registry.json'])

    def test_owner_accepted_content_passes_real_ingestion_qc_and_consumer(self):
        config = self.root / accepted.ACCEPTED / 'ingestion-config.json'
        ingestion = self.root / 'build/vocabulary-ingestion/accepted'
        output = self.root / 'build/vocabulary-quality/accepted'
        self.assertEqual(ingest.run(config, ingestion, root=self.root), 0)
        self.assertEqual(quality.run(ingestion, config, self.root / accepted.ACCEPTED / 'reviews.json',
                                     output, root=self.root), 0)
        data, policy = accepted.load_handoff(output, self.root / accepted.ACCEPTED)
        self.assertEqual(len(data['entries']), 150)
        self.assertEqual(len(data['sources']), 1)
        self.assertEqual(ingest.contract.validate(data, release=True), [])
        self.assertTrue(all(e['sourceId'] == accepted.SOURCE_ID for e in data['entries']))
        for a, b in policy['forbiddenDistractorPairs']:
            self.assertFalse(accepted.can_be_distractor(policy, a, b))
            self.assertFalse(accepted.can_be_distractor(policy, b, a))
        ids = {e['lemma']: e['id'] for e in data['entries']}
        self.assertTrue(accepted.can_be_distractor(policy, ids['study'], ids['method']))
        policy_path = self.root / accepted.ACCEPTED / 'quiz-policy.json'
        original_policy = policy_path.read_bytes()
        tampered = copy.deepcopy(policy)
        tampered['forbiddenDistractorPairs'][0] = [ids['study'], ids['method']]
        policy_path.write_bytes(ingest.encoded(tampered))
        with self.assertRaisesRegex(ValueError, 'accepted restrictions'):
            accepted.load_handoff(output, self.root / accepted.ACCEPTED)
        policy_path.write_bytes(original_policy)
        self.assertEqual(policy['datasetSha256'], quality.entry_digest(data))
        by_id = {e['id']: e['lemma'] for e in data['entries']}
        self.assertEqual([[by_id[a], by_id[b]] for a, b in policy['forbiddenDistractorPairs']],
                         [['research', 'study'], ['home', 'house'], ['customer', 'client']])

    def test_committed_snapshot_matches_reproduction(self):
        files = accepted.compile_accepted()
        for name, raw in files.items():
            self.assertEqual(raw, (ingest.ROOT / accepted.ACCEPTED / name).read_bytes(), name)

    def test_acceptance_is_not_reusable_after_worksheet_or_content_edits(self):
        for relative in (accepted.BASE / 'editorial-v1.tsv', accepted.DRAFT / 'review-worksheet.md',
                         accepted.DRAFT / 'candidate-catalog.json'):
            path = self.root / relative
            original = path.read_bytes()
            path.write_bytes(original + b'changed')
            with self.assertRaisesRegex(ValueError, 'acceptance is stale'):
                accepted.compile_accepted(self.root)
            path.write_bytes(original)

    def test_project_route_does_not_authorize_external_or_unknown_sources(self):
        for source_id in ('ngsl', 'fvdp', 'unknown'):
            with self.assertRaises(ValueError):
                authorize(self.registry, source_id, self.root)
        changed = copy.deepcopy(self.registry)
        changed['sources'].append(copy.deepcopy(changed['sources'][0]))
        with self.assertRaises(ValueError):
            validate_registry(changed)

    def test_revoked_acceptance_or_tampered_artifacts_block_ingestion(self):
        self.assertEqual(authorize(self.registry, accepted.REGISTRY_ID, self.root)['id'],
                         accepted.REGISTRY_ID)
        for mutation in ('hold', 'review', 'bytes'):
            document = copy.deepcopy(self.registry)
            if mutation == 'hold':
                document['sources'][0]['decision'] = 'hold'
            elif mutation == 'review':
                document['sources'][0]['review']['decision'] = 'pending'
            else:
                path = self.root / accepted.ACCEPTED / 'acceptance.json'
                path.write_bytes(path.read_bytes() + b'changed')
            with self.assertRaises(ValueError):
                authorize(document, accepted.REGISTRY_ID, self.root)

    def test_rebound_reviews_remain_required_and_snapshot_is_deterministic(self):
        self.assertEqual(self.files, accepted.compile_accepted(self.root))
        data = json.loads(self.files['canonical-preview.json'])
        reviews = json.loads(self.files['reviews.json'])
        data['entries'][0]['exampleVi'] += ' Đã sửa.'
        issues = quality.evaluate(data, reviews)[1]
        codes = {issue['errorCode'] for issue in issues}
        self.assertIn('REVIEW_DATASET_MISMATCH', codes)
        self.assertIn('REVIEW_CONTENT_MISMATCH', codes)


if __name__ == '__main__':
    unittest.main()
