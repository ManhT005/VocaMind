"""VOC-DATA-04: deterministic build-time QC after verified ingestion."""
from __future__ import annotations

import argparse
from collections import Counter
import copy
from datetime import date
import json
from pathlib import Path
import platform
import re
import sys
import tempfile
import unicodedata

import ingest

contract = ingest.contract
VERSION = '1.0.0'
CHECKS = ('singleSense', 'exampleSense', 'translationEquivalent', 'distinctMeaning')


def entry_digest(entry):
    """Bind a human decision to all canonical fields, including provenance."""
    return ingest.digest(json.dumps(entry, sort_keys=True, ensure_ascii=False,
                                   separators=(',', ':'), allow_nan=False).encode('utf-8'))


def meaning_key(value):
    # Contract BR-08 applied AFTER T1; same rule as canonical Quiz input.
    return contract.meaning_key(contract.clean_text(value))


def normalize(data):
    result = copy.deepcopy(data)
    changes = []
    if not isinstance(result, dict):
        return result, changes
    for collection, fields in (('entries', ('lemma', 'meaningVi', 'exampleEn', 'exampleVi', 'ipa')),
                               ('packs', ('title', 'description', 'category'))):
        items = result.get(collection, [])
        if not isinstance(items, list):
            continue
        for i, item in enumerate(items):
            if not isinstance(item, dict):
                continue
            for field in fields:
                if isinstance(item.get(field), str):
                    clean = contract.clean_text(item[field])
                    if clean != item[field]:
                        changes.append({'recordRef': f'{collection}/{i}', 'field': field, 'rule': 'T1'})
                    item[field] = clean
            if collection == 'entries' and isinstance(item.get('pos'), str):
                pos = ingest.POS.get(item['pos'], item['pos'])
                if pos != item['pos']:
                    changes.append({'recordRef': f'{collection}/{i}', 'field': 'pos', 'rule': 'POS_ALIAS'})
                item['pos'] = pos
    # Identity registry and IDs are never rewritten, including on normalization failure.
    return result, changes


