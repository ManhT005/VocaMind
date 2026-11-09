# PROGRESS-02 review corrections

Implementation commit: `90de7d908cc2f92e15d5c44f664457e8fc30f99f`.
Runner recovery verification: `8c52c0b`. Previous acceptance evidence remains historical and unchanged.

## P2: mixed progress/history snapshots

Previously the Fragment made its list visible and updated summary counts before ListAdapter's asynchronous history diff committed. A delayed diff could leave old attempt rows visible alongside the new summary.

The Fragment now hides the entire snapshot while a successful read's history is pending. Only that submitList commit callback updates the summary and reveals content. A per-view generation, current-state identity and destroyed-view lifecycle guard reject callbacks superseded by loading/error/new content or view destruction. The last committed adapter data stays hidden while loading; unnecessary empty-list diffs are avoided.

`ProgressRenderingTest` holds the real ListAdapter diff on a controlled executor. Four regressions check old history stays hidden until the matching new header/list commit, late completion cannot cover a newer error, superseded diffs cannot reveal stale content, and callbacks cannot bind a destroyed view. Existing Room screen tests use an injected immediate diff executor so their synchronous fixture assertions do not race the production asynchronous differ.

## P2: LD configuration left changed after failure

The runner validates both isolated packages and APK paths before any setting write. It snapshots exact original airplane/Wi-Fi values, font/null setting, night-mode service and stored setting, size and density overrides. No stage forces the network online before recording its state.

Snapshots are persisted atomically outside versioned evidence before configuration changes. A finally block restores all captured settings on success, failure or KeyboardInterrupt; remaining restore commands are attempted when one fails. A failed/interrupted background test is stopped before rollback. Readback verification must match the original configuration; failed restoration leaves the snapshot available to `--stage restore`. Emergency restore also verifies its result. An uncatchable process kill cannot execute finally, so the durable recovery snapshot is retained for that explicit recovery command. A subsequent run refuses to overwrite a pending snapshot.

The `appearance` stage now restores itself automatically. The separate `restore` stage replays an existing snapshot for that same serial and makes no changes if no recovery is pending; it no longer resets the device to hard-coded defaults. `--output-dir` can retain new run evidence without overwriting previous acceptance records.

Eight no-device Python regressions cover missing APK/no mutation; failure with custom values and an originally offline device; success rollback; KeyboardInterrupt; null settings/default display; partial ADB failure with continued rollback/recovery; a silently unapplied restore command; and preventing overwrite of pending recovery.

## Verification

- Full JVM regression: **487 tests, 0 failures/errors/skips**. Includes four new controlled-diff UI regressions and the existing Room screen tests on API 26/35. Gradle `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` reports `BUILD SUCCESSFUL in 3m 32s`.
- Python fault/recovery regression: **8 tests passed**.
- Final ordinary APK and AndroidTest APK build/lint passed after the QA run. No Java sources changed after full regression.
- LDPlayer Android 9/API 28, G82U8: **3 actual instrumented reruns passed** on the corrected APK: production flow (42.073s), real landscape/portrait rotation (15.293s), and small-screen/dark/200% font (10.716s). The production flow includes completed/abandoned Quiz, the real Flashcard writer, error/retry and recreation.
- Each `*-run.json` captures installed APK hashes and the original configuration. Every corresponding `*-restored.json` equals the original snapshot exactly. Actual initial/final values: airplane=0, Wi-Fi=1, font=1.0, stored night mode=null, night service=no, physical size/density without overrides (1080x1920/480dpi). No recovery snapshot remains.
- Final dark/large-font and landscape screenshots were inspected: application content is visible with no System UI dialog covering it.

Evidence: [host JUnit ZIP](evidence/progress-02/review-fix/host/junit-reports.zip), [host log](evidence/progress-02/review-fix/host/regression.log), [Python output](evidence/progress-02/review-fix/host/python-tests.log), [LD production flow](evidence/progress-02/review-fix/ldplayer/flow.log), [rotation](evidence/progress-02/review-fix/ldplayer/rotation.log), [appearance](evidence/progress-02/review-fix/ldplayer/appearance.log), [snapshot restoration](evidence/progress-02/review-fix/ldplayer/appearance-restored.json), [hash manifest](progress-02-review-fix-evidence.json).

The main user app was not replaced or reset. Only the explicitly isolated `com.vocamind.app.progress02validation` fixture install was updated/reset. This correction reran the affected UI and configuration paths; the earlier reboot/force-stop runs remain tied to their historical APK hashes, not relabeled as executions of this correction. No reviewer approval, GitHub authentication, push or PR is performed by these corrections.

## Reproduction

```powershell
.\.tools\python\python.exe tools/validation/test_progress02_ldplayer.py -v
# Host regression/build uses the same SDK/JDK/catalogPython and Windows short-temp init as the original validation.
# Build/install both APKs with -Pprogress02DeviceIsolation=true, then run stages sequentially:
.\.tools\python\python.exe tools/validation/progress02_ldplayer.py `
  --adb 'C:\Users\thanh\AppData\Local\Android\Sdk\platform-tools\adb.exe' `
  --serial 127.0.0.1:5555 --stage appearance `
  --output-dir docs/evidence/progress-02/review-fix/ldplayer
# If interrupted so recovery remains: use the same adb/serial with --stage restore.
```

Both reported P2 findings are addressed. Publication and independent reviewer acceptance remain separate pending steps.
