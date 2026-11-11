"""Native SQLite and FLASH-03 supplemental runs on the isolated four-word fixture.

Build assembleDebug assembleDebugAndroidTest -Pflash03DeviceIsolation=true.
Never run fixture-specific legacy IDs against the 150-word acceptance corpus.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from run_qa02_device import Network, accepted, sha

PACKAGE = 'com.vocamind.app.flash03validation'
CASES = [
    ('sqlite-contracts', ','.join('com.vocamind.app.data.user.' + name for name in (
        'AtomicReviewInstrumentedTest', 'ProgressAggregationInstrumentedTest', 'QuizCompletionInstrumentedTest')), 39),
    ('flash03-triple-tap', 'com.vocamind.app.feature.flashcard.FlashcardDuplicateReviewInstrumentedTest', 1),
]
UI_CASES = [
    ('attribution-offline-navigation', 'attribution.AttributionScreenInstrumentedTest#attributionAvailableBeforeLearningSurvivesRecreationAndBack', 1),
    ('attribution-states-long-text', 'attribution.AttributionRenderingInstrumentedTest#rendersLoadingEmptyErrorRetryAndMultipleLongSources', 1),
    ('search-discovery', 'search.SearchScreenInstrumentedTest#catalogSearchEnglishVietnameseDetailBackRecreateClearAndNoWrites', 1),
    ('search-save', 'search.SearchScreenInstrumentedTest#catalogDetailSaveRequiresExplicitClickAndPersistsAcrossBack', 1),
    ('search-opaque-route', 'search.SearchScreenInstrumentedTest#opaqueNavigationArgumentsAndReboundHolder', 1),
    ('search-read-recovery', 'search.SearchReadRecoveryInstrumentedTest#realRoomReadFailureShowsErrorAndRetryRecoversCurrentQuery', 1),
    ('quiz-component-lifecycle', 'quiz.QuizScreenInstrumentedTest#fullFlowKeepsFeedbackThroughRecreationAndSavesOnce', 1),
    ('quiz-component-abandon', 'quiz.QuizScreenInstrumentedTest#toolbarAndSystemBackShareCancelAndConfirmExit', 1),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('adb', 'serial', 'aapt', 'output'):
        parser.add_argument('--' + key, required=True)
    parser.add_argument('--ui-components-only', action='store_true', help='Independent rendering/fault-injection contracts; not the real main journey')
    parser.add_argument('--catalog-upgrade-only', action='store_true', help='20 DATA-08 UUID-database contracts on the synthetic fixture')
    args = parser.parse_args()
    if args.ui_components_only and args.catalog_upgrade_only:
        parser.error('Choose only one supplemental mode')
    out = Path(args.output).resolve()
    if not out.is_relative_to((Path.cwd() / 'build/qa02').resolve()) or out.exists():
        parser.error('Use a new directory under build/qa02')
    out.mkdir(parents=True)
    adb = [args.adb, '-s', args.serial]

    def run(command, timeout=30):
        return subprocess.run(list(map(str, command)), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True, encoding='utf-8', errors='replace', timeout=timeout)

    def shell(*command):
        return run(adb + ['shell', shlex.join(command)])

    def save(name, value):
        (out / name).write_text(json.dumps(value, indent=2), encoding='utf-8')

    apks = [Path('app/build/outputs/apk/debug/app-debug.apk'),
            Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')]
    import re
    for apk, expected in zip(apks, (PACKAGE, PACKAGE + '.test')):
        badging = run([args.aapt, 'dump', 'badging', apk])
        if badging.returncode or re.findall(r"^package: name='([^']+)'", badging.stdout, re.M) != [expected]:
            raise RuntimeError('Unexpected APK identity; refusing to install/reset')
    files = run(['git', 'ls-files', '--cached', '--others', '--exclude-standard']).stdout.splitlines()
    hashes = {name: sha(name) for name in sorted(files)
              if name.startswith(('app/src/', 'app/build.gradle', 'tools/validation/')) and Path(name).is_file()}
    digest = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
    tested = run(['git', 'rev-parse', 'HEAD']).stdout.strip() + '+sources-sha256:' + digest
    save('source-hashes.json', hashes)
    save('environment.json', dict(testedRevision=tested, package=PACKAGE,
         apks={str(apk): sha(apk) for apk in apks}, utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
         inventory={key: shell('getprop', key).stdout.strip() for key in ('ro.build.fingerprint', 'ro.build.version.sdk', 'ro.product.model')},
         scope='EMULATOR supplemental only; synthetic four-word fixture; no acceptance/performance substitution'))
    for apk in apks:
        installed = run(adb + ['install', '-r', '-t', apk], timeout=180)
        (out / (apk.name + '.install.txt')).write_text(installed.stdout, encoding='utf-8')
        if installed.returncode or 'Success' not in installed.stdout:
            raise RuntimeError('Install failed')
    reset = shell('pm', 'clear', PACKAGE)
    if reset.returncode or reset.stdout.strip() != 'Success':
        raise RuntimeError('Isolated reset failed')
    results = []
    with Network(shell, out).isolated() as network:
        cases = [(label, 'com.vocamind.app.feature.' + selector, count) for label, selector, count in UI_CASES] if args.ui_components_only else CASES
        if args.catalog_upgrade_only:
            cases = [('catalog-upgrade', 'com.vocamind.app.data.catalog.upgrade.CatalogUpgradeInstrumentedTest', 20)]
        for label, selector, count in cases:
            if args.ui_components_only:
                reset = shell('pm', 'clear', PACKAGE)
                if reset.returncode or reset.stdout.strip() != 'Success':
                    raise RuntimeError('Independent component reset failed')
            folder = out / label; folder.mkdir()
            network.require_offline(folder)
            command = adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selector,
                             '-e', 'testedRevision', tested, PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner']
            save(label + '/command.json', command)
            result = run(command, timeout=600)
            (folder / 'instrumentation.txt').write_text(result.stdout, encoding='utf-8')
            (folder / 'logcat.txt').write_text(shell('logcat', '-d', '-v', 'threadtime').stdout, encoding='utf-8')
            ok = accepted(result.stdout, result.returncode, count)
            entry = dict(run=label, expectedTests=count, accepted=ok)
            results.append(entry); save('summary.json', dict(testedRevision=tested, runs=results))
            print(json.dumps(entry), flush=True)
            if not ok:
                raise RuntimeError('Supplement did not pass: ' + label)
    save('artifact-hashes.json', {str(path.relative_to(out)): sha(path) for path in sorted(out.rglob('*')) if path.is_file()})


if __name__ == '__main__':
    main()