def evaluate(data, reviews, *, fixture=False, previous=None):
    """Pure record/dataset gates. CLI additionally verifies custody and source rights."""
    data, changes = normalize(data)
    issues = []

    def issue(code, field, reason, index=None, severity='error'):
        entry = data.get('entries', [])[index] if index is not None else {}
        issues.append({'recordRef': f'entries/{index}' if index is not None else 'dataset',
                       'stableId': entry.get('id') if isinstance(entry, dict) else None,
                       'errorCode': code, 'field': field, 'reason': reason, 'severity': severity,
                       'provenance': entry.get('provenance', []) if isinstance(entry, dict) else []})

    validator = contract.Draft202012Validator(ingest.SCHEMA, format_checker=contract.FormatChecker())
    schema_errors = list(validator.iter_errors(data))
    for error in schema_errors:
        path = list(error.absolute_path)
        index = path[1] if len(path) > 1 and path[0] == 'entries' and isinstance(path[1], int) else None
        code = 'UNSUPPORTED_POS' if path and path[-1] == 'pos' and error.validator == 'enum' else 'SCHEMA'
        issue(code, '/'.join(map(str, path)), error.message, index)
    if schema_errors:
        return data, issues, changes, []

    if data['fixtureOnly'] is not fixture:
        issue('MODE_MISMATCH', 'fixtureOnly', 'Explicit fixture mode must match canonical envelope')
    for error in contract.validate(data, previous=previous):
        code, _, reason = error.partition(': ')
        matched = [i for i, e in enumerate(data['entries']) if e['id'] in reason]
        field = {'IDENTITY_CHANGED': 'id', 'NORMALIZED_LEMMA': 'normalizedLemma',
                 'MISSING_IDENTITY': 'id', 'PROVENANCE_COVERAGE': 'provenance',
                 'PRIMARY_SOURCE_MISMATCH': 'sourceId', 'DUPLICATE_PROVENANCE': 'provenance',
                 'UNKNOWN_SOURCE': 'provenance', 'EMPTY_LOCATOR': 'provenance'}.get(code)
        if matched:
            for index in matched:
                issue(code, field, reason, index)
        else:
            issue(code, None, reason)

    sources = {s['id']: s for s in data['sources']}
    for source in data['sources']:
        if fixture:
            valid = (source['approvalStatus'] == 'fixture_only' and
                     source['licenseCode'] == 'FIXTURE-ONLY' and
                     source['approvedBy'] is None and source['evidenceRef'] is None)
        else:
            valid = (source['approvalStatus'] == 'approved' and
                     bool(source['approvedBy']) and bool(source['evidenceRef']))
        if not valid:
            issue('SOURCE_NOT_APPROVED', 'sources/' + source['id'],
                  'Source requires reviewed rights; license metadata alone is insufficient')

    review_map = {}
    if not isinstance(reviews, dict) or reviews.get('schemaVersion') != VERSION or reviews.get('fixtureOnly') is not fixture:
        issue('INVALID_REVIEW', 'reviews', 'Review version/fixture mode mismatch')
    elif not isinstance(reviews.get('records'), list):
        issue('INVALID_REVIEW', 'reviews.records', 'Array required')
    else:
        try:
            context_matches = reviews.get('datasetSha256') == entry_digest(data)
        except UnicodeError:
            context_matches = False
        if not context_matches:
            issue('REVIEW_DATASET_MISMATCH', 'reviews.datasetSha256',
                  'Review must bind the normalized envelope, including pack meaning context')
        for review in reviews['records']:
            if not isinstance(review, dict) or not isinstance(review.get('stableId'), str):
                issue('INVALID_REVIEW', 'reviews.records', 'Object with stableId required')
                continue
            eid = review['stableId']
            if eid in review_map:
                issue('DUPLICATE_REVIEW', 'reviews.records', eid)
            review_map[eid] = review
        unknown = set(review_map) - {e['id'] for e in data['entries']}
        for eid in sorted(unknown):
            issue('UNKNOWN_REVIEW_RECORD', 'reviews.records', eid)

    ids = Counter(e['id'] for e in data['entries'])
    for i, entry in enumerate(data['entries']):
        eid = entry['id']
        if ids[eid] > 1:
            issue('DUPLICATE_ID', 'id', 'All duplicate claimants rejected', i)
        if entry['pos'] not in ingest.POS:
            issue('UNSUPPORTED_POS', 'pos', 'Unmapped POS', i)
        try:
            expected = contract.stable_id(entry['lemma'], entry['pos'], entry['senseIndex'])
            if eid != expected:
                issue('IDENTITY_HASH', 'id', 'ID differs from contract identity tuple; never repaired', i)
        except (ValueError, UnicodeError) as exc:
            issue('IDENTITY_HASH', 'id', str(exc), i)
        for field in ('meaningVi', 'exampleEn', 'exampleVi'):
            if not entry[field]:
                issue('MISSING_FIELD', field, 'Nonblank text required after T1', i)
        for field in ('exampleEn', 'exampleVi'):
            count = len(entry[field].split())
            if not 5 <= count <= 20:
                issue('EXAMPLE_LENGTH', field, f'{count} whitespace-delimited words; suggested 5–20', i, 'warning')
        if re.search(r'[;\n]|(?:^|\s)\d+[.)]\s|\s/\s', entry['meaningVi']):
            issue('SUSPECT_MULTI_SENSE', 'meaningVi', 'Possible multiple senses; human decision required', i, 'warning')
        for j, ref in enumerate(entry['provenance']):
            if ref['sourceId'] not in sources:
                issue('UNKNOWN_SOURCE', f'provenance/{j}/sourceId', ref['sourceId'], i)
        review = review_map.get(eid, {})
        try:
            reviewed_at = review.get('reviewedAt', '')
            valid_date = isinstance(reviewed_at, str) and date.fromisoformat(reviewed_at).isoformat() == reviewed_at
        except ValueError:
            valid_date = False
        valid_review = (review.get('decision') == 'approved' and
                        isinstance(review.get('reviewer'), str) and bool(contract.clean_text(review['reviewer'])) and
                        valid_date and all(review.get(check) is True for check in CHECKS))
        if not valid_review:
            issue('CONTENT_REVIEW_REQUIRED', 'reviews', 'Dated reviewer approval of sense, examples and meaning distinction required', i)
        try:
            matches = review.get('entrySha256') == entry_digest(entry)
        except UnicodeError:
            matches = False
        if not matches:
            issue('REVIEW_CONTENT_MISMATCH', 'reviews.entrySha256', 'Missing/stale evidence for normalized record', i)

    entries = {e['id']: e for e in data['entries'] if ids[e['id']] == 1}
    pack_stats = []
    for pack in data['packs']:
        members = {m['vocabularyId'] for m in data['packWords'] if m['packId'] == pack['id']}
        meanings = {meaning_key(entries[eid]['meaningVi']) for eid in members if eid in entries}
        pack_stats.append({'packId': pack['id'], 'memberships': len(members),
                           'distinctMeanings': len(meanings), 'quizGate': len(meanings) >= 4})
        if len(meanings) < 4:
            issue('PACK_QUIZ_MEANINGS', 'packWords', pack['id'] + ': fewer than 4 distinct normalized meanings')
    return data, issues, changes, pack_stats


