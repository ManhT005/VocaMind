"""Local integration QA. Keeps original and Quiz validation packages/data intact."""
from pathlib import Path
import hashlib,json,subprocess,sys,time,re,xml.etree.ElementTree as ET
OUT=Path(__file__).resolve().parent
ROOT=OUT.parents[2]
ADB=r'C:\Users\LMT\AppData\Local\Android\Sdk\platform-tools\adb.exe'
QA='com.vocamind.app.qa'
def adb(*args,check=True):
    p=subprocess.run([ADB,'-s','emulator-5554',*args],capture_output=True)
    if check and p.returncode: raise RuntimeError((args,p.stdout.decode(errors='replace'),p.stderr.decode(errors='replace')))
    return p
def text(*args,check=True): return adb(*args,check=check).stdout.decode('utf-8',errors='replace').strip()
def write(name,value): (OUT/name).write_bytes((json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode())
def snapshots():
    result={}
    for pkg in ['com.vocamind.app','com.vocamind.app.quiz03validation']:
        files=text('shell','run-as',pkg,'find','databases','-type','f',check=False).splitlines()
        result[pkg]={'apk':text('shell','pm','path',pkg),'databases':{}}
        for path in files:
            if path.startswith('databases/'):
                result[pkg]['databases'][path]=hashlib.sha256(adb('exec-out','run-as',pkg,'cat',path).stdout).hexdigest()
    return result
if __name__=='__main__':
    mode=sys.argv[1]
    if mode=='before':
        write('existing-apps-before.json',snapshots())
        settings={ns+'.'+key:text('shell','settings','get',ns,key) for ns,key in [('global','airplane_mode_on'),('global','wifi_on'),('system','font_scale')]}
        write('environment.json',dict(model=text('shell','getprop','ro.product.model'),api=text('shell','getprop','ro.build.version.sdk'),mem=text('shell','cat','/proc/meminfo'),settings=settings,qaInstalled=text('shell','pm','path',QA,check=False)))
    elif mode=='install':
        rows={}
        for name,path in [('app','app/build/outputs/apk/catalogQa/app-catalogQa.apk'),('tests','app/build/outputs/apk/androidTest/catalogQa/app-catalogQa-androidTest.apk')]:
            file=ROOT/path
            rows[name]=dict(path=path,sha256=hashlib.sha256(file.read_bytes()).hexdigest(),install=adb('install','-r','-t',str(file)).stdout.decode())
        write('qa-artifacts.json',rows); print(json.dumps(rows))
    elif mode=='run':
        label,classes=sys.argv[2:4]
        p=adb('shell','am','instrument','-w','-r','-e','class',classes,'-e','data05EnforceLatency','false',QA+'.test/androidx.test.runner.AndroidJUnitRunner',check=False)
        (OUT/(label+'.txt')).write_bytes(p.stdout+p.stderr)
        print((p.stdout+p.stderr).decode(errors='replace')[-4200:])
    elif mode=='pull':
        for path in text('shell','run-as',QA,'find','files','-type','f').splitlines():
            if 'search-vocab02-qa/' in path or 'data05-fts-benchmark' in path:
                (OUT/Path(path).name).write_bytes(adb('exec-out','run-as',QA,'cat',path).stdout)
        (OUT/'logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime','SearchTiming:D','AndroidRuntime:E','*:S').stdout)
        write('existing-apps-after.json',snapshots())
    elif mode=='show':
        def tree(label):
            adb('shell','uiautomator','dump','/sdcard/search-vocab02.xml')
            raw=adb('exec-out','cat','/sdcard/search-vocab02.xml').stdout
            (OUT/(label+'.xml')).write_bytes(raw)
            return ET.fromstring(raw)
        def tap(root,suffix,textValue=None):
            for n in root.iter('node'):
                if n.attrib.get('resource-id','').endswith('/'+suffix) and (textValue is None or n.attrib.get('text')==textValue):
                    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); return
            raise AssertionError((suffix,textValue))
        adb('shell','am','start','-n',QA+'/com.vocamind.app.MainActivity'); time.sleep(.5)
        tap(tree('manual-home'),'open_vocabulary'); time.sleep(.3)
        tap(tree('manual-no-input'),'search_src_text')
        adb('shell','input','text','customer'); time.sleep(1)
        root=tree('manual-search'); tap(root,'word_title','customer'); time.sleep(.5)
        (OUT/'manual-detail.xml').write_bytes(adb('exec-out','cat','/sdcard/search-vocab02.xml').stdout)
        (OUT/'06-manual-detail.png').write_bytes(adb('exec-out','screencap','-p').stdout)
        tree('manual-detail')
