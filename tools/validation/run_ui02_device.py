"""Local/offline UI-02 device runner. APK build is a separate step; no uploads.
Runs each new case separately, then selected classes twice, and an expected-failure probe.
Only clears the exact dedicated UI02 package; never the user's installation.
"""
import argparse, datetime, hashlib, html, json, re, subprocess, sys, time
import xml.etree.ElementTree as ET
from pathlib import Path
from ui02_network import OfflineNetwork
from ui02_artifacts import collect_artifacts
from ui02_ci_device import bootstrap_ci_emulator

PACKAGE = 'com.vocamind.app.ui02validation'
PREFIX = 'com.vocamind.app.feature.discovery.'
CASES = {
    'DiscoveryJourneyInstrumentedTest': [
        'offlinePackOrderVisibleItemsDetailsBothIpaAndNoWrites',
        'activateCloseLaunchPauseCloseLaunchPreservesLearningAndSaved',
        'allInactiveTodayCtaOpensDiscoveryWithoutActivation',
        'saveBackReopenCloseLaunchUnsavePreservesCatalogAndLearning',
        'offlineEnglishVietnameseRealFtsVisibleResultDetailResetNoMatch'],
    'DiscoverySearchStateInstrumentedTest': [
        'loadingContentNoMatchAndAtoBtoARejectsOldSuccessAndError',
        'retryCurrentQueryErrorEditInvalidAndResetAreDistinct',
        'resetRunningRejectsLateCompletionAndLateError',
        'whitespaceAndResetPendingNeverSubmitFts',
        'backWhileRunningAndRecreateKeepsCurrentQueryAndDropsAbandonedRead']}

