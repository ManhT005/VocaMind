"""Frozen research inventory and source authorization entry point for handoff."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

from source_gate import ROOT, audit_eligible, authorize, require, verified_artifact


def registry_digest(document):
    # Bind semantic registry content without depending on Git line endings.
    content = json.dumps(document, sort_keys=True, ensure_ascii=True, separators=(',', ':'))
    return hashlib.sha256(content.encode('utf-8')).hexdigest()


def freeze(document, destination, root=ROOT):
    sources = audit_eligible(document, root)
    inventory = {s['id']: s.get('researchArtifacts', []) +
                 (s.get('snapshot') or {}).get('artifacts', []) for s in sources}
    snapshot = {'schemaVersion': 1, 'purpose': 'research-inventory',
                'capturedAt': datetime.now(timezone.utc).isoformat(),
                'registrySha256': registry_digest(document), 'sources': inventory, 'errors': []}
    with destination.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(snapshot, stream, indent=2)
        stream.write('\n')
    return snapshot


def verify_snapshot(snapshot, document=None, root=ROOT):
    require(snapshot.get('schemaVersion') == 1 and snapshot.get('purpose') == 'research-inventory',
            'Unsupported frozen inventory')
    require(snapshot.get('errors') == [], 'Capture has unresolved errors')
    sources = snapshot.get('sources')
    require(isinstance(sources, dict) and bool(sources), 'Snapshot sources required')
    require(any(sources.values()), 'Snapshot contains no artifacts')
    if 'registrySha256' in snapshot:
        require(document is not None and snapshot['registrySha256'] == registry_digest(document),
                'Frozen snapshot registry digest mismatch; review and freeze a new version')
        expected = {s['id']: s.get('researchArtifacts', []) +
                    (s.get('snapshot') or {}).get('artifacts', []) for s in document['sources']}
        require(sources == expected, 'Frozen inventory differs from registry artifacts')
    for artifacts in sources.values():
        require(isinstance(artifacts, list), 'Source artifacts must be an array')
        for artifact in artifacts:
            verified_artifact(artifact, root)
    return snapshot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--registry', type=Path, default=ROOT / 'docs/data/source-registry.json')
    parser.add_argument('--source', help='Authorize source for import; hold/candidate/exclude are blocked')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--freeze', type=Path, help='Create a new immutable snapshot.json; never overwrite')
    group.add_argument('--snapshot', type=Path, help='Verify a frozen research inventory')
    args = parser.parse_args()
    try:
        document = json.loads(args.registry.read_text('utf-8'))
        audit_eligible(document)
        if args.freeze:
            freeze(document, args.freeze)
        if args.snapshot:
            verify_snapshot(json.loads(args.snapshot.read_text('utf-8')), document)
        if args.source:
            authorize(document, args.source)
            print(f'PASS: {args.source} approved snapshot; downstream content QA still required')
        else:
            print('PASS: research inventory only; no source import or release authorized')
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f'BLOCKED: {error}\n')


if __name__ == '__main__':
    main()
