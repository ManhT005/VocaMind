"""Validate the research ledger; authorize an individual snapshot only explicitly."""
import argparse
import hashlib
import json
from datetime import datetime
from pathlib import Path
import re
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = {'ngsl', 'tsl', 'kaikki-viwiktionary', 'tatoeba-text', 'fvdp', 'tatoeba-audio', 'commons-audio'}
EXCLUDED = {'fvdp', 'tatoeba-audio'}
RESEARCH_ONLY = {'commons-audio'}
ALIASES = {'ngsl-12': 'ngsl', 'tsl-12': 'tsl'}
LICENSES = {
    'CC-BY-SA-4.0': 'https://creativecommons.org/licenses/by-sa/4.0/',
    'CC-BY-2.0-FR': 'https://creativecommons.org/licenses/by/2.0/fr/',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def https_url(value):
    if not isinstance(value, str):
        return False
    parsed = urlsplit(value)
    return parsed.scheme == 'https' and bool(parsed.hostname) and not parsed.username and not parsed.password


def snapshot_content_digest(snapshot):
    """Bind approval to snapshot content, excluding its own approval document."""
    content = {key: value for key, value in snapshot.items() if key != 'artifacts'}
    content['artifacts'] = [a for a in snapshot.get('artifacts', []) if a.get('role') != 'approval']
    encoded = json.dumps(content, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('utf-8')
    return hashlib.sha256(encoded).hexdigest()


def validate_registry(document):
    require(isinstance(document, dict), 'Registry must be an object')
    require(document.get('schemaVersion') == 1, 'Unsupported registry version')
    sources = document.get('sources', [])
    require(isinstance(sources, list) and all(isinstance(s, dict) for s in sources),
            'Sources must be an array of objects')
    require(all(isinstance(s.get('id'), str) for s in sources), 'Source ID must be a string')
    require(len(sources) == len(EXPECTED) and {s['id'] for s in sources} == EXPECTED,
            'Registry must contain exactly the seven registered sources')
    for source in sources:
        sid = source['id']
        for field in ('title', 'scope', 'reason', 'obligations', 'evidence'):
            require(bool(source.get(field)), f'{sid}: missing {field}')
        require(source.get('decision') in ('include', 'exclude', 'hold'), f'{sid}: invalid decision')
        require(source.get('packagingStatus') in ('pending', 'approved', 'excluded'),
                f'{sid}: invalid packaging status')
        require(isinstance(source.get('blockers'), list), f'{sid}: missing blockers')
        require(type(source.get('pipelineEligible')) is bool, f'{sid}: missing eligibility')
        require(isinstance(source['evidence'], list) and
                all(isinstance(e, dict) for e in source['evidence']), f'{sid}: invalid evidence array')
        require(source.get('snapshot') is None or isinstance(source.get('snapshot'), dict),
                f'{sid}: snapshot must be an object or null')
        for artifacts in (source.get('researchArtifacts', []),
                          (source.get('snapshot') or {}).get('artifacts', [])):
            require(isinstance(artifacts, list) and all(isinstance(a, dict) for a in artifacts),
                    f'{sid}: artifacts must be an array of objects')
        for evidence in source['evidence']:
            datetime.strptime(evidence['checkedOn'], '%Y-%m-%d')
            require(bool(evidence.get('note')), f'{sid}: missing evidence note')
            require(sid == 'fvdp' or https_url(evidence.get('url')),
                    f'{sid}: missing evidence URL')
        if sid in EXCLUDED:
            require(source['decision'] == 'exclude' and source['packagingStatus'] == 'excluded'
                    and source['pipelineEligible'] is False, f'{sid}: MVP exclusion violated')
        if sid in RESEARCH_ONLY:
            require(source['decision'] == 'hold' and source['pipelineEligible'] is False,
                    f'{sid}: metadata research only; media import forbidden')
        if source['pipelineEligible']:
            require(source['decision'] == 'include' and not source['blockers']
                    and source['packagingStatus'] == 'approved' and source.get('snapshot')
                    and source.get('licenseCode') in LICENSES,
                    f'{sid}: inconsistent eligibility')
    return sources


def verified_artifact(artifact, root, hash_cache=None):
    require(isinstance(artifact, dict), 'Artifact must be an object')
    require(https_url(artifact.get('url')), 'Artifact HTTPS URL required')
    require(isinstance(artifact.get('path'), str), 'Artifact path required')
    relative = Path(artifact.get('path', ''))
    require(str(relative) != '.' and not relative.is_absolute(), 'Artifact path must be relative')
    path = (root / relative).resolve()
    require(path.is_relative_to(root.resolve()), 'Artifact escapes repository')
    require(path.is_file(), f'Missing artifact: {relative}')
    require(type(artifact.get('bytes')) is int and artifact['bytes'] > 0,
            'Artifact byte count required')
    before = path.stat()
    require(before.st_size == artifact['bytes'], f'Byte count mismatch: {relative}')
    require(isinstance(artifact.get('sha256'), str) and
            re.fullmatch('[0-9a-f]{64}', artifact['sha256']), 'Invalid SHA-256')
    fingerprint = (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns)
    cached = (hash_cache or {}).get(path)
    if cached and cached[0] == fingerprint:
        digest = cached[1]
    else:
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        after = path.stat()
        require(fingerprint == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns),
                f'Artifact changed during verification: {relative}')
        if hash_cache is not None:
            hash_cache[path] = (fingerprint, digest)
    require(digest == artifact['sha256'], f'Checksum mismatch: {relative}')


def authorize(document, source_id, root=ROOT, hash_cache=None):
    source_id = ALIASES.get(source_id, source_id)
    sources = validate_registry(document)
    source = next((s for s in sources if s['id'] == source_id), None)
    require(source is not None, 'Unknown source')
    require(source_id not in EXCLUDED | RESEARCH_ONLY and source['decision'] == 'include',
            'Source on hold or excluded; candidate is not include')
    require(source['pipelineEligible'] and source['packagingStatus'] == 'approved'
            and not source['blockers'], 'Source not approved: ' + '; '.join(source['blockers']))
    require(source.get('licenseCode') in LICENSES, 'Unsupported or unresolved license route')
    require(source.get('licenseUrl') == LICENSES[source['licenseCode']], 'License URL mismatch')
    review = source.get('review', {})
    require(isinstance(review, dict), 'Review must be an object')
    require(bool(review.get('reviewer')) and bool(review.get('reviewedAt')), 'Review required')
    require(review.get('role') in ('PO', 'Content Lead') and review.get('decision') == 'approved',
            'PO/Content Lead approval required by BR-12 and DEC-09')
    datetime.strptime(review['reviewedAt'], '%Y-%m-%d')
    snapshot = source.get('snapshot') or {}
    require(review.get('snapshotId') == snapshot.get('id'), 'Approval must match snapshot ID')
    for field in ('id', 'upstreamVersion', 'retrievedAt'):
        require(bool(snapshot.get(field)), f'Missing snapshot {field}')
    if snapshot.get('upstreamDate'):
        value = snapshot['upstreamDate']
        datetime.strptime(value, '%Y-%m' if len(value) == 7 else '%Y-%m-%d')
    else:
        require(bool(snapshot.get('upstreamDateUnknownReason')), 'Explain unknown upstream date')
    retrieved = datetime.fromisoformat(snapshot['retrievedAt'].replace('Z', '+00:00'))
    require(retrieved.utcoffset() is not None and retrieved.utcoffset().total_seconds() == 0,
            'retrievedAt must be UTC')
    artifacts = snapshot.get('artifacts', [])
    require(bool(artifacts), 'Snapshot artifacts required')
    roles = {artifact.get('role') for artifact in artifacts}
    required = {'data', 'license-evidence', 'license-text', 'attribution', 'approval'}
    if source_id == 'tatoeba-text':
        required |= {'sentence-metadata', 'links'}
    require(required <= roles, 'Missing artifact roles: ' + ', '.join(sorted(required - roles)))
    require(len({a.get('path') for a in artifacts}) == len(artifacts), 'Duplicate artifact paths')
    for artifact in artifacts:
        verified_artifact(artifact, root, hash_cache)
    require(review.get('snapshotSha256') == snapshot_content_digest(snapshot),
            'Approval content digest mismatch; snapshot changes require new review')
    return source


def audit_eligible(document, root=ROOT, hash_cache=None):
    """CI must verify actual bytes for every source declared eligible."""
    sources = validate_registry(document)
    if hash_cache is None:
        hash_cache = {}
    for source in sources:
        for artifact in source.get('researchArtifacts', []):
            verified_artifact(artifact, root, hash_cache)
        for artifact in (source.get('snapshot') or {}).get('artifacts', []):
            verified_artifact(artifact, root, hash_cache)
        if source['pipelineEligible']:
            authorize(document, source['id'], root, hash_cache)
    return sources


def status_report(document):
    sources = validate_registry(document)
    return {
        'purpose': 'research-status-not-release-approval',
        'sourceCount': len(sources),
        'eligibleSourceCount': sum(s['pipelineEligible'] for s in sources),
        'declaredRawRows': sum((s.get('snapshot') or {}).get('recordCount', 0) for s in sources),
        'sources': [{
            'id': s['id'], 'decision': s['decision'], 'pipelineEligible': s['pipelineEligible'],
            'snapshotId': (s.get('snapshot') or {}).get('id'),
            'snapshotContentSha256': snapshot_content_digest(s['snapshot']) if s.get('snapshot') else None,
            'declaredRawRows': (s.get('snapshot') or {}).get('recordCount', 0),
            'blockers': s['blockers'],
        } for s in sources],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--registry', type=Path, default=ROOT / 'docs/data/source-registry.json')
    parser.add_argument('--source', help='Require authorization of this source before ETL')
    parser.add_argument('--json', action='store_true', help='Print audited research status as JSON')
    args = parser.parse_args()
    try:
        document = json.loads(args.registry.read_text(encoding='utf-8'))
        hash_cache = {}
        sources = audit_eligible(document, hash_cache=hash_cache)
        if args.source:
            authorize(document, args.source, hash_cache=hash_cache)
        if args.json:
            print(json.dumps(status_report(document), indent=2))
        elif args.source:
            print(f'PASS: {args.source} snapshot verified; record-level checks still required')
        else:
            print(f'PASS: registry audit ({len(sources)} sources); use --source ID before import')
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f'BLOCKED: {error}\n')


if __name__ == '__main__':
    main()
