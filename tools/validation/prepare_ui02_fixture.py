"""UI-02 v1: copy synthetic prepack; add one test-only IPA; never alter main assets."""
import hashlib, json, shutil, sqlite3, sys
from pathlib import Path
source, output = map(Path, sys.argv[1:])
dest = output / 'database'
dest.mkdir(parents=True, exist_ok=True)
for name in ('catalog.db', 'catalog.metadata.json', 'content-credits.json', 'quiz-policy.json'):
    shutil.copyfile(source / name, dest / name)
with sqlite3.connect(dest / 'catalog.db') as db:
    db.execute("UPDATE VocabularyEntry SET ipa='/bæŋk/' WHERE id='5aecfbf5b9a23aee'")
    db.commit()
    words = [dict(zip([c[0] for c in cur.description], row)) for cur in [db.execute('SELECT * FROM VocabularyEntry ORDER BY id')] for row in cur]
    memberships = list(db.execute('SELECT packId,vocabularyId,orderNo FROM PackWord ORDER BY packId,orderNo,vocabularyId'))
metadata = json.loads((dest / 'catalog.metadata.json').read_text(encoding='utf-8'))
metadata['sha256'] = hashlib.sha256((dest / 'catalog.db').read_bytes()).hexdigest()
metadata['kind'] = 'synthetic-test-fixture-ui02-v1'
metadata.pop('canonicalSha256', None)  # upstream canonical excludes the test-only IPA override
(dest / 'catalog.metadata.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
(output / 'ui02-fixture.json').write_text(json.dumps({'version':'ui02-v1', 'sourceSha256':hashlib.sha256((source/'catalog.db').read_bytes()).hexdigest(), 'sha256':metadata['sha256'], 'words':words, 'memberships':memberships, 'usage':'Synthetic test only; not approved release learning content'}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
