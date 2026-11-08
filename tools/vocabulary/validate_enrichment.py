"""Build-time VOC-DATA-05 metadata gate for editorial-accepted-v1."""
import argparse
from collections import Counter
from datetime import date
import hashlib
import json
from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'catalog'))
from build_quiz_policy import dataset_digest

SNAPSHOT = {
    'targetSnapshotId': 'editorial-accepted-v1',
    'targetSnapshotCommit': 'cd83f1ce874a899029a85879c3c40c97343683d6',
    'targetDatasetSha256': 'c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8',
    'targetCanonicalSha256': 'f27e9d2de700fadf13b480df26933c61333ce6261ed872d285e5a111a88110c1',
}
TAXONOMY = 'topic-taxonomy-v1'
VERSION = '0.1.0'
KINDS = {
    'cefr': {'external-sense-cefr', 'editorial-assessment', 'ai-suggestion'},
    'topic': {'external-topic', 'editorial-sense-topic', 'editorial-pack-context', 'ai-suggestion'},
}
SUGGESTION_ONLY = {'ai-suggestion', 'editorial-pack-context'}
STATUSES = ('unknown', 'suggested', 'verified')
DECISIONS = ('pending', 'approved', 'rejected')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(raw):
    def unique(pairs):
        result = dict(pairs)
        require(len(pairs) == len(result), 'duplicate JSON key')
        return result
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def nonblank(value):
    return isinstance(value, str) and bool(value.strip())


def index(rows, key):
    require(isinstance(rows, list), f'{key}: expected an array')
    indexed = {}
    for row in rows:
        identifier = row[key]
        require(nonblank(identifier) and identifier not in indexed, f'invalid or duplicate {key}: {identifier}')
        indexed[identifier] = row
    return indexed


def strings(values, field):
    require(isinstance(values, list) and all(nonblank(v) for v in values), f'{field}: invalid list')
    require(len(set(values)) == len(values), f'{field}: duplicate value')
    return set(values)


def check_date(value):
    require(isinstance(value, str) and date.fromisoformat(value).isoformat() == value, 'invalid review date')


def load_canonical(path):
    raw = path.read_bytes()
    require(digest(raw) == SNAPSHOT['targetCanonicalSha256'], 'canonical file SHA-256 mismatch')
    data = read_json(raw)
    require(dataset_digest(data) == SNAPSHOT['targetDatasetSha256'], 'canonical dataset SHA-256 mismatch')
    entries = index(data['entries'], 'id')
    require(len(entries) == 150 and all(re.fullmatch('[0-9a-f]{16}', key) for key in entries), 'invalid canonical IDs')
    return data, entries


def unknown_sidecar(entries):
    return {'schemaVersion': 1, 'enrichmentVersion': VERSION, **SNAPSHOT, 'taxonomyVersion': TAXONOMY,
            'records': [{'stableId': key, 'cefrLevel': None, 'cefrStatus': 'unknown', 'topicIds': [],
                         'topicStatus': 'unknown', 'evidenceRefs': [], 'reviewDecision': 'pending'}
                        for key in sorted(entries)]}


def verify_artifact(item, base):
    path = Path(item['artifact'])
    resolved = (base / path).resolve()
    require(not path.is_absolute() and resolved.is_relative_to(base) and resolved.is_file(), 'invalid evidence artifact path')
    raw = resolved.read_bytes()
    require(raw and digest(raw) == item['artifactSha256'], 'evidence artifact SHA-256 mismatch')


def load_evidence(ledger, base):
    sources = index(ledger['sources'], 'id')
    evidence = index(ledger['evidence'], 'id')
    for source in sources.values():
        require(source['kind'] in KINDS['cefr'] | KINDS['topic'] | {'frequency-list'}, 'unknown evidence kind')
        require(source['status'] in {'approved', 'held'}, 'unknown source status')
        if source['status'] == 'approved':
            require(all(nonblank(source[field]) for field in ('snapshot', 'permission', 'approvedBy')), 'incomplete source approval')
            check_date(source['approvedAt'])
            verify_artifact(source['approval'], base)
    for item in evidence.values():
        require(item['sourceId'] in sources, 'unknown evidence source')
        source = sources[item['sourceId']]
        require(source['status'] == 'approved', 'evidence source held')
        require(item['field'] in KINDS and source['kind'] in KINDS[item['field']], 'evidence kind cannot support field')
        require(nonblank(item['rationale']) and strings(item['values'], 'evidence values'), 'incomplete evidence')
        verify_artifact(item, base)
        if source['kind'] == 'ai-suggestion':
            require(all(nonblank(item[field]) for field in ('model', 'provider', 'promptVersion')), 'incomplete AI provenance')
            check_date(item['createdAt'])
    return sources, evidence


def claim_digest(row, topics, sources, evidence):
    items = [evidence[key] for key in sorted(row['evidenceRefs'])]
    return dataset_digest({'target': SNAPSHOT, 'claim': row,
                           'topics': [topics[key] for key in row['topicIds']], 'evidence': items,
                           'sources': [sources[key] for key in sorted({item['sourceId'] for item in items})]})


