"""Offline ING-01..14 integration checks, using isolated synthetic data only."""
import copy
import json
from pathlib import Path
import shutil
import socket
import tempfile
import unittest
from unittest.mock import patch

import ingest
from gate_sources import freeze
from source_gate import snapshot_content_digest
import test_source_gate


class IngestionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.relative = 'tools/vocabulary/fixtures/ingestion'
        self.fixtures = self.root / self.relative
        shutil.copytree(ingest.ROOT / self.relative, self.fixtures)
        self.config = ingest.contract.load_json(self.fixtures / 'config.json')
        self.rows = [json.loads(l) for l in (self.fixtures / 'records.jsonl').read_text('utf-8').splitlines()]

    def save(self, name, data):
        (self.fixtures / name).write_bytes(ingest.encoded(data))

    def records(self, rows=None, raw=None):
        raw = raw if raw is not None else b''.join(ingest.encoded(r).replace(b'\n', b'') + b'\n' for r in (rows or self.rows))
        (self.fixtures / 'records.jsonl').write_bytes(raw)
        checksum = ingest.digest(raw)
        self.config['sources'][0]['sha256'] = checksum
        self.config['sources'][0]['metadata']['snapshotSha256'] = checksum

    def run_pipeline(self, name='run', fixture=True, dry_run=False):
        self.save('config.json', self.config)
        output = self.root / 'build/vocabulary-ingestion' / name
        result = ingest.run(self.fixtures / 'config.json', output, fixture=fixture,
                            dry_run=dry_run, root=self.root)
        return result, output

    def report(self, output):
        return ingest.contract.load_json(output / 'reject-report.json')

    def test_ing01_02_07_12_reproducible_offline_multisource(self):
        with patch.object(socket.socket, 'connect', side_effect=AssertionError('Network forbidden')):
            a, first = self.run_pipeline('one')
            b, second = self.run_pipeline('two')
        self.assertEqual((a, b), (0, 0))
        for path in first.iterdir():
            self.assertEqual(path.read_bytes(), (second / path.name).read_bytes())
        data = ingest.contract.load_json(first / 'canonical-catalog.json')
        self.assertEqual(ingest.contract.validate(data), [])
        self.assertEqual(len(data['entries']), 4)
        self.assertEqual(len(data['sources']), 2)
        self.assertTrue(all(s['attributionText'] for s in data['sources']))
        for e in data['entries']:
            self.assertEqual(len({r['sourceId'] for r in e['provenance']}), 2)
        self.assertEqual(data['identityRegistry'], sorted(ingest.contract.load_json(self.fixtures / 'identities.json'), key=lambda i: i['id']))

    def test_ing03_order_independence(self):
        _, first = self.run_pipeline('first')
        self.records(list(reversed(self.rows)))
        result, second = self.run_pipeline('second')
        self.assertEqual(result, 0)
        before = ingest.contract.load_json(first / 'canonical-catalog.json')
        after = ingest.contract.load_json(second / 'canonical-catalog.json')
        for field in ('entries', 'identityRegistry', 'packs', 'packWords'):
            self.assertEqual(before[field], after[field])
        # Rewriting raw bytes creates a different snapshot checksum. It must be
        # reflected in sources, rather than concealed to claim identical payloads.
        self.assertNotEqual(before['sources'][1]['snapshotSha256'], after['sources'][1]['snapshotSha256'])

    def test_ing04_checksum_and_missing_input(self):
        (self.fixtures / 'records.jsonl').write_bytes(b'changed')
        code, out = self.run_pipeline('changed')
        self.assertEqual(code, 1)
        self.assertFalse((out / 'manifest.json').exists())
        (self.fixtures / 'records.jsonl').unlink()
        self.assertEqual(self.run_pipeline('missing')[0], 1)

    def test_ing05_real_gate_blocks_before_parse_also_dry_run(self):
        self.config.update(fixtureOnly=False, sourceRegistry='docs/data/source-registry.json', inventory='docs/data/snapshot.json')
        self.config['sources'][0]['registryId'] = 'ngsl'
        with patch.object(ingest, 'audit_eligible'), patch.object(ingest, 'verify_snapshot'), patch.object(ingest, 'authorize', side_effect=ValueError('hold')) as auth:
            target = self.root / 'docs/data'
            target.mkdir(parents=True)
            (target / 'source-registry.json').write_text('{}')
            (target / 'snapshot.json').write_text('{}')
            for dry_run in (False, True):
                code, out = self.run_pipeline(str(dry_run), fixture=False, dry_run=dry_run)
                self.assertEqual(code, 1)
                self.assertEqual(self.report(out)['counts']['read'], 0)
            self.assertEqual(auth.call_count, 2)
        with patch.object(ingest, 'audit_eligible', side_effect=ValueError('inventory failure')):
            self.assertEqual(self.run_pipeline('inventory', fixture=False)[0], 1)

    def test_ing01_production_gate_integration_with_temporary_synthetic_approval(self):
        helper = test_source_gate.SourceGateTest()
        helper.setUp()
        self.addCleanup(helper.doCleanups)
        helper.root = self.root
        first = helper.approved()
        second = next(s for s in helper.document['sources'] if s['id'] == 'tsl')
        for key in ('decision', 'pipelineEligible', 'packagingStatus', 'blockers', 'review', 'snapshot'):
            second[key] = copy.deepcopy(first[key])
        for source, binding in zip((first, second), self.config['sources']):
            artifact = next(a for a in source['snapshot']['artifacts'] if a['role'] == 'data')
            raw = (self.root / binding['artifact']).read_bytes()
            artifact.update(path=binding['artifact'], bytes=len(raw), sha256=ingest.digest(raw))
            source['review']['snapshotSha256'] = snapshot_content_digest(source['snapshot'], source)
            binding.update(registryId=source['id'], snapshotId='test-only', upstreamVersion='test')
        self.save('registry.json', helper.document)
        freeze(helper.document, self.fixtures / 'inventory.json', self.root)
        self.config.update(fixtureOnly=False, sourceRegistry=self.relative + '/registry.json',
                           inventory=self.relative + '/inventory.json')
        code, out = self.run_pipeline('approved-test-path', fixture=False)
        self.assertEqual(code, 0)
        sources = ingest.contract.load_json(out / 'canonical-catalog.json')['sources']
        self.assertEqual({s['approvedBy'] for s in sources}, {'synthetic-test-reviewer'})
        self.assertTrue(all(s['attributionText'] == 'Synthetic test attribution' for s in sources))
        first['review']['snapshotSha256'] = '0' * 64
        self.save('registry.json', helper.document)
        # Re-freezing does not confer approval; digest binding still blocks it.
        self.assertEqual(self.run_pipeline('bad-binding', fixture=False)[0], 1)

    def test_ing06_reasons(self):
        mutations = [
            ('UNSUPPORTED_POS', lambda r: r.update(pos='unknown')),
            ('MISSING_FIELD', lambda r: r.pop('meaningVi')),
            ('UNRESOLVED_SENSE', lambda r: r.update(senseIndex=99)),
            ('MISSING_PROVENANCE', lambda r: r.update(provenance=[])),
            ('INVALID_SOURCE_REFERENCE', lambda r: r.update(sourceId='unknown')),
            ('CANONICAL_VIOLATION', lambda r: r.update(exampleEn='bad\u200btext')),
        ]
        for reason, mutate in mutations:
            with self.subTest(reason=reason):
                rows = copy.deepcopy(self.rows)
                mutate(rows[0])
                self.records(rows)
                code, out = self.run_pipeline(reason)
                self.assertEqual(code, 1)
                self.assertIn(reason, [r['reason'] for r in self.report(out)['rejects']])
                self.assertEqual(self.report(out)['counts']['rejected'], 1)
                self.assertFalse((out / 'canonical-catalog.json').exists())
        self.records(raw=b'not json\n')
        _, out = self.run_pipeline('parse')
        self.assertEqual(self.report(out)['rejects'][0]['reason'], 'INVALID_PARSE')
        self.records(raw=b'\xff')
        code, out = self.run_pipeline('encoding')
        self.assertEqual(code, 1)
        self.assertIn('utf-8', (out / 'failure.json').read_text())

    def test_ing08_wording_and_normalization(self):
        _, first = self.run_pipeline('first')
        self.rows[0]['meaningVi'] = '  cuốn\u00a0sách sửa  '
        self.rows[0]['lemma'] = '  book\t'
        self.rows[0]['pos'] = 'n.'
        self.records()
        code, out = self.run_pipeline('wording')
        self.assertEqual(code, 0)
        before = ingest.contract.load_json(first / 'canonical-catalog.json')
        after = ingest.contract.load_json(out / 'canonical-catalog.json')
        self.assertEqual([e['id'] for e in before['entries']], [e['id'] for e in after['entries']])
        self.assertEqual(before['identityRegistry'], after['identityRegistry'])
        self.assertTrue((out / 'normalization.json').stat().st_size > 4)

    def test_ing09_conflicts_quarantine_all_and_retired_preserved(self):
        self.records(self.rows + [self.rows[0]])
        _, out = self.run_pipeline('duplicate')
        rejects = self.report(out)['rejects']
        self.assertEqual(sum(r['reason'] == 'IDENTITY_CONFLICT' for r in rejects), 2)
        self.records(self.rows)
        identities = ingest.contract.load_json(self.fixtures / 'identities.json')
        retired = dict(identities[0], identityLemma='retired', status='retired')
        retired['id'] = ingest.contract.stable_id('retired', retired['pos'], retired['senseIndex'])
        retired['fullSha256'] = ingest.contract.identity_hash('retired', retired['pos'], retired['senseIndex'])
        identities.append(retired)
        self.save('identities.json', identities)
        code, out = self.run_pipeline('retired')
        self.assertEqual(code, 0)
        self.assertIn(retired, ingest.contract.load_json(out / 'canonical-catalog.json')['identityRegistry'])
        identities[0]['status'] = 'retired'
        self.save('identities.json', identities)
        _, out = self.run_pipeline('retired-used')
        self.assertIn('IDENTITY_CONFLICT', [r['reason'] for r in self.report(out)['rejects']])

    def test_ing09_collision_and_baseline_removal(self):
        identities = ingest.contract.load_json(self.fixtures / 'identities.json')
        self.save('identities.json', identities + [identities[0]])
        self.assertEqual(self.run_pipeline('collision')[0], 1)
        self.save('identities.json', identities)
        _, out = self.run_pipeline('baseline')
        baseline = ingest.contract.load_json(out / 'canonical-catalog.json')
        self.save('baseline.json', baseline)
        self.config['baseline'] = self.relative + '/baseline.json'
        self.rows[0]['meaningVi'] += ' changed'
        self.records()
        _, out = self.run_pipeline('drift')
        self.assertIn('CONTENT_VERSION_NOT_ADVANCED', (out / 'failure.json').read_text())
        self.rows.pop()
        self.records()
        _, out = self.run_pipeline('removal')
        self.assertIn('REMOVED_PUBLISHED_ENTRY', (out / 'failure.json').read_text())

    def test_ing10_vectors_reference_helpers(self):
        vectors = ingest.contract.load_json(ingest.CONTRACT / 'fixtures/stable-id-vectors.json')
        self.assertTrue(vectors)
        # The full parameterized vectors run in test_contract.py; wrapper uses the
        # exact same implementation, including the ASCII/casefold distinction.
        self.assertEqual(ingest.contract.normalize_lemma(' Straße\u00a0'), 'straße')
        self.assertEqual(ingest.contract.meaning_key(' Straße '), 'strasse')
        self.assertEqual(ingest.contract.clean_text(' e\u0301\t x '), 'é x')

    def test_ing11_publish_failure_has_no_success_payload(self):
        real_write = ingest.write
        def fail(path, data):
            if path.name == 'normalization.json':
                raise OSError('simulated disk failure')
            real_write(path, data)
        with patch.object(ingest, 'write', side_effect=fail):
            code, out = self.run_pipeline()
        self.assertEqual(code, 1)
        self.assertFalse((out / 'canonical-catalog.json').exists())
        self.assertFalse((out / 'manifest.json').exists())

    def test_ing13_output_and_fixture_boundaries(self):
        self.run_pipeline()
        with self.assertRaisesRegex(ValueError, 'already exists'):
            self.run_pipeline()
        for path in ('app/src/main/assets/new', self.relative + '/new', 'docs/data/new'):
            with self.assertRaisesRegex(ValueError, 'Output must'):
                ingest.run(self.fixtures / 'config.json', self.root / path, fixture=True, root=self.root)
        self.config['sources'][0]['metadata']['approvalStatus'] = 'approved'
        self.assertEqual(self.run_pipeline('fake-approval')[0], 1)

    def test_ing14_fixture_release_blocked_dry_run_has_no_records(self):
        _, out = self.run_pipeline()
        errors = ingest.contract.validate(ingest.contract.load_json(out / 'canonical-catalog.json'), release=True)
        self.assertTrue(any(e.startswith('FIXTURE_NOT_RELEASE') for e in errors))
        code, out = self.run_pipeline('dry', dry_run=True)
        self.assertEqual(code, 0)
        self.assertFalse((out / 'manifest.json').exists())
        self.assertEqual(ingest.contract.load_json(out / 'check.json')['recordsParsed'], 0)


if __name__ == '__main__':
    unittest.main()
