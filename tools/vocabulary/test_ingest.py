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

    def enable_reference_checks(self):
        self.config['sources'][0]['referenceAdapter'] = 'reviewed-jsonl-v1'
        binding = self.config['sources'][1]
        binding['referenceAdapter'] = 'component-jsonl-v1'
        raw = b''.join(json.dumps({'key': r['locator'], 'component': 'examplePair',
                                  'exampleEn': r['exampleEn'], 'exampleVi': r['exampleVi']},
                                 ensure_ascii=False).encode('utf-8') + b'\n' for r in self.rows)
        (self.root / binding['artifact']).write_bytes(raw)
        binding['sha256'] = binding['metadata']['snapshotSha256'] = ingest.digest(raw)

    def test_verified_reference_keys_content_and_shared_locators(self):
        self.enable_reference_checks()
        self.assertEqual(self.run_pipeline('verified')[0], 0)
        self.rows[0]['provenance'][2]['locator'] += '-missing'
        self.rows[1]['exampleEn'] = 'Altered text with valid source key.'
        self.records()
        code, out = self.run_pipeline('bad-reference')
        self.assertEqual(code, 1)
        report = self.report(out)
        self.assertEqual(report['counts'], {'read': 4, 'accepted': 2, 'rejected': 2})
        self.assertEqual({r['reason'] for r in report['rejects']},
                         {'PROVENANCE_KEY_NOT_FOUND', 'PROVENANCE_CONTENT_MISMATCH'})
        self.assertTrue(all(r['field'] == 'provenance[2].locator' and r['stage'] == 'mapping'
                            and '#line=' in r['locator'] for r in report['rejects']))
        self.assertFalse((out / 'manifest.json').exists())

    def test_reference_adapters_only_after_all_gates(self):
        self.enable_reference_checks()
        self.config['fixtureOnly'] = False
        self.config.update(sourceRegistry=self.relative + '/registry.json',
                           inventory=self.relative + '/inventory.json')
        self.save('registry.json', {})
        self.save('inventory.json', {})
        with patch.object(ingest, 'audit_eligible'), patch.object(ingest, 'verify_snapshot'), \
                patch.object(ingest, 'authorize', side_effect=ValueError('hold')), \
                patch.object(ingest, 'read_index', side_effect=AssertionError('Must not parse')) as reader:
            self.assertEqual(self.run_pipeline('hold-reference', fixture=False)[0], 1)
            reader.assert_not_called()

    def test_native_reference_adapters(self):
        from ingestion_refs import read_index, matches
        for adapter, raw in (
                ('ngsl-csv-v1', b'Lemma,SFI Rank\nbook,1\n'),
                ('tsl-csv-v1', 'Word,TSL Rank\ncaf\u00e9,1\n'.encode('cp1252'))):
            index = read_index(raw, adapter, ingest.contract)
            self.assertEqual(set(index), {('rank=1', 'lexical')})
            self.assertNotIn(('rank=1', 'meaningVi'), index)
        raw = json.dumps({'word': 'book', 'pos': 'noun', 'lang_code': 'en',
                          'senses': [{'glosses': ['cuốn sách']}],
                          'sounds': [{'ipa': '/bʊk/'}]}, ensure_ascii=False).encode()
        index = read_index(raw, 'kaikki-jsonl-v1', ingest.contract)
        self.assertTrue(matches(index[('line=1;sense=1', 'meaningVi')][0], self.rows[0],
                                ingest.contract, ingest.POS))
        self.assertIn(('line=1;sound=1', 'ipa'), index)
        with self.assertRaises(ValueError):
            read_index(b'Lemma,SFI Rank\nbook,1\nbook,1\n', 'ngsl-csv-v1', ingest.contract)
        with self.assertRaises(ValueError):
            read_index(b'wrong,headers\nbook,1\n', 'ngsl-csv-v1', ingest.contract)

    def test_native_components_through_pipeline(self):
        self.enable_reference_checks()
        for sid, filename, adapter, raw in (
                ('fixture-ngsl-v1', 'ngsl.csv', 'ngsl-csv-v1',
                 b'Lemma,SFI Rank\nbook,1\nbank,2\nairport,3\n'),
                ('fixture-kaikki-v1', 'kaikki.jsonl', 'kaikki-jsonl-v1',
                 b''.join(json.dumps({'word': r['lemma'], 'pos': r['pos'], 'lang_code': 'en',
                                      'senses': [{'glosses': [r['meaningVi']]}]},
                                     ensure_ascii=False).encode() + b'\n' for r in self.rows))):
            binding = copy.deepcopy(self.config['sources'][0])
            binding.update(canonicalId=sid, artifact=self.relative + '/' + filename,
                           referenceAdapter=adapter, sha256=ingest.digest(raw))
            binding['metadata'].update(id=sid, snapshotSha256=ingest.digest(raw))
            (self.fixtures / filename).write_bytes(raw)
            self.config['sources'].append(binding)
        for number, row in enumerate(self.rows, 1):
            row['sourceId'] = 'fixture-ngsl-v1'
            row['provenance'][0].update(sourceId='fixture-ngsl-v1',
                locator=self.relative + '/ngsl.csv#rank=' + str({'book': 1, 'bank': 2, 'airport': 3}[row['lemma']]))
            row['provenance'][1].update(sourceId='fixture-kaikki-v1',
                locator=self.relative + f'/kaikki.jsonl#line={number};sense=1')
        self.records()
        code, out = self.run_pipeline('native-components')
        self.assertEqual(code, 0)
        manifest = ingest.contract.load_json(out / 'manifest.json')
        self.assertTrue(manifest['provenanceContentVerified'])
        self.assertEqual(manifest['counts'], {'read': 4, 'accepted': 4, 'rejected': 0})
        # One wordlist rank can back more than one reviewed sense/POS.
        self.assertEqual(self.rows[0]['provenance'][0], self.rows[1]['provenance'][0])

    def test_tatoeba_reference_pair_ids_credit_and_links(self):
        from ingestion_refs import read_index
        pair = {'linked': True,
                'en': {'id': 1, 'text': 'Book a room.', 'author': 'Fixture EN', 'license': 'FIXTURE-ONLY'},
                'vi': {'id': 2, 'text': 'Đặt phòng.', 'author': 'Fixture VI', 'license': 'FIXTURE-ONLY'}}
        def read(value):
            return read_index(json.dumps(value, ensure_ascii=False).encode(),
                              'tatoeba-linked-jsonl-v1', ingest.contract)
        self.assertEqual(read(pair)[('en=1;vi=2', 'examplePair')],
                         [{'exampleEn': 'Book a room.', 'exampleVi': 'Đặt phòng.'}])
        for target, field, value in ((None, 'linked', False), ('vi', 'author', ''),
                                     ('en', 'license', None), ('en', 'id', True)):
            bad = copy.deepcopy(pair)
            (bad if target is None else bad[target])[field] = value
            with self.assertRaises(ValueError):
                read(bad)

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
        self.enable_reference_checks()
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
        adapter = self.config['sources'][1].pop('referenceAdapter')
        code, out = self.run_pipeline('missing-production-reference-adapter', fixture=False)
        self.assertEqual(code, 1)
        self.assertEqual(self.report(out)['counts']['read'], 0)
        self.assertEqual(self.report(out)['sourceFailures'][0]['stage'], 'references')
        self.config['sources'][1]['referenceAdapter'] = adapter
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

    def test_review_r1_provenance_requires_nonblank_record_key(self):
        for number, suffix in enumerate(('', '    ', '\u00a0\t')):
            with self.subTest(suffix=suffix):
                rows = copy.deepcopy(self.rows)
                ref = rows[0]['provenance'][0]
                ref['locator'] = ref['locator'].split('#', 1)[0] + '#' + suffix
                self.records(rows)
                code, out = self.run_pipeline(f'empty-key-{number}')
                self.assertEqual(code, 1)
                report = self.report(out)
                rejection = next(r for r in report['rejects'] if r['field'] == 'provenance[0].locator')
                self.assertEqual(rejection['locator'], self.config['input'] + '#line=1')
                self.assertEqual(rejection['stage'], 'mapping')
                self.assertEqual(rejection['reason'], 'MISSING_PROVENANCE')
                self.assertEqual(report['counts'], {'read': 4, 'accepted': 3, 'rejected': 1})
                self.assertFalse((out / 'canonical-catalog.json').exists())
                self.assertFalse((out / 'manifest.json').exists())

    def test_review_r1_valid_shared_component_locators_remain_supported(self):
        # Multiple senses may cite the same reviewed component record. There is
        # deliberately no global locator uniqueness requirement.
        for row in self.rows:
            for ref in row['provenance']:
                ref['locator'] = ref['locator'].split('#', 1)[0] + '#shared-reviewed-key'
        self.records()
        code, out = self.run_pipeline('shared-keys')
        self.assertEqual(code, 0)
        self.assertEqual(self.report(out)['counts'], {'read': 4, 'accepted': 4, 'rejected': 0})

    def test_review_r2_bad_types_are_record_rejects_and_scanning_continues(self):
        cases = (
            ('sourceId', lambda r: r.update(sourceId=[])),
            ('provenance[0].component', lambda r: r['provenance'][0].update(component=[])),
            ('provenance[0].sourceId', lambda r: r['provenance'][0].update(sourceId=[])),
        )
        for number, (field, mutate) in enumerate(cases):
            with self.subTest(field=field):
                rows = copy.deepcopy(self.rows)
                mutate(rows[0])
                rows[0]['locator'] = []
                # A later bad row must be diagnosed too; remaining valid rows
                # still count as accepted although strict batch prevents publish.
                rows[2]['pos'] = 'unsupported'
                self.records(rows)
                code, out = self.run_pipeline(f'bad-type-{number}')
                self.assertEqual(code, 1)
                report = self.report(out)
                self.assertEqual(report['counts'], {'read': 4, 'accepted': 2, 'rejected': 2})
                self.assertEqual(report['sourceFailures'], [])
                rejection = next(r for r in report['rejects'] if r['field'] == field)
                self.assertEqual(rejection['reason'], 'INVALID_FIELD_TYPE')
                self.assertEqual(rejection['locator'], self.config['input'] + '#line=1')
                self.assertEqual(rejection['stage'], 'mapping')
                self.assertTrue(any(r['reason'] == 'UNSUPPORTED_POS' for r in report['rejects']))
                self.assertEqual(len({r['locator'] for r in report['rejects']}), 2)
                self.assertFalse((out / 'canonical-catalog.json').exists())
                self.assertFalse((out / 'manifest.json').exists())

    def test_review_r2_invalid_row_locators_use_file_line_fallback(self):
        for number, value in enumerate((None, '', '   ', {}, [], 12)):
            with self.subTest(locator=value):
                rows = copy.deepcopy(self.rows)
                rows[0].update(locator=value, sourceId=[])
                self.records(rows)
                code, out = self.run_pipeline(f'bad-locator-{number}')
                self.assertEqual(code, 1)
                report = self.report(out)
                self.assertEqual(report['counts'], {'read': 4, 'accepted': 3, 'rejected': 1})
                self.assertTrue(report['rejects'])
                self.assertTrue(all(r['locator'] == self.config['input'] + '#line=1'
                                    and r['field'] and r['reason'] and r['stage'] == 'mapping'
                                    for r in report['rejects']))

    def test_review_r2_multiple_bad_records_have_reconcilable_counts(self):
        self.rows[0]['sourceId'] = []
        self.rows[1]['provenance'][0]['component'] = []
        self.rows[2]['provenance'][0]['sourceId'] = []
        self.records()
        code, out = self.run_pipeline('three-bad-records')
        self.assertEqual(code, 1)
        report = self.report(out)
        self.assertEqual(report['counts'], {'read': 4, 'accepted': 1, 'rejected': 3})
        self.assertEqual(report['sourceFailures'], [])
        self.assertEqual({r['locator'] for r in report['rejects']}, {r['locator'] for r in self.rows[:3]})
        self.assertEqual({r['field'] for r in report['rejects'] if r['reason'] == 'INVALID_FIELD_TYPE'},
                         {'sourceId', 'provenance[0].component', 'provenance[0].sourceId'})
        self.assertTrue(all(r['stage'] == 'mapping' for r in report['rejects']))
        self.assertFalse((out / 'canonical-catalog.json').exists())
        self.assertFalse((out / 'manifest.json').exists())

    def test_review_r3_literal_and_escaped_unicode_whitespace(self):
        code, baseline_out = self.run_pipeline('unicode-baseline')
        self.assertEqual(code, 0)
        baseline = ingest.contract.load_json(baseline_out / 'canonical-catalog.json')
        for character in ('\u0085', '\u2028', '\u2029'):
            rows = copy.deepcopy(self.rows)
            rows[0]['exampleEn'] = rows[0]['exampleEn'].replace(' ', character, 1)
            for escaped in (False, True):
                with self.subTest(character=hex(ord(character)), escaped=escaped):
                    raw = ''.join(json.dumps(row, ensure_ascii=escaped) + '\n' for row in rows).encode('utf-8')
                    self.records(raw=raw)
                    code, out = self.run_pipeline(f'unicode-{ord(character)}-{escaped}')
                    self.assertEqual(code, 0)
                    self.assertEqual(self.report(out)['counts'], {'read': 4, 'accepted': 4, 'rejected': 0})
                    data = ingest.contract.load_json(out / 'canonical-catalog.json')
                    self.assertEqual(data['entries'], baseline['entries'])
                    self.assertEqual(data['identityRegistry'], baseline['identityRegistry'])
                    self.assertEqual(ingest.contract.validate(data), [])
                    self.assertTrue(any(r['field'] == 'exampleEn' for r in
                                        ingest.contract.load_json(out / 'normalization.json')))

    def test_review_r3_physical_lines_and_blank_line_policy(self):
        lines = [json.dumps(row, ensure_ascii=False) for row in self.rows]
        for separator in ('\n', '\r\n'):
            for final_newline in (False, True):
                with self.subTest(separator=repr(separator), final_newline=final_newline):
                    raw = separator.join(lines) + (separator if final_newline else '')
                    self.records(raw=raw.encode('utf-8'))
                    code, out = self.run_pipeline(f'lines-{len(separator)}-{final_newline}')
                    self.assertEqual(code, 0)
                    self.assertEqual(self.report(out)['counts'], {'read': 4, 'accepted': 4, 'rejected': 0})
        for number, (content, line_no) in enumerate((
                ('\n'.join(lines[:1] + [''] + lines[1:]) + '\n', 2),
                ('\r\n'.join(lines) + '\r\n\r\n', 5),
                ('\n'.join(lines) + '\n   ', 5))):
            with self.subTest(blank_line=line_no, number=number):
                self.records(raw=content.encode('utf-8'))
                code, out = self.run_pipeline(f'blank-line-{number}')
                self.assertEqual(code, 1)
                report = self.report(out)
                self.assertEqual(report['counts'], {'read': 5, 'accepted': 4, 'rejected': 1})
                self.assertEqual(len(report['rejects']), 1)
                self.assertEqual(report['rejects'][0]['reason'], 'INVALID_PARSE')
                self.assertEqual(report['rejects'][0]['locator'], self.config['input'] + f'#line={line_no}')
                self.assertFalse((out / 'canonical-catalog.json').exists())
                self.assertFalse((out / 'manifest.json').exists())

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