def run(cmd, **kwargs):
    return subprocess.run(list(map(str, cmd)), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', **kwargs)

def main():
    p=argparse.ArgumentParser(); p.add_argument('--adb', default='adb'); p.add_argument('--serial', required=True)
    p.add_argument('--output', required=True); p.add_argument('--only', help='Fully qualified class#method; omit for acceptance sequence')
    p.add_argument('--skip-install',action='store_true'); p.add_argument('--qa',action='store_true')
    p.add_argument('--ci-emulator-api', choices=('26','35'), help='Explicit disposable debug-emulator CI bootstrap; API26 needs root for Wi-Fi control')
    args=p.parse_args()
    global PACKAGE
    if args.qa:
        PACKAGE += '.qa'
        if not args.only: p.error('--qa requires an explicit QA class#method selector')
    out=Path(args.output)
    if out.exists() and any(out.iterdir()):
        p.error('--output must be new or empty; do not reuse evidence from a previous run')
    out.mkdir(parents=True,exist_ok=True)
    adb=[args.adb,'-s',args.serial]
    def shell(*cmd): return run(adb+['shell',*cmd],timeout=10)
    if args.ci_emulator_api:
        bootstrap_ci_emulator(adb, args.ci_emulator_api, out, run)
    revision=run(['git','rev-parse','HEAD']).stdout.strip()
    files=run(['git','ls-files','--cached','--others','--exclude-standard']).stdout.splitlines()
    source_files=[f for f in files if f.startswith(('app/src/','app/build.gradle','tools/validation/','.github/'))]
    hashes={f:hashlib.sha256(Path(f).read_bytes()).hexdigest() for f in sorted(source_files) if Path(f).is_file()}
    digest=hashlib.sha256(json.dumps(hashes,sort_keys=True).encode()).hexdigest()
    tested=revision+'+sources-sha256:'+digest
    (out/'source-hashes.json').write_text(json.dumps(hashes,indent=2),encoding='utf-8')
    (out/'working-tree.patch').write_text(run(['git','diff','--binary','HEAD']).stdout,encoding='utf-8')
    env={'revision':revision,'sourceDigest':digest,'testedRevision':tested,'serial':args.serial,
         'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
         'device':shell('getprop').stdout,'java':run(['java','-version']).stdout}
    variant='catalogQa' if args.qa else 'discovery'
    apks=[Path(f'app/build/outputs/apk/{variant}/app-{variant}.apk'),Path(f'app/build/outputs/apk/androidTest/{variant}/app-{variant}-androidTest.apk')]
    env['apks']={str(f):hashlib.sha256(f.read_bytes()).hexdigest() for f in apks}
    fixture=Path('app/build/generated/voc06-qa/assets/database/catalog.metadata.json') if args.qa else Path('app/build/ui02-fixture/ui02-fixture.json')
    if fixture.is_file(): env['fixture']=json.loads(fixture.read_text(encoding='utf-8-sig'))
    env['settings']={key:shell('settings','get','global',key).stdout.strip() for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']}
    (out/'environment.json').write_text(json.dumps(env,indent=2),encoding='utf-8')
    if not args.skip_install:
        for apk in apks:
            result=run(adb+['install','-r','-t',apk],timeout=180)
            (out/(apk.name+'.install.txt')).write_text(result.stdout,encoding='utf-8')
            if result.returncode: raise RuntimeError(result.stdout)
    sequence=[]
    if args.only: sequence=[('target',args.only,False)]
    else:
        for cls,methods in CASES.items():
            for method in methods: sequence.append(('independent-'+method,PREFIX+cls+'#'+method,False))
        classes=','.join(PREFIX+c for c in CASES)
        sequence += [('suite-1',classes,False),('suite-2',classes,False),('artifact-probe',PREFIX+'DiscoveryJourneyInstrumentedTest#failureArtifactProbe',True)]
    results=[]
    with OfflineNetwork(shell, out).isolated() as network:
        for label,selector,probe in sequence:
            folder=out/label; folder.mkdir(exist_ok=True)
            # A new instrumentation process and a clean dedicated install's data for every run.
            cleared=shell('pm','clear',PACKAGE)
            if cleared.returncode or 'Success' not in cleared.stdout: raise RuntimeError('Isolation reset failed: '+cleared.stdout)
            network.require_offline(folder)
            command=adb+['shell','am','instrument','-w','-r','-e','class',selector,'-e','testedRevision',tested]
            if probe: command+=['-e','ui02FailureProbe','true']
            command += [PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner']
            (folder/'command.json').write_text(json.dumps(command,indent=2),encoding='utf-8')
            started=time.monotonic(); result=run(command,timeout=600)
            (folder/'instrumentation.txt').write_text(result.stdout,encoding='utf-8')
            # Build JUnit XML/HTML from the device's raw AndroidJUnitRunner status events.
            cases=[]; current={}; stack=[]; reading=False
            for line in result.stdout.splitlines():
                m=re.match(r'INSTRUMENTATION_STATUS: ([^=]+)=(.*)',line)
                if m:
                    current[m[1]]=m[2]; reading=m[1]=='stack'
                    if reading: stack=[m[2]]
                elif line.startswith('INSTRUMENTATION_STATUS_CODE:'):
                    code=int(line.split(':',1)[1]); reading=False
                    if code<=0 and 'test' in current:
                        cases.append({'class':current.get('class',''),'test':current['test'],'code':code,'stack':'\n'.join(stack)})
                    current={}; stack=[]
                elif reading: stack.append(line)
            failures=sum(c['code'] in (-1,-2) for c in cases); skipped=sum(c['code'] in (-3,-4) for c in cases)
            root=ET.Element('testsuite',name=label,tests=str(len(cases)),failures=str(failures),skipped=str(skipped),time=str(time.monotonic()-started))
            for c in cases:
                case=ET.SubElement(root,'testcase',classname=c['class'],name=c['test'])
                if c['code'] in (-1,-2): ET.SubElement(case,'failure').text=c['stack']
                elif c['code'] in (-3,-4): ET.SubElement(case,'skipped').text=c['stack']
            ET.ElementTree(root).write(folder/'junit.xml',encoding='utf-8',xml_declaration=True)
            (folder/'report.html').write_text('<meta charset="utf-8"><h1>'+html.escape(label)+'</h1><pre>'+html.escape(result.stdout)+'</pre>',encoding='utf-8')
            artifact_ok=collect_artifacts(adb, PACKAGE, folder, tested, required=probe or failures>0,
                    expected_test=selector if probe else None)
            complete='INSTRUMENTATION_CODE: -1' in result.stdout and 'INSTRUMENTATION_FAILED' not in result.stdout
            expected_count=1 if '#' in selector else sum(map(len,CASES.values()))+1
            expected_skipped=0 if '#' in selector else 1
            ok=(complete and len(cases)==1 and failures==1 and 'UI02_EXPECTED_ARTIFACT_PROBE' in result.stdout and artifact_ok) if probe else (complete and len(cases)==expected_count and failures==0 and skipped==expected_skipped and all(c['code'] in (0,-3,-4) for c in cases))
            entry={'run':label,'selector':selector,'tests':len(cases),'pass':len(cases)-failures-skipped,'fail':failures,'skipped':skipped,'expectedFailureProbe':probe,'artifactsVerified':artifact_ok,'accepted':ok,'seconds':time.monotonic()-started}
            results.append(entry); print(json.dumps(entry),flush=True)
            (out/'summary.json').write_text(json.dumps({'testedRevision':tested,'runs':results},indent=2),encoding='utf-8')
            if not ok:
                # App-side rules cannot run after a native/process crash. Preserve external diagnostics too.
                # This screenshot is explicitly post-run, not a replacement for the pre-teardown rule image.
                (folder/'external-logcat.txt').write_text(shell('logcat','-b','all','-d','-v','threadtime').stdout,encoding='utf-8')
                screen=subprocess.run(adb+['exec-out','screencap','-p'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)
                (folder/'external-post-run-screen.png').write_bytes(screen.stdout)
                (folder/'external-metadata.json').write_text(json.dumps({'testedRevision':tested,'selector':selector,
                    'adbExitCode':result.returncode,'complete':complete,'expectedCount':expected_count,
                    'observed':entry,'screenshotPhase':'post-run; process may have ended'},indent=2),encoding='utf-8')
                return 1
    return 0
if __name__=='__main__': sys.exit(main())
