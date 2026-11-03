"""Local build-time ingestion; no downloads, registry allocation or asset writes."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path
import platform
from importlib.metadata import version as package_version
import sys
import unicodedata

from gate_sources import verify_snapshot
from source_gate import ROOT, ALIASES, audit_eligible, authorize, require

CONTRACT = ROOT / 'docs/data/vocabulary-contract-v1'
spec = importlib.util.spec_from_file_location('ingestion_contract', CONTRACT / 'validate_contract.py')
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)
VERSION = '1.0.0'
ADAPTER = 'reviewed-jsonl-v1'
SCHEMA = contract.load_json(CONTRACT / 'catalog.schema.json')
POS = {p: p for p in SCHEMA['$defs']['entry']['properties']['pos']['enum']}
POS.update({'n': 'noun', 'n.': 'noun', 'NOUN': 'noun', 'v': 'verb', 'v.': 'verb',
            'VERB': 'verb', 'adj': 'adjective', 'adj.': 'adjective',
            'adv': 'adverb', 'adv.': 'adverb'})


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode('utf-8')


def digest(value):
    return hashlib.sha256(value).hexdigest()


def write(path, value):
    with path.open('xb') as stream:
        stream.write(encoded(value))


def ordered(item, definition):
    # Use schema property order; preserve unknown fields so validation rejects them.
    keys = SCHEMA['$defs'][definition]['properties']
    return {k: item[k] for k in list(keys) + sorted(set(item) - set(keys)) if k in item}


def run(config_path, output, *, fixture=False, dry_run=False, root=ROOT):
    root = root.resolve()
    output = output.resolve()
    allowed = root / 'build/vocabulary-ingestion'
    require(output.is_relative_to(allowed) and output != allowed,
            'Output must be a new directory below build/vocabulary-ingestion')
    require(not output.exists(), 'Output already exists; choose a new run directory')
    config_path = config_path.resolve()
    if fixture:
        require(config_path.is_relative_to(root / 'tools/vocabulary/fixtures/ingestion'),
                'Fixture config must be inside isolated ingestion fixtures')
    output.mkdir(parents=True)
    stage = 'input'
    rejects, failures, normalized = [], [], []
    counts = {'read': 0, 'accepted': 0, 'rejected': 0}
    hashes = {}

    def load(relative, *, as_json=True):
        path = (root / relative).resolve()
        require(path.is_relative_to(root) and not output.is_relative_to(path)
                and not path.is_relative_to(output), 'Unsafe input path')
        if fixture:
            require(path.is_relative_to(root / 'tools/vocabulary/fixtures/ingestion'),
                    'Fixture input escapes isolated fixtures')
        raw = path.read_bytes()
        hashes[relative] = digest(raw)
        if as_json:
            return json.loads(raw.decode('utf-8'), object_pairs_hook=contract.no_duplicate_keys)
        return raw

    def reject(locator, code, field, message, source=None, snapshot=None):
        rejects.append({'source': source, 'snapshot': snapshot, 'locator': locator,
                        'stage': stage, 'reason': code, 'field': field, 'message': message})

    try:
        require(config_path.is_relative_to(root), 'Config must be repository-local')
        config = load(config_path.relative_to(root).as_posix())
        require(config['adapter'] == ADAPTER and config['toolVersion'] == VERSION,
                'Unsupported adapter/tool version')
        require(config['schemaVersion'] == '1.0.0', 'Unsupported canonical schema')
        require(config['fixtureOnly'] is fixture, 'Explicit fixture mode must match config')
        stage = 'gate'
        bindings = config['sources']
        require(bool(bindings), 'Source mappings required')
        require(len({b['canonicalId'] for b in bindings}) == len(bindings), 'Duplicate source mapping')
        sources, binding_map = [], {}
        if not fixture:
            registry = load(config['sourceRegistry'])
            frozen = load(config['inventory'])
            audit_eligible(registry, root)
            verify_snapshot(frozen, registry, root)
        for binding in bindings:
            sid = binding['canonicalId']
            if fixture:
                require(binding['registryId'] == 'synthetic-fixture', 'Fixture source required')
                source = binding['metadata']
                require(source['approvalStatus'] == 'fixture_only' and
                        source['approvedBy'] is None and source['evidenceRef'] is None and
                        source['licenseCode'] == 'FIXTURE-ONLY', 'Fixture cannot claim approval')
            else:
                approved = authorize(registry, binding['registryId'], root)
                snapshot = approved['snapshot']
                require(snapshot['id'] == binding['snapshotId'] and
                        snapshot['upstreamVersion'] == binding['upstreamVersion'],
                        'Snapshot ID/version mapping mismatch')
                artifact = next((a for a in snapshot['artifacts']
                                 if a['path'] == binding['artifact'] and a['role'] == 'data'), None)
                require(artifact is not None, 'Mapping must reference approved corpus data artifact')
                credit = next(a for a in snapshot['artifacts'] if a['role'] == 'attribution')
                evidence = next(a for a in snapshot['artifacts'] if a['role'] == 'approval')
                source = {'id': sid, 'title': approved['title'], 'url': artifact['url'],
                          'licenseCode': approved['licenseCode'], 'licenseUrl': approved['licenseUrl'],
                          'snapshotDate': snapshot['retrievedAt'][:10],
                          'snapshotSha256': artifact['sha256'],
                          'attributionText': contract.clean_text(load(credit['path'], as_json=False).decode('utf-8')),
                          'approvalStatus': 'approved', 'approvedBy': approved['review']['reviewer'],
                          'evidenceRef': evidence['path']}
            require(source['id'] == sid, 'Canonical source ID mismatch')
            require(binding['sha256'] == source['snapshotSha256'], 'Snapshot hash mapping mismatch')
            raw = load(binding['artifact'], as_json=False)
            require(digest(raw) == binding['sha256'], 'Corpus checksum mismatch')
            binding_map[sid] = binding
            sources.append(ordered(source, 'source'))
        # All sources must pass before reading/parsing any record.
        if dry_run:
            write(output / 'check.json', {'status': 'gate-pass-only', 'fixtureOnly': fixture,
                                         'inputHashes': hashes, 'recordsParsed': 0})
            return 0
        stage = 'parse'
        require(config['input'] in {b['artifact'] for b in bindings},
                'Input must be a checksum-verified mapped corpus artifact')
        raw = load(config['input'], as_json=False)
        require(digest(raw) == next(b['sha256'] for b in bindings if b['artifact'] == config['input']),
                'Input changed after gate verification')
        require(config['encoding'] == 'utf-8', 'Adapter supports explicit UTF-8 only')
        text = raw.decode('utf-8')
        identities = load(config['identityRegistry'])
        previous = load(config['baseline']) if config.get('baseline') else None
        identity_index = {}
        for item in identities:
            require(item['id'] not in identity_index, 'Identity registry collision/duplicate ID')
            require(item['id'] == contract.stable_id(item['identityLemma'], item['pos'], item['senseIndex'])
                    and item['fullSha256'] == contract.identity_hash(item['identityLemma'], item['pos'], item['senseIndex']),
                    'Identity registry hash conflict')
            identity_index[item['id']] = item
        candidates = []
        for line_no, line in enumerate(text.splitlines(), 1):
            counts['read'] += 1
            line_locator = f'{config["input"]}#line={line_no}'
            locator = line_locator
            before = len(rejects)
            stage = 'parse'
            try:
                row = json.loads(line, object_pairs_hook=contract.no_duplicate_keys)
                require(isinstance(row, dict), 'Record must be an object')
            except ValueError as exc:
                reject(locator, 'INVALID_PARSE', None, str(exc))
                continue
            stage = 'mapping'
            row_locator = row.get('locator', line_locator)
            if isinstance(row_locator, str) and contract.clean_text(row_locator):
                locator = row_locator
            else:
                reject(line_locator, 'INVALID_FIELD_TYPE' if not isinstance(row_locator, str)
                       else 'MISSING_FIELD', 'locator', 'Nonblank string locator required')
            # Never use an unvalidated JSON value as a dict key, set member or
            # sort key. Bad fields belong in record diagnostics, not batch errors.
            sid = row.get('sourceId') if isinstance(row.get('sourceId'), str) else None
            binding = binding_map.get(sid, {})
            snap = binding.get('snapshotId')
            fields = ('lemma', 'pos', 'meaningVi', 'exampleEn', 'exampleVi', 'sourceId')
            for field in fields:
                if field in row and not isinstance(row[field], str):
                    reject(locator, 'INVALID_FIELD_TYPE', field, 'String required', sid, snap)
                elif field not in row or not contract.clean_text(row[field]):
                    reject(locator, 'MISSING_FIELD', field, 'Nonblank string required', sid, snap)
            refs = row.get('provenance', [])
            needed = {'lexical', 'meaningVi', 'examplePair'} | ({'ipa'} if row.get('ipa') is not None else set())
            if not isinstance(refs, list):
                reject(locator, 'INVALID_FIELD_TYPE', 'provenance', 'Array required', sid, snap)
                refs = []
            components = set()
            for ref_no, ref in enumerate(refs):
                field = f'provenance[{ref_no}]'
                if not isinstance(ref, dict):
                    reject(locator, 'INVALID_FIELD_TYPE', field, 'Object required', sid, snap)
                    continue
                component = ref.get('component')
                if not isinstance(component, str):
                    reject(locator, 'INVALID_FIELD_TYPE', field + '.component', 'String required', sid, snap)
                else:
                    components.add(component)
                ref_sid = ref.get('sourceId')
                if not isinstance(ref_sid, str):
                    reject(locator, 'INVALID_FIELD_TYPE', field + '.sourceId', 'String required', sid, snap)
                    mapped = None
                else:
                    mapped = binding_map.get(ref_sid)
                    if mapped is None:
                        reject(locator, 'INVALID_SOURCE_REFERENCE', field + '.sourceId', 'Unmapped component source', sid, snap)
                ref_locator = ref.get('locator')
                if not isinstance(ref_locator, str):
                    reject(line_locator, 'INVALID_FIELD_TYPE', field + '.locator', 'String required', sid, snap)
                elif mapped is not None:
                    artifact, separator, record_key = ref_locator.partition('#')
                    if artifact != mapped['artifact'] or not separator or not contract.clean_text(record_key):
                        reject(line_locator, 'MISSING_PROVENANCE', field + '.locator',
                               'Use mapped artifact#nonblank-record-key', sid, snap)
            if not needed <= components:
                reject(locator, 'MISSING_PROVENANCE', 'provenance', 'Required component coverage missing', sid, snap)
            if len(rejects) != before:
                continue
            if row['pos'] not in POS:
                reject(locator, 'UNSUPPORTED_POS', 'pos', row['pos'], sid, snap)
                continue
            index = row.get('senseIndex')
            if type(index) is not int or index < 1:
                reject(locator, 'UNRESOLVED_SENSE', 'senseIndex', 'Reviewed registry index required', sid, snap)
                continue
            eid = contract.stable_id(row['lemma'], POS[row['pos']], index)
            identity = identity_index.get(eid)
            if identity is None:
                reject(locator, 'UNRESOLVED_SENSE', 'senseIndex', 'Sense absent from supplied registry', sid, snap)
                continue
            if (identity['status'] != 'active' or
                (identity['identityLemma'], identity['pos'], identity['senseIndex']) !=
                (contract.normalize_lemma(row['lemma']), POS[row['pos']], index)):
                reject(locator, 'IDENTITY_CONFLICT', 'senseIndex', 'Retired or conflicting identity', sid, snap)
                continue
            if sid not in binding_map:
                reject(locator, 'INVALID_SOURCE_REFERENCE', 'sourceId', 'Unmapped source', sid, snap)
                continue
            entry = {'id': eid, 'lemma': row['lemma'], 'normalizedLemma': contract.normalize_lemma(row['lemma']),
                     'pos': POS[row['pos']], 'senseIndex': index, 'meaningVi': row['meaningVi'],
                     'exampleEn': row['exampleEn'], 'exampleVi': row['exampleVi'], 'ipa': row.get('ipa'),
                     'sourceId': sid, 'contentVersion': config['contentVersion'],
                     'provenance': sorted([ordered(r, 'provenance') for r in refs],
                                          key=lambda r: (r['component'], r['sourceId'], r['locator']))}
            for field in ('lemma', 'meaningVi', 'exampleEn', 'exampleVi', 'ipa'):
                if isinstance(entry[field], str):
                    clean = contract.clean_text(entry[field])
                    if clean != entry[field]:
                        normalized.append({'locator': locator, 'field': field, 'rule': 'T1'})
                    entry[field] = clean
            # Reuse canonical schema + semantic validator on a small envelope. Ignore
            # cross-record constraints, which are evaluated once on the full export.
            probe = {'schemaVersion': '1.0.0', 'contentVersion': config['contentVersion'],
                     'fixtureOnly': fixture, 'sources': sources, 'entries': [entry],
                     'identityRegistry': [identity], 'packs': config['packs'],
                     'packWords': [p for p in config['packWords'] if p['vocabularyId'] == eid]}
            errors = [e for e in contract.validate(probe)
                      if not e.startswith(('UNASSIGNED_ENTRY:', 'PACK_QUIZ_MEANINGS:'))]
            if errors:
                reject(locator, 'CANONICAL_VIOLATION', None, '; '.join(errors), sid, snap)
            else:
                candidates.append((entry, locator))
        stage = 'identity'
        duplicates = Counter(e['id'] for e, _ in candidates)
        entries = []
        for entry, locator in candidates:
            if duplicates[entry['id']] > 1:
                reject(locator, 'IDENTITY_CONFLICT', 'id', 'Multiple records claim same ID; all quarantined',
                       entry['sourceId'], binding_map[entry['sourceId']]['snapshotId'])
            else:
                entries.append(entry)
        counts['accepted'] = len(entries)
        counts['rejected'] = counts['read'] - counts['accepted']
        stage = 'validation'
        packs = [ordered(p, 'pack') for p in config['packs']]
        for pack in packs:
            for field in ('title', 'description', 'category'):
                pack[field] = contract.clean_text(pack[field])
        data = {'schemaVersion': config['schemaVersion'], 'contentVersion': config['contentVersion'],
                'fixtureOnly': fixture, 'sources': sorted(sources, key=lambda s: s['id']),
                'entries': sorted(entries, key=lambda e: e['id']),
                'identityRegistry': sorted([ordered(i, 'identity') for i in identities], key=lambda i: i['id']),
                'packs': sorted(packs, key=lambda p: (p['orderNo'], p['id'])),
                'packWords': sorted([ordered(p, 'packWord') for p in config['packWords']],
                                    key=lambda p: (p['packId'], p['orderNo'], p['vocabularyId']))}
        failures.extend(contract.validate(data, previous=previous))
        # Strict batch policy: any rejected row blocks publishing a successful batch.
        if rejects or failures:
            raise ValueError('Batch rejected; see reject-report.json')
        stage = 'publish'
        write(output / 'canonical-catalog.json', data)
        write(output / 'normalization.json', sorted(normalized, key=lambda r: (r['locator'], r['field'])))
        write(output / 'reject-report.json', {'counts': counts, 'rejects': [], 'sourceFailures': []})
        tools = [Path(__file__), CONTRACT / 'validate_contract.py', CONTRACT / 'catalog.schema.json',
                 Path(__file__).with_name('source_gate.py'), Path(__file__).with_name('gate_sources.py')]
        manifest = {'status': 'success', 'fixtureOnly': fixture, 'toolVersion': VERSION,
                    'adapter': ADAPTER, 'python': platform.python_version(),
                    'unicode': unicodedata.unidata_version,
                    'jsonschema': package_version('jsonschema'),
                    'inputHashes': dict(sorted(hashes.items())),
                    'toolHashes': {p.name: digest(p.read_bytes().replace(b'\r\n', b'\n')) for p in tools},
                    'baselineCompared': previous is not None, 'counts': counts,
                    'sourceMappings': [{k: b[k] for k in ('registryId', 'snapshotId', 'canonicalId', 'artifact', 'sha256')}
                                       for b in sorted(bindings, key=lambda b: b['canonicalId'])],
                    'outputHashes': {p.name: digest(p.read_bytes()) for p in sorted(output.iterdir())},
                    'limitations': ['No content QA or AC-12 acceptance', 'No catalog/app asset built',
                                    'No published baseline compared' if previous is None else 'Baseline checks applied']}
        # Manifest is the completion marker; consumers must require it.
        write(output / 'manifest.json', manifest)
        return 0
    except (ValueError, KeyError, TypeError, OSError, StopIteration) as exc:
        for name in ('canonical-catalog.json', 'normalization.json', 'manifest.json'):
            (output / name).unlink(missing_ok=True)
        failures.append(f'{stage}: {exc}')
        counts['rejected'] = counts['read'] - counts['accepted']
        write(output / 'failure.json', {'status': 'failed', 'stage': stage, 'errors': failures})
        if not (output / 'reject-report.json').exists():
            write(output / 'reject-report.json', {'counts': counts,
                  'rejects': sorted(rejects, key=lambda r: (str(r['locator']), r['reason'], str(r['field']))),
                  'sourceFailures': [{'stage': stage,
                                      'reason': 'INVALID_ENCODING' if isinstance(exc, UnicodeError) else
                                                'GATE_FAILURE' if stage == 'gate' else 'INPUT_FAILURE',
                                      'message': str(exc)}] if stage in ('input', 'gate', 'parse') else [],
                  'validationErrors': failures if stage == 'validation' else []})
        print(f'BLOCKED ({stage}): {exc}', file=sys.stderr)
        return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--fixture', action='store_true', help='Isolated synthetic inputs; output always fixtureOnly')
    parser.add_argument('--dry-run', action='store_true', help='Verify gate and hashes only; parse no records')
    args = parser.parse_args()
    try:
        return run(args.config, args.output, fixture=args.fixture, dry_run=args.dry_run)
    except (ValueError, OSError) as exc:
        print(f'INPUT_ERROR: {exc}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