def verified_input(input_dir, config_path, *, fixture, root):
    """Replay existing ingestion gates, including actual ledger/evidence and component bytes.

    A claimed approval flag or caller-written manifest cannot bypass authorization.
    Replay uses a temporary directory and never modifies the original import.
    """
    manifest = contract.load_json(input_dir / 'manifest.json')
    ingest.require(isinstance(manifest, dict), 'Ingestion manifest must be an object')
    ingest.require(manifest.get('status') == 'success' and manifest.get('fixtureOnly') is fixture,
                   'Successful ingestion manifest with matching mode required')
    ingest.require(manifest.get('provenanceContentVerified') is True,
                   'Content-verified component adapters required')
    for name in ('canonical-catalog.json', 'normalization.json', 'reject-report.json'):
        ingest.require(ingest.digest((input_dir / name).read_bytes()) == manifest['outputHashes'][name],
                       'Ingestion artifact checksum mismatch: ' + name)
    scratch = root / 'build/vocabulary-ingestion'
    scratch.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='qc-replay-', dir=scratch) as temp:
        replay = Path(temp) / 'run'
        code = ingest.run(config_path, replay, fixture=fixture, root=root)
        ingest.require(code == 0, 'Ingestion source/snapshot replay failed')
        fresh = contract.load_json(replay / 'manifest.json')
        ingest.require(fresh.get('provenanceContentVerified') is True,
                       'Replay must verify component content, not just structural provenance')
        for field in ('inputHashes', 'toolHashes', 'sourceMappings', 'baselineCompared'):
            ingest.require(manifest[field] == fresh[field], 'Ingestion custody mismatch: ' + field)
        ingest.require((input_dir / 'canonical-catalog.json').read_bytes() ==
                       (replay / 'canonical-catalog.json').read_bytes(), 'Canonical differs from verified replay')
    return manifest, contract.load_json(input_dir / 'canonical-catalog.json')


