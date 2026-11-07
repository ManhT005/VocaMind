#!/usr/bin/env python3
"""Build a staged, owner-accepted catalog candidate; never authorize publication."""

from __future__ import annotations

import argparse
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(ROOT / 'tools/vocabulary'))

import accept_editorial
import build_quiz_policy
import check_credits
import catalog_snapshot
import ingest
import quality

SCHEMA_PATH = Path('app/schemas/com.vocamind.app.data.catalog.CatalogDatabase/2.json')
KIND = 'owner-accepted-release-candidate-not-authorized-for-distribution'
OUTPUTS = {
    'catalog.db', 'catalog.metadata.json', 'quiz-policy.json',
    'content-credits.json', 'release-gate-report.json',
}
RELEASE_BLOCKERS = [
    'PO/Content Lead distribution rights and verified author ledger required',
    'Reference-device offline UI, APK checksum/size and 5000-word benchmark required',
    'DATA-08 installed-catalog upgrade/rollback and userdata preservation required',
]
TABLES = (
    ('ContentSource', 'sources',
     'id,title,url,licenseCode,licenseUrl,snapshotDate,attributionText'),
    ('VocabularyEntry', 'entries',
     'id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion'),
    ('LearningPack', 'packs', 'id,title,description,category,orderNo,version'),
    ('PackWord', 'packWords', 'packId,vocabularyId,orderNo'),
)


