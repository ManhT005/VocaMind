from pathlib import Path
import subprocess,sys,json,os,datetime,hashlib
os.environ['PYTHONUTF8']='1'
run='pack02-followup-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
out=Path('build')/run;out.mkdir()
config='tools/vocabulary/fixtures/ingestion/verified/config.json'
proof='build/vocabulary-ingestion/'+run
qa='build/vocabulary-quality/'+run
corpus='build/vocabulary-corpus/'+run
checks=[
('source-gate',['tools/vocabulary/gate_sources.py','--snapshot','docs/data/snapshot.json','--json'],0),
('vocabulary-tests',['-m','unittest','discover','-s','tools/vocabulary','-p','test_*.py','-v'],0),
('credits',['tools/catalog/check_credits.py'],0),
('catalog-tests',['-m','unittest','discover','-s','tools/catalog','-p','test_*.py','-v'],0),
('contract-tests',['-m','unittest','discover','-s','docs/data/vocabulary-contract-v1','-p','test_contract.py','-v'],0),
('contract',['docs/data/vocabulary-contract-v1/validate_contract.py','docs/data/vocabulary-contract-v1/fixtures/valid/catalog.json'],0),
('ingestion',['tools/vocabulary/verify_ingestion.py','--fixture','--config',config,'--output',proof],0),
('ingestion-contract',['docs/data/vocabulary-contract-v1/validate_contract.py',proof+'/a/canonical-catalog.json'],0),
('release-rejection',['docs/data/vocabulary-contract-v1/validate_contract.py',proof+'/a/canonical-catalog.json','--release'],1),
('qc-a',['tools/vocabulary/quality.py','--fixture','--input',proof+'/a','--config',config,'--reviews','tools/vocabulary/fixtures/quality/reviews.json','--output',qa+'-a'],0),
('qc-b',['tools/vocabulary/quality.py','--fixture','--input',proof+'/a','--config',config,'--reviews','tools/vocabulary/fixtures/quality/reviews.json','--output',qa+'-b'],0),
('qc-contract',['docs/data/vocabulary-contract-v1/validate_contract.py',qa+'-a/canonical-catalog.json'],0),
('qc-rejection',['tools/vocabulary/quality.py','--fixture','--input',proof+'/a','--config',config,'--reviews','tools/vocabulary/fixtures/quality/pending-reviews.json','--output',qa+'-rejected'],1),
('corpus-a',['tools/vocabulary/prepare_corpus.py','--require-approved','--output',corpus+'-a'],1),
('corpus-b',['tools/vocabulary/prepare_corpus.py','--require-approved','--output',corpus+'-b'],1),
('editorial-rejection',['tools/vocabulary/ingest.py','--config','docs/data/voc-data-04/corpus-v1/ingestion-proposal.json','--output',proof+'-editorial-rejected'],1),
('accept-editorial',['tools/vocabulary/accept_editorial.py','--output',corpus+'-accepted'],0),
('accepted-ingestion',['tools/vocabulary/ingest.py','--config','docs/data/voc-data-04/accepted-v1/ingestion-config.json','--output',proof+'-accepted'],0),
('accepted-quality',['tools/vocabulary/quality.py','--input',proof+'-accepted','--config','docs/data/voc-data-04/accepted-v1/ingestion-config.json','--reviews','docs/data/voc-data-04/accepted-v1/reviews.json','--output',qa+'-accepted'],0),
('accepted-contract',['docs/data/vocabulary-contract-v1/validate_contract.py',qa+'-accepted/canonical-catalog.json','--release'],0),
('accepted-verify',['tools/vocabulary/accept_editorial.py','--verify',qa+'-accepted'],0)]
results=[]
for name,args,expected in checks:
 r=subprocess.run([sys.executable]+args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 (out/(name+'.txt')).write_bytes(r.stdout)
 results.append(dict(name=name,command='python '+' '.join(args),exit=r.returncode,expected=expected))
 print(name,r.returncode,'expected',expected,flush=True)
def hashes(name):
 p=Path(name)
 return {str(x.relative_to(p)):hashlib.sha256(x.read_bytes()).hexdigest() for x in p.rglob('*') if x.is_file()}
for a,b in [(qa+'-a',qa+'-b'),(corpus+'-a',corpus+'-b'),('docs/data/voc-data-04/corpus-v1',corpus+'-a'),('docs/data/voc-data-04/accepted-v1',corpus+'-accepted')]:
 equal=hashes(a)==hashes(b)
 results.append(dict(name='byte-compare',a=a,b=b,equal=equal))
 print('compare',a,b,equal,flush=True)
for folder,files in [(qa+'-rejected',['manifest.json','canonical-catalog.json','validated_vocab.jsonl']),(corpus+'-a',['manifest.json','validated_vocab.jsonl']),(proof+'-editorial-rejected',['manifest.json'])]:
 results.append(dict(name='refused-output-absent',folder=folder,passed=all(not (Path(folder)/f).exists() for f in files)))
(out/'results.json').write_text(json.dumps(results,indent=2)+'\n')
Path('build/pack02-followup-python-run.txt').write_text(str(out))
