"""Offline synthetic tests for VOC-DATA-01 candidate staging."""
import csv
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from prepare_1000_candidates import prepare, load_ngsl, enrich_audio, enrich_examples


class TestPrepareCandidates(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        p = self.root / 'ngsl.csv'
        with p.open('w', newline='', encoding='utf-8') as f:
            w = csv.writer(f)
            w.writerow(['Lemma', 'SFI Rank'])
            w.writerows([['apple', 2], ['book', 1], ['tree', 3]])
        digest = hashlib.sha256(p.read_bytes()).hexdigest()
        registry = {'sources': [{'id': 'ngsl', 'decision': 'hold', 'licenseCode': 'CC-BY-SA-4.0',
                                 'snapshot': {'id': 'test-snapshot', 'artifacts': [
                                     {'role': 'data', 'path': 'ngsl.csv', 'sha256': digest}]}}]}
        self.registry = self.root / 'registry.json'
        self.registry.write_text(json.dumps(registry), encoding='utf-8')

    def tearDown(self):
        self.tmp.cleanup()

    def test_deterministic_research_output_is_not_release(self):
        entries, report = prepare(self.root, self.registry, 2, None, None, None)
        self.assertEqual([x['lemma'] for x in entries], ['book', 'apple'])
        self.assertFalse(report['releaseEligible'])
        self.assertEqual(report['approvedCompleteWords'], 0)
        self.assertIsNone(entries[0]['stableId'])

    def test_modified_source_is_rejected(self):
        (self.root / 'ngsl.csv').write_text('tampered', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            load_ngsl(self.root, self.registry, 2)

    def test_kaikki_gloss_and_ipa_are_candidates_only(self):
        k = self.root / 'kaikki.jsonl'
        k.write_text(json.dumps({'word':'book','lang_code':'en','pos':'noun',
                                 'senses':[{'glosses':['quyển sách']}],
                                 'sounds':[{'ipa':'/bʊk/'}]}, ensure_ascii=False) + '\n', encoding='utf-8')
        entries, report = prepare(self.root, self.registry, 1, k, None, None)
        self.assertEqual(entries[0]['meaningViCandidates'][0]['text'], 'quyển sách')
        self.assertEqual(entries[0]['ipaCandidates'][0]['ipa'], '/bʊk/')
        self.assertEqual(report['withMeaningCandidates'], 1)
        self.assertFalse(entries[0]['releaseEligible'])

    def test_audio_metadata_cannot_grant_release(self):
        path = self.root / 'audio.jsonl'
        sample = {'lemma':'book', 'title':'File:En-book.ogg','mediaMime':'audio/ogg',
                  'descriptionUrl':'https://commons.wikimedia.org/wiki/File:En-book.ogg',
                  'upstreamMediaSha1':'abc', 'metadata':{
                      'LicenseShortName':{'value':'CC BY-SA 4.0'},
                      'LicenseUrl':{'value':'https://creativecommons.org/licenses/by-sa/4.0/'},
                      'Artist':{'value':'Test artist'}}}
        path.write_text(json.dumps(sample)+'\n',encoding='utf-8')
        audio = enrich_audio(path, {'book'})['book'][0]
        self.assertFalse(audio['releaseEligible'])
        self.assertFalse(audio['mediaDownloaded'])

    def test_unlinked_examples_are_rejected(self):
        path = self.root / 'pairs.jsonl'
        path.write_text(json.dumps({'lemma':'book', 'linked':False,
                                    'en':{'id':1,'text':'A book','author':'a','license':'CC0'},
                                    'vi':{'id':2,'text':'Một quyển sách','author':'b','license':'CC0'}})+'\n',encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'requires linked'):
            enrich_examples(path, {'book'})


if __name__ == '__main__':
    unittest.main()
