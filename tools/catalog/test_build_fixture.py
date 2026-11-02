"""Canonical JSON -> exported Room schema -> checked-in SQLite asset gates."""
from contextlib import closing
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

import build_fixture as builder


class CatalogBuildTests(unittest.TestCase):
    def test_canonical_round_trip_and_deterministic_checked_in_asset(self):
        data = builder.load_json(builder.FIXTURE)
        with tempfile.TemporaryDirectory() as folder:
            asset = Path(folder) / 'catalog.db'
            first = builder.build(asset)
            self.assertEqual(first, builder.build(asset))
            committed = json.loads(builder.ASSET.with_suffix('.metadata.json').read_text(encoding='utf-8'))
            self.assertEqual(committed['sha256'], hashlib.sha256(builder.ASSET.read_bytes()).hexdigest())
            for key in first.keys() - {'sha256', 'sqliteVersion'}:
                self.assertEqual(first[key], committed[key])
            with closing(sqlite3.connect(asset)) as fresh, closing(sqlite3.connect(builder.ASSET)) as checked:
                self.assertEqual(list(fresh.iterdump()), list(checked.iterdump()))
            with closing(sqlite3.connect(asset)) as db:
                self.assertEqual([('ok',)], db.execute('PRAGMA integrity_check').fetchall())
                self.assertEqual([], db.execute('PRAGMA foreign_key_check').fetchall())
                for table, records, fields in (
                    ('ContentSource', data['sources'], 'id,title,url,licenseCode,licenseUrl,snapshotDate,attributionText'),
                    ('VocabularyEntry', data['entries'], 'id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion'),
                    ('LearningPack', data['packs'], 'id,title,description,category,orderNo,version'),
                ):
                    self.assertEqual(sorted(tuple(r[c] for c in fields.split(',')) for r in records),
                                     db.execute(f'SELECT {fields} FROM {table} ORDER BY id').fetchall())
                for pack in data['packs']:
                    expected = sorted((r['orderNo'], r['vocabularyId']) for r in data['packWords'] if r['packId'] == pack['id'])
                    self.assertEqual(expected, db.execute('SELECT orderNo,vocabularyId FROM PackWord WHERE packId=? ORDER BY orderNo', (pack['id'],)).fetchall())
                self.assertEqual(4, db.execute('SELECT COUNT(*) FROM VocabularyFts f JOIN VocabularyEntry v ON f.rowid=v.rowid AND f.lemma=v.lemma AND f.meaningVi=v.meaningVi').fetchone()[0])
                for term in ('airport', '"sân bay"'):
                    self.assertEqual([('923d45253cc2c67c',)], db.execute('SELECT v.id FROM VocabularyFts f JOIN VocabularyEntry v ON f.rowid=v.rowid WHERE VocabularyFts MATCH ?', (term,)).fetchall())
                for sql in (
                    "UPDATE LearningPack SET orderNo=0 WHERE id='pack_toeic_office'",
                    "UPDATE PackWord SET orderNo=0 WHERE packId='pack_toeic_basic' AND orderNo=1",
                ):
                    with self.assertRaises(sqlite3.IntegrityError):
                        db.execute(sql)

    def test_invalid_canonical_never_replaces_asset(self):
        with tempfile.TemporaryDirectory() as folder:
            asset = Path(folder) / 'catalog.db'
            asset.write_bytes(b'preserve existing asset')
            invalid = builder.CONTRACT / 'fixtures/invalid/duplicate-order.json'
            with self.assertRaises(ValueError):
                builder.build(asset, invalid)
            self.assertEqual(b'preserve existing asset', asset.read_bytes())

    def test_synthetic_fixture_is_blocked_from_release(self):
        errors = builder.validate(builder.load_json(builder.FIXTURE), release=True)
        codes = {e.split(':', 1)[0] for e in errors}
        self.assertTrue({'FIXTURE_NOT_RELEASE', 'RELEASE_UNIQUE_WORDS', 'RELEASE_SOURCE_NOT_APPROVED'} <= codes)


if __name__ == '__main__':
    unittest.main()
