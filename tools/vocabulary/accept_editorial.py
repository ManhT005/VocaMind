"""Materialize the owner's explicit worksheet acceptance; never infer approvals."""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path

import ingest
import quality
from gate_sources import registry_digest
from source_gate import authorize, snapshot_content_digest

ROOT = ingest.ROOT
BASE = Path('docs/data/voc-data-04')
DRAFT = BASE / 'corpus-v1'
ACCEPTED = BASE / 'accepted-v1'
DECISION = BASE / 'owner-acceptance.json'
SOURCE_ID = 'vocamind-ai-editorial-v1'
REGISTRY_ID = 'vocamind-ai-editorial'
URL = 'https://github.com/ManhT005/VocaMind/blob/feature/voc-data-04-quality-gates/'
VERSION = '1.0.0'


def compile_accepted(root=ROOT):
    root = root.resolve()
    decision = ingest.contract.load_json(root / DECISION)
    ingest.require(decision.get('decision') == 'accepted' and decision.get('reviewer') and
                   decision.get('reviewedAt'), 'Explicit owner worksheet acceptance required')
    for relative, key in ((BASE / 'editorial-v1.tsv', 'draftSha256'),
                          (DRAFT / 'review-worksheet.md', 'worksheetSha256'),
                          (DRAFT / 'candidate-catalog.json', 'candidateSha256')):
        ingest.require(ingest.digest((root / relative).read_bytes()) == decision[key],
                       'Owner acceptance is stale: ' + str(relative))
    # Validate all historical draft files before using any proposed content.
    manifest = ingest.contract.load_json(root / DRAFT / 'candidate-manifest.json')
    ingest.require(manifest['inputHashes'][(BASE / 'editorial-v1.tsv').as_posix()] ==
                   decision['draftSha256'], 'Draft/owner input mismatch')
    for name, checksum in manifest['outputHashes'].items():
        ingest.require(Path(name).name == name and
                       ingest.digest((root / DRAFT / name).read_bytes()) == checksum,
                       'Draft artifact changed: ' + name)
    data = copy.deepcopy(ingest.contract.load_json(root / DRAFT / 'candidate-catalog.json'))
    staging = []
    for entry in data['entries']:
        key = f"{entry['normalizedLemma']}/{entry['pos']}/{entry['senseIndex']}"
        entry['sourceId'] = SOURCE_ID
        entry['provenance'] = [
            {'component': part, 'sourceId': SOURCE_ID,
             'locator': (ACCEPTED / 'staging.jsonl').as_posix() + '#' + key}
            for part in ('examplePair', 'lexical', 'meaningVi')]
        stage = {k: entry[k] for k in ('lemma', 'pos', 'senseIndex', 'meaningVi',
                                      'exampleEn', 'exampleVi', 'ipa', 'sourceId', 'provenance')}
        stage['locator'] = key
        staging.append(stage)
    # The original lexical/rank lineage is retained in corpus-v1. This accepted
    # route makes no claim that NGSL supplied or approved editorial components.
    stage_bytes = b''.join((json.dumps(row, ensure_ascii=False, separators=(',', ':')) + '\n')
                           .encode('utf-8') for row in staging)
    stage_hash = ingest.digest(stage_bytes)
    credit = ('VocaMind AI-generated editorial text. Project use and all 150 worksheet records '
              'accepted by ' + decision['reviewer'] + ' on ' + decision['reviewedAt'] +
              '. No external dictionary supplied meanings or example pairs. Historical lexical '
              'research references remain in corpus-v1; this is not an external license grant.\n').encode('utf-8')
    acceptance = ingest.encoded(decision)
    files = {'staging.jsonl': stage_bytes, 'attribution.txt': credit, 'acceptance.json': acceptance}
    artifacts = []
    for name, role in (('staging.jsonl', 'data'), ('attribution.txt', 'attribution'),
                       ('acceptance.json', 'approval')):
        relative = (ACCEPTED / name).as_posix()
        artifacts.append({'role': role, 'path': relative, 'url': URL + relative,
                          'bytes': len(files[name]), 'sha256': ingest.digest(files[name])})
    source = {'id': REGISTRY_ID, 'title': 'VocaMind AI editorial corpus v1',
              'origin': 'project-ai-generated', 'licenseCode': 'PROJECT-USE-OWNER-ACCEPTED',
              'licenseUrl': URL + DECISION.as_posix(), 'decision': 'include',
              'pipelineEligible': True, 'packagingStatus': 'approved', 'blockers': [],
              'snapshot': {'id': 'editorial-accepted-v1', 'upstreamVersion': VERSION,
                           'retrievedAt': decision['reviewedAt'] + 'T00:00:00Z',
                           'artifacts': artifacts},
              'review': {'role': 'Project owner', 'decision': 'accepted',
                         'reviewer': decision['reviewer'], 'reviewedAt': decision['reviewedAt'],
                         'snapshotId': 'editorial-accepted-v1'}}
    source['review']['snapshotSha256'] = snapshot_content_digest(source['snapshot'], source)
    registry = {'schemaVersion': 1, 'purpose': 'project-generated-content', 'sources': [source]}
    inventory = {'schemaVersion': 1, 'purpose': 'research-inventory',
                 'registrySha256': registry_digest(registry),
                 'sources': {REGISTRY_ID: artifacts}, 'errors': []}
    data['sources'] = [{
        'id': SOURCE_ID, 'title': source['title'], 'url': artifacts[0]['url'],
        'licenseCode': source['licenseCode'], 'licenseUrl': source['licenseUrl'],
        'snapshotDate': decision['reviewedAt'], 'snapshotSha256': stage_hash,
        'attributionText': ingest.contract.clean_text(credit.decode('utf-8')),
        'approvalStatus': 'approved', 'approvedBy': decision['reviewer'],
        'evidenceRef': (ACCEPTED / 'acceptance.json').as_posix()}]
    # Keep wording, IDs, membership and pack metadata unchanged from the worksheet.
    ingest.require(not ingest.contract.validate(data), 'Accepted canonical contract failed')
    reviews = {'schemaVersion': VERSION, 'fixtureOnly': False,
               'datasetSha256': quality.entry_digest(data),
               'records': [{'stableId': e['id'], 'entrySha256': quality.entry_digest(e),
                            'reviewer': decision['reviewer'], 'reviewedAt': decision['reviewedAt'],
                            'decision': 'approved', **{key: True for key in quality.CHECKS}}
                           for e in data['entries']]}
    _, issues, changes, _ = quality.evaluate(data, reviews)
    ingest.require(not changes and not [i for i in issues if i['severity'] == 'error'],
                   'Accepted content QC failed')
    noun_ids = {e['normalizedLemma']: e['id'] for e in data['entries'] if e['pos'] == 'noun'}
    pairs = [[noun_ids[a], noun_ids[b]]
             for a, b in decision['quizPolicy']['forbiddenLemmaPairs']]
    policy = {'schemaVersion': 1, 'datasetSha256': quality.entry_digest(data),
              'scope': decision['quizPolicy']['scope'], 'forbiddenDistractorPairs': pairs,
              'reason': 'Overlapping translations; different keys do not prove semantic distinction.'}
    config = {'toolVersion': ingest.VERSION, 'adapter': ingest.ADAPTER,
              'schemaVersion': VERSION, 'contentVersion': VERSION, 'fixtureOnly': False,
              'encoding': 'utf-8', 'input': (ACCEPTED / 'staging.jsonl').as_posix(),
              'identityRegistry': (ACCEPTED / 'identity-registry.json').as_posix(),
              'baseline': None, 'sourceRegistry': (ACCEPTED / 'source-registry.json').as_posix(),
              'inventory': (ACCEPTED / 'inventory.json').as_posix(),
              'sources': [{'registryId': REGISTRY_ID, 'canonicalId': SOURCE_ID,
                           'snapshotId': 'editorial-accepted-v1', 'upstreamVersion': VERSION,
                           'artifact': (ACCEPTED / 'staging.jsonl').as_posix(),
                           'sha256': stage_hash, 'referenceAdapter': 'reviewed-jsonl-v1'}],
              'packs': data['packs'], 'packWords': data['packWords']}
    files.update({
        'source-registry.json': ingest.encoded(registry), 'inventory.json': ingest.encoded(inventory),
        'identity-registry.json': ingest.encoded(data['identityRegistry']),
        'canonical-preview.json': ingest.encoded(data), 'reviews.json': ingest.encoded(reviews),
        'quiz-policy.json': ingest.encoded(policy), 'ingestion-config.json': ingest.encoded(config)})
    return files


