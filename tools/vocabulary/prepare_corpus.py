"""Prepare the VOC-DATA-04 editorial review corpus. Never approves or publishes it."""
from __future__ import annotations

import argparse
from collections import Counter
import csv
import io
import json
from pathlib import Path
import sys

import ingest
import quality
from ingestion_refs import read_index, matches
from prepare_1000_candidates import load_ngsl

ROOT = ingest.ROOT
DRAFT = Path('docs/data/voc-data-04/editorial-v1.tsv')
SNAPSHOT = Path('docs/data/voc-data-04/corpus-v1')
STAGING = (SNAPSHOT / 'staging.jsonl').as_posix()
EDITORIAL = 'vocamind-editorial-draft-v1'
NGSL = 'ngsl-12-20261025'
PACKS = {
    'basic': ('pack_toeic_basic', 'TOEIC Căn bản', 'toeic'),
    'office': ('pack_toeic_office', 'TOEIC Công sở & Giao dịch', 'toeic'),
    'daily': ('pack_daily_life', 'Tiếng Anh Đời sống Hàng ngày', 'general'),
    'academic': ('pack_academic', 'Từ vựng Học thuật Thiết yếu', 'academic'),
}
QUOTAS = dict(zip(PACKS, (40, 40, 40, 30)))
VERSION = '1.0.0'


def jsonl(rows):
    return b''.join((json.dumps(row, ensure_ascii=False, separators=(',', ':'), allow_nan=False) + '\n')
                    .encode('utf-8') for row in rows)


