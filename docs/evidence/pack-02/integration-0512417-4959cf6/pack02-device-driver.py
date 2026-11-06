import subprocess,time,json,datetime,hashlib,sqlite3,xml.etree.ElementTree as ET
from pathlib import Path
adb=r'C:/Users/LMT/AppData/Local/Android/Sdk/platform-tools/adb.exe'
serial='emulator-5560';pkg='com.vocamind.app';klass=pkg+'.feature.packs.PackActivationInstrumentedTest'
out=Path('build/pack02-device-final-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));out.mkdir()
Path('build/pack02-followup-device-run.txt').write_text(str(out))
records=[]
def cmd(args,name=None,timeout=90):
 r=subprocess.run([adb,'-s',serial]+args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
 t=r.stdout.decode('utf-8',errors='replace')
 records.append(dict(command='adb -s '+serial+' '+' '.join(args),exit=r.returncode,output=t))
 if name:(out/(name+'.txt')).write_text(t,encoding='utf-8')
 (out/'commands.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
 return t
assert cmd(['emu','avd','name']).strip().splitlines()[0]=='pack02-followup','Refuse non-QA emulator'
for a in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
 assert 'Success' in cmd(['install','-r',a])
# Only the freshly created disposable QA AVD/package, never Small_Phone or an existing user profile.
assert 'Success' in cmd(['shell','pm','clear',pkg],'fresh-qa-install')
cmd(['shell','cmd','connectivity','airplane-mode','enable'])
cmd(['shell','svc','wifi','disable']);cmd(['shell','svc','data','disable'])
assert cmd(['shell','settings','get','global','airplane_mode_on']).strip()=='1'
assert cmd(['shell','settings','get','global','wifi_on']).strip()=='0'
env={k:cmd(['shell','getprop',k]).strip() for k in ['ro.product.model','ro.build.version.sdk','ro.build.fingerprint']}
env.update(serial=serial,avd='pack02-followup',date=datetime.datetime.now().astimezone().isoformat(),apkSha256={p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']})
(out/'environment.json').write_text(json.dumps(env,indent=2))
def test(method,name,extra=[]):
 t=cmd(['shell','am','instrument','-w','-e','class',klass+'#'+method]+extra+[pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],name)
 assert 'OK (1 test)' in t,t
 print(name,'PASS',flush=True)
def snapshot(name):
 folder=out/name;folder.mkdir()
 for filename in ['userdata.db','userdata.db-wal','userdata.db-shm']:
  r=subprocess.run([adb,'-s',serial,'exec-out','run-as',pkg,'cat','databases/'+filename],capture_output=True)
  if r.returncode==0:(folder/filename).write_bytes(r.stdout)
 c=sqlite3.connect(folder/'userdata.db')
 tables=[r[0] for r in c.execute("select name from sqlite_master where type='table' order by name") if r[0]!='android_metadata']
 data={t:sorted(c.execute('select * from ['+t+']').fetchall(),key=repr) for t in tables};c.close()
 (out/(name+'.json')).write_text(json.dumps(data,sort_keys=True,indent=2))
 return data
def reboot(name):
 cmd(['reboot'])
 deadline=time.time()+180
 while time.time()<deadline:
  time.sleep(3)
  r=subprocess.run([adb,'-s',serial,'shell','getprop','sys.boot_completed'],capture_output=True,timeout=10)
  if r.returncode==0 and r.stdout.strip()==b'1':break
 else:raise AssertionError('reboot timeout')
 assert cmd(['shell','settings','get','global','airplane_mode_on']).strip()=='1'
 assert cmd(['shell','settings','get','global','wifi_on']).strip()=='0'
 print(name,'booted offline',flush=True)
test('offlineFirstLaunchCtaActivateVocabularyBackPauseAndConfirmedRotation','M01-M03-offline-navigation')
test('rotationWhileWritePendingKeepsSingleMutationAndReconciles','M05-pending-rotation')
test('writeFailureAndSuccessfulWriteFailedRereadUseReadOnlyRetry','M08-error-reconcile')
for state in ['true','false']:
 test('persistenceProbe','prepare-'+state,['-e','active',state])
 cmd(['shell','am','force-stop',pkg])
 before=snapshot(state+'-before')
 test('persistenceProbe','relaunch-'+state,['-e','active',state,'-e','verifyOnly','true'])
 assert before==snapshot(state+'-relaunch'),'force-stop snapshot mismatch'
 reboot('reboot-'+state)
 test('persistenceProbe','reboot-'+state,['-e','active',state,'-e','verifyOnly','true'])
 assert before==snapshot(state+'-reboot'),'reboot snapshot mismatch'
 print('M04',state,'PASS exact UserPack/progress/history snapshot',flush=True)
# Real background process death and restoration of the detail destination without a fresh navigate.
test('backgroundDestinationForProcessRestoration','M06-stage',['-e','finishActivities','false'])
pid=cmd(['shell','pidof',pkg]).strip()
assert pid,'No background process to kill'
cmd(['shell','am','kill',pkg]);time.sleep(2)
assert not cmd(['shell','pidof',pkg]).strip(),'Background process was not killed'
cmd(['shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n',pkg+'/.MainActivity'])
time.sleep(3)
newpid=cmd(['shell','pidof',pkg]).strip();assert newpid and newpid!=pid
cmd(['shell','uiautomator','dump','/sdcard/pack02-restored.xml'])
xml=cmd(['shell','cat','/sdcard/pack02-restored.xml'],'M06-restored-hierarchy')
nodes=ET.fromstring(xml).iter('node')
assert any(n.get('resource-id')==pkg+':id/activation' and n.get('text')=='Kích hoạt' for n in nodes),'Detail destination not restored'
assert before==snapshot('restored'),'restoration unexpectedly mutated userdata'
r=subprocess.run([adb,'-s',serial,'exec-out','screencap','-p'],capture_output=True,check=True)
(out/'M06-restored.png').write_bytes(r.stdout)
(out/'result.json').write_text(json.dumps(dict(status='PASS',processDeath=dict(oldPid=pid,newPid=newpid),cases=['M01','M02','M03','M04-active','M04-paused','M05-pending-and-confirmed','M06','M08']),indent=2))
print('M06 process restoration PASS',flush=True)