def load_handoff(qc_directory, snapshot=ROOT / ACCEPTED):
    """Return validated content AND its dataset-bound distractor restrictions."""
    data = quality.load_validated(qc_directory)
    snapshot = Path(snapshot).resolve()
    root = snapshot.parents[3]
    ingest.require(snapshot == root / ACCEPTED, 'Accepted snapshot location required')
    return verify_handoff(data, snapshot, root)


def verify_handoff(data, snapshot, root):
    registry = ingest.contract.load_json(snapshot / 'source-registry.json')
    source = authorize(registry, REGISTRY_ID, root)
    ingest.require(len(data['sources']) == 1 and
                   data['sources'][0]['id'] == SOURCE_ID and
                   data['sources'][0]['snapshotSha256'] ==
                   next(a['sha256'] for a in source['snapshot']['artifacts'] if a['role'] == 'data'),
                   'Handoff source differs from accepted snapshot')
    policy = ingest.contract.load_json(Path(snapshot) / 'quiz-policy.json')
    ingest.require(policy.get('schemaVersion') == 1 and
                   policy.get('datasetSha256') == quality.entry_digest(data),
                   'Quiz policy must match the validated dataset')
    ids = {entry['id'] for entry in data['entries']}
    pairs = policy.get('forbiddenDistractorPairs')
    ingest.require(isinstance(pairs, list) and len(pairs) == 3 and
                   all(isinstance(pair, list) and len(pair) == 2 and pair[0] != pair[1] and
                       all(eid in ids for eid in pair) for pair in pairs),
                   'Invalid distractor exclusions')
    decision = ingest.contract.load_json(Path(snapshot) / 'acceptance.json')
    by_lemma = {(entry['normalizedLemma'], entry['pos']): entry['id'] for entry in data['entries']}
    expected = [[by_lemma[(a, 'noun')], by_lemma[(b, 'noun')]]
                for a, b in decision['quizPolicy']['forbiddenLemmaPairs']]
    ingest.require(pairs == expected, 'Quiz policy differs from accepted restrictions')
    return data, policy