def compile_corpus(root=ROOT):
    """Deterministic proposal artifacts; existing registry and app assets stay read only."""
    root = root.resolve()
    registry_path = root / 'docs/data/source-registry.json'
    ranked, source_info = load_ngsl(root, registry_path, 2809)
    ranks = {normalized: rank for rank, _, normalized in ranked}
    source_registry = ingest.contract.load_json(registry_path)
    ngsl = next(s for s in source_registry['sources'] if s['id'] == 'ngsl')
    source_artifact = next(a for a in ngsl['snapshot']['artifacts'] if a['role'] == 'data')
    # TSV bytes are an authored source snapshot, not a frozen upstream dictionary.
    raw = (root / DRAFT).read_bytes()
    reader = csv.DictReader(io.StringIO(raw.decode('utf-8')), delimiter='\t')
    expected = ['pack', 'lemma', 'pos', 'senseIndex', 'meaningVi', 'exampleEn', 'exampleVi']
    ingest.require(reader.fieldnames == expected, 'Unexpected editorial TSV header')
    rows = list(reader)
    ingest.require(len(rows) == 150, 'This corpus proposal requires exactly 150 edited rows')
    staging, entries, identities, proposals, memberships, review_rows = [], [], [], [], [], []
    seen, counts, lexical_matches = set(), Counter(), 0
    for physical_line, row in enumerate(rows, 2):
        ingest.require(set(row) == set(expected) and all(isinstance(row[k], str) for k in expected),
                       f'Malformed TSV fields at line {physical_line}')
        row = {k: ingest.contract.clean_text(v) for k, v in row.items()}
        ingest.require(all(row.values()), f'Empty editorial field at line {physical_line}')
        ingest.require(row['pack'] in PACKS and row['pos'] in ingest.POS and ingest.POS[row['pos']] == row['pos'],
                       f'Unmapped pack/POS at line {physical_line}')
        ingest.require(row['senseIndex'].isascii() and row['senseIndex'].isdecimal()
                       and int(row['senseIndex']) >= 1, f'Explicit proposed senseIndex required at line {physical_line}')
        lemma = ingest.contract.normalize_lemma(row['lemma'])
        pos, sense = row['pos'], int(row['senseIndex'])
        eid = ingest.contract.stable_id(lemma, pos, sense)
        ingest.require(eid not in seen, f'Duplicate proposed identity at line {physical_line}')
        seen.add(eid)
        key = f'{lemma}/{pos}/{sense}'
        refs = [{'component': component, 'sourceId': EDITORIAL, 'locator': STAGING + '#' + key}
                for component in ('lexical', 'meaningVi', 'examplePair')]
        if lemma in ranks:
            lexical_matches += 1
            refs.append({'component': 'lexical', 'sourceId': NGSL,
                         'locator': source_artifact['path'] + '#rank=' + str(ranks[lemma])})
        refs.sort(key=lambda r: (r['component'], r['sourceId'], r['locator']))
        # No published ID is allocated; the integer here is a reviewed-sense PROPOSAL,
        # never an upstream rank. The preview envelope uses it only for contract checks.
        stage = {'lemma': row['lemma'], 'pos': pos, 'senseIndex': sense,
                 'meaningVi': row['meaningVi'], 'exampleEn': row['exampleEn'],
                 'exampleVi': row['exampleVi'], 'ipa': None, 'sourceId': EDITORIAL,
                 'provenance': refs, 'locator': key}
        staging.append(stage)
        entry = {k: stage[k] for k in ('lemma', 'pos', 'senseIndex', 'meaningVi', 'exampleEn', 'exampleVi', 'ipa', 'sourceId', 'provenance')}
        entry.update(id=eid, normalizedLemma=lemma, contentVersion=VERSION)
        entries.append(ingest.ordered(entry, 'entry'))
        identity = {'id': eid, 'identityLemma': lemma, 'pos': pos, 'senseIndex': sense,
                    'fullSha256': ingest.contract.identity_hash(lemma, pos, sense),
                    'senseDefinition': row['meaningVi'], 'status': 'active'}
        identities.append(ingest.ordered(identity, 'identity'))
        pid = PACKS[row['pack']][0]
        memberships.append({'packId': pid, 'vocabularyId': eid, 'orderNo': counts[row['pack']]})
        counts[row['pack']] += 1
        proposals.append({'locator': key, 'stableId': None, 'proposedStableId': eid,
                          'proposedSenseIndex': sense, 'status': 'pending', 'packId': pid,
                          'inputRef': DRAFT.as_posix() + '#line=' + str(physical_line),
                          'selection': {'ngslRank': ranks.get(lemma),
                                        'method': 'NGSL lexical match' if lemma in ranks else 'editorial headword selection'},
                          'authorship': 'AI-assisted editorial draft; no human author or reviewer asserted',
                          'licenseApproval': 'pending', 'semanticReview': 'pending'})
        review_rows.append({'stableId': eid, 'entrySha256': quality.entry_digest(entries[-1]),
                            'reviewer': None, 'reviewedAt': None, 'decision': 'pending',
                            **{check: False for check in quality.CHECKS}})
    ingest.require(dict(counts) == QUOTAS, 'Editorial pack counts differ from the 40/40/40/30 proposal')
    staging_bytes = jsonl(staging)
    editorial_hash = ingest.digest(staging_bytes)
    indexes = {EDITORIAL: read_index(staging_bytes, 'reviewed-jsonl-v1', ingest.contract),
               NGSL: read_index((root / source_artifact['path']).read_bytes(), 'ngsl-csv-v1', ingest.contract)}
    verified_refs = 0
    for entry in entries:
        for ref in entry['provenance']:
            key = ref['locator'].split('#', 1)[1]
            evidence = indexes[ref['sourceId']].get((key, ref['component']), [])
            ingest.require(any(matches(record, entry, ingest.contract, ingest.POS) for record in evidence),
                           'Component reference mismatch: ' + ref['locator'])
            verified_refs += 1
    repo_doc = 'https://github.com/ManhT005/VocaMind/blob/feature/voc-data-04-quality-gates/docs/data/voc-data-04/README.md'
    sources = [
        {'id': EDITORIAL, 'title': 'VocaMind AI-assisted editorial review draft v1',
         'url': repo_doc, 'licenseCode': 'PENDING-EDITORIAL-RIGHTS', 'licenseUrl': repo_doc,
         'snapshotDate': '2026-11-05', 'snapshotSha256': editorial_hash,
         'attributionText': 'AI-assisted draft meanings and bilingual examples prepared for VocaMind; rights and human content review pending.',
         'approvalStatus': 'pending', 'approvedBy': None, 'evidenceRef': None},
        {'id': NGSL, 'title': ngsl['title'], 'url': source_artifact['url'],
         'licenseCode': ngsl['licenseCode'], 'licenseUrl': ngsl['licenseUrl'],
         'snapshotDate': ngsl['snapshot']['retrievedAt'][:10], 'snapshotSha256': source_artifact['sha256'],
         'attributionText': 'NGSL 1.2: Charles Browne, Brent Culligan, Joseph Phillips. Lexical selection only; no glosses or sentences copied.',
         'approvalStatus': 'pending', 'approvedBy': None, 'evidenceRef': None},
    ]
    packs = [{'id': pid, 'title': title, 'description': 'Bản nháp học liệu chờ duyệt nội dung và quyền.',
              'category': category, 'orderNo': index, 'version': VERSION}
             for index, (pid, title, category) in enumerate(PACKS.values())]
    data = {'schemaVersion': VERSION, 'contentVersion': VERSION, 'fixtureOnly': False,
            'sources': sorted(sources, key=lambda s: s['id']),
            'entries': sorted(entries, key=lambda e: e['id']),
            'identityRegistry': sorted(identities, key=lambda i: i['id']), 'packs': packs,
            'packWords': sorted(memberships, key=lambda m: (m['packId'], m['orderNo'], m['vocabularyId']))}
    errors = ingest.contract.validate(data)
    ingest.require(not errors, 'Draft contract errors: ' + '; '.join(errors))
    reviews = {'schemaVersion': VERSION, 'fixtureOnly': False, 'datasetSha256': quality.entry_digest(data),
               'records': sorted(review_rows, key=lambda r: r['stableId'])}
    normalized, issues, changes, pack_stats = quality.evaluate(data, reviews)
    ingest.require(normalized == data and not changes, 'Draft is not idempotently normalized')
    blocking = [i for i in issues if i['severity'] == 'error']
    # Development checks do not establish source authorization. Exercise actual
    # authorize() separately and preserve exact blocking reasons in the dossier.
    source_decisions = []
    for registry_id in ('ngsl', 'vocamind-editorial'):
        try:
            ingest.authorize(source_registry, registry_id, root)
            reason = None
        except (ValueError, OSError) as exc:
            reason = str(exc)
        source_decisions.append({'registryId': registry_id, 'authorized': reason is None, 'reason': reason})
    report = {'status': 'candidate-only', 'releaseEligible': False, 'productionQcPassed': False,
              'inputSha256': ingest.digest(raw), 'stagingSha256': editorial_hash,
              'candidateDatasetSha256': quality.entry_digest(data),
              'counts': {'candidateSenses': len(entries), 'proposedIds': len(seen), 'publishedIds': 0,
                         'memberships': len(memberships), 'ngslLexicalMatches': lexical_matches,
                         'editorialOnlyHeadwords': len(entries) - lexical_matches, 'humanApprovedRecords': 0},
              'developmentContractErrors': errors, 'packs': pack_stats,
              'componentReferencesVerified': verified_refs,
              'sources': source_decisions, 'qcGateExitCode': 1 if blocking else 0,
              'errorsByCode': dict(sorted(Counter(i['errorCode'] for i in blocking).items())),
              'warningsByCode': dict(sorted(Counter(i['errorCode'] for i in issues if i['severity'] == 'warning').items())),
              'issues': issues,
              'reviewFocus': ['Check kinship translations: parent/brother/sister are one English relation with Vietnamese age/gender variants.',
                              'Review research versus study and home versus house; different strings do not prove semantic distinction.',
                              'Check English POS/selected sense, exact EN/VI correspondence and every warning.',
                              'Editorial rights route is not registered in the seven-source ledger; requires an explicit reviewed extension, not relabeling as NGSL.']}
    config = {'toolVersion': ingest.VERSION, 'adapter': ingest.ADAPTER, 'schemaVersion': VERSION,
              'contentVersion': VERSION, 'fixtureOnly': False, 'encoding': 'utf-8',
              'input': STAGING, 'identityRegistry': (SNAPSHOT / 'identity-registry-proposal.json').as_posix(),
              'baseline': None, 'sourceRegistry': 'docs/data/source-registry.json', 'inventory': 'docs/data/snapshot.json',
              'sources': [
                  {'registryId': 'ngsl', 'canonicalId': NGSL, 'snapshotId': source_info['snapshotId'],
                   'upstreamVersion': ngsl['snapshot']['upstreamVersion'], 'artifact': source_artifact['path'],
                   'sha256': source_artifact['sha256'], 'referenceAdapter': 'ngsl-csv-v1'},
                  {'registryId': 'vocamind-editorial', 'canonicalId': EDITORIAL, 'snapshotId': 'editorial-draft-v1',
                   'upstreamVersion': VERSION, 'artifact': STAGING, 'sha256': editorial_hash,
                   'referenceAdapter': 'reviewed-jsonl-v1'}],
              'packs': packs, 'packWords': data['packWords']}
    worksheet = ['# VOC-DATA-04 — 150-sense editorial review worksheet', '',
                 '**DRAFT: no source approval, no allocated identities, no human review.**', '',
                 'Fill decisions in reviews-pending.json only after inspecting each record and its pack.',
                 'Rights/registry/identity proposals require their own owner decisions before production ingestion.', '',
                 '| Proposed ID / source row | Pack / lemma / POS | Selected meaning | EN example | VI translation |',
                 '| --- | --- | --- | --- | --- |']
    by_id = {e['id']: e for e in entries}
    for p in proposals:
        e = by_id[p['proposedStableId']]
        worksheet.append(f"| {e['id']} / {p['inputRef']} | {p['packId']} / {e['lemma']} / {e['pos']} | {e['meaningVi']} | {e['exampleEn']} | {e['exampleVi']} |")
    worksheet += ['', '## Review focus', '', *('- ' + note for note in report['reviewFocus']), '']
    artifacts = {'staging.jsonl': staging_bytes, 'candidate-catalog.json': ingest.encoded(data),
                 'identity-registry-proposal.json': ingest.encoded(data['identityRegistry']),
                 'proposals.json': ingest.encoded(proposals), 'reviews-pending.json': ingest.encoded(reviews),
                 'candidate-qc-report.json': ingest.encoded(report), 'ingestion-proposal.json': ingest.encoded(config),
                 'review-worksheet.md': '\n'.join(worksheet).encode('utf-8')}
    artifacts['candidate-manifest.json'] = ingest.encoded({
        'status': 'candidate-only', 'releaseEligible': False, 'productionQcPassed': False,
        'toolVersion': VERSION, 'inputHashes': {DRAFT.as_posix(): ingest.digest(raw),
                                             source_artifact['path']: source_artifact['sha256']},
        'sourceRegistrySemanticSha256': quality.entry_digest(source_registry),
        'toolSha256': ingest.digest(Path(__file__).read_bytes().replace(b'\r\n', b'\n')),
        'outputHashes': {name: ingest.digest(value) for name, value in sorted(artifacts.items())}})
    return artifacts, report


def run(output, *, root=ROOT, require_approved=False):
    root, output = root.resolve(), output.resolve()
    allowed = (root / 'build/vocabulary-corpus', root / SNAPSHOT)
    ingest.require(not output.exists() and any(output == p or output.is_relative_to(p) for p in allowed),
                   'Choose a new directory under build/vocabulary-corpus or the versioned corpus snapshot')
    artifacts, report = compile_corpus(root)
    output.mkdir(parents=True)
    for name, raw in artifacts.items():
        (output / name).write_bytes(raw)
    print(f"Prepared {report['counts']['candidateSenses']} draft senses; production QC BLOCKED; no published identities")
    return report['qcGateExitCode'] if require_approved else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--require-approved', action='store_true', help='Return nonzero for blocked production QC')
    args = parser.parse_args()
    try:
        return run(args.output, require_approved=args.require_approved)
    except (ValueError, TypeError, KeyError, OSError, UnicodeError) as exc:
        print(f'CORPUS_INPUT_ERROR: {exc}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
