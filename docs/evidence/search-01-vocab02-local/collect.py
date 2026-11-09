"""Collect source-bound evidence for the uncommitted SEARCH-01/VOCAB-02 integration."""
from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,re,shutil,subprocess,xml.etree.ElementTree as ET,zipfile
OUT=Path(__file__).resolve().parent
ROOT=OUT.parents[2]
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def sha(file): return hashlib.sha256(file.read_bytes()).hexdigest()
def write(name,value): (OUT/name).write_bytes((json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode())
for path in OUT.glob('*.txt'):
    raw=path.read_bytes()
    text=raw.decode('utf-16') if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else raw.decode('utf-8-sig')
    normalized='\n'.join(line.rstrip() for line in text.replace('\r\r\n','\n').splitlines()).rstrip()
    path.write_bytes((normalized+'\n' if normalized else '').encode())
head=git('rev-parse','HEAD'); develop=git('rev-parse','MERGE_HEAD')
tree=git('write-tree')
assert head=='371d9eca5e084cc7e8ca57721a4efe78e64ccf3c'
assert develop=='78b9add1f5b628408013de034fb7b7651f382856'
subprocess.check_call(['git','diff','--quiet','--','app','tools'],cwd=ROOT)
assert sha(ROOT/'gradle/wrapper/gradle-wrapper.properties')==sha(ROOT/'build/search01-vocab02-integration/user-gradle-wrapper.properties')
inputs=[]
for line in git('ls-tree','-r',tree).splitlines():
    meta,path=line.split('\t',1)
    if path.startswith(('app/','tools/','docs/data/','gradle/','.github/')) or path in ['build.gradle','settings.gradle','gradle.properties']:
        inputs.append(dict(path=path,gitBlob=meta.split()[-1]))
write('tested-inputs.json',dict(head=head,develop=develop,integrationTree=tree,
     status='Uncommitted local merge; tree uses pinned wrapper, actual command uses Gradle8.9 directly',files=inputs))
suites=[]
for file in sorted((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    root=ET.parse(file).getroot()
    suites.append({k:root.attrib[k] for k in ['name','tests','failures','errors','skipped']})
    if any(name in file.name for name in ['feature.search.','VocabularyDetailTest','RepositoryIntegrationTest','VocabularyListTest']):
        shutil.copyfile(file,OUT/file.name)
totals={k:sum(int(s[k]) for s in suites) for k in ['tests','failures','errors','skipped']}
assert totals==dict(tests=517,failures=0,errors=0,skipped=0)
lint={}
for variant in ['debug','catalogQa']:
    file=ROOT/f'app/build/reports/lint-results-{variant}.xml'
    shutil.copyfile(file,OUT/file.name)
    root=ET.parse(file).getroot()
    lint[variant]=dict(errors=sum(i.attrib['severity'] in ['Error','Fatal'] for i in root.findall('issue')),
                       warnings=sum(i.attrib['severity']=='Warning' for i in root.findall('issue')))
    assert lint[variant]['errors']==0
raw=(OUT/'qa-instrumentation.txt').read_text(encoding='utf-8')
assert 'OK (4 tests)' in raw and 'INSTRUMENTATION_STATUS_CODE: -2' not in raw
python={}
for name in ['vocabulary','catalog','contract']:
    text=(OUT/f'python-{name}.txt').read_text(encoding='utf-8')
    assert re.search(r'^OK$',text,re.M)
    python[name]=int(re.search(r'Ran (\d+) tests',text).group(1))
before=json.loads((OUT/'existing-apps-before.json').read_text(encoding='utf-8'))
after=json.loads((OUT/'existing-apps-after.json').read_text(encoding='utf-8'))
assert before==after
artifacts=json.loads((OUT/'qa-artifacts.json').read_text(encoding='utf-8'))
for row in artifacts.values(): assert sha(ROOT/row['path'])==row['sha256']
with zipfile.ZipFile(ROOT/artifacts['app']['path']) as z:
    catalogHash=hashlib.sha256(z.read('assets/database/catalog.db')).hexdigest()
    assert not any(p.startswith('assets/benchmark/') for p in z.namelist())
benchmark=json.loads((OUT/'data05-fts-benchmark-api30.json').read_text(encoding='utf-8'))
assert all(len(c['samplesNs'])==100 for c in benchmark['cases'])
write('verification.json',dict(dateUtc=datetime.now(timezone.utc).isoformat(),branch='feature/search-01-search-ui',
    head=head,develop=develop,integrationTree=tree,commitCreated=False,pushed=False,
    sourceManifestSha256=sha(OUT/'tested-inputs.json'),androidTests=totals,suiteCount=len(suites),suites=suites,
    lint=lint,pythonTests=python,pythonTotal=sum(python.values()),instrumentation=dict(tests=4,failures=0),
    qaCatalog=dict(words=150,package='com.vocamind.app.qa',apkCatalogSha256=catalogHash,releaseAuthorized=False),
    artifacts=artifacts,existingPackagesDataUnchanged=True,userWrapperPreserved=True,
    actualGradle='8.9 cache executable, bypasses user-modified 8.14.6 wrapper file',jdk='Temurin17.0.20.1',
    benchmarkFixtureSha256=sha(ROOT/'app/src/catalogTest/assets/benchmark/catalog-5000.db'),
    benchmark=dict(raw='data05-fts-benchmark-api30.json',samples=500,warmupsPerWorkload=20,referenceHardwareVerified=False,
        observedAllBelow50Ms=all(c['maxMs']<50 for c in benchmark['cases']),
        broadCase=benchmark['cases'][-1],scope=benchmark['timingScope']),
    AC05='HOLD',NFR_VM_03='HOLD',notRun=['Helio G85/3GB/Android10 benchmark','GitHub CI for uncommitted integration']))
print(json.dumps(dict(tree=tree,tests=totals,lint=lint,python=python,existingAppDataUnchanged=True)))
