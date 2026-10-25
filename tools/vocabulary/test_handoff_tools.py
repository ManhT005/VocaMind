import copy
import base64
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import capture_sources
from commons_audio_metadata import fetch_metadata, metadata_url
from gate_sources import freeze, verify_snapshot
from source_gate import ROOT


class Response(io.BytesIO):
    url = 'https://example.invalid/words.csv'

    class headers:
        @staticmethod
        def get_content_type():
            return 'text/csv'


class HandoffToolsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.document = json.loads((ROOT / 'docs/data/source-registry.json').read_text('utf-8'))

    def test_frozen_inventory_verifies_and_refuses_overwrite(self):
        output = self.root / 'snapshot.json'
        snapshot = freeze(self.document, output)
        verify_snapshot(snapshot, self.document)
        with self.assertRaises(FileExistsError):
            freeze(self.document, output)

    def test_frozen_inventory_rejects_changed_registry(self):
        snapshot = freeze(self.document, self.root / 'snapshot.json')
        changed = copy.deepcopy(self.document)
        changed['sources'][0]['reason'] = 'Changed after review'
        with self.assertRaisesRegex(ValueError, 'digest mismatch'):
            verify_snapshot(snapshot, changed)

    def test_frozen_inventory_rejects_missing_artifact_listing(self):
        snapshot = freeze(self.document, self.root / 'snapshot.json')
        snapshot['sources']['ngsl'].pop()
        with self.assertRaisesRegex(ValueError, 'differs from registry'):
            verify_snapshot(snapshot, self.document)

    def test_acquisition_preserves_bytes_and_records_hash(self):
        data = b'word,rank\r\ncaf\xe9,1\r\n'
        with patch.object(capture_sources, 'ROOT', self.root), patch(
                'capture_sources.urllib.request.urlopen', return_value=Response(data)) as request:
            snapshot = capture_sources.capture(self.root / 'capture', {
                'tsl': [('data', 'words.csv', 'https://example.invalid/words.csv')]})
        self.assertEqual(1, request.call_count)
        artifact = snapshot['sources']['tsl'][0]
        self.assertEqual(data, (self.root / artifact['path']).read_bytes())
        self.assertEqual(hashlib.sha256(data).hexdigest(), artifact['sha256'])
        verify_snapshot(snapshot, root=self.root)

    def test_incomplete_acquisition_cannot_verify(self):
        with patch.object(capture_sources, 'ROOT', self.root), patch(
                'capture_sources.urllib.request.urlopen', side_effect=OSError('offline')):
            snapshot = capture_sources.capture(self.root / 'capture', {
                'tsl': [('data', 'words.csv', 'https://example.invalid/words.csv')]})
        with self.assertRaisesRegex(ValueError, 'unresolved errors'):
            verify_snapshot(snapshot, root=self.root)

    def test_metadata_queries_only_api_and_keeps_hold(self):
        payload = {'query': {'pages': [{'imageinfo': [{
            'url': 'https://upload.wikimedia.org/never-fetch.ogg', 'mime': 'audio/ogg',
            'descriptionurl': 'https://commons.wikimedia.org/wiki/File:Test.ogg',
            'extmetadata': {'Artist': {'value': 'Example'},
                            'LicenseShortName': {'value': 'CC BY 4.0'},
                            'LicenseUrl': {'value': 'https://creativecommons.org/licenses/by/4.0/'}}
        }]}]}}
        response = Response(json.dumps(payload).encode())
        response.url = 'https://commons.wikimedia.org/w/api.php'
        with patch('commons_audio_metadata.urllib.request.urlopen', return_value=response) as request:
            result = fetch_metadata('File:Test.ogg')
        self.assertEqual(1, request.call_count)
        self.assertTrue(request.call_args.args[0].full_url.startswith('https://commons.wikimedia.org/w/api.php?'))
        self.assertFalse(result['mediaFetched'])
        self.assertFalse(result['pipelineEligible'])
        self.assertEqual('hold', result['decision'])
        self.assertEqual(result['responseSha256'], hashlib.sha256(
            base64.b64decode(result['rawResponseBase64'])).hexdigest())

    def test_missing_metadata_is_rejected(self):
        response = Response(b'{"query":{"pages":[{"missing":true}]}}')
        response.url = 'https://commons.wikimedia.org/w/api.php'
        with patch('commons_audio_metadata.urllib.request.urlopen', return_value=response):
            with self.assertRaisesRegex(ValueError, 'unavailable'):
                fetch_metadata('File:Missing.ogg')

    def test_metadata_title_cannot_expand_query(self):
        for title in ('https://example.invalid/audio', 'File:a.ogg|File:b.ogg', 'File:'):
            with self.assertRaises(ValueError):
                metadata_url(title)


if __name__ == '__main__':
    unittest.main()