def checksum(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_json(path):
    return ingest.contract.load_json(Path(path))


def write_json(path, document):
    Path(path).write_bytes(ingest.encoded(document))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_inputs(qc_dir, root=ROOT, snapshot_dir=None):
    """Check the QC consumer, frozen acceptance and current source authorization."""
    qc_dir, root = Path(qc_dir).resolve(), Path(root).resolve()
    snapshot = Path(snapshot_dir or root / accept_editorial.ACCEPTED).resolve()
    frozen = catalog_snapshot.load(root, snapshot)
    data, policy = accept_editorial.verify_handoff(quality.load_validated(qc_dir), snapshot, root)

    errors = ingest.contract.validate(data, release=True)
    require(not errors, 'Release contract failed: ' + '\n'.join(errors))

    require(data == json.loads(frozen['canonical-preview.json']),
            'QC dataset differs from frozen owner acceptance')

    report = read_json(qc_dir / 'qc_report.json')
    require(report['counts'] == {
        'input': len(data['entries']), 'accepted': len(data['entries']),
        'rejected': 0, 'withheld': 0,
    }, 'QC acceptance counts mismatch')
    require(report['reviewSha256'] == checksum(snapshot / 'reviews.json'),
            'QC review binding mismatch')
    require(report['configSha256'] == checksum(snapshot / 'ingestion-config.json'),
            'QC config binding mismatch')

    _, issues, changes, _ = quality.evaluate(data, read_json(snapshot / 'reviews.json'))
    require(not changes and not any(i['severity'] == 'error' for i in issues),
            'Frozen editorial QC failed')

    schema_path = root / SCHEMA_PATH
    schema = read_json(schema_path)['database']
    binding = {
        'datasetSha256': build_quiz_policy.dataset_digest(data),
        **({'snapshotId': 'editorial-qa-v2', 'distributionScope': 'internal-qa',
            'snapshotManifestSha256': checksum(snapshot / 'snapshot-manifest.json')}
           if snapshot == root / catalog_snapshot.QA_PATH else {}),
        'canonicalSha256': checksum(qc_dir / 'canonical-catalog.json'),
        'qcManifestSha256': checksum(qc_dir / 'manifest.json'),
        'roomSchemaSha256': checksum(schema_path),
        'acceptedInputHashes': {
            name: checksum(snapshot / name) for name in sorted(frozen)
        },
        'sourceSnapshots': [
            {key: source[key] for key in ('id', 'snapshotDate', 'snapshotSha256', 'licenseCode')}
            for source in sorted(data['sources'], key=lambda source: source['id'])
        ],
    }
    return data, build_quiz_policy.companion(data, policy), schema, binding


def create_database(path, data, schema):
    """Use compiler-exported DDL and keep all inserts in one transaction."""
    with closing(sqlite3.connect(path)) as db, db:
        db.execute('PRAGMA foreign_keys=ON')
        db.execute('PRAGMA page_size=4096')
        db.execute('BEGIN')

        for entity in schema['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for sql in schema.get('setupQueries', []):
            db.execute(sql)
        db.execute(f"PRAGMA user_version={int(schema['version'])}")

        for table, key, fields in TABLES:
            columns = fields.split(',')
            rows = sorted(data[key], key=lambda row: (
                (row['packId'], row['orderNo']) if key == 'packWords' else (row['id'],)
            ))
            placeholders = ','.join('?' for _ in columns)
            db.executemany(
                f'INSERT INTO {table} ({fields}) VALUES ({placeholders})',
                [tuple(row[column] for column in columns) for row in rows],
            )

        db.execute(
            'INSERT INTO VocabularyFts(rowid,lemma,normalizedLemma,meaningVi) '
            'SELECT rowid,lemma,normalizedLemma,meaningVi FROM VocabularyEntry ORDER BY rowid'
        )


def inspect_database(path, data, schema):
    """Measure the resulting SQLite, including all EN/VI search round trips."""
    with closing(sqlite3.connect(Path(path).resolve().as_uri() + '?mode=ro', uri=True)) as db:
        require(db.execute('PRAGMA integrity_check').fetchall() == [('ok',)],
                'SQLite integrity check failed')
        require(not db.execute('PRAGMA foreign_key_check').fetchall(),
                'SQLite foreign key check failed')
        require(db.execute('PRAGMA user_version').fetchone()[0] == schema['version'],
                'Room schema version mismatch')
        require(db.execute('SELECT identity_hash FROM room_master_table WHERE id=42').fetchone()
                == (schema['identityHash'],), 'Room identity mismatch')

        for table, key, fields in TABLES:
            actual = db.execute(f'SELECT {fields} FROM {table} ORDER BY {fields}').fetchall()
            expected = [tuple(row[column] for column in fields.split(',')) for row in data[key]]
            require(actual == sorted(expected), 'SQLite canonical projection mismatch: ' + table)

        unique = db.execute('SELECT COUNT(DISTINCT id) FROM VocabularyEntry').fetchone()[0]
        memberships = db.execute('SELECT COUNT(*) FROM PackWord').fetchone()[0]
        require(unique >= 150 and memberships >= 150, 'Catalog release quota failed')

        pack_counts = {}
        distinct_meanings = {}
        for pack in sorted(data['packs'], key=lambda row: row['orderNo']):
            rows = db.execute(
                'SELECT v.meaningVi FROM PackWord pw '
                'JOIN VocabularyEntry v ON v.id=pw.vocabularyId '
                'WHERE pw.packId=? ORDER BY pw.orderNo', (pack['id'],),
            ).fetchall()
            pack_counts[pack['id']] = len(rows)
            distinct_meanings[pack['id']] = len({quality.meaning_key(row[0]) for row in rows})
            require(distinct_meanings[pack['id']] >= 4, 'Insufficient distinct pack meanings')

        required_packs = {pack['id']: sum(word['packId'] == pack['id'] for word in data['packWords'])
                          for pack in data['packs']}
        require(pack_counts == required_packs, 'Pack membership quota mismatch')

        mapped = db.execute(
            'SELECT COUNT(*) FROM VocabularyFts f JOIN VocabularyEntry v ON v.rowid=f.rowid '
            'WHERE typeof(f.rowid)=\'integer\' AND typeof(v.id)=\'text\' '
            'AND f.lemma=v.lemma AND f.normalizedLemma=v.normalizedLemma AND f.meaningVi=v.meaningVi'
        ).fetchone()[0]
        require(mapped == unique == db.execute('SELECT COUNT(*) FROM VocabularyFts').fetchone()[0],
                'FTS rowid/stableId mapping mismatch')

        search_count = 0
        for entry in data['entries']:
            for column in ('lemma', 'meaningVi'):
                phrase = '"' + entry[column].replace('"', '""') + '"'
                matches = db.execute(
                    'SELECT v.id FROM VocabularyFts f JOIN VocabularyEntry v ON v.rowid=f.rowid '
                    'WHERE VocabularyFts MATCH ?', (column + ': ' + phrase,),
                ).fetchall()
                require((entry['id'],) in matches, 'FTS search failed: ' + entry['id'] + '/' + column)
                search_count += 1

        logical_dump = '\n'.join(db.iterdump()).encode('utf-8')
        return {
            'uniqueVocabularyCount': unique,
            'membershipCount': memberships,
            'packCounts': pack_counts,
            'distinctMeaningsPerPack': distinct_meanings,
            'examplePairCount': db.execute(
                "SELECT COUNT(*) FROM VocabularyEntry WHERE trim(exampleEn)<>'' AND trim(exampleVi)<>''"
            ).fetchone()[0],
            'nullableIpaCount': db.execute('SELECT COUNT(*) FROM VocabularyEntry WHERE ipa IS NULL').fetchone()[0],
            'ftsMappedRows': mapped,
            'ftsSearchChecks': search_count,
            'integrityCheck': 'ok',
            'foreignKeyViolations': 0,
            'logicalDumpSha256': hashlib.sha256(logical_dump).hexdigest(),
        }


def credits_for(data, *, internal_qa=False):
    # The accepted dossier names an approver, not a verified human author.
    # Keep the authors explicitly missing instead of inventing a credit grant.
    return {
        'schemaVersion': 1,
        'sources': [
            {'sourceId': source['id'], 'snapshotDate': source['snapshotDate'],
             'authors': [], 'creditText': source['attributionText'] + (
                 ' Bản QA nội bộ. Hồ sơ lịch sử ghi nhận 135 tham chiếu lemma/rank NGSL 1.2 '
                 '(Charles Browne, Brent Culligan, Joseph Phillips) và 15 mục không có tham chiếu NGSL. '
                 'Dữ liệu nghĩa và ví dụ thuộc bản biên soạn của dự án.' if internal_qa else '')}
            for source in sorted(data['sources'], key=lambda source: source['id'])
        ],
    }


def gate_report(counts, credit_status, *, internal_qa=False):
    return {
        'technicalStatus': 'pass',
        **({'contentRightsStatus': 'hold', 'packagingScope': 'internal-qa'} if internal_qa else {}),
        'releaseStatus': 'blocked',
        'releaseAuthorized': False,
        'blockers': RELEASE_BLOCKERS,
        'sqlQa': counts,
        'credits': {
            **credit_status,
            'missingAuthors': [list(pair) for pair in credit_status['missingAuthors']],
        },
        'deviceAcceptance': 'not-run',
        'upgradeRollbackAcceptance': 'not-run',
    }


def build(qc_dir, output_dir, *, root=ROOT, snapshot_dir=None):
    root, output_dir = Path(root).resolve(), Path(output_dir).resolve()
    allowed = root / 'build/catalog-release'
    require(output_dir.is_relative_to(allowed) and output_dir != allowed and not output_dir.exists(),
            'Output must be a fresh directory below build/catalog-release')
    data, policy, schema, binding = load_inputs(qc_dir, root, snapshot_dir)
    internal_qa = binding.get('distributionScope') == 'internal-qa'
    credits = credits_for(data, internal_qa=internal_qa)
    pairs = {(source['id'], source['snapshotDate']) for source in data['sources']}
    credit_status = check_credits.validate(credits, pairs)

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.catalog-stage-', dir=output_dir.parent) as temporary:
        stage = Path(temporary) / 'candidate'
        stage.mkdir()
        create_database(stage / 'catalog.db', data, schema)
        counts = inspect_database(stage / 'catalog.db', data, schema)
        metadata = {
            'kind': KIND,
            'fixtureOnly': False,
            'sqliteVersion': sqlite3.sqlite_version,
            'schemaVersion': schema['version'],
            'identityHash': schema['identityHash'],
            'contentVersion': data['contentVersion'],
            **binding,
            'sha256': checksum(stage / 'catalog.db'),
            **counts,
        }
        write_json(stage / 'catalog.metadata.json', metadata)
        write_json(stage / 'quiz-policy.json', policy)
        write_json(stage / 'content-credits.json', credits)
        write_json(stage / 'release-gate-report.json', gate_report(counts, credit_status, internal_qa=internal_qa))
        write_json(stage / 'manifest.json', {
            'schemaVersion': 1,
            'status': 'candidate-ready',
            'releaseAuthorized': False,
            'outputHashes': {name: checksum(stage / name) for name in sorted(OUTPUTS)},
        })
        # Verify the complete set before publishing the directory in one rename.
        verify(qc_dir, stage, root=root, snapshot_dir=snapshot_dir)
        require(not output_dir.exists(), 'Output appeared during build; refusing overwrite')
        os.rename(stage, output_dir)
    return metadata


def verify(qc_dir, directory, *, root=ROOT, snapshot_dir=None):
    directory = Path(directory)
    data, policy, schema, binding = load_inputs(qc_dir, root, snapshot_dir)
    internal_qa = binding.get('distributionScope') == 'internal-qa'
    manifest = read_json(directory / 'manifest.json')
    require(manifest.get('schemaVersion') == 1 and manifest.get('status') == 'candidate-ready'
            and manifest.get('releaseAuthorized') is False, 'Invalid candidate completion marker')
    hashes = manifest.get('outputHashes')
    require(isinstance(hashes, dict) and set(hashes) == OUTPUTS, 'Candidate output set mismatch')
    require({p.name for p in directory.iterdir()} == OUTPUTS | {'manifest.json'},
            'Unexpected candidate artifacts')
    for name in sorted(OUTPUTS):
        require(checksum(directory / name) == hashes[name], 'Candidate checksum mismatch: ' + name)

    counts = inspect_database(directory / 'catalog.db', data, schema)
    metadata = read_json(directory / 'catalog.metadata.json')
    expected = {
        'kind': KIND, 'fixtureOnly': False,
        'schemaVersion': schema['version'], 'identityHash': schema['identityHash'],
        'contentVersion': data['contentVersion'], **binding,
        'sha256': checksum(directory / 'catalog.db'), **counts,
    }
    require(all(metadata.get(key) == value for key, value in expected.items()),
            'Candidate metadata binding mismatch')
    require(read_json(directory / 'quiz-policy.json') == policy, 'Candidate quiz policy mismatch')
    credits = read_json(directory / 'content-credits.json')
    require(credits == credits_for(data, internal_qa=internal_qa), 'Candidate source/credit mismatch')
    pairs = {(source['id'], source['snapshotDate']) for source in data['sources']}
    require(read_json(directory / 'release-gate-report.json') ==
            gate_report(counts, check_credits.validate(credits, pairs), internal_qa=internal_qa), 'Candidate release report mismatch')
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--qc-dir', required=True, type=Path)
    parser.add_argument('--snapshot-dir', type=Path)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--output-dir', type=Path)
    mode.add_argument('--verify-dir', type=Path)
    args = parser.parse_args()
    try:
        metadata = (build(args.qc_dir, args.output_dir, snapshot_dir=args.snapshot_dir) if args.output_dir else
                    verify(args.qc_dir, args.verify_dir, snapshot_dir=args.snapshot_dir))
        print(json.dumps({'status': 'candidate-ready', 'releaseStatus': 'blocked',
                          'datasetSha256': metadata['datasetSha256'],
                          'uniqueVocabularyCount': metadata['uniqueVocabularyCount'],
                          'packCounts': metadata['packCounts']}, ensure_ascii=False, indent=2))
        return 0
    except (ValueError, KeyError, TypeError, AttributeError, OSError, sqlite3.Error) as exc:
        parser.exit(1, f'CATALOG_BLOCKED: {exc}\n')


if __name__ == '__main__':
    raise SystemExit(main())
