import subprocess,json,time,sqlite3,xml.etree.ElementTree as ET
from pathlib import Path
adb=r'C:/Users/LMT/AppData/Local/Android/Sdk/platform-tools/adb.exe';serial='emulator-5560';pkg='com.vocamind.app'
out=Path(Path('build/pack02-followup-device-run.txt').read_text());records=json.loads((out/'commands.json').read_text())
def cmd(args):
 r=subprocess.run([adb,'-s',serial]+args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
 text=r.stdout.decode('utf-8',errors='replace');records.append(dict(command='adb -s '+serial+' '+' '.join(args),exit=r.returncode,output=text))
 (out/'commands.json').write_text(json.dumps(records,indent=2))
 return text
def hierarchy():
 cmd(['shell','uiautomator','dump','/sdcard/pack02-restoration.xml'])
 xml=cmd(['shell','cat','/sdcard/pack02-restoration.xml'])
 return xml,ET.fromstring(xml)
def find(tree,rid):
 return next((n for n in tree.iter('node') if n.get('resource-id')==pkg+':id/'+rid),None)
def tap(node):
 import re
 x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
 cmd(['shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
cmd(['shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n',pkg+'/.MainActivity'])
for _ in range(8):
 xml,tree=hierarchy()
 if find(tree,'activation') is not None: break
 home=find(tree,'open_packs'); row=find(tree,'pack_root')
 if home is not None:tap(home)
 elif row is not None:tap(row)
 else:time.sleep(1)
else: raise AssertionError('Cannot stage detail destination')
assert find(tree,'activation').get('text')=='Kích hoạt'
(out/'M06-before-hierarchy.xml').write_text(xml,encoding='utf-8')
pid=cmd(['shell','pidof',pkg]).strip();assert pid
cmd(['shell','input','keyevent','KEYCODE_HOME']);time.sleep(2)
cmd(['shell','am','kill',pkg]);time.sleep(2)
assert not cmd(['shell','pidof',pkg]).strip(),'Process still alive'
cmd(['shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n',pkg+'/.MainActivity'])
for _ in range(8):
 xml,tree=hierarchy()
 node=find(tree,'activation')
 if node is not None and node.get('text')=='Kích hoạt' and node.get('enabled')=='true':break
 time.sleep(1)
else:raise AssertionError('Detail destination was not restored after process death')
newpid=cmd(['shell','pidof',pkg]).strip();assert newpid and newpid!=pid
(out/'M06-restored-hierarchy.xml').write_text(xml,encoding='utf-8')
r=subprocess.run([adb,'-s',serial,'exec-out','screencap','-p'],capture_output=True,check=True);(out/'M06-restored.png').write_bytes(r.stdout)
folder=out/'restored';folder.mkdir(exist_ok=True)
for name in ['userdata.db','userdata.db-wal','userdata.db-shm']:
 r=subprocess.run([adb,'-s',serial,'exec-out','run-as',pkg,'cat','databases/'+name],capture_output=True)
 if r.returncode==0:(folder/name).write_bytes(r.stdout)
c=sqlite3.connect(folder/'userdata.db');tables=[r[0] for r in c.execute("select name from sqlite_master where type='table' order by name") if r[0]!='android_metadata']
data={t:sorted(c.execute('select * from ['+t+']').fetchall(),key=repr) for t in tables};c.close()
assert json.loads(json.dumps(data))==json.loads((out/'false-before.json').read_text()),'Restoration changed userdata'
(out/'restored.json').write_text(json.dumps(data,sort_keys=True,indent=2))
result=dict(status='PASS',processDeath=dict(oldPid=pid,newPid=newpid,mechanism='HOME then adb am kill, verified PID absent; launcher reused task'),cases=['M01','M02','M03','M04-active','M04-paused','M05-loading-pending-confirmed','M06','M08'])
(out/'result.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result))
