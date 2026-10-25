import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from source_gate import ROOT, audit_eligible, authorize, validate_registry


class SourceGateTest(unittest.TestCase):
    def setUp(self):
        self.document = json.loads((ROOT / 'docs/data/source-registry.json').read_text('utf-8'))
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def approved(self):
        source = self.document['sources'][0]
        source.update(pipelineEligible=True, packagingStatus='approved', blockers=[],
                      review={'reviewer': 'synthetic-test-reviewer', 'reviewedAt': '2026-10-25'})
        artifacts = []
        for role in ('data', 'license-evidence', 'attribution'):
            content = f'Synthetic test {role}'.encode()
            (self.root / role).write_bytes(content)
            artifacts.append(dict(role=role, path=role, bytes=len(content),
                                  url='https://example.invalid/test',
                                  sha256=hashlib.sha256(content).hexdigest()))
        source['snapshot'] = dict(id='test-only', upstreamVersion='test', upstreamDate='2026-10-25',
                                  retrievedAt='2026-10-25T00:00:00Z', artifacts=artifacts)
        return source

    def test_registry_structure(self):
        self.assertEqual(6, len(validate_registry(self.document)))

    def test_all_live_sources_blocked(self):
        for source in self.document['sources']:
            with self.subTest(source=source['id']), self.assertRaises(ValueError):
                authorize(self.document, source['id'], self.root)

    def test_exclusion_cannot_be_overridden(self):
        source = next(s for s in self.document['sources'] if s['id'] == 'fvdp')
        source['decision'] = 'include'
        with self.assertRaises(ValueError):
            validate_registry(self.document)

    def test_approved_synthetic_snapshot(self):
        self.approved()
        self.assertEqual('ngsl', authorize(self.document, 'ngsl', self.root)['id'])

    def test_corrupt_bytes_same_length(self):
        self.approved()
        path = self.root / 'data'
        path.write_bytes(b'X' * path.stat().st_size)
        with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
            authorize(self.document, 'ngsl', self.root)

    def test_missing_license_evidence(self):
        source = self.approved()
        source['snapshot']['artifacts'].pop(1)
        with self.assertRaisesRegex(ValueError, 'Missing artifact roles'):
            authorize(self.document, 'ngsl', self.root)

    def test_path_escape(self):
        source = self.approved()
        source['snapshot']['artifacts'][0]['path'] = '../outside'
        with self.assertRaisesRegex(ValueError, 'escapes repository'):
            authorize(self.document, 'ngsl', self.root)

    def test_missing_review_and_unknown_license(self):
        source = self.approved()
        del source['review']
        with self.assertRaisesRegex(ValueError, 'Review required'):
            authorize(self.document, 'ngsl', self.root)
        source['licenseCode'] = 'UNKNOWN'
        with self.assertRaises(ValueError):
            authorize(self.document, 'ngsl', self.root)

    def test_duplicate_registry_id(self):
        self.document['sources'][1] = copy.deepcopy(self.document['sources'][0])
        with self.assertRaises(ValueError):
            validate_registry(self.document)

    def test_ci_audit_checks_eligible_artifacts(self):
        self.approved()
        (self.root / 'data').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing artifact'):
            audit_eligible(self.document, self.root)

    def test_invalid_dates(self):
        source = self.approved()
        for section, field in ((source['review'], 'reviewedAt'),
                               (source['snapshot'], 'upstreamDate'),
                               (source['snapshot'], 'retrievedAt')):
            previous = section[field]
            section[field] = 'invalid'
            with self.subTest(field=field), self.assertRaises(ValueError):
                authorize(self.document, 'ngsl', self.root)
            section[field] = previous

    def test_tatoeba_requires_sentence_metadata_and_links(self):
        self.approved()
        source = self.document['sources'][0]
        target = next(s for s in self.document['sources'] if s['id'] == 'tatoeba-text')
        for field in ('pipelineEligible', 'packagingStatus', 'blockers', 'review', 'snapshot'):
            target[field] = copy.deepcopy(source[field])
        with self.assertRaisesRegex(ValueError, 'Missing artifact roles'):
            authorize(self.document, 'tatoeba-text', self.root)

    def test_invalid_registry_shape(self):
        for document in ([], {'schemaVersion': 1, 'sources': [None]}):
            with self.subTest(document=document), self.assertRaises(ValueError):
                validate_registry(document)


if __name__ == '__main__':
    unittest.main()
