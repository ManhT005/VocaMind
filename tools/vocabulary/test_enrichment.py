"""Coverage, provenance, review and tamper checks for VOC-DATA-05."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import validate_enrichment as gate

ROOT = Path(__file__).resolve().parents[2]
CANONICAL = ROOT / 'docs/data/voc-data-04/accepted-v1/canonical-preview.json'
METADATA = ROOT / 'docs/data/voc-data-05'


class EnrichmentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data, cls.entries = gate.load_canonical(CANONICAL)

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.sidecar = gate.unknown_sidecar(self.entries)
        self.taxonomy = gate.read_json((METADATA / 'topic-taxonomy-v1.json').read_bytes())
        self.ledger = {'schemaVersion': 1, 'sources': [], 'evidence': []}
        self.reviews = {'schemaVersion': 1, 'enrichmentVersion': gate.VERSION, **gate.SNAPSHOT, 'decisions': []}
        self.row = self.sidecar['records'][0]

    def validate(self):
        return gate.validate(self.sidecar, self.data, self.entries, self.taxonomy,
                             self.ledger, self.reviews, self.base, self.base)

    def artifact(self, name):
        raw = f'Synthetic test evidence for {name}\n'.encode()
        (self.base / name).write_bytes(raw)
        return {'artifact': name, 'artifactSha256': gate.digest(raw)}

    def claim(self, field='cefr', kind='external-sense-cefr', status='suggested', values=('B1',)):
        source = {'id': field+'-source', 'kind': kind, 'status': 'approved', 'snapshot': 'test-v1',
                  'permission': 'synthetic test only', 'approvedBy': 'Test approver',
                  'approvedAt': '2026-11-07', 'approval': self.artifact(field+'-approval.txt')}
        evidence = {'id': field+'-evidence', 'sourceId': source['id'], 'stableId': self.row['stableId'],
                    'field': field, 'values': list(values), 'rationale': 'Synthetic same-sense evidence',
                    **self.artifact(field+'-evidence.txt')}
        if kind == 'ai-suggestion':
            evidence.update(model='synthetic', provider='test', promptVersion='1', createdAt='2026-11-07')
        self.ledger['sources'].append(source)
        self.ledger['evidence'].append(evidence)
        self.row['cefrLevel' if field == 'cefr' else 'topicIds'] = values[0] if field == 'cefr' else list(values)
        self.row[field+'Status'] = status
        self.row['evidenceRefs'].append(evidence['id'])
        return source, evidence

    def decide(self, decision='approved'):
        self.row['reviewDecision'] = decision
        sha = gate.claim_digest(self.row, gate.index(self.taxonomy['topics'], 'id'),
                                gate.index(self.ledger['sources'], 'id'), gate.index(self.ledger['evidence'], 'id'))
        self.reviews['decisions'] = [{'stableId': self.row['stableId'], 'decision': decision,
                                      'reviewedBy': 'Test reviewer', 'reviewedAt': '2026-11-07',
                                      'claimSha256': sha, **self.artifact('review.txt')}]

    def test_full_unknown_snapshot(self):
        report = self.validate()
        self.assertEqual(report['coverage']['cefr']['unknown'], 150)
        self.assertEqual(report['coverage']['topic']['unknown'], 150)
        self.assertEqual([r['total'] for r in report['byPack'].values()], [40, 40, 40, 30])
        for group in ('byPos', 'byCanonicalSource'):
            self.assertEqual(sum(r['total'] for r in report[group].values()), 150)

    def test_verified_labels_do_not_change_canonical(self):
        baseline = copy.deepcopy(self.data)
        self.claim(status='verified')
        self.claim('topic', 'editorial-sense-topic', 'verified', ('education',))
        self.decide()
        report = self.validate()
        self.assertEqual(report['coverage']['cefr']['verified'], 1)
        self.assertEqual(report['coverage']['topic']['verified'], 1)
        self.assertEqual(baseline, self.data)

    def test_duplicate_missing_and_foreign_id(self):
        records = self.sidecar['records']
        for invalid, message in (
            (records[:-1]+[records[0]], 'duplicate'),
            (records[:-1], 'coverage'),
            (records[:-1]+[{**records[-1], 'stableId': '0'*16}], 'coverage'),
        ):
            with self.subTest(message=message):
                self.sidecar['records'] = invalid
                with self.assertRaisesRegex(ValueError, message):
                    self.validate()

    def test_invalid_level_topic_and_duplicate_topic(self):
        for updates, message in (
            ({'cefrLevel': 'B7'}, 'invalid CEFR'),
            ({'topicIds': ['unlisted']}, 'unknown topic'),
            ({'topicIds': ['education', 'education']}, 'duplicate'),
            ({'cefrLevel': 'B1'}, 'status and values disagree'),
        ):
            with self.subTest(message=message):
                prior = self.row.copy()
                self.row.update(updates)
                with self.assertRaisesRegex(ValueError, message):
                    self.validate()
                self.row.update(prior)

    def test_snapshot_binding_mismatch(self):
        self.sidecar['targetDatasetSha256'] = '0'*64
        with self.assertRaisesRegex(ValueError, 'snapshot binding'):
            self.validate()

    def test_canonical_byte_tamper(self):
        path = self.base / 'canonical.json'
        path.write_bytes(CANONICAL.read_bytes()+b' ')
        with self.assertRaisesRegex(ValueError, 'canonical file SHA-256 mismatch'):
            gate.load_canonical(path)

    def test_held_and_unknown_source(self):
        source, item = self.claim()
        source['status'] = 'held'
        with self.assertRaisesRegex(ValueError, 'source held'):
            self.validate()
        item['sourceId'] = 'unlisted'
        with self.assertRaisesRegex(ValueError, 'unknown evidence source'):
            self.validate()

    def test_unknown_evidence_reference(self):
        self.row['evidenceRefs'] = ['unlisted']
        with self.assertRaisesRegex(ValueError, 'unknown evidence reference'):
            self.validate()

    def test_artifact_path_cannot_escape_ledger(self):
        _, item = self.claim()
        for path in (str(CANONICAL), '../outside.txt'):
            with self.subTest(path=path):
                item['artifact'] = path
                with self.assertRaisesRegex(ValueError, 'invalid evidence artifact path'):
                    self.validate()

    def test_tampered_approval_evidence_or_review(self):
        self.claim(status='verified')
        self.decide()
        for name in ('cefr-approval.txt', 'cefr-evidence.txt', 'review.txt'):
            with self.subTest(name=name):
                path = self.base / name
                raw = path.read_bytes()
                path.write_bytes(raw+b'tamper')
                with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
                    self.validate()
                path.write_bytes(raw)

    def test_evidence_binds_sense_and_label(self):
        _, item = self.claim()
        item['stableId'] = self.sidecar['records'][1]['stableId']
        with self.assertRaisesRegex(ValueError, 'different sense'):
            self.validate()
        item['stableId'] = self.row['stableId']
        item['values'] = ['A1']
        with self.assertRaisesRegex(ValueError, 'conflicting evidence'):
            self.validate()

    def test_verified_requires_record_and_matching_claim(self):
        self.claim(status='verified')
        with self.assertRaisesRegex(ValueError, 'requires approval'):
            self.validate()
        self.row['reviewDecision'] = 'approved'
        with self.assertRaisesRegex(ValueError, 'missing review evidence'):
            self.validate()
        self.decide()
        self.ledger['evidence'][0]['rationale'] += ' changed'
        with self.assertRaisesRegex(ValueError, 'different claim'):
            self.validate()

    def test_rejected_suggestion_is_not_verified(self):
        self.claim()
        self.decide('rejected')
        self.assertEqual(self.validate()['coverage']['review']['rejected'], 1)
        self.row['cefrStatus'] = 'verified'
        self.decide('rejected')
        with self.assertRaisesRegex(ValueError, 'requires approval'):
            self.validate()

    def test_ai_and_pack_context_are_only_suggestions(self):
        self.claim(kind='ai-suggestion')
        self.claim('topic', 'editorial-pack-context', 'suggested', ('education',))
        self.assertEqual(self.validate()['coverage']['cefr']['suggested'], 1)
        for field in ('cefr', 'topic'):
            with self.subTest(field=field):
                self.row[field+'Status'] = 'verified'
                self.decide()
                with self.assertRaisesRegex(ValueError, 'lacks eligible evidence'):
                    self.validate()
                self.row[field+'Status'] = 'suggested'

    def test_frequency_rank_cannot_label_cefr(self):
        self.claim(kind='frequency-list')
        with self.assertRaisesRegex(ValueError, 'kind cannot support field'):
            self.validate()

    def test_duplicate_taxonomy_id(self):
        self.taxonomy['topics'].append(self.taxonomy['topics'][0])
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            self.validate()

    def test_deterministic_generator_and_cli_failure(self):
        cli = [sys.executable, str(Path(gate.__file__)), '--canonical', str(CANONICAL)]
        first = subprocess.check_output(cli+['--generate-unknown'])
        self.assertEqual(first, subprocess.check_output(cli+['--generate-unknown']))
        self.assertEqual(gate.read_json(first), self.sidecar)
        bad = self.base / 'bad.json'
        bad.write_text(json.dumps({**self.sidecar, 'records': []}), encoding='utf-8')
        run = subprocess.run(cli+['--input', str(bad), '--taxonomy', str(METADATA/'topic-taxonomy-v1.json'),
                                  '--sources', str(METADATA/'label-sources.json'),
                                  '--reviews', str(METADATA/'review-decisions.json')],
                             capture_output=True, text=True)
        self.assertEqual(run.returncode, 1)
        self.assertIn('ENRICHMENT_INVALID', run.stderr)
        self.assertEqual(run.stdout, '')


if __name__ == '__main__':
    unittest.main()
