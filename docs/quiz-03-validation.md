# QUIZ-03 — Validation and handoff

Technical implementation and host/device validation are complete. **Assigned human review remains pending; this is not a claim that Trường or Nhân approved the task.**

- Branch: `feature/quiz-03-ui-session`; PR: [#33](https://github.com/ManhT005/VocaMind/pull/33), targeting `develop`.
- Base: `8209ef75bc882ca10367abd40900e44e537571cb`.
- Implementation: `b98780fda530a7d0ebf0a6e8603fbaee11988386`.
- Tested implementation/test-harness head: `5d546e77d7ce462194c231cb74eac4b71c10a372`.
- PR merge ref checked out by CI: `cf32146ed48ac4164c11fe930bbd0375706596d6`.
- [Android CI run 147 — SUCCESS](https://github.com/ManhT005/VocaMind/actions/runs/42342310118), 2026-11-07. Both Build & Test and Quiz device jobs passed. Subsequent documentation-only commits do not change the tested application/test source; current PR checks must still complete on the published head.
- Documentation head `51b42bb6a9e74191dd4ded2fdb16fd8e419447ff` also passed both jobs in [CI run 149](https://github.com/ManhT005/VocaMind/actions/runs/42342913242).

## Behavior and completion protocol

QuizFragment renders LOADING, EMPTY, ERROR, ANSWERING, ANSWERED, SAVING, SAVE_ERROR and RESULT. It retains the generator's four option IDs/order and displays lemma, POS and context. Feedback uses text and a check/cross symbol. Content scrolls; buttons have a minimum height of 48dp.

QuizViewModel handles immutable domain transitions on main. A serial worker performs generation and storage. Next requires an answer; the final accepted answer completes immediately. Scores come from QuizResult. Production debounce uses injected elapsedRealtime: rejected answers do not move the 600ms boundary, including across questions.

Screen and system Back share confirmation. Cancel keeps the attempt; confirmed exit abandons without history. The Activity owns the model, so rotation/navigation retain the current session and save state. A confirmed abandoned attempt starts fresh on the next visit. A new attempt reads a fresh active-pack snapshot.

SavedStateHandle stores an Android-only Bundle with completion key, fixed completion timestamp, shuffled questions/options, position and answers. Restoration reconstructs questions with the Unicode normalizer and replays validated domain transitions. Invalid state produces an explicit restore error. Completed restoration reconciles with the original key instead of creating a new attempt. Rendering never inserts history.

`saveAttemptOnce(key, result, completedAt)` returns the same attempt ID for an identical key/payload and CONFLICT for a changed payload. UserDatabase v2 adds nullable QuizAttempt.completionKey plus a unique index. MIGRATION_1_2 adds column/index without resetting data; legacy keys remain NULL. Lookup, comparison and insert share one Room transaction. The previous saveAttempt API remains available. Generate/abandon do not update progress, catalog or ReviewEvent.

## Verified results

| Check | Result |
|---|---|
| CI host/JVM suite | 397 tests, 0 failures, 0 errors, 0 skips |
| CI debug APK / instrumentation APK | PASS |
| CI lintDebug | PASS; 0 errors, 7 existing warnings |
| CI connectedDebugAndroidTest | 4 tests, 0 failures, 0 errors, 0 skips |
| User's LDPlayer, direct AndroidJUnitRunner | 4 tests PASS; Wi-Fi retained, functional validation only |
| git diff --check | PASS |
| Assigned reviewer approval | PENDING |
| Persistence/data-owner approval | PENDING |

The two new SetTextI18n warnings from the initial local iteration were fixed. CI warnings concern existing target SDK/dependency/layout/resource/manifest settings.

Host cases cover answer locking, invalid IDs, cross-question debounce, Next guards, final completion, 0/100/17/29% scores, save retry/key preservation, snapshot restoration/corruption, empty/error separation, abandonment and late callbacks. Room tests include upgrade preservation, duplicate retry, conflict, independent keys, reopen and concurrent completion writers through two database instances.

The four executed device tests were:

1. Real MainActivity/NavHost: full four-question flow, feedback/disabled options, recreation, result and reopening without repeated save (deterministic domain/UI fixture).
2. Screen/system Back, cancel/confirm, abandonment and fresh reopening (same UI fixture).
3. Build a real v1 DB from the exported schema; migrate through UserDatabase.open; preserve legacy history/progress; check duplicate key and conflicting payload.
4. Production factory/generator/repositories/Room against the isolated accepted-corpus asset: offline first opening with EMPTY; explicit pack activation; ten questions; recreation during feedback/result; Back/reopen; exactly one persisted attempt matching domain score; no progress or ReviewEvent writes; DB reopen.

## Device and corpus provenance

The successful device run used GitHub's disposable official Android emulator, **API 28 / Android 9 / x86_64**, model `Android SDK built for x86_64`. JDK 17 and Gradle wrapper 8.9 ran on Ubuntu. Airplane setting was **1**, Wi-Fi disabled, and connectivity reported **Active default network: none** before the first application launch. These facts are retained in `docs/evidence/quiz-03/device-ci/`.

The APK used application ID `com.vocamind.app.quiz03validation`, with its own userdata. `-Pquiz03DeviceIsolation=true` generates a test-only Room asset from the unchanged accepted 150-word canonical preview and original companion policy. The builder verifies the policy binding and database integrity. It does not replace production assets, rewrite IDs, relax distractor restrictions, or publish a release catalog.

**Bundled-asset limitation:** the ordinary APK still contains the existing synthetic four-sense catalog. It includes ambiguous same-lemma senses; the unchanged QUIZ-02 generator correctly returns INVALID_INPUT/AMBIGUOUS_DISTRACTORS. The device happy-path proof uses the accepted test asset, not a claim that the ordinary APK now ships the full release corpus. Packaging release vocabulary remains a separate data/release task.

Successful device APK SHA-256:

- App: `0177939b0acae197110906496ee3617c9ce64e9cba9c966649666a02aa479752`.
- AndroidTest: `a07ba57a1445e4a1a7505d23a836253f68eba99a0f1105f2152ef990f5222f60`.
- Accepted canonical preview SHA-256: `f27e9d2de700fadf13b480df26933c61333ce6261ed872d285e5a111a88110c1`.

[Device CI artifact](https://github.com/ManhT005/VocaMind/actions/runs/42342310118/artifacts/13562556272) contains APKs, XML, logs and screenshots. Retained repository evidence includes [empty](evidence/quiz-03/device-ci/01-offline-empty.png), [feedback](evidence/quiz-03/device-ci/02-feedback.png) and [result](evidence/quiz-03/device-ci/03-result.png), hashes, device properties and network state. [Host CI artifact](https://github.com/ManhT005/VocaMind/actions/runs/42342310118/artifacts/13562720347) contains all 397 host test reports and lint output.

## Reproduction

Ordinary app / host checks:

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

On a **disposable, clean** emulator with Python available, run:

```sh
bash tools/catalog/validate_quiz03_device.sh
```

The script deliberately disables the disposable emulator's network. It selects the three QUIZ-03 test classes, runs four instrumented tests, captures APK hashes/network/device facts and copies screenshots before connected-test uninstall. The Android CI workflow supplies that disposable emulator; do not run the script on a personal daily-use device.

Windows can provide Python explicitly with `-Pquiz03PythonExecutable=absolute/path/to/python.exe`. Native Robolectric SQLite required a short temp path on this workstation; `app/build/quiz03/short-temp.init.gradle` locally sets `java.io.tmpdir=C:/Windows/Temp`. This is a test-environment workaround, not a storage implementation change. Initial local evidence (397 tests, JBR 21, lint 8 warnings) is retained as historical evidence; the JDK 17 CI results above supersede it.

## Local attempts and review gates

LDPlayer (`emulator-5554`, API 28, Android 9, x86_64, model G82U8) initially reported ADB offline/timeouts. After the user restored the connection, all **4 tests passed directly on their LDPlayer**, AndroidJUnitRunner output `OK (4 tests)`, duration 32.366 seconds. The installed app/test APKs were the exact accepted-corpus isolated APKs from the successful CI artifact, with the hashes listed above. Evidence: `docs/evidence/quiz-03/ldplayer/` (instrumentation output, minimum device metadata, network summary and result screenshot).

Wi-Fi remained enabled during this LDPlayer run; despite the existing airplane setting being 1, the active network was 101. Consequently this result proves functionality on LDPlayer, **not offline acceptance**; offline proof comes from the separate CI emulator with no active default network. Afterwards airplane mode was restored to its original 0, Wi-Fi confirmed 1, and ADB confirmed `device`. The main VocaMind installation was never replaced or reset.

A separate local official API 28 emulator executed both four-question UI tests successfully. It exposed a schema-fixture assumption (optional JSON indices) and the ordinary bundled catalog limitation; the schema test was corrected and the accepted-corpus isolation asset added. A later local connection loss did not count as a pass. The successful result is the CI device run documented above.

Review was requested on PR #33 from **Letruong18 (Trường)** and **nhandanghoc (Nhân)**. As of this validation no review submission has been received. Only those reviewers can supply their actual sign-off; no approval was fabricated and the PR has not been merged.