def verify_row(row, topics, sources, evidence, review, review_base):
    require(set(row) == {'stableId', 'cefrLevel', 'cefrStatus', 'topicIds', 'topicStatus',
                         'evidenceRefs', 'reviewDecision'}, 'invalid sidecar row fields')
    level = row['cefrLevel']
    require(level is None or level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'), 'invalid CEFR')
    topic_ids = strings(row['topicIds'], 'topic IDs')
    require(topic_ids <= topics.keys(), 'unknown topic')
    refs = strings(row['evidenceRefs'], 'evidence refs')
    require(refs <= evidence.keys(), 'unknown evidence reference')
    items = [evidence[key] for key in refs]
    require(all(item['stableId'] == row['stableId'] for item in items), 'evidence belongs to a different sense')
    for field, values in (('cefr', {level} if level else set()), ('topic', topic_ids)):
        status = row[field + 'Status']
        require(status in STATUSES and (status == 'unknown') == (not values), 'status and values disagree')
        supported = set()
        for item in (item for item in items if item['field'] == field):
            require(set(item['values']) <= values, 'conflicting evidence values')
            if status != 'verified' or sources[item['sourceId']]['kind'] not in SUGGESTION_ONLY:
                supported.update(item['values'])
        require(supported == values, f'{field} lacks eligible evidence')
    decision = row['reviewDecision']
    require(decision in DECISIONS, 'invalid review decision')
    require(not any(row[field + 'Status'] == 'verified' for field in KINDS) or decision == 'approved',
            'verified label requires approval')
    if decision == 'pending':
        require(review is None, 'unexpected decision for pending row')
    else:
        require(refs and review and review['decision'] == decision and nonblank(review['reviewedBy']), 'missing review evidence')
        check_date(review['reviewedAt'])
        require(review['claimSha256'] == claim_digest(row, topics, sources, evidence), 'review binds a different claim')
        verify_artifact(review, review_base)


def summarize(rows):
    return {'total': len(rows),
            **{field: dict.fromkeys(STATUSES, 0) | Counter(row[field + 'Status'] for row in rows) for field in KINDS},
            'review': dict.fromkeys(DECISIONS, 0) | Counter(row['reviewDecision'] for row in rows)}


def validate(sidecar, data, entries, taxonomy, ledger, reviews, sources_base, review_base):
    for doc in (sidecar, taxonomy, ledger, reviews):
        require(type(doc['schemaVersion']) is int and doc['schemaVersion'] == 1, 'invalid schema version')
    for doc in (sidecar, reviews):
        require(all(doc.get(key) == value for key, value in SNAPSHOT.items()), 'snapshot binding mismatch')
    require(re.fullmatch(r'\d+\.\d+\.\d+', sidecar['enrichmentVersion'])
            and sidecar['enrichmentVersion'] == reviews['enrichmentVersion'], 'invalid enrichment version')
    require(sidecar['taxonomyVersion'] == taxonomy['taxonomyVersion'] == TAXONOMY, 'taxonomy version mismatch')
    require(set(sidecar) == {'schemaVersion', 'enrichmentVersion', 'taxonomyVersion', 'records'} | SNAPSHOT.keys(),
            'unexpected sidecar fields')
    topics = index(taxonomy['topics'], 'id')
    require(topics and all(re.fullmatch('[a-z][a-z0-9-]*', key) and all(nonblank(row[field]) for field in
            ('label', 'scope', 'include', 'exclude', 'reviewOwner')) for key, row in topics.items()), 'invalid topic taxonomy')
    sources, evidence = load_evidence(ledger, sources_base)
    records = index(sidecar['records'], 'stableId')
    decisions = index(reviews['decisions'], 'stableId')
    require(records.keys() == entries.keys() and list(records) == sorted(records), 'stable ID coverage or order mismatch')
    require(decisions.keys() <= records.keys(), 'review has unknown stable ID')
    for key, row in records.items():
        verify_row(row, topics, sources, evidence, decisions.get(key), review_base)
    require(evidence.keys() == {ref for row in records.values() for ref in row['evidenceRefs']}, 'unused evidence')
    return {'result': 'PASS_ENRICHMENT_CHECKS', **SNAPSHOT,
            'enrichmentVersion': sidecar['enrichmentVersion'], 'taxonomyVersion': TAXONOMY,
            'coverage': summarize(list(records.values())),
            'byPack': {pack['id']: summarize([records[word['vocabularyId']] for word in data['packWords']
                                             if word['packId'] == pack['id']]) for pack in data['packs']},
            **{key: {value: summarize([records[eid] for eid, row in entries.items() if row[field] == value])
                     for value in sorted({row[field] for row in entries.values()})}
               for key, field in (('byPos', 'pos'), ('byCanonicalSource', 'sourceId'))},
            'labelSources': {key: sum(item['sourceId'] == key for item in evidence.values()) for key in sorted(sources)},
            'conflicts': 0, 'errors': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--input', type=Path)
    mode.add_argument('--generate-unknown', action='store_true')
    parser.add_argument('--canonical', type=Path, required=True)
    for name in ('taxonomy', 'sources', 'reviews'):
        parser.add_argument('--' + name, type=Path)
    args = parser.parse_args()
    if args.input and not all((args.taxonomy, args.sources, args.reviews)):
        parser.error('--input requires --taxonomy, --sources and --reviews')
    try:
        data, entries = load_canonical(args.canonical)
        if args.generate_unknown:
            report = unknown_sidecar(entries)
        else:
            raw = {key: getattr(args, key).read_bytes() for key in ('input', 'taxonomy', 'sources', 'reviews')}
            report = validate(read_json(raw['input']), data, entries, read_json(raw['taxonomy']),
                              read_json(raw['sources']), read_json(raw['reviews']),
                              args.sources.resolve().parent, args.reviews.resolve().parent)
            report['inputSha256'] = {key: digest(value) for key, value in raw.items()}
        sys.stdout.buffer.write((json.dumps(report, indent=2) + '\n').encode('utf-8'))
    except (ValueError, TypeError, KeyError, AttributeError, OSError) as error:
        print(f'ENRICHMENT_INVALID: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
