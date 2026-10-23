#!/usr/bin/env python3
"""Build-time VOC-DATA-02 contract checks, not Android/Room or legal approval."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import unicodedata

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parent
WHITE_SPACE = frozenset(chr(n) for n in (
    list(range(0x09, 0x0E)) + [0x20, 0x85, 0xA0, 0x1680]
    + list(range(0x2000, 0x200B)) + [0x2028, 0x2029, 0x202F, 0x205F, 0x3000]
))
ASCII_LOWER = str.maketrans('ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')
PACK_MINIMUMS = {
    'toeic-basic': 40,
    'toeic-office': 40,
    'everyday-english': 40,
    'essential-academic': 30,
}


def clean_text(value: str) -> str:
    """NFC, explicit Unicode White_Space set -> ASCII space, collapse/trim."""
    value = unicodedata.normalize('NFC', value)
    value = ''.join(' ' if c in WHITE_SPACE else c for c in value)
    return re.sub(' +', ' ', value).strip(' ')


def normalize_lemma(value: str) -> str:
    """English identity policy: only ASCII A-Z folds; preserve other code points."""
    return clean_text(value).translate(ASCII_LOWER)


def identity_key(lemma: str, pos: str, sense_index: int) -> str:
    if type(sense_index) is not int or sense_index < 1:
        raise ValueError('senseIndex must be an integer >= 1')
    return f'{normalize_lemma(lemma)}_{pos}_{sense_index}'


def identity_hash(lemma: str, pos: str, sense_index: int) -> str:
    return hashlib.sha256(identity_key(lemma, pos, sense_index).encode('utf-8')).hexdigest()


def stable_id(lemma: str, pos: str, sense_index: int) -> str:
    return identity_hash(lemma, pos, sense_index)[:16]


def meaning_key(value: str) -> str:
    # BR-08: NFC + trim + full Unicode casefold. Do not strip Vietnamese accents.
    value = unicodedata.normalize('NFC', value)
    return value.strip(''.join(WHITE_SPACE)).casefold()


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'Duplicate JSON property: {key}')
        result[key] = value
    return result


def load_json(path: Path):
    with path.open(encoding='utf-8') as stream:
        return json.load(stream, object_pairs_hook=no_duplicate_keys)


def validate(data: dict, *, release: bool = False, previous: dict | None = None) -> list[str]:
    """Return errors; no mutation, network calls, DB writes or inferred approvals."""
    schema = load_json(ROOT / 'catalog.schema.json')
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    errors = [f'SCHEMA /{"/".join(map(str, e.absolute_path))}: {e.message}'
              for e in validator.iter_errors(data)]
    if errors:
        return errors

    # JSON Schema considers 1.0 an integer mathematically; canonical exports
    # require integer JSON tokens for stable decimal serialization/Java mapping.
    for collection, field in (('entries', 'senseIndex'), ('identityRegistry', 'senseIndex'),
                              ('packs', 'orderNo'), ('packWords', 'orderNo')):
        for i, item in enumerate(data[collection]):
            if type(item[field]) is not int:
                errors.append(f'INTEGER_REPRESENTATION: /{collection}/{i}/{field} must use an integer token')
    if errors:
        return errors

    def error(code, message):
        errors.append(f'{code}: {message}')

    def index(items, label):
        result = {}
        for item in items:
            if item['id'] in result:
                error('DUPLICATE_ID', f'{label}: {item["id"]}')
            result[item['id']] = item
        return result

    sources = index(data['sources'], 'sources')
    entries = index(data['entries'], 'entries')
    registry = index(data['identityRegistry'], 'identityRegistry')
    packs = index(data['packs'], 'packs')

    # Every string is NFC; display fields use canonical whitespace. Never silently repair.
    def scan(value, path=''):
        if isinstance(value, str):
            if unicodedata.normalize('NFC', value) != value:
                error('NON_NFC', path)
            if any(unicodedata.category(c) in ('Cc', 'Cs') for c in value):
                error('CONTROL_CHARACTER', path)
            if any(c in ('\u200b', '\ufeff', '\u2060') for c in value):
                error('INVISIBLE_CHARACTER', path)
        elif isinstance(value, list):
            for i, child in enumerate(value):
                scan(child, f'{path}/{i}')
        elif isinstance(value, dict):
            for key, child in value.items():
                scan(child, f'{path}/{key}')
    scan(data)
    # Reject malformed Unicode before UTF-8 hashing, rather than raising an
    # encoding exception. Canonical text violations are data errors (exit 1).
    if errors:
        return errors

    identity_keys = set()
    for item in registry.values():
        key = identity_key(item['identityLemma'], item['pos'], item['senseIndex'])
        if item['identityLemma'] != normalize_lemma(item['identityLemma']):
            error('IDENTITY_NORMALIZATION', item['id'])
        if key in identity_keys:
            error('DUPLICATE_IDENTITY', key)
        identity_keys.add(key)
        full_hash = identity_hash(item['identityLemma'], item['pos'], item['senseIndex'])
        if item['fullSha256'] != full_hash or item['id'] != full_hash[:16]:
            error('IDENTITY_HASH', item['id'])
        if not clean_text(item['senseDefinition']):
            error('EMPTY_SENSE_DEFINITION', item['id'])
        if item['status'] == 'active' and item['id'] not in entries:
            error('ACTIVE_ID_WITHOUT_ENTRY', item['id'])
        if item['status'] == 'retired' and item['id'] in entries:
            error('RETIRED_ID_IN_ENTRIES', item['id'])

    for entry in entries.values():
        eid = entry['id']
        for field in ('lemma', 'meaningVi', 'exampleEn', 'exampleVi', 'ipa'):
            value = entry[field]
            if value is not None and (not clean_text(value) or clean_text(value) != value):
                error('TEXT_NOT_CANONICAL', f'{eid}.{field}')
        if entry['normalizedLemma'] != normalize_lemma(entry['lemma']):
            error('NORMALIZED_LEMMA', eid)
        if entry['contentVersion'] != data['contentVersion']:
            error('CONTENT_VERSION', eid)
        item = registry.get(eid)
        if item is None:
            error('MISSING_IDENTITY', eid)
        elif (item['identityLemma'], item['pos'], item['senseIndex']) != (
            entry['normalizedLemma'], entry['pos'], entry['senseIndex']
        ):
            error('IDENTITY_CHANGED', eid)
        if entry['sourceId'] not in sources:
            error('UNKNOWN_SOURCE', f'{eid}: {entry["sourceId"]}')
        seen_components = set()
        covered = set()
        for ref in entry['provenance']:
            pair = (ref['component'], ref['sourceId'], ref['locator'])
            if pair in seen_components:
                error('DUPLICATE_PROVENANCE', eid)
            seen_components.add(pair)
            covered.add(ref['component'])
            if ref['sourceId'] not in sources:
                error('UNKNOWN_SOURCE', f'{eid}: {ref["sourceId"]}')
            if not clean_text(ref['locator']):
                error('EMPTY_LOCATOR', eid)
        required = {'lexical', 'meaningVi', 'examplePair'}
        if entry['ipa'] is not None:
            required.add('ipa')
        if not required <= covered:
            error('PROVENANCE_COVERAGE', f'{eid}: missing {sorted(required - covered)}')
        if not any(r['component'] == 'lexical' and r['sourceId'] == entry['sourceId']
                   for r in entry['provenance']):
            error('PRIMARY_SOURCE_MISMATCH', eid)

    for source in sources.values():
        for field in ('title', 'licenseCode', 'attributionText'):
            if not clean_text(source[field]):
                error('EMPTY_SOURCE_FIELD', f'{source["id"]}.{field}')
        if source['approvalStatus'] == 'approved' and not source['evidenceRef']:
            error('MISSING_APPROVAL_EVIDENCE', source['id'])
        if source['approvalStatus'] == 'approved':
            for field in ('approvedBy', 'evidenceRef'):
                value = source[field]
                if value is None or not clean_text(value):
                    error('EMPTY_APPROVAL', f'{source["id"]}.{field}')

    memberships = set()
    orders = set()
    words_by_pack = {pid: set() for pid in packs}
    for membership in data['packWords']:
        pid, eid = membership['packId'], membership['vocabularyId']
        pair = (pid, eid)
        if pair in memberships:
            error('DUPLICATE_MEMBERSHIP', str(pair))
        memberships.add(pair)
        order = (pid, membership['orderNo'])
        if order in orders:
            error('DUPLICATE_ORDER', str(order))
        orders.add(order)
        if pid not in packs:
            error('UNKNOWN_PACK', pid)
        if eid not in entries:
            error('UNKNOWN_VOCABULARY', eid)
        if pid in packs and eid in entries:
            words_by_pack[pid].add(eid)
    referenced_words = set().union(*words_by_pack.values()) if words_by_pack else set()
    if set(entries) - referenced_words:
        error('UNASSIGNED_ENTRY', str(sorted(set(entries) - referenced_words)))
    for pid, word_ids in words_by_pack.items():
        if len({meaning_key(entries[eid]['meaningVi']) for eid in word_ids}) < 4:
            error('PACK_QUIZ_MEANINGS', pid)
    for pack in packs.values():
        if pack['version'] != data['contentVersion']:
            error('PACK_VERSION', pack['id'])
        for field in ('title', 'description', 'category'):
            if not clean_text(pack[field]) or clean_text(pack[field]) != pack[field]:
                error('PACK_TEXT', f'{pack["id"]}.{field}')
    if len({p['orderNo'] for p in packs.values()}) != len(packs):
        error('PACK_ORDER', 'pack orderNo must be unique')

    if previous is not None:
        old_errors = validate(previous)
        if old_errors:
            error('INVALID_BASELINE', '; '.join(old_errors))
        else:
            old_version = tuple(map(int, previous['contentVersion'].split('.')))
            new_version = tuple(map(int, data['contentVersion'].split('.')))
            if new_version < old_version or (data != previous and new_version == old_version):
                error('CONTENT_VERSION_NOT_ADVANCED', 'changed export must advance contentVersion')
            old_registry = {r['id']: r for r in previous['identityRegistry']}
            for eid, old in old_registry.items():
                current = registry.get(eid)
                if current is None:
                    error('REMOVED_REGISTRY_ID', eid)
                elif any(current[f] != old[f] for f in (
                    'identityLemma', 'pos', 'senseIndex', 'fullSha256'
                )):
                    error('MUTATED_REGISTRY_ID', eid)
                elif old['status'] == 'retired' and current['status'] != 'retired':
                    error('REUSED_RETIRED_ID', eid)
            # V1 intentionally has no alias format. Block removal until a reviewed
            # compatibility/migration extension exists (DEC-04); never guess remaps.
            removed = {e['id'] for e in previous['entries']} - set(entries)
            if removed:
                error('REMOVED_PUBLISHED_ENTRY', str(sorted(removed)))
            removed_packs = {p['id'] for p in previous['packs']} - set(packs)
            if removed_packs:
                error('REMOVED_PUBLISHED_PACK', str(sorted(removed_packs)))
            old_sources = {s['id']: s for s in previous['sources']}
            for sid, source in sources.items():
                if sid in old_sources and any(source[f] != old_sources[sid][f] for f in (
                    'url', 'licenseCode', 'licenseUrl', 'snapshotDate', 'snapshotSha256'
                )):
                    error('MUTATED_SOURCE_SNAPSHOT', sid)

    if release:
        if data['fixtureOnly']:
            error('FIXTURE_NOT_RELEASE', 'fixtureOnly=true')
        if len(entries) < 150:
            error('RELEASE_UNIQUE_WORDS', f'{len(entries)} < 150')
        if not set(PACK_MINIMUMS) <= set(packs):
            error('RELEASE_PACKS', 'missing a required MVP pack')
        for pid, minimum in PACK_MINIMUMS.items():
            if len(words_by_pack.get(pid, set())) < minimum:
                error('RELEASE_PACK_QUOTA', f'{pid}: < {minimum}')
        for source in sources.values():
            if source['approvalStatus'] != 'approved' or not source['evidenceRef']:
                error('RELEASE_SOURCE_NOT_APPROVED', source['id'])
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dataset', type=Path)
    parser.add_argument('--release', action='store_true', help='additional MVP metadata/count gates')
    parser.add_argument('--previous', type=Path, help='validate against a preserved prior published export')
    args = parser.parse_args()
    try:
        data = load_json(args.dataset)
        previous = load_json(args.previous) if args.previous else None
        errors = validate(data, release=args.release, previous=previous)
    except (OSError, ValueError, TypeError) as exc:
        print(f'INPUT_ERROR: {exc}', file=sys.stderr)
        return 2
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    report = {
        'result': 'PASS_CONTRACT_CHECKS',
        'mode': 'release-metadata' if args.release else 'development',
        'uniqueWords': len(data['entries']),
        'memberships': len(data['packWords']),
        'packs': len(data['packs']),
        'baselineCompared': previous is not None,
        'limitations': 'Does not verify semantic quality, license evidence, Room asset, FTS or app release readiness.',
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
