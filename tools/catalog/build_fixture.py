#!/usr/bin/env python3
"""Build DATA-01's synthetic test catalog from the actual exported Room schema.
Not the production vocabulary ETL: no external data or license claims are imported.
"""
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import tempfile
import unicodedata

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / 'app/schemas/com.vocamind.app.data.catalog.CatalogDatabase/1.json'
ASSET = ROOT / 'app/src/main/assets/database/catalog.db'
WORDS = [
    ('contract', 'noun', 'hợp đồng', 'Please read the contract before signing it.', 'Vui lòng đọc hợp đồng trước khi ký.'),
    ('office', 'noun', 'văn phòng', 'Our office is near the city library.', 'Văn phòng của chúng tôi ở gần thư viện thành phố.'),
    ('book', 'noun', 'quyển sách', 'This book has many useful English examples.', 'Quyển sách này có nhiều ví dụ tiếng Anh hữu ích.'),
    ('study', 'verb', 'học', 'We study English together after our classes.', 'Chúng tôi cùng học tiếng Anh sau giờ lên lớp.'),
]
PACKS = [('pack_toeic_basic', 'TOEIC Basic'), ('pack_toeic_office', 'TOEIC Office'),
         ('pack_daily_life', 'Daily Life'), ('pack_academic', 'Academic')]

def stable_id(lemma, pos, sense_index=1):
    return hashlib.sha256(f'{lemma}_{pos}_{sense_index}'.encode('utf-8')).hexdigest()[:16]

def build():
    schema = json.loads(SCHEMA.read_text())['database']
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=ASSET.parent, suffix='.db')
    os.close(fd)
    try:
        with closing(sqlite3.connect(tmp)) as db:
            db.execute('PRAGMA foreign_keys=ON')
            db.execute('PRAGMA page_size=4096')
            for entity in schema['entities']:
                db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
                for index in entity.get('indices', []):
                    db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for sql in schema.get('setupQueries', []):
                db.execute(sql)
            db.execute(f"PRAGMA user_version={int(schema['version'])}")
            db.execute('INSERT INTO ContentSource VALUES (?,?,?,?,?,?)', (
                'project_fixture', 'VocaMind synthetic DATA-01 test fixture; not release vocabulary',
                'https://github.com/ManhT005/VocaMind', 'LicenseRef-Project-Test-Fixture',
                'https://github.com/ManhT005/VocaMind/blob/develop/tools/catalog/README.md', '2026-10-25'))
            for i, (lemma, pos, meaning, en, vi) in enumerate(WORDS):
                sid = stable_id(lemma, pos)
                db.execute('INSERT INTO VocabularyEntry (id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) VALUES (?,?,?,?,?,?,?,?,?,?)',
                           (sid, lemma, unicodedata.normalize('NFC', lemma).casefold().strip(), pos, meaning, en, vi, None, 'project_fixture', '1.0.0'))
                rowid = db.execute('SELECT rowid FROM VocabularyEntry WHERE id=?', (sid,)).fetchone()[0]
                db.execute('INSERT INTO VocabularyFts (rowid,lemma,normalizedLemma,meaningVi) VALUES (?,?,?,?)',
                           (rowid, lemma, lemma, meaning))
            for order, (pid, title) in enumerate(PACKS):
                db.execute('INSERT INTO LearningPack VALUES (?,?,?,?,?,?)', (pid, title, 'Synthetic integration fixture — not a complete MVP pack', 'fixture', order, '1.0.0'))
                for order_no, (lemma, pos, *_rest) in enumerate(WORDS):
                    db.execute('INSERT INTO PackWord VALUES (?,?,?)', (pid, stable_id(lemma, pos), order_no))
            assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
            assert not db.execute('PRAGMA foreign_key_check').fetchall()
            assert db.execute('SELECT COUNT(DISTINCT id) FROM VocabularyEntry').fetchone()[0] == 4
            assert db.execute('SELECT COUNT(*) FROM VocabularyFts f JOIN VocabularyEntry v ON f.rowid=v.rowid AND f.lemma=v.lemma').fetchone()[0] == 4
            assert db.execute("SELECT v.lemma FROM VocabularyFts f JOIN VocabularyEntry v ON f.rowid=v.rowid WHERE VocabularyFts MATCH ?", ('"hợp đồng"',)).fetchall() == [('contract',)]
            db.commit()
        os.replace(tmp, ASSET)
    finally:
        if os.path.exists(tmp): os.unlink(tmp)
    metadata = {'kind': 'synthetic-test-fixture-not-production', 'schemaVersion': schema['version'],
                'identityHash': schema['identityHash'], 'contentVersion': '1.0.0',
                'sha256': hashlib.sha256(ASSET.read_bytes()).hexdigest(), 'uniqueVocabularyCount': 4,
                'membershipCount': 16, 'packCounts': {p: 4 for p, _ in PACKS}}
    ASSET.with_suffix('.metadata.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(metadata, ensure_ascii=False))

if __name__ == '__main__': build()
