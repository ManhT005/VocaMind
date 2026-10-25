"""Frozen research inventory and source authorization entry point for handoff."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

from source_gate import ROOT, audit_eligible, authorize, require, verified_artifact, status_report


def registry_digest(document):
    # Bind semantic registry content without depending on Git line endings.
    content = json.dumps(document, sort_keys=True, ensure_ascii=True, separators=(',', ':'))
    return hashlib.sha256(content.encode('utf-8')).hexdigest()


def freeze(document, destination, root=ROOT, hash_cache=None):
    sources = audit_eligible(document, root, hash_cache)
    inventory = {s['id']: s.get('researchArtifacts', []) +
                 (s.get('snapshot') or {}).get('artifacts', []) for s in sources}
    snapshot = {'schemaVersion': 1, 'purpose': 'research-inventory',
                'capturedAt': datetime.now(timezone.utc).isoformat(),
                'registrySha256': registry_digest(document), 'sources': inventory, 'errors': []}
    with destination.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(snapshot, stream, indent=2)
        stream.write('\n')
    return snapshot


def verify_snapshot(snapshot, document=None, root=ROOT, hash_cache=None):
    require(isinstance(snapshot, dict), 'Frozen snapshot must be an object')
    require(snapshot.get('schemaVersion') == 1 and snapshot.get('purpose') == 'research-inventory',
            'Unsupported frozen inventory')
    require(snapshot.get('errors') == [], 'Capture has unresolved errors')
    sources = snapshot.get('sources')
    require(isinstance(sources, dict) and bool(sources), 'Snapshot sources required')
    require(any(sources.values()), 'Snapshot contains no artifacts')
    require(isinstance(snapshot.get('registrySha256'), str), 'Frozen inventory registrySha256 required')
    require(document is not None and snapshot['registrySha256'] == registry_digest(document),
            'Frozen snapshot registry digest mismatch; review and freeze a new version')
    expected = {s['id']: s.get('researchArtifacts', []) +
                (s.get('snapshot') or {}).get('artifacts', []) for s in document['sources']}
    require(sources == expected, 'Frozen inventory differs from registry artifacts')
    for artifacts in sources.values():
        require(isinstance(artifacts, list), 'Source artifacts must be an array')
        for artifact in artifacts:
            verified_artifact(artifact, root, hash_cache)
    return snapshot


def verify_capture_snapshot(snapshot, root=ROOT, hash_cache=None):
    """Check acquisition bytes only; never treat a capture as a frozen registry."""
    require(isinstance(snapshot, dict) and snapshot.get('schemaVersion') == 1 and
            snapshot.get('purpose') == 'source-capture', 'Unsupported acquisition manifest')
    require('registrySha256' not in snapshot, 'Acquisition manifest cannot claim registry binding')
    require(snapshot.get('errors') == [], 'Capture has unresolved errors')
    sources = snapshot.get('sources')
    require(isinstance(sources, dict) and bool(sources) and any(sources.values()),
            'Capture artifacts required')
    for artifacts in sources.values():
        require(isinstance(artifacts, list), 'Source artifacts must be an array')
        for artifact in artifacts:
            verified_artifact(artifact, root, hash_cache)
    return snapshot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--registry', type=Path, default=ROOT / 'docs/data/source-registry.json')
    parser.add_argument('--source', help='Authorize source for import; hold/candidate/exclude are blocked')
    parser.add_argument('--json', action='store_true', help='Print audited status and blockers as JSON')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--freeze', type=Path, help='Create a new immutable snapshot.json; never overwrite')
    group.add_argument('--snapshot', type=Path, help='Verify a frozen research inventory')
    group.add_argument('--capture-snapshot', type=Path, help='Verify acquisition bytes only, not registry binding')
    args = parser.parse_args()
    if args.capture_snapshot and args.source:
        parser.error('--capture-snapshot cannot authorize a source; use a frozen --snapshot')
    try:
        document = json.loads(args.registry.read_text('utf-8'))
        hash_cache = {}
        audit_eligible(document, hash_cache=hash_cache)
        if args.freeze:
            freeze(document, args.freeze, hash_cache=hash_cache)
        if args.snapshot:
            verify_snapshot(json.loads(args.snapshot.read_text('utf-8')), document, hash_cache=hash_cache)
        if args.capture_snapshot:
            verify_capture_snapshot(json.loads(args.capture_snapshot.read_text('utf-8')), hash_cache=hash_cache)
        if args.source:
            authorize(document, args.source, hash_cache=hash_cache)
        if args.json:
            print(json.dumps(status_report(document), indent=2))
        elif args.source:
            print(f'PASS: {args.source} approved snapshot; downstream content QA still required')
        else:
            print('PASS: research inventory only; no source import or release authorized')
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f'BLOCKED: {error}\n')


if __name__ == '__main__':
    main()
