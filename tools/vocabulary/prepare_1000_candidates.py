"""Build a RESEARCH-ONLY ranked vocabulary candidate set from audited NGSL data.

No output from this tool is a release catalog, approved translation, licensed audio
asset, stable sense ID, or authorization to bypass VOC-DATA-01 source gates.
Python 3.11+, standard library only.
"""
from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import json
from collections import Counter
from pathlib import Path
import unicodedata
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]


def file_hash(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def normalized_word(text: str) -> str:
    return unicodedata.normalize('NFC', ' '.join(str(text).split())).translate(
        str.maketrans('ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'))


def load_ngsl(root: Path, registry_path: Path, limit: int):
    if not 1 <= limit <= 2809:
        raise ValueError('--limit must be 1..2809')
    registry = json.loads(registry_path.read_text(encoding='utf-8'))
    source = next((s for s in registry['sources'] if s.get('id') == 'ngsl'), None)
    if not source or source.get('decision') == 'exclude':
        raise ValueError('NGSL source is missing or excluded')
    snapshot = source.get('snapshot') or {}
    data = [a for a in snapshot.get('artifacts', []) if a.get('role') == 'data']
    if len(data) != 1:
        raise ValueError('NGSL must have exactly one frozen data artifact')
    artifact = data[0]
    path = (root / artifact['path']).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('Snapshot path escapes repository')
    if not path.is_file() or file_hash(path) != artifact['sha256']:
        raise ValueError('NGSL snapshot data checksum mismatch or file absent')
    decoded = path.read_bytes().decode('utf-8-sig')
    rows = []
    for row in csv.DictReader(io.StringIO(decoded)):
        lemma = (row.get('Lemma') or '').strip()
        rank = (row.get('SFI Rank') or '').strip()
        if not lemma and not rank:
            continue
        if not lemma or not rank.isdecimal():
            raise ValueError('Invalid NGSL row: missing lemma or rank')
        rows.append((int(rank), lemma))
    rows.sort(key=lambda item: item[0])
    result, seen, ranks = [], set(), set()
    for rank, lemma in rows:
        key = normalized_word(lemma)
        if not key or key in seen or rank in ranks:
            raise ValueError(f'Invalid duplicate or empty NGSL lemma/rank: {lemma}/{rank}')
        seen.add(key)
        ranks.add(rank)
        result.append((rank, lemma, key))
        if len(result) == limit:
            break
    if len(result) != limit:
        raise ValueError(f'NGSL snapshot contains only {len(result)} unique entries')
    return result, {'sourceId': 'ngsl', 'snapshotId': snapshot['id'],
                    'artifactSha256': artifact['sha256'], 'licenseCode': source.get('licenseCode'),
                    'sourceDecision': source.get('decision')}


def jsonl_rows(path: Path):
    opener = gzip.open if path.suffix == '.gz' else open
    with opener(path, 'rt', encoding='utf-8') as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f'Malformed JSONL at {path}:{line_number}') from exc


def enrich_kaikki(path: Path | None, targets: set[str]):
    result = {word: {'meaningViCandidates': [], 'ipaCandidates': []} for word in targets}
    if path is None:
        return result
    for obj in jsonl_rows(path):
        lemma = str(obj.get('word') or '')
        key = normalized_word(lemma)
        if key not in targets or (obj.get('lang_code') != 'en' and
                                   obj.get('lang') not in ('Tiếng Anh', 'English')):
            continue
        origin = {'sourceId': 'kaikki-viwiktionary',
                  'locator': 'https://vi.wiktionary.org/wiki/' + quote(lemma, safe='')}
        for index, sense in enumerate(obj.get('senses') or [], 1):
            for gloss in (sense.get('glosses') or []):
                if isinstance(gloss, str) and gloss.strip():
                    item = {'text': gloss.strip(), 'posCandidate': obj.get('pos'),
                            'sourceSenseIndex': index, 'provenance': origin,
                            'reviewStatus': 'pending'}
                    if item not in result[key]['meaningViCandidates']:
                        result[key]['meaningViCandidates'].append(item)
        for sound in (obj.get('sounds') or []):
            ipa = sound.get('ipa')
            if isinstance(ipa, str) and ipa.strip():
                item = {'ipa': ipa.strip(), 'tags': sound.get('tags') or [],
                        'provenance': origin, 'reviewStatus': 'pending'}
                if item not in result[key]['ipaCandidates']:
                    result[key]['ipaCandidates'].append(item)
    return result


def enrich_examples(path: Path | None, targets: set[str]):
    result = {word: [] for word in targets}
    if path is None:
        return result
    for obj in jsonl_rows(path):
        key = normalized_word(obj.get('lemma') or '')
        if key not in targets:
            continue
        en, vi = obj.get('en') or {}, obj.get('vi') or {}
        if not obj.get('linked') or not all(
            isinstance(record.get(field), (str, int)) and str(record[field]).strip()
            for record in (en, vi) for field in ('id', 'text', 'author', 'license')):
            raise ValueError('Tatoeba candidate requires linked sentences, IDs, texts, authors, licenses')
        result[key].append({'en': en, 'vi': vi, 'sourceId': 'tatoeba-text',
                            'reviewStatus': 'pending', 'senseMatchVerified': False})
    return result


