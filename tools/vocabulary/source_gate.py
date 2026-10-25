"""Validate the research ledger; authorize an individual snapshot only explicitly."""
import argparse
import hashlib
import json
from datetime import datetime
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = {'ngsl', 'tsl', 'kaikki-viwiktionary', 'tatoeba-text', 'fvdp', 'tatoeba-audio'}
EXCLUDED = {'fvdp', 'tatoeba-audio'}
LICENSES = {
    'CC-BY-SA-4.0': 'https://creativecommons.org/licenses/by-sa/4.0/',
    'CC-BY-2.0-FR': 'https://creativecommons.org/licenses/by/2.0/fr/',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_registry(document):
    require(isinstance(document, dict), 'Registry must be an object')
    require(document.get('schemaVersion') == 1, 'Unsupported registry version')
    sources = document.get('sources', [])
    require(isinstance(sources, list) and all(isinstance(s, dict) for s in sources),
            'Sources must be an array of objects')
    require(len(sources) == 6 and {s['id'] for s in sources} == EXPECTED,
            'Registry must contain exactly the six researched sources')
    for source in sources:
        sid = source['id']
        for field in ('title', 'scope', 'reason', 'obligations', 'evidence'):
            require(bool(source.get(field)), f'{sid}: missing {field}')
        require(source.get('decision') in ('include', 'exclude'), f'{sid}: invalid decision')
        require(source.get('packagingStatus') in ('pending', 'approved', 'excluded'),
                f'{sid}: invalid packaging status')
        require(isinstance(source.get('blockers'), list), f'{sid}: missing blockers')
        require(type(source.get('pipelineEligible')) is bool, f'{sid}: missing eligibility')
        for evidence in source['evidence']:
            datetime.strptime(evidence['checkedOn'], '%Y-%m-%d')
            require(bool(evidence.get('note')), f'{sid}: missing evidence note')
            require(sid == 'fvdp' or str(evidence.get('url', '')).startswith('https://'),
                    f'{sid}: missing evidence URL')
        if sid in EXCLUDED:
            require(source['decision'] == 'exclude' and source['packagingStatus'] == 'excluded'
                    and source['pipelineEligible'] is False, f'{sid}: MVP exclusion violated')
        if source['pipelineEligible']:
            require(source['decision'] == 'include' and not source['blockers']
                    and source['packagingStatus'] == 'approved' and source.get('snapshot')
                    and source.get('licenseCode') in LICENSES,
                    f'{sid}: inconsistent eligibility')
    return sources


def verified_artifact(artifact, root):
    require(str(artifact.get('url', '')).startswith('https://'), 'Artifact URL required')
    relative = Path(artifact.get('path', ''))
    require(str(relative) != '.' and not relative.is_absolute(), 'Artifact path must be relative')
    path = (root / relative).resolve()
    require(path.is_relative_to(root.resolve()), 'Artifact escapes repository')
    require(path.is_file(), f'Missing artifact: {relative}')
    require(type(artifact.get('bytes')) is int and artifact['bytes'] > 0,
            'Artifact byte count required')
    require(path.stat().st_size == artifact['bytes'], f'Byte count mismatch: {relative}')
    require(re.fullmatch('[0-9a-f]{64}', artifact.get('sha256', '')), 'Invalid SHA-256')
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    require(digest == artifact['sha256'], f'Checksum mismatch: {relative}')


def authorize(document, source_id, root=ROOT):
    sources = validate_registry(document)
    source = next((s for s in sources if s['id'] == source_id), None)
    require(source is not None, 'Unknown source')
    require(source_id not in EXCLUDED and source['decision'] == 'include', 'Source excluded')
    require(source['pipelineEligible'] and source['packagingStatus'] == 'approved'
            and not source['blockers'], 'Source not approved: ' + '; '.join(source['blockers']))
    require(source.get('licenseCode') in LICENSES, 'Unsupported or unresolved license route')
    require(source.get('licenseUrl') == LICENSES[source['licenseCode']], 'License URL mismatch')
    review = source.get('review', {})
    require(bool(review.get('reviewer')) and bool(review.get('reviewedAt')), 'Review required')
    datetime.strptime(review['reviewedAt'], '%Y-%m-%d')
    snapshot = source.get('snapshot') or {}
    for field in ('id', 'upstreamVersion', 'upstreamDate', 'retrievedAt'):
        require(bool(snapshot.get(field)), f'Missing snapshot {field}')
    datetime.strptime(snapshot['upstreamDate'], '%Y-%m-%d')
    retrieved = datetime.fromisoformat(snapshot['retrievedAt'].replace('Z', '+00:00'))
    require(retrieved.utcoffset() is not None and retrieved.utcoffset().total_seconds() == 0,
            'retrievedAt must be UTC')
    artifacts = snapshot.get('artifacts', [])
    require(bool(artifacts), 'Snapshot artifacts required')
    roles = {artifact.get('role') for artifact in artifacts}
    required = {'data', 'license-evidence', 'attribution'}
    if source_id == 'tatoeba-text':
        required |= {'sentence-metadata', 'links'}
    require(required <= roles, 'Missing artifact roles: ' + ', '.join(sorted(required - roles)))
    require(len({a.get('path') for a in artifacts}) == len(artifacts), 'Duplicate artifact paths')
    for artifact in artifacts:
        verified_artifact(artifact, root)
    return source


def audit_eligible(document, root=ROOT):
    """CI must verify actual bytes for every source declared eligible."""
    sources = validate_registry(document)
    for source in sources:
        if source['pipelineEligible']:
            authorize(document, source['id'], root)
    return sources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--registry', type=Path, default=ROOT / 'docs/data/source-registry.json')
    parser.add_argument('--source', help='Require authorization of this source before ETL')
    args = parser.parse_args()
    try:
        document = json.loads(args.registry.read_text(encoding='utf-8'))
        sources = audit_eligible(document)
        if args.source:
            authorize(document, args.source)
            print(f'PASS: {args.source} snapshot verified; record-level checks still required')
        else:
            print(f'PASS: registry audit ({len(sources)} sources); use --source ID before import')
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f'BLOCKED: {error}\n')


if __name__ == '__main__':
    main()
