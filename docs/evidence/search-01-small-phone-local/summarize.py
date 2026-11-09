"""Summarize actual Small Phone executions; no tests or product changes."""
from pathlib import Path
import hashlib, json, re, subprocess, zipfile
from datetime import datetime, timezone

OUT=Path(__file__).resolve().parent
ROOT=OUT.parents[2]
def read(name): return json.loads((OUT/name).read_text(encoding='utf-8-sig'))
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def write(name,value): (OUT/name).write_bytes((json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8'))
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
assert head=='1aad5653c801224316e1bdaec096b2a9b7582607'
subprocess.check_call(['git','diff','--exit-code'],cwd=ROOT)
subprocess.check_call(['git','diff','--cached','--exit-code'],cwd=ROOT)
assert read('original-app-before.json')==read('original-app-after-final.json')
original=read('environment.json')['initialSettings']
restored=read('settings-after.json')
assert all(restored[k]==v for k,v in original.items())
assert restored['secure.show_ime_with_hard_keyboard']==(OUT/'ime-original.txt').read_text(encoding='utf-8-sig').strip()

test_runs={}
for label,count in [('existing-instrumentation',12),('local-ui-final',5),('large-font-150',3),('offline-first-launch',1)]:
    raw=(OUT/(label+'.txt')).read_text(encoding='utf-8')
    assert re.search(r'OK \('+str(count)+r' tests?\)',raw),label
    assert 'INSTRUMENTATION_STATUS_CODE: -2' not in raw,label
    test_runs[label]={'status':'PASS','invocations':count,'log':label+'.txt'}
benchmark=read('data05-fts-benchmark-api30.json')
assert benchmark['datasetWords']==5000 and len(benchmark['cases'])==5
assert [case['matches'] for case in benchmark['cases']]==[1,1,100,0,5000]
assert all(len(case['samplesNs'])==100 for case in benchmark['cases'])
phase_raw=(OUT/'manual-phase-logcat.txt').read_text(encoding='utf-8')
phases={}
for line in phase_raw.splitlines():
    match=re.search(r'(\w+_ms)=([0-9.]+)',line)
    if match: phases.setdefault(match[1],[]).append(float(match[2]))
write('phase-summary.json',dict(process=read('manual-process.json'),samplesMs=phases,
    scope={'debounce_ms':'input to scheduled start; actual emulator clock',
           'queue_ms':'SearchProvider executor submit to worker start',
           'query_hydration_ms':'Repository FTS read + entity/domain hydration',
           'submit_to_main_ms':'VM submit to main delivery; overlaps queue/query',
           'bind_to_post_ms':'state observation to diff commit + posted callback, not GPU present'},
    rawLog='manual-phase-logcat.txt'))

artifacts=read('installed-artifacts.json')
offline=read('installed-artifacts-offline.json')
for collection in (artifacts,offline):
    for artifact in collection.values(): assert sha(ROOT/artifact['path'])==artifact['sha256']
fixture='app/src/catalogTest/assets/benchmark/catalog-5000.db'
with zipfile.ZipFile(ROOT/artifacts['app']['path']) as z:
    assert not any(p.startswith('assets/benchmark/') for p in z.namelist())
    assert hashlib.sha256(z.read('assets/database/catalog.db')).hexdigest()==sha(ROOT/'app/src/main/assets/database/catalog.db')
with zipfile.ZipFile(ROOT/artifacts['tests']['path']) as z:
    assert hashlib.sha256(z.read('assets/benchmark/catalog-5000.db')).hexdigest()==sha(ROOT/fixture)

write('verification.json',dict(verifiedAtUtc=datetime.now(timezone.utc).isoformat(),head=head,
    branch='feature/search-01-search-ui',productionSourceChanged=False,trackedFilesChanged=False,
    sourceCodeCommit='67c06d4aca3b237d7eb8565dfbb410b5acafd674',
    repoState='Only this untracked evidence directory added; no staging/commit/push/PR/merge',
    emulator=read('environment.json'),runtimeRamMb=2048,ramNote='Emulator log explicitly raises configured 1024 MB to 2048 MB; /proc/meminfo MemTotal=2028236 kB',
    sourceFixtureHashes={fixture:sha(ROOT/fixture),'app/src/main/assets/database/catalog.db':sha(ROOT/'app/src/main/assets/database/catalog.db')},
    instrumentation=test_runs,acceptedRunInvocations=21,uniqueTestMethods=17,
    earlierSupplementalRun='local-ui-instrumentation.txt: 5 PASS, repeated after fixing screenshot timing/IME evidence; not counted again',
    manualAdb=read('manual-actions.json')[-1],originalAppDataUnchanged=True,settingsRestored=True,
    installedQaArtifacts=artifacts,offlineQaArtifacts=offline,
    benchmark=dict(status='COLLECTED - emulator only',raw='data05-fts-benchmark-api30.json',warmupsPerWorkload=20,samplesPerWorkload=100,totalRawSamples=500,
                   scope=benchmark['timingScope'],latencyAssertionsEnabled=benchmark['latencyAssertionsEnabled'],referenceHardwareVerified=False,
                   observedAllSamplesBelow50Ms=all(c['maxMs']<50 for c in benchmark['cases']),
                   observedAllWorkloadMeansBelow15Ms=all(c['meanMs']<15 for c in benchmark['cases'])),
    notRun=['Reference Helio G85/3GB/Android10 benchmark','UI Error/Retry driven by a real repository read failure (closed-DB repository contract tested separately)','Full VOCAB-02 detail/Save',
            'Physical handset QA','Host tests/full lint/CI rerun in this emulator-only session','5000-row real FTS-to-UI render stress'],
    AC05='HOLD - emulator QA supplement; reference performance unproven',NFR_VM_03='HOLD'))

# Convert terminal logs to readable UTF-8, without modifying raw samples/screenshots.
for path in OUT.glob('*.txt'):
    raw=path.read_bytes()
    decoded=raw.decode('utf-16') if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else raw.decode('utf-8-sig')
    decoded='\n'.join(line.rstrip() for line in decoded.replace('\r\r\n','\n').splitlines()).rstrip()
    path.write_bytes((decoded+'\n' if decoded else '').encode('utf-8'))
print(json.dumps({'head':head,'runs':test_runs,'originalAppUnchanged':True,'settingsRestored':True,'benchmarkSamples':500}))