def enrich_audio(path: Path | None, targets: set[str]):
    result = {word: [] for word in targets}
    if path is None:
        return result
    for obj in jsonl_rows(path):
        key = normalized_word(obj.get('lemma') or '')
        if key not in targets:
            continue
        metadata = obj.get('metadata') or {}
        license_name = metadata.get('LicenseShortName', {}).get('value')
        license_url = metadata.get('LicenseUrl', {}).get('value')
        artist = metadata.get('Artist', {}).get('value')
        if (not obj.get('title', '').startswith('File:') or
            not obj.get('mediaMime', '').startswith('audio/') or
            not license_name or not license_url or not artist or
            not obj.get('descriptionUrl') or not obj.get('upstreamMediaSha1')):
            raise ValueError('Commons metadata incomplete; no audio candidate permitted')
        result[key].append({'title': obj['title'], 'descriptionUrl': obj['descriptionUrl'],
                            'license': license_name, 'licenseUrl': license_url,
                            'artistRaw': artist, 'upstreamMediaSha1': obj['upstreamMediaSha1'],
                            'mime': obj['mediaMime'], 'sourceId': 'commons-audio',
                            'reviewStatus': 'pending', 'mediaDownloaded': False,
                            'releaseEligible': False})
    return result


def prepare(root: Path, registry: Path, limit: int, kaikki: Path | None,
            examples: Path | None, audio: Path | None):
    words, ngsl = load_ngsl(root, registry, limit)
    targets = {key for _, _, key in words}
    meanings = enrich_kaikki(kaikki, targets)
    example_pairs = enrich_examples(examples, targets)
    sounds = enrich_audio(audio, targets)
    coverage = Counter()
    entries = []
    for rank, lemma, key in words:
        item = {'lemma': lemma, 'normalizedLemma': key, 'ngslRank': rank,
                'source': {**ngsl, 'locator': f'rank:{rank};lemma:{lemma}'},
                'meaningViCandidates': meanings[key]['meaningViCandidates'],
                'ipaCandidates': meanings[key]['ipaCandidates'],
                'examplePairCandidates': example_pairs[key],
                'audioCandidates': sounds[key], 'reviewStatus': 'pending',
                'releaseEligible': False, 'stableId': None}
        for field in ('meaningViCandidates', 'ipaCandidates', 'examplePairCandidates', 'audioCandidates'):
            if item[field]:
                coverage[field] += 1
        entries.append(item)
    report = {'purpose': 'research-candidates-only', 'releaseEligible': False,
              'sourceApproved': ngsl['sourceDecision'] == 'include',
              'candidateWords': len(entries), 'targetWords': limit,
              'withMeaningCandidates': coverage['meaningViCandidates'],
              'withIpaCandidates': coverage['ipaCandidates'],
              'withExamplePairCandidates': coverage['examplePairCandidates'],
              'withAudioMetadataCandidates': coverage['audioCandidates'],
              'approvedCompleteWords': 0,
              'blockers': ['Source approval required', 'Sense/meaning/example human QA required',
                           'Commons audio media license, speaker and file must be approved individually',
                           'VOC-DATA-02 canonical schema validation is not performed'],
              'inputs': {'ngsl': ngsl, 'kaikkiSha256': file_hash(kaikki) if kaikki else None,
                         'examplesSha256': file_hash(examples) if examples else None,
                         'audioMetadataSha256': file_hash(audio) if audio else None}}
    return entries, report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo-root', type=Path, default=ROOT)
    p.add_argument('--registry', type=Path)
    p.add_argument('--limit', type=int, default=1000)
    p.add_argument('--kaikki-jsonl', type=Path, help='Downloaded viwiktionary raw JSONL or .gz')
    p.add_argument('--tatoeba-pairs', type=Path, help='Human-curated linked EN/VI candidate JSONL')
    p.add_argument('--commons-metadata', type=Path, help='Metadata JSONL, NO media')
    p.add_argument('--output', type=Path, required=True, help='New research output directory')
    args = p.parse_args()
    root = args.repo_root.resolve()
    registry = args.registry or root / 'docs/data/source-registry.json'
    output = args.output.resolve()
    if output == root / 'app' or (root / 'app') in output.parents:
        p.error('Research artifacts must never be written into Android app/assets')
    try:
        if output.exists():
            raise ValueError('Output directory exists. Choose a new directory to preserve snapshots')
        entries, report = prepare(root, registry, args.limit, args.kaikki_jsonl,
                                  args.tatoeba_pairs, args.commons_metadata)
        output.mkdir(parents=True)
        with (output / 'candidates.jsonl').open('x', encoding='utf-8') as stream:
            for entry in entries:
                stream.write(json.dumps(entry, ensure_ascii=False, sort_keys=True) + '\n')
        (output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
    except (OSError, ValueError, KeyError, TypeError, UnicodeError) as exc:
        p.exit(1, f'BLOCKED: {exc}\n')


if __name__ == '__main__':
    main()
