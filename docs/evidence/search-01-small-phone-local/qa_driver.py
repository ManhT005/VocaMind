"""Local ADB evidence driver. Never clears/uninstalls the original package or alters repo source."""
from pathlib import Path
import hashlib, json, subprocess, sys, time, zipfile, os

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
ADB = r'C:\Users\LMT\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERIAL = 'emulator-5554'
PKG = os.environ.get('SEARCH01_QA_PACKAGE', 'com.vocamind.app.search01qa')
TEST = PKG + '.test/androidx.test.runner.AndroidJUnitRunner'
def adb(*args, check=True):
    p = subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True)
    if check and p.returncode: raise RuntimeError((args, p.stdout.decode(errors='replace'), p.stderr.decode(errors='replace')))
    return p
def text(*args, check=True): return adb(*args, check=check).stdout.decode('utf-8', errors='replace').strip()
def write(name, value): (OUT/name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def original_snapshot():
    result = {'apkPath':text('shell','pm','path','com.vocamind.app')}
    p = adb('shell','run-as','com.vocamind.app','find','databases','-type','f',check=False)
    result['databaseListingExit'] = p.returncode
    result['databaseListingStderr'] = p.stderr.decode(errors='replace')
    result['databaseHashes'] = {}
    if p.returncode == 0:
        for path in p.stdout.decode().splitlines():
            data = adb('exec-out','run-as','com.vocamind.app','cat',path).stdout
            result['databaseHashes'][path] = hashlib.sha256(data).hexdigest()
    return result
def screenshot(name):
    file = OUT/(name+'.png'); file.write_bytes(adb('exec-out','screencap','-p').stdout)
    return str(file)

if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'inventory':
        props = ['ro.product.model','ro.product.device','ro.product.cpu.abi','ro.build.version.release','ro.build.version.sdk','ro.build.fingerprint','ro.hardware']
        info = {p:text('shell','getprop',p) for p in props}
        info.update(avd=text('emu','avd','name'), meminfo=text('shell','cat','/proc/meminfo'),
                    size=text('shell','wm','size'), density=text('shell','wm','density'),
                    emulatorConfigRamMb=1024, head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                    initialSettings={ns+'.'+key:text('shell','settings','get',ns,key) for ns,key in [('global','airplane_mode_on'),('global','wifi_on'),('system','font_scale'),('system','accelerometer_rotation'),('system','user_rotation')]})
        write('environment.json',info); write('original-app-before.json',original_snapshot()); screenshot('00-initial-emulator')
        print(json.dumps({k:v for k,v in info.items() if k!='meminfo'},ensure_ascii=False))
    elif mode == 'install':
        artifacts = {}
        for key, rel in [('app','outputs/apk/debug/app-debug.apk'),('tests','outputs/apk/androidTest/debug/app-debug-androidTest.apk')]:
            builddir='build/search01-small-phone-offline-apk' if PKG.endswith('.offline') else 'build/search01-small-phone-apk'
            path=ROOT/builddir/rel
            p=adb('install','-r','-t',str(path))
            artifacts[key]={'path':str(path.relative_to(ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'install':p.stdout.decode()}
        write('installed-artifacts-offline.json' if PKG.endswith('.offline') else 'installed-artifacts.json',artifacts); print(json.dumps(artifacts))
    elif mode == 'run':
        label, classes=sys.argv[2:4]
        start=time.time()
        p=adb('shell','am','instrument','-w','-r','-e','class',classes,'-e','data05EnforceLatency','false',TEST,check=False)
        (OUT/(label+'.txt')).write_bytes(p.stdout+p.stderr)
        print(json.dumps({'label':label,'adbExitCode':p.returncode,'elapsedSeconds':time.time()-start}))
        print((p.stdout+p.stderr).decode(errors='replace')[-3500:])
    elif mode == 'pull':
        capture = OUT / os.environ.get('SEARCH01_CAPTURE_DIR', '.')
        capture.mkdir(parents=True, exist_ok=True)
        listing=text('shell','run-as',PKG,'find','files','-type','f')
        for path in listing.splitlines():
            if 'small-phone-qa/' in path or 'data05-fts-benchmark' in path:
                target=capture/Path(path).name
                target.write_bytes(adb('exec-out','run-as',PKG,'cat',path).stdout)
        (capture/'logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime','SearchTiming:D','AndroidRuntime:E','*:S').stdout)
        write('original-app-after.json',original_snapshot()); print(listing)
    elif mode == 'screenshot': print(screenshot(sys.argv[2]))