def run(input_dir, config_path, review_path, output, *, fixture=False, root=ingest.ROOT):
    root = root.resolve()
    input_dir, config_path, review_path, output = (p.resolve() for p in (input_dir, config_path, review_path, output))
    allowed = root / 'build/vocabulary-quality'
    ingest.require(output.is_relative_to(allowed) and output != allowed and not output.exists(),
                   'Output must be a new directory below build/vocabulary-quality')
    for path in (input_dir, config_path, review_path):
        ingest.require(path.is_relative_to(root) and not path.is_relative_to(output) and not output.is_relative_to(path),
                       'Inputs must be repository-local and separate from output')
    output.mkdir(parents=True)
    issues, changes, packs, data = [], [], [], {}
    binding = {'toolVersion': VERSION, 'fixtureOnly': fixture,
               'command': 'python tools/vocabulary/quality.py --input ' + input_dir.relative_to(root).as_posix() +
                          ' --config ' + config_path.relative_to(root).as_posix() +
                          ' --reviews ' + review_path.relative_to(root).as_posix() +
                          ' --output <new-directory>' + (' --fixture' if fixture else ''),
               'python': platform.python_version(), 'unicode': unicodedata.unidata_version,
               'toolSha256': ingest.digest(Path(__file__).read_bytes().replace(b'\r\n', b'\n'))}
    try:
        binding['inputSha256'] = ingest.digest((input_dir / 'canonical-catalog.json').read_bytes())
        binding['manifestSha256'] = ingest.digest((input_dir / 'manifest.json').read_bytes())
        review_raw = review_path.read_bytes()
        binding['reviewSha256'] = ingest.digest(review_raw)
        binding['configSha256'] = ingest.digest(config_path.read_bytes())
        manifest, data = verified_input(input_dir, config_path, fixture=fixture, root=root)
        binding['snapshots'] = manifest['sourceMappings']
        reviews = json.loads(review_raw.decode('utf-8'), object_pairs_hook=contract.no_duplicate_keys)
        config = contract.load_json(config_path)
        previous = contract.load_json(root / config['baseline']) if config.get('baseline') else None
        data, issues, changes, packs = evaluate(data, reviews, fixture=fixture, previous=previous)
    except (ValueError, KeyError, TypeError, AttributeError, OSError) as exc:
        issues.append({'recordRef': 'dataset', 'stableId': None, 'errorCode': 'CUSTODY_FAILURE',
                       'field': 'input', 'reason': str(exc), 'severity': 'error', 'provenance': []})
    issues.sort(key=lambda r: (r['recordRef'], r['errorCode'], str(r['field']), r['reason']))
    errors = [i for i in issues if i['severity'] == 'error']
    rejected_indices = {i['recordRef'] for i in errors if i['recordRef'].startswith('entries/')}
    count = len(data.get('entries', [])) if isinstance(data, dict) and isinstance(data.get('entries', []), list) else 0
    passed = not errors
    report = {**binding, 'status': ('fixture-pass' if fixture else 'pass') if passed else 'failed',
              'counts': {'input': count, 'accepted': count if passed else 0,
                         'rejected': len(rejected_indices), 'withheld': count - len(rejected_indices) if errors else 0},
              'errorsByCode': dict(sorted(Counter(i['errorCode'] for i in errors).items())),
              'warningsByCode': dict(sorted(Counter(i['errorCode'] for i in issues if i['severity'] == 'warning').items())),
              'packs': packs, 'issues': issues, 'normalization': changes,
              'limitations': ['Fixture results never approve production data' if fixture else 'Human decisions supplied by review sidecar',
                              'No release quotas, database build or Android acceptance; review by Trường remains required']}
    try:
        write_reports(output, report)
        if passed:
            ingest.write(output / 'canonical-catalog.json', data)
            (output / 'validated_vocab.jsonl').write_bytes(b''.join(
                (json.dumps(e, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8') for e in data['entries']))
            # Completion marker written last; consumers require it and all output hashes.
            ingest.write(output / 'manifest.json', {'status': report['status'], 'fixtureOnly': fixture,
                         'toolVersion': VERSION, 'inputSha256': binding['inputSha256'],
                         'reviewSha256': binding['reviewSha256'],
                         'outputHashes': {p.name: ingest.digest(p.read_bytes()) for p in sorted(output.iterdir())}})
    except OSError as exc:
        # A failed publication must not leave a plausible successful payload.
        for name in ('canonical-catalog.json', 'validated_vocab.jsonl', 'manifest.json'):
            try:
                (output / name).unlink(missing_ok=True)
            except OSError:
                pass  # Disk/permission failures still cannot create a valid completion marker.
        report['status'] = 'failed'
        report['counts']['accepted'] = 0
        report['counts']['withheld'] = count - report['counts']['rejected']
        report['issues'].append({'recordRef': 'dataset', 'stableId': None,
                                'errorCode': 'PUBLICATION_FAILURE', 'field': 'output',
                                'reason': str(exc), 'severity': 'error', 'provenance': []})
        report['errorsByCode']['PUBLICATION_FAILURE'] = 1
        try:
            write_reports(output, report)
        except OSError:
            pass
        print(f'QC failed: publication error: {exc}', file=sys.stderr)
        return 1
    print(f"QC {report['status']}: {count} input, {len(errors)} errors")
    return 0 if passed else 1


def write_reports(output, report):
    (output / 'qc_report.json').write_bytes(ingest.encoded(report))
    errors = [i for i in report['issues'] if i['severity'] == 'error']
    (output / 'rejected_records.jsonl').write_bytes(b''.join(
        (json.dumps(i, ensure_ascii=False, sort_keys=True) + '\n').encode('utf-8') for i in errors))
    markdown = ['# VOC-DATA-04 QC', '', 'Status: ' + report['status'], '',
                '```sh', report['command'], '```', '',
                '```json', json.dumps(report, ensure_ascii=False, indent=2), '```', '']
    (output / 'qc_report.md').write_bytes('\n'.join(markdown).encode('utf-8'))


def load_validated(directory, *, fixture=False):
    """Consumer entrypoint for VOC-DATA-05/06; incomplete or altered batches fail."""
    directory = Path(directory)
    manifest = contract.load_json(directory / 'manifest.json')
    expected_status = 'fixture-pass' if fixture else 'pass'
    ingest.require(isinstance(manifest, dict) and manifest.get('status') == expected_status
                   and manifest.get('fixtureOnly') is fixture and manifest.get('toolVersion') == VERSION,
                   'QC completion marker/version/mode mismatch')
    expected = {'qc_report.json', 'qc_report.md', 'rejected_records.jsonl',
                'canonical-catalog.json', 'validated_vocab.jsonl'}
    hashes = manifest.get('outputHashes')
    ingest.require(isinstance(hashes, dict) and set(hashes) == expected, 'QC output set mismatch')
    for name in sorted(expected):
        ingest.require(ingest.digest((directory / name).read_bytes()) == hashes[name],
                       'QC artifact checksum mismatch: ' + name)
    report = contract.load_json(directory / 'qc_report.json')
    ingest.require(report.get('status') == expected_status and report.get('fixtureOnly') is fixture
                   and not report.get('errorsByCode') and
                   report.get('inputSha256') == manifest.get('inputSha256') and
                   report.get('reviewSha256') == manifest.get('reviewSha256'), 'QC report/manifest mismatch')
    data = contract.load_json(directory / 'canonical-catalog.json')
    ingest.require(data.get('fixtureOnly') is fixture and not contract.validate(data),
                   'QC canonical contract/mode mismatch')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True, help='Successful VOC-DATA-03 output directory')
    parser.add_argument('--config', type=Path, required=True, help='Exact ingestion config; replayed for custody')
    parser.add_argument('--reviews', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--fixture', action='store_true')
    args = parser.parse_args()
    try:
        return run(args.input, args.config, args.reviews, args.output, fixture=args.fixture)
    except (ValueError, OSError) as exc:
        print(f'INPUT_ERROR: {exc}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
