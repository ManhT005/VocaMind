# DATA-07 — PR #26 integration fix (06/11/2026)

Target: [PR #26](https://github.com/ManhT005/VocaMind/pull/26), branch
`feature/data-07-progress-aggregation-queries`. Original head
`7f0e9b6e682f994d4cdb42f3a1f02e10e36ff0ac`; merged develop
`67e5e13d8990121f454ac432c79ccad3f007ce62`, including the DATA-05 and DATA-06 fixes.
PR #25 remains closed and is not the delivery target.

Implements the 05/11/2026 DATA-07 Fix Bug Report and the reviewer request to retain
schema-v2 fixtures, reverse insertion order, duplicate-order rejection and both
documentation indexes. The original aggregation SQL/mapper logic remains intact;
no confirmed formula defect was found in this review.

## Resolution and scope audit

| Plan | Current outcome |
| --- | --- |
| D07-01 integration | Merged current develop into the existing DATA-07 branch; no reset/force push or changes to other feature branches. |
| D07-02 UserDao | Resolved actual conflict by keeping both `progressSnapshot` and full `recordReviewIfUnchanged`/`sameProgress` implementation, including DATA-06 ID/outcome validation. |
| D07-03 CatalogDao | Auto-merged to the exact develop version: FTS4 search, legacy MATCH, batching and pack membership all remain. Search/DAO tests pass together. |
| D07-04 scope/fixture | Kept develop source attribution text, reverse insertion order and DATA-07 duplicate word/pack order assertions. CI, Gradle, schemas, assets and CatalogDao have no diff from the integrated develop. No obsolete DATA-03 documentation copies remain in the effective diff. |
| D07-05 composition/handoff | RepositoryProvider now exposes `progress()` and `progressUseCase()` using its existing UserDatabase, shared with Review/Quiz. PROGRESS-02 remains the UI owner. |
| D07-06 cross-feature evidence | Added five shared SQLite integration cases for real atomic review commit, three failure points and three read/write interleavings per API. Full DATA-03/05/06/07 suite passes. |
| D07-07 history size | Kept full history sorted by completedAt DESC, id DESC; no unapproved LIMIT/pagination. |
| D07-08 fixture regression | All 32 DAO foundation executions pass with schema-v2 attribution, pack order and unique word order. |

Actual conflicts: `UserDao.java`, `DaoFoundationTest.java`, `docs/README.md`.
The only retained fixture diff from current develop is the six lines asserting
SQLite rejects duplicate word/pack order. Extra scope beyond original DATA-07 is
composition wiring and the required combined transaction tests.

## Preserved contract

- Total is COUNT DISTINCT vocabularyId across all UserWordProgress; no catalog or
  pack JOIN and no active/saved/learnedAt filter. Saved-only rows already present
  in userdata count according to PROGRESS-01.
- Six boxes 0..5 always exist in domain output; their sum equals total. Counts use
  long. Learning/Reviewing/Mastered remain 0–1 / 2–3 / 4–5.
- Count/groups/Quiz history are read in one Room transaction. Mapping produces
  immutable domain snapshots; invalid rows return INVALID_DATA, query failure
  returns READ_FAILED, valid empty data returns six zeros and empty history.
- No duplicate domain models, DAO exposure to UI, schema/version/tokenizer/asset
  change, destructive migration or user-data reset.

## New integration cases

All use UUID-named test database copies and the canonical DATA-06 writer with
real catalog validation. Fault injection is SQLite RAISE(ABORT) in test DBs only.

| Case | Verified state |
| --- | --- |
| Commit/refresh and shared word across packs | One canonical word in multiple catalog packs counts once, even without active packs. Two REMEMBERED ratings move box1→box2, total stays1, events/count become2. Old domain snapshot remains unchanged. |
| INSERT ReviewEvent fails | Seeded progress, summary, all history fields and event count remain unchanged. Removing trigger permits a real successful refresh. |
| INSERT first UserWordProgress fails | Inserted event rolls back, no phantom learned word appears; existing Quiz history remains unchanged. |
| UPDATE existing progress fails | No box move or event leak; summary/history match the exact pre-failure snapshot. |
| Atomic writer interleaves with grouped read | Reader pauses between count and groups while a writer rates an existing word, inserts a second reviewed word and Quiz attempt in one userdata transaction. Reader sees the old total1/box0/history1; refresh sees total2/box1=2/history2 and three committed review events. |

The interleaving case runs **three rounds on API26 and three on API35** without
sleep-based scheduling. Latches control query order; a SQL callback is used only
in tests. PRAGMA integrity_check and foreign_key_check pass after the combined
commit/rollback scenarios. Catalog and userdata remain separate files; only the
userdata writes are claimed to be one transaction.

## Verification

Evidence: [fix-20261106](evidence/data-07/fix-20261106/), including `verification.json`,
337 tracked input hashes, JUnit XML, Python/Gradle logs, lint report and APK hashes.

- **380/380 Android/JVM tests PASS**, 0 failures/errors/skips: 34 Progress aggregation
  executions (17 cases × API26/35), 3 Progress mapper tests, 42 AtomicReview tests,
  and all inherited search, DAO, repository, scheduler, pack and Quiz regression.
- **159/159 Python unittests PASS** plus workflow source/credit/ingestion/QC/corpus/
  accepted editorial/Unicode checks. Fresh DATA-07 output directories respect the
  tools' immutable-output policy; actual commands are in python-results.json.
- assembleDebug, assembleDebugAndroidTest and lintDebug PASS; **0 lint errors,
  6 warnings**. Product catalog in the app APK matches the source bytes; benchmark
  fixture is absent from the app APK.
- JDK17 (Temurin17.0.21+9), checksum-verified Gradle8.9, compileSdk35,
  AGP-selected build-tools34.0.0, Python3.12.14.
- Original **185-test** branch evidence remains historical and is not reused as
  current integration evidence. CI on the published head is linked from the PR.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2 --console=plain
```

The local run used the identical Gradle distribution directly with machine-local
SDK/proxy configuration. No toolchain workaround was added to the project.

## Remaining acceptance gates

**Connected instrumentation is BLOCKED** by no attached device and no `/dev/kvm`.
Host native SQLite and test-APK assembly do not certify a physical device, reboot,
process kill or release migration. Run on an API26+ device:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.user.ProgressAggregationInstrumentedTest,com.vocamind.app.data.user.AtomicReviewInstrumentedTest
```

**PROGRESS-02 / AC-11 UI remain PENDING**. Inject `repositories.progressUseCase()`
into the ViewModel and execute on a worker. Render Loading/Content/Empty/Error,
refresh after FLASH/Quiz commits, preserve all six boxes, and use box0 “Học lại” /
box1..5 “Cấp ôn 1..5”. Explain Mastered as internal progress, not a qualification.
Verify history time/score, offline mode, navigation, rotation, empty/error/history
states and refresh on device. No spreadsheet Done status or reviewer sign-off
was recorded by this fix; no blanket AC-11 acceptance is claimed.
