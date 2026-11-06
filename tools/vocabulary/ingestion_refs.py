"""Read checksum-verified local component artifacts, after the source gate only.

These adapters resolve evidence; they never allocate senses or infer translations.
Keys belong to the pinned artifact, not to the canonical identity registry.
"""
import csv
import io
import json


def read_index(raw, adapter, contract):
    index = {}

    def add(key, component, fields):
        if not isinstance(key, str) or not contract.clean_text(key):
            raise ValueError('Reference record key must be a nonblank string')
        index.setdefault((key, component), []).append(fields)

    if adapter in ('ngsl-csv-v1', 'tsl-csv-v1'):
        ngsl = adapter == 'ngsl-csv-v1'
        text = raw.decode('utf-8-sig' if ngsl else 'cp1252')
        reader = csv.DictReader(io.StringIO(text, newline=''))
        lemma, rank = ('Lemma', 'SFI Rank') if ngsl else ('Word', 'TSL Rank')
        if not {lemma, rank} <= set(reader.fieldnames or []):
            raise ValueError('Unsupported wordlist headers')
        seen = set()
        for row in reader:
            key = row[rank]
            if not key.isascii() or not key.isdecimal() or int(key) < 1 or key in seen:
                raise ValueError('Invalid/duplicate wordlist rank')
            seen.add(key)
            if not contract.clean_text(row[lemma]):
                raise ValueError('Empty wordlist lemma')
            add('rank=' + key, 'lexical', {'lemma': row[lemma]})
        return index

    if adapter not in ('reviewed-jsonl-v1', 'component-jsonl-v1', 'kaikki-jsonl-v1',
                       'tatoeba-linked-jsonl-v1'):
        raise ValueError('Unsupported referenceAdapter: ' + str(adapter))
    lines = raw.decode('utf-8').split('\n')
    if lines[-1] == '':
        lines.pop()
    for number, line in enumerate(lines, 1):
        row = json.loads(line, object_pairs_hook=contract.no_duplicate_keys)
        if not isinstance(row, dict):
            raise ValueError(f'Reference line {number} must be an object')
        if adapter == 'reviewed-jsonl-v1':
            key = row.get('locator')
            for component, fields in (
                    ('lexical', ('lemma', 'pos')), ('meaningVi', ('meaningVi',)),
                    ('examplePair', ('exampleEn', 'exampleVi')), ('ipa', ('ipa',))):
                add(key, component, {f: row.get(f) for f in fields})
        elif adapter == 'component-jsonl-v1':
            component = row.get('component')
            fields = {'lexical': ('lemma', 'pos'), 'meaningVi': ('meaningVi',),
                      'examplePair': ('exampleEn', 'exampleVi'), 'ipa': ('ipa',)}
            if not isinstance(component, str) or component not in fields:
                raise ValueError('Unsupported component')
            add(row.get('key'), component, {f: row.get(f) for f in fields[component]})
        elif adapter == 'tatoeba-linked-jsonl-v1':
            en, vi = row.get('en'), row.get('vi')
            if row.get('linked') is not True or not isinstance(en, dict) or not isinstance(vi, dict):
                raise ValueError('Verified EN/VI pair link required')
            for sentence in (en, vi):
                if type(sentence.get('id')) is not int or sentence['id'] < 1:
                    raise ValueError('Positive sentence ID required')
                if any(not isinstance(sentence.get(f), str) or not contract.clean_text(sentence[f])
                       for f in ('text', 'author', 'license')):
                    raise ValueError('Sentence text, author and license required for both EN/VI')
            add(f'en={en["id"]};vi={vi["id"]}', 'examplePair',
                {'exampleEn': en['text'], 'exampleVi': vi['text']})
        else:
            # Exact extraction line + source sense ordinal; never a canonical senseIndex.
            if row.get('lang_code') != 'en':
                continue
            for ordinal, sense in enumerate(row.get('senses', []), 1):
                key = f'line={number};sense={ordinal}'
                add(key, 'lexical', {'lemma': row.get('word'), 'pos': row.get('pos')})
                for gloss in sense.get('glosses', []):
                    add(key, 'meaningVi', {'meaningVi': gloss})
            for ordinal, sound in enumerate(row.get('sounds', []), 1):
                if sound.get('ipa') is not None:
                    add(f'line={number};sound={ordinal}', 'ipa', {'ipa': sound['ipa']})
    return index


def matches(expected, row, contract, pos_aliases):
    for field, value in expected.items():
        actual = row.get(field)
        if not isinstance(value, str) or not isinstance(actual, str):
            return False
        if field == 'lemma':
            if contract.normalize_lemma(value) != contract.normalize_lemma(actual):
                return False
        elif field == 'pos':
            if pos_aliases.get(value) is None or pos_aliases.get(value) != pos_aliases.get(actual):
                return False
        elif contract.clean_text(value) != contract.clean_text(actual):
            return False
    return True
