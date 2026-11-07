import json,sqlite3,hashlib,base64,zipfile
from pathlib import Path
root=Path.cwd(); run=root/'docs/validation/vocab-02/replay-49ca6b4-20261107'
def clean(v): return {'base64':base64.b64encode(v).decode()} if isinstance(v,bytes) else v
out={}
for name in ['catalog.db','userdata.db']:
 p=root/'build/vocab02-device/replay-databases'/name
 c=sqlite3.connect(p.as_uri()+'?mode=ro',uri=True)
 tables={}
 for (table,) in c.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"):
  cur=c.execute('SELECT * FROM "'+table.replace('"','""')+'"')
  cols=[d[0] for d in cur.description]
  tables[table]=sorted([{k:clean(v) for k,v in zip(cols,row)} for row in cur],key=lambda r:json.dumps(r,sort_keys=True))
 out[name]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'tables':tables}
 if name=='userdata.db':
  out['baselineCounts']={'total':c.execute('SELECT COUNT(DISTINCT vocabularyId) FROM UserWordProgress').fetchone()[0],
   'boxes':list(c.execute('SELECT box,COUNT(DISTINCT vocabularyId) FROM UserWordProgress GROUP BY box ORDER BY box')),
   'reviewEvents':c.execute('SELECT COUNT(*) FROM ReviewEvent').fetchone()[0],
   'userPacks':c.execute('SELECT COUNT(*) FROM UserPack').fetchone()[0], 'learnedToday':c.execute('SELECT COUNT(*) FROM UserWordProgress WHERE learnedAt>=? AND learnedAt<?',(1793984400000,1794070800000)).fetchone()[0]}
 c.close()
(run/'db-snapshot.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
assets={}
for name,p in [('app',root/'app/build/outputs/apk/debug/app-debug.apk'),('test',root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
 with zipfile.ZipFile(p) as z:
  assets[name]=[{'entry':e.filename,'sha256':hashlib.sha256(z.read(e)).hexdigest(),'bytes':e.file_size} for e in z.infolist() if e.filename.startswith('assets/') and e.filename.endswith(('.db','.json'))]
(run/'apk-assets.json').write_text(json.dumps(assets,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(out['baselineCounts']))