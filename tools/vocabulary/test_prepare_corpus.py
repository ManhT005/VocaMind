"""Checks on the actual 150-sense editorial proposal, never production approval."""
import json
from pathlib import Path
import shutil
import tempfile
import unittest

import ingest
import prepare_corpus as corpus
import quality


class EditorialCorpusTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.artifacts, cls.report = corpus.compile_corpus()
        cls.data = json.loads(cls.artifacts['candidate-catalog.json'])

    def isolated_root(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        for relative in (corpus.DRAFT, Path('docs/data/source-registry.json'),
                         Path('docs/data/evidence/voc-data-01-research-20261025/ngsl/words.csv')):
            (root / relative).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ingest.ROOT / relative, root / relative)
        return root

    def test_actual_corpus_complete_and_contract_compatible(self):
        self.assertEqual(len(self.data['entries']), 150)
        self.assertEqual(len({e['id'] for e in self.data['entries']}), 150)
        self.assertEqual([p['memberships'] for p in self.report['packs']], [40, 40, 40, 30])
        self.assertEqual([p['distinctMeanings'] for p in self.report['packs']], [40, 40, 40, 30])
        self.assertEqual(ingest.contract.validate(self.data), [])
        for entry in self.data['entries']:
            self.assertTrue(all(entry[field] for field in ('meaningVi', 'exampleEn', 'exampleVi')))
            self.assertIsNone(entry['ipa'])
            self.assertEqual(entry['id'], ingest.contract.stable_id(entry['lemma'], entry['pos'], entry['senseIndex']))

    def test_draft_cannot_pass_production_qc_or_release(self):
        self.assertFalse(self.report['releaseEligible'])
        self.assertFalse(self.report['productionQcPassed'])
        self.assertEqual(self.report['qcGateExitCode'], 1)
        self.assertEqual(self.report['errorsByCode'], {'CONTENT_REVIEW_REQUIRED': 150, 'SOURCE_NOT_APPROVED': 2})
        self.assertEqual(self.report['counts']['publishedIds'], 0)
        self.assertTrue(all(not source['authorized'] for source in self.report['sources']))
        release_errors = ingest.contract.validate(self.data, release=True)
        self.assertTrue(release_errors)
        self.assertTrue(all(error.startswith('RELEASE_SOURCE_NOT_APPROVED') for error in release_errors))

    def test_provenance_distinguishes_wordlist_from_authored_components(self):
        self.assertEqual(self.report['counts']['ngslLexicalMatches'], 135)
        self.assertEqual(self.report['counts']['editorialOnlyHeadwords'], 15)
        self.assertEqual(self.report['componentReferencesVerified'], 585)
        for entry in self.data['entries']:
            for ref in entry['provenance']:
                if ref['sourceId'] == corpus.NGSL:
                    self.assertEqual(ref['component'], 'lexical')
                    self.assertIn('#rank=', ref['locator'])
                else:
                    self.assertEqual(ref['sourceId'], corpus.EDITORIAL)
                    self.assertTrue(ref['locator'].startswith(corpus.STAGING + '#'))

    def test_no_autoallocated_identity_or_human_review(self):
        proposals = json.loads(self.artifacts['proposals.json'])
        reviews = json.loads(self.artifacts['reviews-pending.json'])
        self.assertEqual(len(proposals), 150)
        for proposal in proposals:
            self.assertIsNone(proposal['stableId'])
            self.assertEqual(proposal['status'], 'pending')
        for review in reviews['records']:
            self.assertIsNone(review['reviewer'])
            self.assertIsNone(review['reviewedAt'])
            self.assertEqual(review['decision'], 'pending')
            self.assertTrue(all(review[k] is False for k in quality.CHECKS))

    def test_compilation_deterministic_and_snapshot_up_to_date(self):
        self.assertEqual(self.artifacts, corpus.compile_corpus()[0])
        snapshot = ingest.ROOT / corpus.SNAPSHOT
        for name, raw in self.artifacts.items():
            self.assertEqual(raw, (snapshot / name).read_bytes(), name)
        manifest = json.loads(self.artifacts['candidate-manifest.json'])
        for name, checksum in manifest['outputHashes'].items():
            self.assertEqual(ingest.digest(self.artifacts[name]), checksum)

    def test_existing_consumer_refuses_candidate_output(self):
        with self.assertRaises(OSError):
            quality.load_validated(ingest.ROOT / corpus.SNAPSHOT)
        self.assertNotIn('manifest.json', self.artifacts)
        self.assertNotIn('validated_vocab.jsonl', self.artifacts)

    def test_missing_text_and_duplicate_senses_fail_before_materialization(self):
        root = self.isolated_root()
        path = root / corpus.DRAFT
        raw = path.read_text('utf-8')
        lines = raw.splitlines()
        fields = lines[1].split('\t')
        fields[4] = '   '
        lines[1] = '\t'.join(fields)
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Empty editorial'):
            corpus.compile_corpus(root)
        lines = raw.splitlines()
        lines[2] = lines[1]
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Duplicate proposed identity'):
            corpus.compile_corpus(root)

    def test_corpus_requires_audited_wordlist_bytes(self):
        root = self.isolated_root()
        raw = root / 'docs/data/evidence/voc-data-01-research-20261025/ngsl/words.csv'
        raw.write_bytes(raw.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            corpus.compile_corpus(root)

    def test_wording_edit_keeps_proposed_ids_and_invalidates_content_binding(self):
        root = self.isolated_root()
        path = root / corpus.DRAFT
        raw = path.read_text('utf-8')
        # One selected meaning is edited; this must never create a new identity.
        path.write_text(raw.replace('khách hàng\tThe customer', 'người mua hàng\tThe customer', 1), encoding='utf-8')
        edited, report = corpus.compile_corpus(root)
        data = json.loads(edited['candidate-catalog.json'])
        self.assertEqual([e['id'] for e in self.data['entries']], [e['id'] for e in data['entries']])
        self.assertNotEqual(self.report['candidateDatasetSha256'], report['candidateDatasetSha256'])
        self.assertNotEqual(self.artifacts['reviews-pending.json'], edited['reviews-pending.json'])

    def test_output_boundaries_immutable_runs_and_gate_exit(self):
        root = self.isolated_root()
        output = root / 'build/vocabulary-corpus/test'
        self.assertEqual(corpus.run(output, root=root, require_approved=True), 1)
        self.assertTrue((output / 'candidate-manifest.json').exists())
        self.assertFalse((output / 'manifest.json').exists())
        with self.assertRaisesRegex(ValueError, 'new directory'):
            corpus.run(output, root=root)
        with self.assertRaisesRegex(ValueError, 'new directory'):
            corpus.run(root / 'app/src/main/assets/corpus', root=root)


if __name__ == '__main__':
    unittest.main()
