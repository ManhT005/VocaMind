"""Export a required, dataset-bound Quiz policy companion without changing Room schema.

For real content supply the policy returned by the validated VOC-DATA-04 handoff.
Omitting it is allowed only for explicitly synthetic canonical fixtures.
"""
import argparse
import hashlib
import json
from pathlib import Path


def dataset_digest(data):
    return hashlib.sha256(json.dumps(data, sort_keys=True, ensure_ascii=False,
                                    separators=(',', ':'), allow_nan=False).encode('utf-8')).hexdigest()


def projection_digest(entries):
    digest = hashlib.sha256()
    ids = set()
    for row in sorted(entries, key=lambda e: e['id']):
        if row['id'] in ids:
            raise ValueError('Duplicate catalog ID')
        ids.add(row['id'])
        for field in ('id', 'lemma', 'pos', 'meaningVi', 'exampleEn'):
            encoded = row[field].encode('utf-8')
            digest.update(len(encoded).to_bytes(4, 'big'))
            digest.update(encoded)
    return digest.hexdigest()


def companion(data, policy=None):
    dataset = dataset_digest(data)
    if policy is None:
        if data.get('fixtureOnly') is not True:
            raise ValueError('Real content requires an explicit validated handoff policy')
        pairs = []
    else:
        if type(policy.get('schemaVersion')) is not int or policy['schemaVersion'] != 1:
            raise ValueError('Unsupported policy schema')
        if policy.get('datasetSha256') != dataset:
            raise ValueError('Policy belongs to a different canonical dataset')
        pairs = policy.get('forbiddenDistractorPairs')
        ids = {row['id'] for row in data['entries']}
        if not isinstance(pairs, list) or any(
                not isinstance(pair, list) or len(pair) != 2 or
                any(not isinstance(eid, str) or eid not in ids for eid in pair) or
                pair[0] == pair[1] for pair in pairs):
            raise ValueError('Invalid policy references')
    return {'schemaVersion': 1, 'datasetSha256': dataset,
            'projectionSha256': projection_digest(data['entries']),
            'forbiddenDistractorPairs': pairs}


def write(path, data, policy=None):
    result = companion(data, policy)
    Path(path).write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--canonical', type=Path, required=True)
    parser.add_argument('--policy', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    read = lambda path: json.loads(path.read_text(encoding='utf-8'))
    write(args.output, read(args.canonical), read(args.policy) if args.policy else None)