def can_be_distractor(policy, target_id, candidate_id):
    """Apply in both directions across all active packs, before sampling."""
    return target_id != candidate_id and not any(
        {target_id, candidate_id} == set(pair) for pair in policy['forbiddenDistractorPairs'])


def run(output, root=ROOT):
    root, output = root.resolve(), output.resolve()
    ingest.require(not output.exists() and (output == root / ACCEPTED or
                   output.is_relative_to(root / 'build/vocabulary-corpus')),
                   'Choose a new accepted snapshot or build/vocabulary-corpus directory')
    files = compile_accepted(root)
    output.mkdir(parents=True)
    for name, raw in files.items():
        (output / name).write_bytes(raw)
    print('Prepared 150 owner-accepted records; run ingestion and production QC before handoff.')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--output', type=Path)
    mode.add_argument('--verify', type=Path, help='Verify a production QC directory and Quiz handoff')
    args = parser.parse_args()
    try:
        if args.verify:
            data, policy = load_handoff(args.verify)
            ingest.require(not ingest.contract.validate(data, release=True), 'Release contract failed')
            print(json.dumps({'status': 'pass', 'entries': len(data['entries']),
                              'datasetSha256': quality.entry_digest(data),
                              'forbiddenDistractorPairs': policy['forbiddenDistractorPairs'],
                              'fixtureOnly': data['fixtureOnly']}, ensure_ascii=False, indent=2))
            return 0
        return run(args.output)
    except (ValueError, KeyError, TypeError, OSError) as exc:
        parser.exit(1, f'ACCEPTANCE_BLOCKED: {exc}\n')


if __name__ == '__main__':
    raise SystemExit(main())
