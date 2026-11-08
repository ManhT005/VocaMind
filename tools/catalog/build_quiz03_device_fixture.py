#!/usr/bin/env python3
"""Accepted-corpus TEST fixture for the opt-in isolated QUIZ-03 APK, never a release asset.

The ordinary bundled synthetic catalog is deliberately left unchanged. This fixture
uses exported Room DDL and the original accepted companion policy without changing
entries, stable IDs, restrictions or approval status.
"""
from contextlib import closing
import hashlib
import json
from pathlib import Path
import sqlite3
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_quiz_policy

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/data/voc-data-04/accepted-v1'
OUTPUT = ROOT / 'app/build/quiz03-device-assets/database'


def build():
    canonical = SOURCE / 'canonical-preview.json'
    data = json.loads(canonical.read_text(encoding='utf-8'))
    restrictions = json.loads((SOURCE / 'quiz-policy.json').read_text(encoding='utf-8'))
    policy = build_quiz_policy.companion(data, restrictions)
    schema = json.loads((ROOT / 'app/schemas/com.vocamind.app.data.catalog.CatalogDatabase/2.json')
                             .read_text(encoding='utf-8'))['database']
    OUTPUT.mkdir(parents=True, exist_ok=True)
    database = OUTPUT / 'catalog.db'
    with closing(sqlite3.connect(database)) as db, db:
        db.execute('PRAGMA foreign_keys=ON')
        for entity in schema['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for sql in schema.get('setupQueries', []):
            db.execute(sql)
        for table in ['PackWord', 'VocabularyFts', 'VocabularyEntry', 'LearningPack', 'ContentSource']:
            db.execute(f'DELETE FROM {table}')
        for table, key, fields in (
            ('ContentSource', 'sources', 'id,title,url,licenseCode,licenseUrl,snapshotDate,attributionText'),
            ('VocabularyEntry', 'entries', 'id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion'),
            ('LearningPack', 'packs', 'id,title,description,category,orderNo,version'),
            ('PackWord', 'packWords', 'packId,vocabularyId,orderNo'),
        ):
            columns = fields.split(',')
            rows = sorted(data[key], key=lambda row: tuple(str(row[c]) for c in columns))
            db.executemany(f"INSERT INTO {table} ({fields}) VALUES ({','.join('?' for _ in columns)})",
                           [tuple(row[c] for c in columns) for row in rows])
        db.execute('INSERT INTO VocabularyFts(rowid,lemma,normalizedLemma,meaningVi) '
                   'SELECT rowid,lemma,normalizedLemma,meaningVi FROM VocabularyEntry ORDER BY rowid')
        db.execute(f"PRAGMA user_version={int(schema['version'])}")
        assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert not db.execute('PRAGMA foreign_key_check').fetchall()
        assert db.execute('SELECT COUNT(*) FROM VocabularyEntry').fetchone()[0] == 150
    (OUTPUT / 'quiz-policy.json').write_text(json.dumps(policy, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    metadata = {'kind': 'QUIZ-03-isolated-device-test-only-not-release',
                'canonicalSha256': hashlib.sha256(canonical.read_bytes()).hexdigest(),
                'databaseSha256': hashlib.sha256(database.read_bytes()).hexdigest(),
                'vocabularyCount': 150}
    (OUTPUT / 'quiz03-fixture-metadata.json').write_text(json.dumps(metadata, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(metadata))


if __name__ == '__main__':
    build()
