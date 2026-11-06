#!/usr/bin/env python3
"""Synthetic 5,000-word prepack for DATA-05 profiling. Never release vocabulary."""
from contextlib import closing
from pathlib import Path
import hashlib
import json
import os
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'app/src/main/assets/database/catalog.db'
OUTPUT = ROOT / 'app/src/catalogTest/assets/benchmark/catalog-5000.db'

def build():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=OUTPUT.parent, suffix='.db'); os.close(fd)
    try:
        with closing(sqlite3.connect(f'file:{SOURCE}?mode=ro', uri=True)) as source, \
                closing(sqlite3.connect(tmp)) as db:
            source.backup(db)
            db.execute('PRAGMA foreign_keys=ON')
            with db:
                for table in ['PackWord', 'VocabularyFts', 'VocabularyEntry', 'LearningPack', 'ContentSource']:
                    db.execute(f'DELETE FROM {table}')
                db.execute('INSERT INTO ContentSource (id,title,url,licenseCode,licenseUrl,snapshotDate,attributionText) VALUES (?,?,?,?,?,?,?)',
                           ('benchmark', 'Synthetic benchmark, not release content', 'https://example.invalid/benchmark',
                            'LicenseRef-Test-Only', 'https://example.invalid/benchmark', '2026-11-03', 'Original synthetic profiling data'))
                for i in range(5000):
                    lemma = f'benchword{i:05d}'; sid = hashlib.sha256(f'{lemma}_noun_1'.encode()).hexdigest()[:16]
                    meaning = f'nghĩa kiểm thử {i:05d}'; rowid = 10000 + i
                    db.execute('INSERT INTO VocabularyEntry (rowid,id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) VALUES (?,?,?,?,?,?,?,?,?,?,?)',
                               (rowid, sid, lemma, lemma, 'noun', meaning, 'Synthetic English example.', 'Ví dụ kiểm thử.', None, 'benchmark', 'benchmark-1'))
                    db.execute('INSERT INTO VocabularyFts (rowid,lemma,normalizedLemma,meaningVi) VALUES (?,?,?,?)',
                               (rowid, lemma, lemma, meaning))
                assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
                assert not db.execute('PRAGMA foreign_key_check').fetchall()
                assert db.execute('SELECT COUNT(*) FROM VocabularyFts f JOIN VocabularyEntry v ON f.rowid=v.rowid').fetchone()[0] == 5000
            db.execute('VACUUM')
        os.replace(tmp, OUTPUT)
    finally:
        if os.path.exists(tmp): os.unlink(tmp)
    metadata = {'kind':'synthetic-benchmark-not-release', 'uniqueWords':5000,
                'sourceSha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(), 'bytes':OUTPUT.stat().st_size}
    OUTPUT.with_suffix('.metadata.json').write_text(json.dumps(metadata, indent=2)+'\n')
    print(json.dumps(metadata))

if __name__ == '__main__': build()
