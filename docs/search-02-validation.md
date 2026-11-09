# SEARCH-02 local validation — 2026-11-09

Branch: `feature/search-02-search-states`. Base/source APK SHA:
`a5ff1dd354c35f942123a9067dc121072b584737`, fetched origin/develop matched;
initial working tree clean. User authorized commit/push in follow-up; no PR or merge.
No product change required.
Read task attachment, README, Android setup, CI and SRS v1.1 DOCX directly
(FR-VM-012 current keyword/no-match; UC-02 discovery; NFR-VM-10 clear empty feedback).

## Requirement audit

| Task scenario | Existing implementation / evidence | Result |
|---|---|---|
| T01 initial/null/blank/tokenless | parser early return; clearWhitespaceTokenlessInvalidateRunningAndPending; added null assertion | inherited + supplemented |
| T02 no-match | errorsAreDistinctFromNoMatchRetryUsesCurrentInput; SearchScreenContract | inherited |
| T03 invalid | invalidInputDoesNotSubmitOrBecomeNoMatch; UI hides retry | inherited |
| T04 reset debounce | clearWhitespaceTokenlessInvalidateRunningAndPending | inherited |
| T05 reset running/queued | clearRejectsQueuedCompletionAndError; uncancellable futures | inherited |
| T06 stale/A-B-A | outOfOrderContentAndOldErrorCannotStopNewLoading; aToBToAUsesGenerationNotQueryEquality | inherited |
| T07 typed errors | errorsAreDistinctFromNoMatchRetryUsesCurrentInput | inherited |
| T08 retry recovery | same unit test + realRoomReadFailureShowsErrorAndRetryRecoversCurrentQuery | inherited |
| T09 reset then retry | nullResetRestoreAndRetryCannotReplayAbandonedQuery | supplemented |
| T10 double retry/immediate/throw | added double retry count; immediateExceptionalAndRejectedFuturesUseMainDelivery | inherited + supplemented |
| T11 Room failure | isolated UUID test DB close/reopen, never shared app DB | host + device pass |
| T12 lifecycle/restore | disposedOwnerIgnoresLateCompletionAndCancelsDebounce; savedRawQueryRestoresAndNavigationUsesOpaqueId; added reset restore; screen recreate | inherited + supplemented |
| T13 UI/a11y | state renderer clears list, named buttons minHeight 48dp, polite live region; new QA control test | font150 + visible IME automated QA PASS; spoken TalkBack/manual visual QA NOT RUN |
| T14 offline/detail/save/Back | QA catalog and fixture screen contracts; Airplane Mode=1 | device pass; no product writes introduced |

All five states already exist. Empty/invalid inputs avoid repository submission;
generation guards queued and running completions, including equal query strings.
Retry uses current input and is hidden for invalid input. View observers use view
lifecycle and listener/adapter detach on destroy. Parser, limits, debounce,
catalog, progress and DATA-05 architecture remain unchanged.

## Fresh execution

Windows, Temurin 17.0.20.1, Gradle 8.9 offline cache. Set JAVA_HOME to
`C:\Users\LMT\.jdks\vocamind-temurin-17`, GRADLE_USER_HOME to
`C:\Users\LMT\.gradle`; successful host runs use TEMP/TMP `C:\tvm`.
Installed repository requirements with `python -m pip install -r
docs/data/vocabulary-contract-v1/requirements-validation.txt` after missing jsonschema.

- `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline`:
  short-temp run PASS, 533 tests/54 suites, 0 failures/errors/skips; build PASS;
  lint 0 errors/14 warnings. This run compiled the original tests before the new
  test edit; it does not validate the new assertion.
- `gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.feature.search.*' --offline`:
  final run PASS, 30 tests (22 ViewModel + 6 screen + 2 recovery), API26/35,
  0 failures/errors/skips, including new test.
- `gradlew.bat -PcatalogQa=true assembleCatalogQa assembleCatalogQaAndroidTest --offline`:
  Gradle BUILD SUCCESSFUL. PowerShell process reported exit 1 with native stderr;
  both APKs installed successfully and executed below.
- Small_Phone AVD, API30, sdk_gphone_x86, emulator-5554, airplane_mode_on=1:
  QA instrumentation SearchCatalogQaInstrumentedTest + SearchReadRecoveryInstrumentedTest
  PASS 2/2; debug fixture SearchScreenInstrumentedTest + SearchReadRecoveryInstrumentedTest
  PASS 4/4. Used `adb install -r` then `adb shell am instrument -w -e class
  <comma-separated classes> <applicationId>.test/androidx.test.runner.AndroidJUnitRunner`;
  application IDs com.vocamind.app.qa and com.vocamind.app respectively.
- `git diff --check`: PASS before this report; rerun at handoff.

Logs and final focused JUnit XML: [evidence/search-02](evidence/search-02/).
Failed attempts retained: Gradle cache permissions, missing jsonschema, full suite
SQLite TEMP error, overlapping Gradle output lock. The mistaken fixture4 contract
on QA150 failed 2/5; correct separate reruns above do not erase that failure.

New test file SHA256:
`5DB666AAB8243F54671753636779832E9CC77A10C8F069624C28ABE8824AFC0C`.
Source modifications are the unit test and QA instrumentation test; APK production source remains base SHA.
Full CI data/catalog pipelines and manual visual/spoken TalkBack QA NOT RUN.
No benchmark acceptance claimed: AC-05/NFR-VM-03 remain HOLD.

Thành review: verify audit mapping, new retry/reset assertions, retained failed
attempts and manual accessibility gaps. Local validation is not reviewer approval
or task completion. Draft PR scope: reuse SEARCH-01 states; add missing null,
double retry and reset restoration coverage; attach fresh host/device evidence.

## Follow-up before authorized publish

Added `searchStateControlsRemainAccessibleWithKeyboard` in
`SearchCatalogQaInstrumentedTest.java`: requests IME and waits for visible IME
insets, checks polite live region, button names/minimum 48dp height, fully visible
enabled Reset, no-match/loading/list/retry visibility, invalid-query Retry hidden
and Reset returning empty query/message. No duplicate product state implementation.

Fresh final host command (same JDK/cache/TEMP above):
`gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline`:
**535 tests / 54 suites, 0 failures/errors/skips**, build PASS, lint 0 errors/14 warnings.
This supersedes the original 533-test run for the new unit assertions.
QA commands: `gradlew.bat -PcatalogQa=true assembleCatalogQa assembleCatalogQaAndroidTest --offline`,
then `gradlew.bat -PcatalogQa=true assembleCatalogQaAndroidTest --offline` after final
instrumentation assertions. Both PASS. Debug and QA testBuildType configurations
must run separately; failed combined command retained in publish-checks.txt.

Installed final QA APKs with adb install -r; set `settings put system font_scale 1.5`;
ran SearchCatalogQaInstrumentedTest and SearchReadRecoveryInstrumentedTest using
`am instrument -w -e class` as above: **3/3 PASS**, API30 Small_Phone, Airplane Mode=1.
Original font_scale was absent (`null`); restored with `settings delete system font_scale`
in finally. This is automated UI evidence, not a claim of spoken TalkBack testing.
Logs: publish-host.txt, publish-qa-build.txt, publish-qa-final-build.txt,
publish-qa-font150.txt. Final source/APK hashes and JUnit summaries are in
publish-manifest.txt. Tests change only; no benchmark, catalog or progress change.

Reviewer Thành still needs to review the changes and remaining manual accessibility
coverage. No task completion status or SRS/assignment changes.
