# DATA-01 — Validation and handoff

## Scope and baseline

Owner: Nhân. Reviewer: Thi. Task: week 1, D1–D2, 8-hour estimate, Critical.
Requirements: SRS v1.1 FR-VM-002/005/006/008/014, AC-02/04 (partial foundation),
ADR-01/03 and SRS §7.3 DATA-05. See [plan](plans/PlanDATA01.md).

Implementation branch: `feature/data-01-catalog`, based on fetched `develop`
commit `37f14ca3cdb9086e9960fef988290c0839d5aa3e` containing ARC-01/02/03 and
QUIZ-01. No reset, push, merge or publication was performed for this task.

## Implementation

- Five Room entities, schema version 1, compiler-exported JSON committed for review.
- VocabularyEntry.id TEXT primary key, hidden integer rowid joined to standalone FTS4.
- Separate `catalog.db` via createFromAsset; no user data tables or business writes.
- Java-only domain models, mapper, read-only repository and application-scope factory.
- Deterministic synthetic asset and metadata generated from actual Room schema.
- 4 packs × 4 entries = 16 memberships; **only 4 unique stableIds**, not ≥150.
- No UI changes and no wiring business data into placeholder screens.

## Environment and verification

Toolchain prepared outside checkout under `/workspace/toolchains`: checksum-verified
Temurin JDK 17.0.16+8, Android platform 35, Build Tools 34.0.0 and platform-tools.
Gradle wrapper 8.9 retains its committed distribution SHA-256 verification.
TLS verification stays enabled with system trust plus the environment-provided
proxy CA in a local trust store. Writable Android/Gradle caches stay in /workspace.

On this cloud machine:

```bash
source /workspace/toolchains/activate.sh
cd /workspace/VocaMind
bash ./gradlew :app:compileDebugJavaWithJavac --max-workers=2
python3 tools/catalog/build_fixture.py
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
```

Observed: Room compilation and actual schema export succeeded. Two consecutive
fixture rebuilds produced identical SHA-256:
`e88f46f4a10e835d97ab03a87a534ca345321d7cf7738569f49524abda1d4104`.
SQLite integrity, foreign keys and fixture MATCH assertions passed during generation.

Unit test report: **22 catalog test executions passed** (11 contracts × API 26/35,
Robolectric native SQLite), plus **10 existing Quiz domain tests passed**;
0 failures/errors/skips. Reports: `app/build/reports/tests/testDebugUnitTest/`
and machine-readable `app/build/test-results/testDebugUnitTest/`.

Both `assembleDebug` and `assembleDebugAndroidTest` succeeded. Generated artifacts:
`app/build/outputs/apk/debug/app-debug.apk` (~7.1 MiB) and
`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` (~357 KiB).

Physical-device/emulator instrumentation execution has not been performed.
There is no /dev/kvm; `adb devices` also aborts because this sandbox cannot create
`/home/agent/.android` (the current adb does not honor the writable SDK cache
settings). No application was installed or started on a device. Building the
test APK is not a connected test result; run the documented connected test in
Android Studio or an emulator-enabled runner for that evidence.

## Acceptance limits and review

- DATA-01 foundation is exercised by host-native Android integration tests.
- AC-02 full MVP gate remains **pending VOC-DATA-06**: ≥150 unique IDs and
  per-pack minimums 40/40/40/30, QA and approved sources/license ledger.
- AC-04 data/mapping is exercised; UI placeholder and saved-word behavior remain
  feature/userdata work. No claim of complete AC-04 acceptance.
- Full DAO foundation is DATA-03; production search and benchmark are DATA-05.
- Catalog upgrade/rollback and userdata preservation remain DATA-08 and related work.
- VOC-DATA-02 contract candidate was inspected, not merged or approved on behalf
  of Nhân. Fixture identity inputs align with its senseIndex=1 convention.
- Thi's code review and connected-device evidence remain outstanding before team
  sign-off. No claim that the task was reviewed, merged or released.

For asset regeneration and runtime use see [catalog tooling](../tools/catalog/README.md).

## Reusable cloud configuration

Saved install_script (activate retained toolchain, build app/test APKs) and
start_skill (activation, test commands, scope and device limitations) to the
environment draft. Saving was confirmed; configuration is not yet published
and restoration in a new task has not been verified. Review/save in environment
settings and publish when desired.

The debug APK was also inspected as a ZIP: assets/database/catalog.db exactly
matches the checked-in asset and its metadata SHA-256.

## DATA-01 review fixes

- Explicitly close the SQLite builder connection before os.replace and temporary
  cleanup. Python's connection context manager alone only controls transactions.
- Added two Python regressions for closed handles, repeatability and replacement
  failure preserving the existing output. Android CI includes these tests.
- Moved deterministic catalog assertions to a separate test-only fixture loaded
  through createFromInputStream. It is not bundled in the application APK.
- Added four shared production-asset checks through createFromAsset without
  assumptions about fixture word names, exact counts, number of sources or IPA.
- Validation: 40 Android/JVM test executions passed: 22 fixed fixture + 8 actual
  asset + 10 existing Quiz; 0 failed/skipped. Two Python regressions passed.
  Both app APK and instrumentation APK assembly succeeded.
- Connected instrumentation remains unexecuted. The fixed-fixture API change
  does not establish device evidence; the actual shipped asset still has only
  4 unique vocabulary entries and is not the full release catalog.
