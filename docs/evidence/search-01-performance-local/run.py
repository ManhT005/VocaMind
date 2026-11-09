"""Reproducible local collector. Never clears data; only installs named QA APKs.

python .../run.py LABEL APK CLASSES
Requires adb on the SDK path below. Use a verified physical serial via ANDROID_SERIAL.
Latency assertions remain off here: this emulator run is not SRS acceptance.
"""
import hashlib, json, os, pathlib, subprocess, sys, time

OUT = pathlib.Path(__file__).resolve().parent
ROOT = OUT.parents[2]
ADB = r'C:\Users\LMT\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')

def adb(*args):
    return subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True, check=True).stdout

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

if __name__ == '__main__':
    label, apk, classes = sys.argv[1:]
    apk = ROOT / apk
    meta = {'serial': SERIAL, 'head': subprocess.check_output(['git','rev-parse','HEAD'], cwd=ROOT).decode().strip(),
            'apk': str(apk.relative_to(ROOT)), 'apkSha256': sha(apk), 'classes': classes,
            'latencyAssertionsEnabled': False, 'referenceHardwareVerified': False,
            'fixtureSha256': sha(ROOT/'app/src/catalogTest/assets/benchmark/catalog-5000.db')}
    meta['install'] = adb('install','-r','-t',str(apk)).decode()
    test_path = adb('shell','pm','path','com.vocamind.app.qa.test').decode().strip().removeprefix('package:')
    meta['instrumentationApkSha256'] = hashlib.sha256(adb('exec-out','cat',test_path)).hexdigest()
    for key in ['ro.product.model','ro.hardware','ro.build.version.sdk','ro.build.fingerprint']:
        meta[key] = adb('shell','getprop',key).decode().strip()
    meta['meminfo'] = adb('shell','cat','/proc/meminfo').decode()
    (OUT/(label+'-environment.json')).write_text(json.dumps(meta, indent=2)+'\n',encoding='utf8')
    time.sleep(2)
    cmd = ['shell','am','instrument','-w','-r','-e','class',classes,'-e','data05EnforceLatency','false',
           'com.vocamind.app.qa.test/androidx.test.runner.AndroidJUnitRunner']
    (OUT/(label+'-command.json')).write_text(json.dumps([ADB,'-s',SERIAL,*cmd],indent=2)+'\n',encoding='utf8')
    log = adb(*cmd)
    (OUT/(label+'-instrumentation.txt')).write_bytes(log)
    if b'OK (' not in log or b'FAILURES!!!' in log:
        raise RuntimeError(log.decode(errors='replace')[-3000:])
    for file in ['data05-fts-benchmark-api30.json','search-cost.json']:
        # Only collect files actually produced by the requested test, never stale samples.
        if ('BenchmarkInstrumentedTest' in classes and file.startswith('data05')) or ('CostInstrumentedTest' in classes and file.startswith('search-cost')):
            raw = adb('exec-out','run-as','com.vocamind.app.qa','cat','files/'+file)
            (OUT/(label+'-'+file)).write_bytes(raw)
            print(label, file, [(c.get('variant',c.get('matches')), c['meanMs'],c['p95Ms'],c['maxMs']) for c in json.loads(raw)['cases']])
    print(log.decode(errors='replace')[-180:])
