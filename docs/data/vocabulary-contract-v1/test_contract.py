"""Acceptance tests for contract boundaries, IDs and cross-record invariants."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import validate_contract as vc


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.data = vc.load_json(vc.ROOT / 'fixtures/valid/catalog.json')

    def codes(self, data=None, **kwargs):
        return {e.split(':', 1)[0].split(' ', 1)[0]
                for e in vc.validate(self.data if data is None else data, **kwargs)}

    def bump(self, data):
        data['contentVersion'] = '1.0.1'
        for e in data['entries']:
            e['contentVersion'] = '1.0.1'
        for p in data['packs']:
            p['version'] = '1.0.1'

    def test_valid_contract_fixture(self):
        self.assertEqual([], vc.validate(self.data))

    def test_null_ipa_is_valid(self):
        self.assertTrue(all(e['ipa'] is None for e in self.data['entries']))
        self.assertEqual([], vc.validate(self.data))

    def test_invalid_fixtures_have_declared_failure(self):
        directory = vc.ROOT / 'fixtures/invalid'
        for name, expected in vc.load_json(directory / 'expected-errors.json').items():
            with self.subTest(case=name):
                data = vc.load_json(directory / expected['file'])
                self.assertIn(expected['expectedCode'], self.codes(data))

    def test_stable_id_vectors(self):
        for vector in vc.load_json(vc.ROOT / 'fixtures/stable-id-vectors.json'):
            with self.subTest(lemma=vector['inputLemma'], pos=vector['pos'], sense=vector['senseIndex']):
                args = vector['inputLemma'], vector['pos'], vector['senseIndex']
                self.assertEqual(vector['identityKey'], vc.identity_key(*args))
                self.assertEqual(vector['fullSha256'], vc.identity_hash(*args))
                self.assertEqual(vector['id'], vc.stable_id(*args))

    def test_same_word_different_pos_or_sense_has_different_id(self):
        self.assertEqual(3, len({vc.stable_id('book', 'noun', 1),
                                vc.stable_id('book', 'verb', 1),
                                vc.stable_id('book', 'noun', 2)}))

    def test_unicode_composition_and_spaces_have_same_identity(self):
        self.assertEqual(vc.stable_id('café', 'noun', 1), vc.stable_id('cafe\u0301', 'noun', 1))
        self.assertEqual(vc.stable_id('check in', 'verb', 1),
                         vc.stable_id('  CHECK\u00a0IN  ', 'verb', 1))

    def test_identity_whitespace_set_is_explicit(self):
        self.assertEqual('a b', vc.clean_text('a\u0085\u2003b'))
        self.assertEqual('a\u001cb', vc.clean_text('a\u001cb'))

    def test_punctuation_and_accents_are_not_erased(self):
        self.assertNotEqual(vc.stable_id('café', 'noun', 1), vc.stable_id('cafe', 'noun', 1))
        self.assertNotEqual(vc.stable_id('check-in', 'noun', 1), vc.stable_id('check in', 'noun', 1))

    def test_invalid_sense_index(self):
        for value in [True, 0, -1, 1.5, '1']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                vc.stable_id('book', 'noun', value)

    def test_decimal_integer_tokens_return_contract_error(self):
        for collection, field in [('entries', 'senseIndex'), ('identityRegistry', 'senseIndex'),
                                   ('packs', 'orderNo'), ('packWords', 'orderNo')]:
            with self.subTest(collection=collection):
                data = copy.deepcopy(self.data)
                data[collection][0][field] = float(data[collection][0][field])
                self.assertIn('INTEGER_REPRESENTATION', self.codes(data))

    def test_unpaired_surrogate_returns_contract_error(self):
        for collection, field in [('identityRegistry', 'identityLemma'), ('entries', 'lemma')]:
            with self.subTest(collection=collection):
                data = copy.deepcopy(self.data)
                data[collection][0][field] = '\ud800'
                self.assertIn('CONTROL_CHARACTER', self.codes(data))

    def test_cli_malformed_canonical_values_are_exit_one(self):
        with tempfile.TemporaryDirectory() as directory:
            for kind in ['decimal', 'surrogate']:
                with self.subTest(kind=kind):
                    data = copy.deepcopy(self.data)
                    if kind == 'decimal':
                        data['identityRegistry'][0]['senseIndex'] = 1.0
                    else:
                        data['identityRegistry'][0]['identityLemma'] = '\ud800'
                    path = Path(directory) / (kind + '.json')
                    path.write_text(json.dumps(data), encoding='utf-8')
                    result = subprocess.run([sys.executable, str(vc.ROOT / 'validate_contract.py'),
                                             str(path)], capture_output=True, text=True)
                    self.assertEqual(1, result.returncode, result.stderr)
                    self.assertNotIn('Traceback', result.stderr)

    def test_display_capitalization_preserves_identity(self):
        self.data['entries'][0]['lemma'] = 'Book'
        self.assertEqual([], vc.validate(self.data))

    def test_wording_update_preserves_id_against_previous(self):
        previous = copy.deepcopy(self.data)
        ids = [e['id'] for e in self.data['entries']]
        self.data['entries'][0]['meaningVi'] = 'quyển sách'
        self.data['entries'][0]['exampleEn'] = 'I read the book every morning.'
        self.data['entries'][0]['exampleVi'] = 'Tôi đọc cuốn sách mỗi buổi sáng.'
        self.bump(self.data)
        self.assertEqual([], vc.validate(self.data, previous=previous))
        self.assertEqual(ids, [e['id'] for e in self.data['entries']])

    def test_reordering_does_not_reassign_ids(self):
        previous = copy.deepcopy(self.data)
        self.data['entries'].reverse()
        self.data['identityRegistry'].reverse()
        self.bump(self.data)
        self.assertEqual([], vc.validate(self.data, previous=previous))

    def test_unchanged_baseline_is_valid(self):
        self.assertEqual([], vc.validate(self.data, previous=copy.deepcopy(self.data)))

    def test_changed_export_requires_new_content_version(self):
        previous = copy.deepcopy(self.data)
        self.data['entries'][0]['meaningVi'] = 'quyển sách'
        self.assertIn('CONTENT_VERSION_NOT_ADVANCED', self.codes(previous=previous))

    def test_removed_published_entry_is_blocked(self):
        previous = copy.deepcopy(self.data)
        eid = self.data['entries'].pop()['id']
        self.data['identityRegistry'][-1]['status'] = 'retired'
        self.data['packWords'] = [m for m in self.data['packWords'] if m['vocabularyId'] != eid]
        self.bump(self.data)
        self.assertIn('REMOVED_PUBLISHED_ENTRY', self.codes(previous=previous))

    def test_changed_sense_index_is_blocked(self):
        self.data['entries'][0]['senseIndex'] = 2
        self.assertIn('IDENTITY_CHANGED', self.codes())

    def test_registry_hash_tampering_is_blocked(self):
        self.data['identityRegistry'][0]['fullSha256'] = '0' * 64
        self.assertIn('IDENTITY_HASH', self.codes())

    def test_removed_registry_is_blocked(self):
        previous = copy.deepcopy(self.data)
        self.data['identityRegistry'].pop()
        self.bump(self.data)
        self.assertIn('REMOVED_REGISTRY_ID', self.codes(previous=previous))

    def test_changed_source_snapshot_requires_new_source_id(self):
        previous = copy.deepcopy(self.data)
        self.data['sources'][0]['snapshotSha256'] = '0' * 64
        self.bump(self.data)
        self.assertIn('MUTATED_SOURCE_SNAPSHOT', self.codes(previous=previous))

    def test_multi_source_provenance_is_valid(self):
        other = copy.deepcopy(self.data['sources'][0])
        other['id'] = 'fixture-second-source'
        self.data['sources'].append(other)
        self.data['entries'][0]['provenance'][2]['sourceId'] = other['id']
        self.assertEqual([], vc.validate(self.data))

    def test_non_null_ipa_requires_provenance(self):
        self.data['entries'][0]['ipa'] = '/bʊk/'
        self.assertIn('PROVENANCE_COVERAGE', self.codes())
        self.data['entries'][0]['provenance'].append({
            'component': 'ipa', 'sourceId': self.data['sources'][0]['id'], 'locator': 'book/noun/1/ipa'
        })
        self.assertEqual([], vc.validate(self.data))

    def test_meaning_casefold_vectors(self):
        for vector in vc.load_json(vc.ROOT / 'fixtures/meaning-key-vectors.json'):
            with self.subTest(value=vector['input']):
                self.assertEqual(vector['expected'], vc.meaning_key(vector['input']))
        self.assertNotEqual(vc.meaning_key('ban'), vc.meaning_key('bàn'))

    def test_duplicate_meanings_fail_pack_quiz_gate(self):
        self.data['entries'][1]['meaningVi'] = self.data['entries'][0]['meaningVi'].upper()
        self.assertIn('PACK_QUIZ_MEANINGS', self.codes())

    def test_missing_source_does_not_pass_with_provenance_only(self):
        self.data['entries'][0]['provenance'][1]['sourceId'] = 'missing-source'
        self.assertIn('UNKNOWN_SOURCE', self.codes())

    def test_control_and_hidden_characters_are_rejected(self):
        self.data['entries'][0]['lemma'] = 'bo\u200bok'
        self.assertIn('INVISIBLE_CHARACTER', self.codes())
        self.data['entries'][0]['lemma'] = 'book\u001c'
        self.assertIn('CONTROL_CHARACTER', self.codes())

    def test_non_nfc_canonical_text_is_rejected(self):
        self.data['entries'][0]['meaningVi'] = 'cuo\u0302\u0301n sách'
        self.assertIn('NON_NFC', self.codes())

    def test_approved_source_needs_named_evidence(self):
        self.data['sources'][0]['approvalStatus'] = 'approved'
        self.assertIn('SCHEMA', self.codes())

    def test_fixture_cannot_pass_release_gate(self):
        codes = self.codes(release=True)
        self.assertTrue({'FIXTURE_NOT_RELEASE', 'RELEASE_UNIQUE_WORDS',
                         'RELEASE_PACK_QUOTA', 'RELEASE_SOURCE_NOT_APPROVED'} <= codes)

    def test_release_counts_unique_ids_not_memberships(self):
        self.assertEqual(4, len(self.data['entries']))
        self.assertEqual(16, len(self.data['packWords']))
        self.assertIn('RELEASE_UNIQUE_WORDS', self.codes(release=True))

    def test_release_metadata_positive_boundary_150_words(self):
        # Synthetic in-memory object tests counts/metadata ONLY. It is not saved
        # as an approved dataset and does not certify any real source or content.
        data = copy.deepcopy(self.data)
        data['fixtureOnly'] = False
        data['sources'][0].update(approvalStatus='approved',
                                  approvedBy='Synthetic unit-test reviewer',
                                  evidenceRef='synthetic-test-only/not-real-approval')
        data['entries'] = []
        data['identityRegistry'] = []
        data['packWords'] = []
        template = self.data['entries'][0]
        for i in range(150):
            lemma = f'fixtureword{i:03d}'
            entry = copy.deepcopy(template)
            entry.update(id=vc.stable_id(lemma, 'noun', 1), lemma=lemma,
                         normalizedLemma=lemma, meaningVi=f'nghĩa kiểm thử {i}')
            data['entries'].append(entry)
            data['identityRegistry'].append({
                'id': entry['id'], 'identityLemma': lemma, 'pos': 'noun',
                'senseIndex': 1, 'fullSha256': vc.identity_hash(lemma, 'noun', 1),
                'senseDefinition': entry['meaningVi'], 'status': 'active',
            })
        offset = 0
        for pid, minimum in vc.PACK_MINIMUMS.items():
            for order, entry in enumerate(data['entries'][offset:offset + minimum]):
                data['packWords'].append({'packId': pid, 'vocabularyId': entry['id'], 'orderNo': order})
            offset += minimum
        self.assertEqual([], vc.validate(data, release=True))
        # 149 unique entries cannot be hidden by adding extra pack memberships.
        removed = data['entries'].pop()['id']
        data['identityRegistry'].pop()
        data['packWords'] = [m for m in data['packWords'] if m['vocabularyId'] != removed]
        self.assertIn('RELEASE_UNIQUE_WORDS', self.codes(data, release=True))

    def test_unassigned_word_is_rejected(self):
        eid = self.data['entries'][0]['id']
        self.data['packWords'] = [m for m in self.data['packWords'] if m['vocabularyId'] != eid]
        self.assertIn('UNASSIGNED_ENTRY', self.codes())

    def test_retired_identity_cannot_be_reused(self):
        previous = copy.deepcopy(self.data)
        lemma = 'previously-retired'
        retired = {'id': vc.stable_id(lemma, 'noun', 1), 'identityLemma': lemma,
                   'pos': 'noun', 'senseIndex': 1,
                   'fullSha256': vc.identity_hash(lemma, 'noun', 1),
                   'senseDefinition': 'mục đã ngừng dùng', 'status': 'retired'}
        previous['identityRegistry'].append(retired)
        self.data['identityRegistry'].append({**retired, 'status': 'active'})
        self.bump(self.data)
        self.assertIn('REUSED_RETIRED_ID', self.codes(previous=previous))

    def test_removed_published_pack_is_blocked(self):
        previous = copy.deepcopy(self.data)
        pid = self.data['packs'].pop()['id']
        self.data['packWords'] = [m for m in self.data['packWords'] if m['packId'] != pid]
        self.bump(self.data)
        self.assertIn('REMOVED_PUBLISHED_PACK', self.codes(previous=previous))

    def test_invalid_previous_baseline_does_not_certify_upgrade(self):
        previous = copy.deepcopy(self.data)
        previous['entries'][0]['sourceId'] = 'missing-source'
        self.assertIn('INVALID_BASELINE', self.codes(previous=previous))

    def test_duplicate_json_keys_are_rejected(self):
        with self.assertRaises(ValueError):
            json.loads('{"id":"a","id":"b"}', object_pairs_hook=vc.no_duplicate_keys)

    def test_cli_valid_and_invalid_exit_codes(self):
        script = str(vc.ROOT / 'validate_contract.py')
        valid = str(vc.ROOT / 'fixtures/valid/catalog.json')
        result = subprocess.run([sys.executable, script, valid], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        result = subprocess.run([sys.executable, script, valid, '--release'], capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn('FIXTURE_NOT_RELEASE', result.stderr)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'broken.json'
            path.write_text('{', encoding='utf-8')
            result = subprocess.run([sys.executable, script, str(path)], capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('INPUT_ERROR', result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
