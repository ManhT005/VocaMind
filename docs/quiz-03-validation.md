# QUIZ-03 — Validation and handoff

Implementation date: 2026-11-07. Branch: `feature/quiz-03-ui-session`.
Base: `8209ef75bc882ca10367abd40900e44e537571cb`. Changes are uncommitted; this document does not claim CI or reviewer approval.

## Implemented behavior

- QuizFragment renders loading, empty, generation/restore error, answering, answered, saving, save error and saved result. Four generator options retain their IDs/order; lemma, POS and context are displayed. Feedback uses text and a check/cross symbol. Content scrolls and buttons have a minimum height of 48dp.
- QuizViewModel consumes immutable domain transitions on main. Generation and persistence run on a serial worker. Next requires an answer; the final answer immediately completes the session. Scores come from QuizResult.
- The injected production monotonic clock uses elapsedRealtime. Accepted answers establish a 600ms debounce boundary across questions; rejected answers do not change it.
- Screen Back and system Back use the same confirmation handler. Cancel keeps the attempt; confirmed exit abandons it without saving history.
- The Activity owns the ViewModel, so rotation and leaving/reopening Quiz preserve the current completion key, result and save/retry state. Confirmed abandoned attempts generate a fresh quiz on the next visit. A new quiz reads a fresh active-pack snapshot.
- SavedStateHandle stores an Android-only Bundle containing the completion key, fixed completion timestamp, shuffled questions/options, current position and answers. Restoration rebuilds questions using the Unicode normalizer and replays domain transitions; invalid snapshots produce an explicit restore error. Completed restoration reconciles through the same idempotent write instead of generating a new attempt.
- Completion writes continue when the view is destroyed. Clearing the Activity discards late rendering callbacks and closes providers after queued worker operations.

## Persistence contract and migration

`QuizRepository.saveAttemptOnce(key, result, completedAt)` returns the same attempt ID for the same key and payload. Changed payload returns CONFLICT. The original saveAttempt API remains available for existing callers.

UserDatabase v2 adds nullable QuizAttempt.completionKey with a unique index. MIGRATION_1_2 adds the column/index without replacing tables or resetting data. Legacy rows retain NULL keys. UserDao performs lookup, payload comparison and insertion within a Room transaction; the unique index also protects the storage invariant. Retries preserve both key and completedAt, including an ambiguous commit followed by process restoration.

Only completion inserts quiz history. Quiz generation and abandon do not update progress, catalog or ReviewEvent. Changes to active packs affect the next quiz, not the current snapshot.

## Automated coverage

- QuizViewModelTest: answer locking, invalid IDs, cross-question debounce, Next guards, final completion, score rounding, retry payload/key, restoration, malformed snapshots, empty/error distinction, abandonment and late generation callbacks.
- QuizScreenContract (host and instrumented wrappers): real MainActivity/NavHost, feedback, disabled options/Next, Activity recreation, completion, reopening a completed quiz without repeating save, screen/system Back, cancel/confirm and restarting after abandonment. Deterministic quiz data is injected; this is not device evidence for the production catalog.
- UserDatabaseTest: production v1→v2 table migration with legacy history/progress, duplicate retry, payload conflict, independent keys, reopen and concurrent writers through two database instances. Existing schema-evolution tests now use synthetic v3→v2 rather than the old v2→v1 fixture.
- QuizCompletionInstrumentedTest: builds an actual v1 database from the exported schema, migrates through UserDatabase.open and checks history/progress preservation, idempotent insert and conflict. It uses an isolated database name and does not reset app userdata.

## Local verification

Toolchain: Windows, JBR 21.0.11 (the installed Android Studio JDK), Gradle wrapper 8.9, compile/target SDK 35, Java source/target 17. JDK 17 runtime validation has not been performed on this machine.

PowerShell setup:

```powershell
$env:JAVA_HOME='C:\Users\thanh\.jdks\jbr-21.0.11'
$env:GRADLE_USER_HOME='C:\Users\thanh\.gradle'
```

The first full run used the default Windows temporary directory. Four tests in RepositoryIntegrationTest/ProgressAggregationTest failed with SQLITE_CANTOPEN. A rerun using a shorter temporary directory passed all 394 host tests at that intermediate source state. The following local init script changes only the test JVM temp directory; it does not change application code or suppress tests:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'java.io.tmpdir', 'C:/Windows/Temp'
    }
}
```

The local script is at `app/build/quiz03/short-temp.init.gradle` (ignored build artifact). Reproduce with a writable short temporary path on your own Windows machine.

Final source validation command:

```powershell
.\gradlew.bat -I app/build/quiz03/short-temp.init.gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain
```

Final run: **BUILD SUCCESSFUL**, exit **0**, duration **3m 52s**, 2026-11-07. No production/test source changes followed this run.

| Check | Result |
|---|---|
| testDebugUnitTest | 397 tests, 0 failures, 0 errors, 0 skips |
| QuizViewModelTest | 9 passed |
| QuizScreenTest | 2 passed (host/Robolectric) |
| UserDatabaseTest | 22 passed across API 26/35 host configurations |
| assembleDebug | PASS |
| assembleDebugAndroidTest | PASS (build only) |
| lintDebug | PASS; 0 errors, 8 warnings |
| git diff --check | PASS, exit 0 |
| connectedDebugAndroidTest | NOT RUN: no connected device |

Six lint warnings concern existing dependency/resources/layouts/manifest; two SetTextI18n warnings concern composing quiz status/context text. They do not block the build.

APK SHA-256:

- `app/build/outputs/apk/debug/app-debug.apk`: `249826377405AF1CA5BD4999908AD8409FAB864B10E7B344EACB120971B7F0A0`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: `8C6D09855EA2838907F17E30285A07E9F34E1C88354CD518816FE19C128990D5`

Current machine reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`. Retained evidence is in `docs/evidence/quiz-03/`: final suite counts, QUIZ-03/Room JUnit XML, lint report and Gradle output. The APKs are local ignored build artifacts, not published releases.

## Remaining release/review gates

`adb devices` returned no connected devices. connectedDebugAndroidTest was not run; no device PASS, offline/airplane-mode evidence or screenshots are claimed. Building the instrumented APK is not a device test result.

On a connected test device, run connectedDebugAndroidTest, then manually verify production-catalog quizzes with 4 and 10 eligible questions, offline first opening, long option/context text, exit confirmation, rotation during loading/feedback/saving and reopening. Record API level, airplane mode, tested APK hash and source commit. Do not reset real userdata to run this checklist.

Trường's review, Nhân's persistence/migration review and CI against a committed head remain external handoff gates. No messages were sent, PR merged or release gate marked complete.
