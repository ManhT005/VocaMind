"""Collect existing local results without rerunning tests or changing product inputs."""
from pathlib import Path
import hashlib
import json
import platform
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def write(name, value): (OUT / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
def git(*args): return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()

sha = '67c06d4aca3b237d7eb8565dfbb410b5acafd674'
subprocess.check_call(['git', 'diff', '--exit-code', sha, '--', 'app', 'tools', 'gradle', '.github',
                       'docs/data', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat'], cwd=ROOT)
suite_rows, trace, benchmarks = [], [], []
for path in sorted((ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    root = ET.parse(path).getroot()
    suite_rows.append({k: root.attrib[k] for k in ('name', 'tests', 'failures', 'errors', 'skipped', 'timestamp')})
    stdout = root.findtext('system-out', '')
    for line in stdout.splitlines():
        if line.startswith('SEARCH01_HOST_TRACE '): trace.append(line.removeprefix('SEARCH01_HOST_TRACE '))
        if line.startswith('DATA05_BENCHMARK_JSON='): benchmarks.append(json.loads(line.split('=', 1)[1]))
    if any(x in path.name for x in ('feature.search.', 'FtsSearch', 'VocabularyListTest', 'PackVocabularyIntegrationTest', 'RepositoryIntegrationTest', 'ProgressAggregationTest')):
        shutil.copyfile(path, OUT / path.name)
totals = {key: sum(int(s[key]) for s in suite_rows) for key in ('tests','failures','errors','skipped')}
assert totals == dict(tests=406, failures=0, errors=0, skipped=0), totals
assert len(benchmarks) == 2 and trace
phases = {}
for item in trace:
    match = re.search(r'(\w+_ms)=([0-9.]+)', item)
    if match: phases.setdefault(match[1], []).append(float(match[2]))
write('latency.json', dict(referenceHardwareVerified=False, acceptance='HOLD',
    runner='Robolectric API26/35 on Windows host, virtual main-looper clock',
    uiFixtureWords=4, benchmarkFixtureWords=5000,
    caveats=['Host observations cannot certify reference hardware',
             'debounce_ms is wall time while scheduler advances virtually; not device debounce duration',
             'bind_to_post_ms is not frame-present time',
             'query_hydration_ms includes domain mapping, unlike DATA-05 DAO benchmark'],
    rawPhaseTrace=trace, phaseSamplesMs=phases, data05Benchmarks=benchmarks))

inputs = []
for line in git('ls-tree', '-r', sha).splitlines():
    meta, path = line.split('\t', 1)
    if path.startswith(('app/', 'tools/', 'gradle/', '.github/', 'docs/data/')) or path in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat'):
        inputs.append(dict(path=path, gitBlob=meta.split()[-1]))
write('tested-inputs.json', dict(commit=sha, hashKind='Git blob IDs with repository line-ending filters', inputs=inputs))
shutil.copyfile(ROOT / 'app/build/reports/lint-results-debug.xml', OUT / 'lint-results-debug.xml')
lint = ET.parse(OUT / 'lint-results-debug.xml').getroot()
warnings = sum(i.attrib['severity']=='Warning' for i in lint.findall('issue'))
errors = sum(i.attrib['severity'] in ('Error','Fatal') for i in lint.findall('issue'))
artifacts = {}
for key, path in dict(app='app/build/outputs/apk/debug/app-debug.apk', tests='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk').items():
    file = ROOT / path
    with zipfile.ZipFile(file) as z:
        assets = [n for n in z.namelist() if n.startswith('assets/benchmark/')]
    artifacts[key] = dict(path=path, sha256=digest(file), benchmarkAssets=assets)
assert not artifacts['app']['benchmarkAssets'] and artifacts['tests']['benchmarkAssets']
python_steps = json.loads((OUT/'python-results.json').read_text(encoding='utf-8'))
assert len(python_steps)==22 and all(s['exitCode']==s['expected'] for s in python_steps)
write('verification.json', dict(codeSha=sha, developBase='8209ef75bc882ca10367abd40900e44e537571cb',
    environment=dict(host=platform.platform(), cpu='Intel Core i5-11400H', jdk='Temurin 17.0.20.1+1',
                     python=platform.python_version(), gradle='8.9', agp='8.7.3', compileSdk=35, buildTools='34.0.0',
                     TEMP='C:/tvm', GRADLE_USER_HOME='C:/Users/LMT/.gradle'),
    androidCommand='gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline',
    androidExitCode=0, androidTests=totals, suites=suite_rows, searchTests=26,
    lint=dict(errors=errors,warnings=warnings), apk=artifacts,
    pythonSteps=22, pythonUnittestTests=159,
    testedInputsSha256=digest(OUT/'tested-inputs.json'),
    fixtureHashes={p:digest(ROOT/p) for p in ['app/src/main/assets/database/catalog.db','app/src/catalogTest/assets/benchmark/catalog-5000.db']},
    connectedInstrumentation='NOT RUN - no device attached', airplaneModeFirstLaunch='NOT RUN',
    physicalUiKeyboardAndLongList='NOT RUN', referenceHardwareVerified=False,
    AC05='HOLD', NFR_VM_03='HOLD', fullVocab02Detail='PENDING - receiver preview only', githubCI='PENDING on pushed head'))
for path in OUT.glob('*.txt'):
    raw = path.read_bytes()
    if raw.startswith((b'\xff\xfe', b'\xfe\xff')): path.write_text(raw.decode('utf-16'), encoding='utf-8')
print(json.dumps(dict(tests=totals, lint=dict(errors=errors,warnings=warnings), traceSamples={k:len(v) for k,v in phases.items()}, benchmarkRuns=len(benchmarks))))
