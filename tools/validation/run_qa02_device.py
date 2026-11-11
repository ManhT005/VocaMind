"""QA-02 supplemental device journey; never grants physical-device acceptance.

Build first with -PcatalogQa=true -Pqa02Acceptance=true. Requires an explicit
serial, exact isolated APK identity and a new evidence directory. Raw evidence
stays under ignored build/qa02. Network settings are restored even on failure.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parent))
from ui02_network import OfflineNetwork

PACKAGE = 'com.vocamind.app.qa02validation.qa'
CLASS = 'com.vocamind.app.feature.qa.Qa02JourneyInstrumentedTest'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def accepted(output, returncode, expected=1):
    """A missing, crashed, skipped or multi-test invocation cannot become PASS."""
    codes = re.findall(r'^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$', output, re.M)
    return (expected > 0 and returncode == 0 and codes == ['1', '0'] * expected
            and re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)\s*$', output, re.M) == ['-1']
            and 'INSTRUMENTATION_FAILED' not in output and 'FAILURES!!!' not in output)


class Network(OfflineNetwork):
    def checked(self, *command):
        # Android's controller must receive the change, not only a settings flag.
        if command[:4] == ('settings', 'put', 'global', 'airplane_mode_on'):
            super().checked('cmd', 'connectivity', 'airplane-mode', 'enable' if command[4] == '1' else 'disable')
        elif command == ('settings', 'delete', 'global', 'airplane_mode_on'):
            super().checked('cmd', 'connectivity', 'airplane-mode', 'disable')
        return super().checked(*command)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', required=True)
    p.add_argument('--aapt', required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--reboot', action='store_true', help='Reboot the explicitly selected emulator/device after the journey')
    args = p.parse_args()
    out = Path(args.output).resolve()
    allowed = (Path.cwd() / 'build/qa02').resolve()
    if not out.is_relative_to(allowed) or (out.exists() and any(out.iterdir())):
        p.error('Use a new directory under build/qa02; raw evidence is private')
    out.mkdir(parents=True, exist_ok=True)
    adb = [args.adb, '-s', args.serial]

    def run(command, timeout=30):
        return subprocess.run(list(map(str, command)), stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', timeout=timeout)

    def shell(*command):
        return run(adb + ['shell', shlex.join(command)])

    def checked(*command):
        result = shell(*command)
        if result.returncode:
            raise RuntimeError(result.stdout)
        return result.stdout.strip()

    def write(path, value):
        (out / path).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

    apks = [Path('app/build/outputs/apk/catalogQa/app-catalogQa.apk'),
            Path('app/build/outputs/apk/androidTest/catalogQa/app-catalogQa-androidTest.apk')]
    for apk, package in zip(apks, (PACKAGE, PACKAGE + '.test')):
        badging = run([args.aapt, 'dump', 'badging', apk])
        names = re.findall(r"^package: name='([^']+)'", badging.stdout, re.M)
        if badging.returncode or names != [package]:
            raise RuntimeError('Refuse to install/reset an unexpected package: ' + str(names))
    if 'airplane-mode' not in shell('cmd', 'connectivity', 'help').stdout:
        raise RuntimeError('Airplane controller unsupported; refusing to mutate network')
    source_paths = run(['git', 'ls-files', '--cached', '--others', '--exclude-standard']).stdout.splitlines()
    hashes = {name: sha(name) for name in sorted(source_paths)
              if name.startswith(('app/src/', 'app/build.gradle', 'tools/validation/')) and Path(name).is_file()}
    source_digest = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
    revision = run(['git', 'rev-parse', 'HEAD']).stdout.strip()
    tested = revision + '+sources-sha256:' + source_digest
    write('source-hashes.json', hashes)
    (out / 'tracked.patch').write_text(run(['git', 'diff', '--binary', 'HEAD']).stdout, encoding='utf-8')
    inventory = {key: checked('getprop', key) for key in (
        'ro.product.manufacturer', 'ro.product.model', 'ro.build.version.release',
        'ro.build.version.sdk', 'ro.build.fingerprint', 'ro.kernel.qemu', 'persist.sys.timezone')}
    inventory.update(display=checked('wm', 'size'), density=checked('wm', 'density'),
                     memory=checked('head', '-n', '3', '/proc/meminfo'),
                     deviceClock=checked('date', '-u'), hostUtc=dt.datetime.now(dt.timezone.utc).isoformat())
    with zipfile.ZipFile(apks[0]) as apk:
        catalog_hash = hashlib.sha256(apk.read('assets/database/catalog.db')).hexdigest()
    write('environment.json', dict(testedRevision=tested, inventory=inventory,
          apks={str(path): sha(path) for path in apks}, catalogSha256=catalog_hash,
          deviceClassification='EMULATOR supplemental scope; model property does not prove physical hardware',
          acceptance='SUPPLEMENTAL ONLY; physical/reference/rights/reviewer gates remain separate'))
    for apk in apks:
        result = run(adb + ['install', '-r', '-t', apk], timeout=180)
        (out / (apk.name + '.install.txt')).write_text(result.stdout, encoding='utf-8')
        if result.returncode or 'Success' not in result.stdout:
            raise RuntimeError('Installation failed')
    if checked('pm', 'clear', PACKAGE) != 'Success':
        raise RuntimeError('Dedicated QA reset failed')
    results = []

    def invoke(label, method, phase, network):
        folder = out / label; folder.mkdir()
        network.require_offline(folder)
        command = adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', CLASS + '#' + method,
                         '-e', 'qa02Phase', phase, '-e', 'testedRevision', tested,
                         PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner']
        write(label + '/command.json', command)
        # Linked screenrecord segments cover the command; never publish unreviewed device video.
        recording = subprocess.Popen(adb + ['shell', 'screenrecord', '--time-limit', '180',
                                           '/data/local/tmp/qa02-' + label + '.mp4'],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        started = time.monotonic()
        try:
            result = run(command, timeout=600)
        finally:
            # Only the dedicated screenrecord process, never other user apps/processes.
            shell('pkill', '-INT', '-f', '^screenrecord --time-limit 180 /data/local/tmp/qa02-' + label + '.mp4$')
            try: recording.wait(timeout=10)
            except subprocess.TimeoutExpired: recording.terminate()
        (folder / 'instrumentation.txt').write_text(result.stdout, encoding='utf-8')
        run(adb + ['pull', '/data/local/tmp/qa02-' + label + '.mp4', folder / 'screen.mp4'])
        result_ok = accepted(result.stdout, result.returncode)
        entry = dict(run=label, selector=CLASS + '#' + method, accepted=result_ok,
                     seconds=time.monotonic() - started, code=result.returncode,
                     videoCoverage='Single segment max180s; review duration and encoding before acceptance')
        results.append(entry); write('summary.json', dict(testedRevision=tested, runs=results))
        print(json.dumps(entry), flush=True)
        # Copy evidence without extracting an untrusted tar into the workspace.
        for directory in ('qa02', 'ui02-failures'):
            raw = subprocess.run(adb + ['exec-out', 'run-as', PACKAGE, 'tar', '-cf', '-', 'files/' + directory],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
            if raw.returncode == 0: (folder / (directory + '.tar')).write_bytes(raw.stdout)
        (folder / 'logcat.txt').write_text(checked('logcat', '-d', '-v', 'threadtime'), encoding='utf-8')
        if not result_ok:
            raise RuntimeError('Instrumentation did not pass: ' + label)

    with Network(shell, out).isolated() as network:
        invoke('journey', 'offlineJourney', 'journey', network)
        checked('am', 'force-stop', PACKAGE)
        invoke('force-stop-reopen', 'committedSnapshotSurvivesReopen', 'probe', network)
        if args.reboot:
            before = checked('cat', '/proc/sys/kernel/random/boot_id')
            write('reboot-request.json', dict(before=before))
            # adbd may disconnect before acknowledging reboot. A changed boot ID
            # and completed boot below are the required proof, not shell exit0.
            reboot = shell('reboot')
            write('reboot-command.json', dict(code=reboot.returncode, output=reboot.stdout))
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                try:
                    if checked('getprop', 'sys.boot_completed') == '1' and checked('cat', '/proc/sys/kernel/random/boot_id') != before:
                        break
                except RuntimeError: pass
                time.sleep(1)
            else: raise RuntimeError('Reboot did not complete with a changed boot ID')
            write('reboot.json', dict(before=before, after=checked('cat', '/proc/sys/kernel/random/boot_id')))
            # Record/reassert after reboot; do not claim uninterrupted isolation across boot.
            write('network-after-boot.json', network.flags())
            network.checked('settings', 'put', 'global', 'airplane_mode_on', '1')
            network.checked('svc', 'wifi', 'disable'); network.checked('svc', 'data', 'disable')
            invoke('reboot-reopen', 'committedSnapshotSurvivesReopen', 'probe', network)
    write('artifact-hashes.json', {str(f.relative_to(out)): sha(f) for f in sorted(out.rglob('*')) if f.is_file()})
    return 0


if __name__ == '__main__':
    sys.exit(main())
