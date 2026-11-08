"""Run PROGRESS-02 acceptance against the explicitly isolated package on LDPlayer.
Uses device-side background instrumentation because toggling LD Wi-Fi interrupts ADB.
"""
import argparse,subprocess,time,os,json,hashlib
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--serial',default='emulator-5554');parser.add_argument('--stage',choices=['flow','probe','appearance','collect','restore'],required=True)
a=parser.parse_args();root=Path(__file__).resolve().parents[2];out=root/'docs/evidence/progress-02/ldplayer';out.mkdir(parents=True,exist_ok=True)
pkg='com.vocamind.app.progress02validation'
def adb(*args,timeout=60,check=True):
 r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,timeout=timeout,encoding='utf-8',errors='replace')
 if check and r.returncode: raise RuntimeError(r.stdout+r.stderr)
 return r.stdout+r.stderr
def shell(cmd,**kwargs):return adb('shell',cmd,**kwargs)
if a.stage=='restore':
 for cmd in ['settings put global airplane_mode_on 0','svc wifi enable','settings put global wifi_on 1','settings delete system font_scale','cmd uimode night no','settings delete secure ui_night_mode','wm size reset','wm density reset']:
  print(cmd,shell(cmd))
 raise SystemExit(0)
if a.stage in ['flow','probe','appearance']:
 # Start online. The @Before/@After hooks disconnect and restore from inside Android.
 shell('settings put global airplane_mode_on 0');shell('svc wifi enable')
 if a.stage=='appearance':
  for cmd in ['wm size 720x1280','wm density 360','settings put system font_scale 2.0','cmd uimode night yes']:
   print(cmd,shell(cmd))
  time.sleep(5)
 method={'flow':'aFullProductionFlow','probe':'bPersistenceProbe','appearance':'cSmallDarkLargeFont'}[a.stage]
 record={'stage':a.stage,'method':method,'serial':a.serial,'base_commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'device_apks':{}}
 for package in [pkg,pkg+'.test']:
  paths=shell('pm path '+package).strip().splitlines()
  assert len(paths)==1 and paths[0].startswith('package:/data/app/')
  path=paths[0][len('package:'):]
  record['device_apks'][package]={'path':path,'sha256':shell('sha256sum '+path).split()[0]}
 (out/(a.stage+'-run.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
 remote='/sdcard/progress02-'+a.stage+'.log'
 command='am instrument -w -r -e class com.vocamind.app.feature.progress.ProgressAcceptanceInstrumentedTest#'+method+' '+pkg+'.test/androidx.test.runner.AndroidJUnitRunner > '+remote+' 2>&1 < /dev/null &'
 shell('rm -f '+remote)
 print('Launching',method,flush=True)
 try:shell(command,timeout=10)
 except subprocess.TimeoutExpired:pass # Android-side instrumentation continues independently.
 deadline=time.monotonic()+180;result=''
 while time.monotonic()<deadline:
  time.sleep(3)
  try:
   result=shell('cat '+remote,timeout=5,check=False)
   if 'INSTRUMENTATION_CODE:' in result:break
  except subprocess.TimeoutExpired:
   subprocess.run([a.adb,'connect','127.0.0.1:5555'],capture_output=True,timeout=5)
 else: raise RuntimeError('Device test did not finish; restore LD Wi-Fi and collect '+remote)
 (out/(a.stage+'.log')).write_text(result,encoding='utf-8');print(result)
 if 'FAILURES!!!' in result or 'OK (1 test)' not in result or 'INSTRUMENTATION_STATUS_CODE: -3' in result or 'INSTRUMENTATION_STATUS_CODE: -4' in result:raise RuntimeError('Instrumentation did not pass')
if a.stage=='collect':
 metadata=''
 for cmd in ['getprop ro.build.version.sdk','getprop ro.product.model','getprop ro.build.fingerprint','getprop persist.sys.timezone','settings get global airplane_mode_on','settings get global wifi_on','wm size','wm density']:
  metadata+=cmd+'\n'+shell(cmd)+'\n'
 (out/'device.txt').write_text(metadata,encoding='utf-8')
 for remote in shell('ls /data/local/tmp/progress02-*.png').splitlines():
  if remote.startswith('/data/local/tmp/progress02-'):
   print(adb('pull',remote,str(out/Path(remote).name)))
 for filename in ['progress02-expected.txt','progress02-offline.txt']:
  print(adb('pull','/sdcard/Android/data/'+pkg+'/files/'+filename,str(out/filename)))
