#!/usr/bin/env python3
"""Build synthetic canonical JSON using compiler-exported Room DDL; not release ETL."""
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / 'docs/data/vocabulary-contract-v1'
sys.path.insert(0, str(CONTRACT))
from validate_contract import load_json, validate

SCHEMA = ROOT / 'app/schemas/com.vocamind.app.data.catalog.CatalogDatabase/2.json'
FIXTURE = CONTRACT / 'fixtures/valid/catalog.json'
ASSET = ROOT / 'app/src/main/assets/database/catalog.db'


def build(asset=ASSET, fixture=FIXTURE):
    asset, fixture = Path(asset), Path(fixture)
    data = load_json(fixture)
    errors = validate(data)
    if errors:
        raise ValueError('\n'.join(errors))
    if not data['fixtureOnly']:
        raise ValueError('Fixture builder only; release approval is separate')
    schema = json.loads(SCHEMA.read_text(encoding='utf-8'))['database']
    asset.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=asset.parent, suffix='.db')
    os.close(fd)
    try:
        with closing(sqlite3.connect(tmp)) as db, db:
            db.execute('PRAGMA foreign_keys=ON')
            db.execute('PRAGMA page_size=4096')
            for entity in schema['entities']:
                db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
                for index in entity.get('indices', []):
                    db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for sql in schema.get('setupQueries', []):
                db.execute(sql)
            db.execute(f"PRAGMA user_version={int(schema['version'])}")
            for table, rows, fields in (
                ('ContentSource', data['sources'], 'id,title,url,licenseCode,licenseUrl,snapshotDate,attributionText'),
                ('VocabularyEntry', data['entries'], 'id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion'),
                ('LearningPack', sorted(data['packs'], key=lambda p: p['orderNo']), 'id,title,description,category,orderNo,version'),
                ('PackWord', sorted(data['packWords'], key=lambda p: (p['packId'], p['orderNo'])), 'packId,vocabularyId,orderNo'),
            ):
                columns = fields.split(',')
                db.executemany(f"INSERT INTO {table} ({fields}) VALUES ({','.join('?' for _ in columns)})",
                               [tuple(row[c] for c in columns) for row in rows])
            db.execute('INSERT INTO VocabularyFts(rowid,lemma,normalizedLemma,meaningVi) '
                       'SELECT rowid,lemma,normalizedLemma,meaningVi FROM VocabularyEntry ORDER BY rowid')
            if db.execute('PRAGMA integrity_check').fetchall() != [('ok',)]:
                raise ValueError('SQLite integrity check failed')
            if db.execute('PRAGMA foreign_key_check').fetchall():
                raise ValueError('SQLite foreign key check failed')
            db.commit()
        os.replace(tmp, asset)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    metadata = {
        'kind': 'synthetic-test-fixture-not-production',
        'sqliteVersion': sqlite3.sqlite_version,
        'schemaVersion': schema['version'], 'identityHash': schema['identityHash'],
        'contentVersion': data['contentVersion'],
        'canonicalSha256': hashlib.sha256(fixture.read_bytes()).hexdigest(),
        'sha256': hashlib.sha256(asset.read_bytes()).hexdigest(),
        'uniqueVocabularyCount': len(data['entries']), 'membershipCount': len(data['packWords']),
        'packCounts': {p['id']: sum(w['packId'] == p['id'] for w in data['packWords']) for p in data['packs']},
    }
    asset.with_suffix('.metadata.json').write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    return metadata


if __name__ == '__main__':
    print(json.dumps(build(), ensure_ascii=False))
