# PROGRESS-02 validation and handoff

Date: 2026-11-08, Asia/Saigon. Implementation: `5872cffbed20e9808eaaa375cbd0e81067768790`; final test/source head: `8c91fbd` on `feature/progress-02-ui-history`. Updated base: `develop b299cab09cca18a2a5b3bc26fa0facd67f1421ab` (PR #42). The original base `08a182f` was 11 commits behind; it was fast-forwarded, the strings conflict was resolved preserving upstream Flashcard/Today strings, and validation was repeated.

**Technical implementation and acceptance checks are complete. Publication/assigned-reviewer acceptance remain pending.** This document does not mark the assignment or a PR approved on someone else's behalf.

## Delivered behavior

- Four DB/domain metrics; all six named boxes including zeros; always-readable Mastered disclaimer in successful/empty content.
- Complete persisted Quiz history, repository/domain ordering, stable IDs (header 0, positive attempt IDs), domain wrong counts/scores, device locale/timezone formatting. One RecyclerView with a single summary header and ListAdapter history; no ten-record cap or history TextView expansion.
- Existing `RepositoryProvider.progressUseCase()` composition and DATA-07 aggregation. Fragment has no DAO calls. A serial worker performs IO; main publishes immutable state. Refresh while busy coalesces into one follow-up read, suppressing superseded results. Clear discards callbacks and releases only owned connections/executor.
- `onResume` is the single automatic load entry point. `viewLifecycleOwner` observation and adapter detachment protect destroyed views. Loading hides previous values; error/invalid data show retry rather than zero data.
- New text is in resources. No schema, aggregation SQL, Leitner/scoring, writer, dependency or CI changes. The QA flag in Gradle is the only scope extension: it isolates device fixtures from the user's installed app and reuses the existing QUIZ-03 accepted-corpus builder.

## Host results

**483 tests, 0 failures, 0 errors, 0 skipped.** Full execution: `host/full-execution.log`, Gradle `BUILD SUCCESSFUL in 5m 3s`. Final ordinary build verification: `host/final-regression.log`, `BUILD SUCCESSFUL in 30s`; Gradle reused valid up-to-date JVM test outputs. Both ordinary APKs and lint succeeded. Final lint has 0 errors and 14 warnings, retained in its XML report. The summary adapter now sends item-specific notifications, removing its previous NotifyDataSetChanged warning.

Evidence: [JUnit archive](evidence/progress-02/host/junit-reports.zip), [full run](evidence/progress-02/host/full-execution.log), [final run](evidence/progress-02/host/final-regression.log), [lint XML](evidence/progress-02/host/lint-results-debug.xml), [hash manifest](progress-02-evidence.json).

Windows native Robolectric SQLite failed with SQLITE_CANTOPEN on the initial attempt. The previously established local `java.io.tmpdir=C:/Windows/Temp` init script fixes this workstation's path limitation; no production SQL, test expectation, or CI workaround was introduced. Python 3.13 in `.tools/python` supplies jsonschema; catalog preparation was kept enabled. JDK 17.0.20.1+1, Gradle 8.9, Robolectric 4.16.1, SDK 35/min 26.

```powershell
$env:JAVA_HOME = "$PWD\.tools\jdk17-fast\jdk-17.0.20.1+1"
$env:GRADLE_USER_HOME = 'C:\Users\thanh\.gradle'
# Local init script: allprojects { tasks.withType(Test).configureEach {
#   systemProperty 'java.io.tmpdir', 'C:/Windows/Temp'
# } }
.\gradlew.bat -I app/build/quiz03/short-temp.init.gradle `
  testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug `
  -PcatalogPython="$PWD\.tools\python\python.exe" --console=plain
exit $LASTEXITCODE
```

## LDPlayer acceptance

Device: LDPlayer 9.5.41.1, Android 9/API 28, x86_64, model G82U8, locale vi-VN, timezone Asia/Ho_Chi_Minh. Normal screen 1080x1920 at 480dpi; stress screen 720x1280 at 360dpi (320dp wide), font 2.0, dark mode.

Only `com.vocamind.app.progress02validation` and its test package were installed/reset. The main `com.vocamind.app` installation was not replaced or reset. All test fixture writes are guarded by the isolated package check; ordinary instrumentation skips these optional acceptance cases. No vocabulary fixture was published to main assets.

All executed methods independently require airplane=1, wifi=0, and `Active default network: none` **before opening the Activity/DB**, and check offline again during the flow. `progress02-offline.txt` retains actual disconnected-device output. Wi-Fi changes interrupt LD's host ADB, so instrumentation runs device-side, with network restoration in `@After`; the host polls the device log after reconnecting. The driver rejects failures and skipped tests. Each final run record hashes the APKs installed on the device rather than assuming the current build output is what ran.

| Acceptance | Executed proof |
| --- | --- |
| P02-01 | Successful empty DB: four zeros, six zero boxes, progress/history messages and disclaimer. |
| P02-02/03 | Real Room -> repository -> use case -> production ViewModel/Fragment: 12 unique vocabulary IDs, boxes `[2,1,3,2,1,3]`, groups 3/5/4. Explicit duplicate cross-pack membership, paused pack and saved-only learnedAt=null row remain counted once. Production unsave retains row/total. |
| P02-04 | Independently verified empty, history-only and progress-only sections. |
| P02-05 | Twelve equal-timestamp attempts retain descending ID order. Every actual history row was scrolled to and checked, including 70/75/33% scores; no hidden cap. |
| P02-06 | Device vi-VN/+07 date/time screenshots; exact injected US/France and Los Angeles/Ho Chi Minh boundary tests on host. |
| P02-07 | Invalid persisted score -> visible error, hidden list -> repair/retry -> actual snapshot. Host separately covers READ_FAILED, INVALID_DATA and unexpected exceptions. |
| P02-08 | Deterministic worker/main queues prove coalescing and no stale publication. Loading disables refresh and hides previous content. |
| P02-09 | Device recreation during Progress and Quiz result, actual landscape/portrait transitions with unchanged metrics/history, result reopening, host destruction during pending read, and fresh process after force-stop. |
| P02-10/11 | Real production ten-question Quiz: exactly one persisted completion key, matching correct count/score; rotation/reopen does not duplicate. Start another Quiz, answer then confirm abandon: no new attempt. Quiz leaves word progress and review-event counts unchanged. |
| P02-12 | Production review use case moves box1 -> box2: boxes `[2,0,4,2,1,3]`, groups 2/6/4. Real Today -> Flashcard -> flip -> HARD commits a second review, then returning to Progress shows the committed snapshot; HARD preserves its box. |
| P02-13 | Offline before first Activity opening; force-stop/reopen and full LD reboot/reopen match the saved complete snapshot signature (all box counts and all 13 attempts' IDs/times/counts/scores). |
| P02-14 | 320dp width, dark, 200% font: actual text layouts have no ellipsis; refresh >=48dp; list scrolls to final row. Manual screenshot review confirms readable wrapping, numeric labels, full disclaimer and history. This is a visual/accessibility review, not a claimed TalkBack certification. |

Flow log: [production acceptance](evidence/progress-02/ldplayer/flow.log). Persistence logs: [force-stop/reopen](evidence/progress-02/ldplayer/force-stop-reopen.log), [reboot/reopen](evidence/progress-02/ldplayer/reboot-reopen.log). Appearance: [log](evidence/progress-02/ldplayer/appearance.log). Actual rotation: [log](evidence/progress-02/ldplayer/rotation.log). Per-run `*-run.json`, device metadata and SHA manifest bind screenshots/logs to the installed package hashes. The main flow was started before the source commit was created, so its run record accurately retains base b299cab; later probes record 5872cff; final rotation and appearance record 8c91fbd. Each record is kept with its actual revision and APK hashes. No historical hash was replaced. Final app/test APKs are preserved locally in `.tools/progress02-final-apks/` and fingerprinted in the manifest.

The fixture includes twelve seeded completed rows plus one real Quiz completion (13 total). The complete expected signature is retained in `progress02-expected.txt`. The 150-word accepted QA catalog is test-only, consistent with QUIZ-03; ordinary release catalog packaging remains outside PROGRESS-02.

Initial device attempts are not acceptance evidence: one test selected the first row of the ID-ascending raw DAO rather than the row with a production completion key; this assertion was corrected. An initial appearance run had a System UI ANR dialog despite application assertions passing; those images were rejected. The screenshot helper now requires the active window to belong to the app. The System UI dialog was explicitly closed and appearance rerun; only unobscured final images are linked here.

## Visual evidence

- [12-word fixture](evidence/progress-02/ldplayer/progress02-04-fixture-top.png)
- [Final history rows, 33% and 75%](evidence/progress-02/ldplayer/progress02-05-history-last.png)
- [Data error/retry](evidence/progress-02/ldplayer/progress02-06-error.png)
- [Production Flashcard saved](evidence/progress-02/ldplayer/progress02-06a-flashcard-saved.png)
- [Real Quiz result](evidence/progress-02/ldplayer/progress02-07-quiz-result.png)
- [Dark/200% font metrics](evidence/progress-02/ldplayer/progress02-10-dark-large-top.png)
- [Dark/200% font disclaimer](evidence/progress-02/ldplayer/progress02-11-dark-large-boxes.png)
- [Landscape history](evidence/progress-02/ldplayer/progress02-13-landscape-history.png)
- [Dark/200% font final history](evidence/progress-02/ldplayer/progress02-12-dark-large-history.png)

## Device reproduction and restoration

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest `
  -Pprogress02DeviceIsolation=true `
  -Pquiz03PythonExecutable="$PWD\.tools\python\python.exe" `
  -PcatalogPython="$PWD\.tools\python\python.exe"
# Install both isolated APKs using adb -s <LD serial> install -r.
# flow requires fresh QA data; never clear the main application.
# Driver stages must run sequentially: flow -> force-stop + probe ->
# LD reboot, wait for ADB -> probe -> rotation -> appearance -> collect -> restore.
.\.tools\python\python.exe tools/validation/progress02_ldplayer.py `
  --adb 'C:\Users\thanh\AppData\Local\Android\Sdk\platform-tools\adb.exe' `
  --serial 127.0.0.1:5555 --stage flow
```

Restoration sets the original physical size/density (no override), default font (no stored override), original light mode, airplane=0 and Wi-Fi enabled. Device restoration facts are retained separately; do not infer restoration merely from a passed test. No personal app userdata was deleted.

## Review/publication

[Prepared PR description](progress-02-pr-description.md) is ready. No reviewer approval, PR creation, push or merge is claimed. Automatic approval review rejected reading a GitHub credential from the local helper for an API call as unapproved credential disclosure/authentication. That action was not bypassed. Authenticated publication needs authorization; assigned reviewer sign-off must come from the reviewer.

Final retained LD runs: five passes across four methods (production flow, force-stop probe, reboot probe, appearance, actual landscape/portrait rotation). The older test APK hash and run-start commits are retained as actually executed. Application code is unchanged between 5872cff and 8c91fbd; the latter adds only the rotation test and driver stage. `device.txt` shows restored airplane=0, wifi=1 and physical 1080x1920/480dpi without overrides. `restored-device.txt` records ABI/locale, and `appearance-device.txt` records the stress configuration. Final screenshots were visually inspected with no dialog covering the application.
