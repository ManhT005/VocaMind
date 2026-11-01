import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from gate_sources import verify_snapshot
from source_gate import ROOT, audit_eligible, verified_artifact, validate_registry, status_report, authorize
import test_source_gate


class GateOptimizationTest(unittest.TestCase):
    def test_audit_and_frozen_check_hash_each_file_once(self):
        document = json.loads((ROOT / 'docs/data/source-registry.json').read_text('utf-8'))
        snapshot = json.loads((ROOT / 'docs/data/snapshot.json').read_text('utf-8'))
        cache = {}
        with patch('source_gate.hashlib.file_digest', wraps=hashlib.file_digest) as digest:
            audit_eligible(document, hash_cache=cache)
            verify_snapshot(snapshot, document, hash_cache=cache)
        paths = {a['path'] for artifacts in snapshot['sources'].values() for a in artifacts}
        self.assertEqual(len(paths), digest.call_count)
        self.assertEqual(14, digest.call_count)

    def test_cached_hash_rechecks_declared_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'data'
            path.write_bytes(b'abcd')
            artifact = dict(url='https://example.invalid/data', path='data', bytes=4,
                            sha256=hashlib.sha256(b'abcd').hexdigest())
            cache = {}
            verified_artifact(artifact, root, cache)
            artifact['sha256'] = '0' * 64
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                verified_artifact(artifact, root, cache)

    def test_same_size_rewrite_invalidates_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'data'
            path.write_bytes(b'abcd')
            artifact = dict(url='https://example.invalid/data', path='data', bytes=4,
                            sha256=hashlib.sha256(b'abcd').hexdigest())
            cache = {}
            verified_artifact(artifact, root, cache)
            path.write_bytes(b'wxyz')
            # Make the metadata change deterministic even on coarse timestamp filesystems.
            import os
            stamp = path.stat().st_mtime_ns + 1_000_000_000
            os.utime(path, ns=(stamp, stamp))
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                verified_artifact(artifact, root, cache)

    def test_malformed_structures_fail_with_validation_error(self):
        document = json.loads((ROOT / 'docs/data/source-registry.json').read_text('utf-8'))
        for field, value in (('evidence', 'invalid'), ('snapshot', []), ('researchArtifacts', [None])):
            original = document['sources'][0].get(field)
            document['sources'][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_registry(document)
            document['sources'][0][field] = original

    def test_report_distinguishes_raw_rows_and_eligibility(self):
        document = json.loads((ROOT / 'docs/data/source-registry.json').read_text('utf-8'))
        report = status_report(document)
        self.assertEqual((7, 4059, 0), (report['sourceCount'], report['declaredRawRows'],
                                       report['eligibleSourceCount']))

    def test_reused_snapshot_id_with_new_valid_bytes_needs_new_review(self):
        fixture = test_source_gate.SourceGateTest()
        fixture.setUp()
        try:
            source = fixture.approved()
            artifact = source['snapshot']['artifacts'][0]
            content = b'Updated but not approved data'
            (fixture.root / artifact['path']).write_bytes(content)
            artifact.update(bytes=len(content), sha256=hashlib.sha256(content).hexdigest())
            with self.assertRaisesRegex(ValueError, 'Approval content digest mismatch'):
                authorize(fixture.document, 'ngsl', fixture.root)
        finally:
            fixture.doCleanups()


if __name__ == '__main__':
    unittest.main()
