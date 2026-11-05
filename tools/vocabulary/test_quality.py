"""QC-01..19, custody, strict publication and actual CLI integration."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import ingest
import quality


def reviewed(data):
    return {'schemaVersion': quality.VERSION, 'fixtureOnly': True,
            'datasetSha256': quality.entry_digest(data), 'records': [
        {'stableId': e['id'], 'entrySha256': quality.entry_digest(e),
         'reviewer': 'Synthetic fixture reviewer (not production approval)',
         'reviewedAt': '2026-11-05', 'decision': 'approved',
         **{key: True for key in quality.CHECKS}} for e in data['entries']]}


class QualityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        relative = Path('tools/vocabulary/fixtures/ingestion')
        shutil.copytree(ingest.ROOT / relative, self.root / relative)
        self.config = self.root / relative / 'verified/config.json'
        self.input = self.root / 'build/vocabulary-ingestion/input'
        self.assertEqual(ingest.run(self.config, self.input, fixture=True, root=self.root), 0)
        self.data = ingest.contract.load_json(self.input / 'canonical-catalog.json')
        self.reviews = reviewed(self.data)
        self.review_path = self.root / 'reviews.json'
        self.review_path.write_bytes(ingest.encoded(self.reviews))

    def evaluate(self, data=None, reviews=None):
        result = quality.evaluate(self.data if data is None else data,
                                  self.reviews if reviews is None else reviews, fixture=True)
        return result, [i for i in result[1] if i['severity'] == 'error']

    def run_qc(self, name='qc'):
        output = self.root / 'build/vocabulary-quality' / name
        code = quality.run(self.input, self.config, self.review_path, output, fixture=True, root=self.root)
        return code, output

    def test_qc01_04_12_19_valid_nullable_ipa_four_meanings_and_component_sources(self):
        result, errors = self.evaluate()
        self.assertEqual(errors, [])
        self.assertTrue(all(p['distinctMeanings'] == 4 for p in result[3]))
        self.assertIsNone(result[0]['entries'][0]['ipa'])
        self.assertEqual(len({r['sourceId'] for r in result[0]['entries'][0]['provenance']}), 2)

    def test_qc02_03_required_fields(self):
        for field in ('meaningVi', 'exampleEn', 'exampleVi'):
            with self.subTest(field=field):
                data = copy.deepcopy(self.data)
                data['entries'][0][field] = ' \t '
                _, errors = self.evaluate(data)
                self.assertTrue(any(field in str(i['field']) for i in errors))

    def test_qc05_all_duplicate_claimants_rejected(self):
        self.data['entries'].append(copy.deepcopy(self.data['entries'][0]))
        _, errors = self.evaluate()
        refs = {i['recordRef'] for i in errors if i['errorCode'] == 'DUPLICATE_ID'}
        self.assertIn('entries/0', refs)
        self.assertIn('entries/4', refs)

    def test_qc06_wording_does_not_change_identity_but_invalidates_review(self):
        old = self.data['entries'][0]['id']
        self.data['entries'][0]['meaningVi'] += ' mới'
        result, errors = self.evaluate()
        self.assertEqual(old, result[0]['entries'][0]['id'])
        self.assertTrue(any(i['errorCode'] == 'REVIEW_CONTENT_MISMATCH' for i in errors))
        self.assertFalse(any(i['errorCode'] == 'IDENTITY_HASH' for i in errors))
        self.assertEqual(self.evaluate(reviews=reviewed(self.data))[1], [])

    def test_qc07_15_unicode_normalization_idempotent(self):
        import unicodedata
        original = copy.deepcopy(self.data)
        for entry in self.data['entries']:
            entry['meaningVi'] = '  ' + unicodedata.normalize('NFD', entry['meaningVi']) + '\t '
        result, errors = self.evaluate()
        self.assertEqual(errors, [])
        self.assertEqual(original, result[0])
        self.assertTrue(result[2])
        self.assertEqual(quality.normalize(result[0]), (original, []))

    def test_qc08_unknown_pos_and_alias(self):
        data = copy.deepcopy(self.data)
        data['entries'][0]['pos'] = 'alien'
        self.assertTrue(self.evaluate(data)[1])
        data['entries'][0]['pos'] = 'n.'
        # Sorted first entry is a noun in this fixture.
        self.assertEqual(self.data['entries'][0]['pos'], 'noun')
        self.assertEqual(self.evaluate(data)[1], [])

    def test_qc09_provenance_missing_or_unknown(self):
        for refs in ([], [{'component': 'meaningVi', 'sourceId': 'unknown', 'locator': 'a#1'}]):
            data = copy.deepcopy(self.data)
            data['entries'][0]['provenance'] = refs
            _, errors = self.evaluate(data)
            self.assertTrue(any(i['errorCode'] in ('SCHEMA', 'UNKNOWN_SOURCE', 'PROVENANCE_COVERAGE') for i in errors))

    def test_qc10_license_string_is_not_approval(self):
        self.data['sources'][0]['approvalStatus'] = 'pending'
        _, errors = self.evaluate()
        self.assertTrue(any(i['errorCode'] == 'SOURCE_NOT_APPROVED' for i in errors))

    def test_qc11_13_three_meanings_fail_despite_case_nfc_whitespace(self):
        self.data['entries'][0]['meaningVi'] = '  NGÂN  HÀNG '
        self.data['entries'][1]['meaningVi'] = 'ngân hàng'
        self.assertEqual(quality.meaning_key('  NGÂN\t HÀNG '), quality.meaning_key('ngân hàng'))
        result, errors = self.evaluate(reviews=reviewed(quality.normalize(self.data)[0]))
        self.assertTrue(any(i['errorCode'] == 'PACK_QUIZ_MEANINGS' for i in errors))
        self.assertTrue(all(p['distinctMeanings'] <= 3 for p in result[3]))
        self.assertNotEqual(quality.meaning_key('ma'), quality.meaning_key('má'))
        self.assertNotEqual(quality.meaning_key('sách'), quality.meaning_key('sách.'))

    def test_qc14_deterministic_publication_and_envelope_compatibility(self):
        a_code, a = self.run_qc('a')
        b_code, b = self.run_qc('b')
        self.assertEqual((a_code, b_code), (0, 0))
        self.assertEqual({p.name for p in a.iterdir()}, {p.name for p in b.iterdir()})
        for path in a.iterdir():
            self.assertEqual(path.read_bytes(), (b / path.name).read_bytes(), path.name)
        data = ingest.contract.load_json(a / 'canonical-catalog.json')
        self.assertEqual(ingest.contract.validate(data), [])
        self.assertTrue(ingest.contract.validate(data, release=True))

    def test_qc16_fail_no_artifacts_and_old_output_refused(self):
        self.review_path.write_bytes(ingest.encoded({'schemaVersion': '1.0.0', 'fixtureOnly': True, 'records': []}))
        code, output = self.run_qc()
        self.assertEqual(code, 1)
        self.assertEqual({p.name for p in output.iterdir()}, {'qc_report.json', 'qc_report.md', 'rejected_records.jsonl'})
        report = ingest.contract.load_json(output / 'qc_report.json')
        self.assertEqual(report['counts']['accepted'], 0)
        self.assertEqual(report['counts']['rejected'], 4)
        with self.assertRaisesRegex(ValueError, 'new directory'):
            self.run_qc()

    def test_qc17_short_examples_and_multisense_are_warnings(self):
        self.data['entries'][0]['exampleEn'] = 'Read.'
        self.data['entries'][0]['meaningVi'] = 'cuốn sách; tài liệu'
        result, errors = self.evaluate(reviews=reviewed(self.data))
        self.assertEqual(errors, [])
        self.assertTrue(any(i['errorCode'] == 'EXAMPLE_LENGTH' for i in result[1]))
        self.assertTrue(any(i['errorCode'] == 'SUSPECT_MULTI_SENSE' for i in result[1]))

    def test_qc18_unknown_vocabulary_and_pack_references(self):
        for field, value, code in (('vocabularyId', '0000000000000000', 'UNKNOWN_VOCABULARY'),
                                   ('packId', 'pack_unknown', 'UNKNOWN_PACK')):
            data = copy.deepcopy(self.data)
            data['packWords'][0][field] = value
            self.assertTrue(any(i['errorCode'] == code for i in self.evaluate(data)[1]))

    def test_identity_not_silently_repaired_and_control_characters_blocked(self):
        data = copy.deepcopy(self.data)
        data['entries'][0]['id'] = '0000000000000000'
        result, errors = self.evaluate(data)
        self.assertEqual(result[0]['entries'][0]['id'], '0000000000000000')
        self.assertTrue(any(i['errorCode'] == 'IDENTITY_HASH' for i in errors))
        for value in ('bad\x00text', 'bad\u200btext', 'bad\ud800text'):
            data = copy.deepcopy(self.data)
            data['entries'][0]['meaningVi'] = value
            self.assertTrue(self.evaluate(data)[1])

    def test_changed_pack_context_requires_new_review(self):
        self.data['packWords'][0]['orderNo'] = 10
        self.assertTrue(any(i['errorCode'] == 'REVIEW_DATASET_MISMATCH' for i in self.evaluate()[1]))

    def test_reviews_are_required_dated_explicit_and_nonduplicated(self):
        for field, value in (('decision', 'pending'), ('singleSense', False),
                             ('reviewedAt', 'invalid'), ('reviewer', '   '), ('translationEquivalent', 1)):
            reviews = copy.deepcopy(self.reviews)
            reviews['records'][0][field] = value
            self.assertTrue(any(i['errorCode'] == 'CONTENT_REVIEW_REQUIRED' for i in self.evaluate(reviews=reviews)[1]))
        self.reviews['records'].append(copy.deepcopy(self.reviews['records'][0]))
        self.assertTrue(any(i['errorCode'] == 'DUPLICATE_REVIEW' for i in self.evaluate()[1]))

    def test_custody_detects_tampered_canonical_even_with_rewritten_hash(self):
        data = copy.deepcopy(self.data)
        data['entries'][0]['meaningVi'] += ' altered'
        path = self.input / 'canonical-catalog.json'
        path.write_bytes(ingest.encoded(data))
        manifest_path = self.input / 'manifest.json'
        manifest = ingest.contract.load_json(manifest_path)
        manifest['outputHashes'][path.name] = ingest.digest(path.read_bytes())
        manifest_path.write_bytes(ingest.encoded(manifest))
        code, out = self.run_qc()
        self.assertEqual(code, 1)
        self.assertFalse((out / 'manifest.json').exists())
        self.assertIn('verified replay', (out / 'qc_report.json').read_text('utf-8'))

    def test_custody_detects_changed_source_bytes_and_missing_manifest(self):
        source = self.config.with_name('records.jsonl')
        source.write_bytes(source.read_bytes() + b' ')
        self.assertEqual(self.run_qc('changed')[0], 1)
        (self.input / 'manifest.json').unlink()
        self.assertEqual(self.run_qc('missing')[0], 1)

    def test_cli_nonzero_and_synthetic_cannot_be_production(self):
        # Repository root is intentionally actual ROOT for real CLI preflight.
        script = ingest.ROOT / 'tools/vocabulary/quality.py'
        # Also works with this workspace's embedded Python (isolated sys.path).
        bootstrap = 'import sys, runpy; sys.path.insert(0, sys.argv[1]); runpy.run_path(sys.argv[1]+"/quality.py", run_name="__main__")'
        result = subprocess.run([sys.executable, '-c', bootstrap, str(script.parent),
                                 '--input', str(self.input), '--config', str(self.config),
                                 '--reviews', str(self.review_path), '--output', str(self.root / 'unsafe')],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        _, errors, _, _ = quality.evaluate(self.data, self.reviews, fixture=False)
        self.assertTrue(any(i['errorCode'] == 'SOURCE_NOT_APPROVED' for i in errors))

    def test_malformed_envelopes_report_instead_of_crashing(self):
        for data in (None, [], {}, {'entries': [None]}):
            result = quality.evaluate(data, {}, fixture=True)
            self.assertTrue(result[1])

    def test_malformed_manifest_fails_closed_with_report(self):
        for number, value in enumerate((None, [], 'invalid', {'status': 'success', 'fixtureOnly': True})):
            (self.input / 'manifest.json').write_bytes(ingest.encoded(value))
            code, out = self.run_qc(f'malformed-{number}')
            self.assertEqual(code, 1)
            self.assertFalse((out / 'manifest.json').exists())
            self.assertIn('CUSTODY_FAILURE', (out / 'qc_report.json').read_text('utf-8'))

    def test_publication_failure_cleans_payload_and_marks_report_failed(self):
        original = ingest.write
        def write(path, data):
            if path.name == 'manifest.json' and 'vocabulary-quality' in path.parts:
                path.write_bytes(b'{"incomplete":')
                raise OSError('simulated completion-marker write failure')
            return original(path, data)
        with patch.object(ingest, 'write', side_effect=write):
            code, out = self.run_qc('disk-failure')
        self.assertEqual(code, 1)
        for name in ('canonical-catalog.json', 'validated_vocab.jsonl', 'manifest.json'):
            self.assertFalse((out / name).exists())
        report = ingest.contract.load_json(out / 'qc_report.json')
        self.assertEqual(report['status'], 'failed')
        self.assertEqual(report['counts']['accepted'], 0)
        self.assertEqual(report['errorsByCode']['PUBLICATION_FAILURE'], 1)

    def test_consumer_requires_completion_mode_and_all_artifact_hashes(self):
        code, out = self.run_qc('consumer')
        self.assertEqual(code, 0)
        self.assertEqual(quality.load_validated(out, fixture=True), self.data)
        with self.assertRaisesRegex(ValueError, 'mode mismatch'):
            quality.load_validated(out)
        for name in ('canonical-catalog.json', 'validated_vocab.jsonl', 'qc_report.json',
                     'qc_report.md', 'rejected_records.jsonl'):
            path = out / name
            original = path.read_bytes()
            path.write_bytes(original + b' ')
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                quality.load_validated(out, fixture=True)
            path.write_bytes(original)
        (out / 'manifest.json').unlink()
        with self.assertRaises(OSError):
            quality.load_validated(out, fixture=True)

    def test_consumer_envelope_can_be_built_by_existing_catalog_consumer(self):
        # Synthetic temporary database only; never writes app assets or claims release QA.
        import importlib.util
        spec = importlib.util.spec_from_file_location('qc_catalog_consumer', ingest.ROOT / 'tools/catalog/build_fixture.py')
        builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(builder)
        code, out = self.run_qc('consumer-build')
        self.assertEqual(code, 0)
        data = quality.load_validated(out, fixture=True)
        metadata = builder.build(asset=self.root / 'consumer-fixture.db', fixture=out / 'canonical-catalog.json')
        self.assertEqual(metadata['uniqueVocabularyCount'], len(data['entries']))
        self.assertEqual(metadata['membershipCount'], len(data['packWords']))

    def test_production_authorization_path_with_isolated_synthetic_rights(self):
        # Tests real authorize()/inventory checks without modifying live source approvals.
        import test_source_gate
        from gate_sources import freeze
        from source_gate import snapshot_content_digest
        helper = test_source_gate.SourceGateTest()
        helper.setUp()
        self.addCleanup(helper.doCleanups)
        helper.root = self.root
        first = helper.approved()
        second = next(s for s in helper.document['sources'] if s['id'] == 'tsl')
        for key in ('decision', 'pipelineEligible', 'packagingStatus', 'blockers', 'review', 'snapshot'):
            second[key] = copy.deepcopy(first[key])
        config = ingest.contract.load_json(self.config)
        for source, binding in zip((first, second), config['sources']):
            artifact = next(a for a in source['snapshot']['artifacts'] if a['role'] == 'data')
            raw = (self.root / binding['artifact']).read_bytes()
            artifact.update(path=binding['artifact'], bytes=len(raw), sha256=ingest.digest(raw))
            source['review']['snapshotSha256'] = snapshot_content_digest(source['snapshot'], source)
            binding.update(registryId=source['id'], snapshotId='test-only', upstreamVersion='test')
        registry_path = self.config.with_name('registry.json')
        inventory_path = self.config.with_name('inventory.json')
        registry_path.write_bytes(ingest.encoded(helper.document))
        freeze(helper.document, inventory_path, self.root)
        config.update(fixtureOnly=False, sourceRegistry=registry_path.relative_to(self.root).as_posix(),
                      inventory=inventory_path.relative_to(self.root).as_posix())
        self.config.write_bytes(ingest.encoded(config))
        self.input = self.root / 'build/vocabulary-ingestion/production-codepath-test'
        self.assertEqual(ingest.run(self.config, self.input, fixture=False, root=self.root), 0)
        data = ingest.contract.load_json(self.input / 'canonical-catalog.json')
        reviews = reviewed(data)
        reviews['fixtureOnly'] = False
        self.review_path.write_bytes(ingest.encoded(reviews))
        out = self.root / 'build/vocabulary-quality/production-codepath-test'
        self.assertEqual(quality.run(self.input, self.config, self.review_path, out, root=self.root), 0)
        self.assertEqual(quality.load_validated(out), data)
        first['review']['snapshotSha256'] = '0' * 64
        registry_path.write_bytes(ingest.encoded(helper.document))
        broken = self.root / 'build/vocabulary-quality/revoked'
        self.assertEqual(quality.run(self.input, self.config, self.review_path, broken, root=self.root), 1)
        self.assertFalse((broken / 'manifest.json').exists())


if __name__ == '__main__':
    unittest.main()
