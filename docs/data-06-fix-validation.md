# DATA-06 — PR #24 fix validation (06/11/2026)

Implements the DATA-06 Fix Bug Report dated 05/11/2026 for
[PR #24](https://github.com/ManhT005/VocaMind/pull/24).
Original head `f86332b1d3a33b89f71ed3e1e73840f78a47a882`; integrated develop
`e923c993e9ba731bb2aab146e712885731e20479`.
This task is Android atomic review, distinct from SRS §7.3 attribution DATA-06.

## Fixes

| Plan | Implementation / outcome |
| --- | --- |
| D06-01 contract | One domain `ReviewRepository`, with explicit full-snapshot compare-and-commit and deprecated DATA-04 adapter. All consumers/fakes compile. Added typed `RepoError.CONFLICT`. |
| D06-02 duplicate writers | Removed `data/user/RoomReviewRepository` and its redundant mapper. The canonical `data/repository/RoomReviewRepository` uses `UserDao.recordReviewIfUnchanged`. Both provider APIs use that same instance. `ReviewProvider` only borrows it; no per-click DB creation. |
| D06-03 DEC-12 | [Dedicated scheduler correction](https://github.com/ManhT005/VocaMind/commit/3705641d6b9c74d8b53d2ed0cd542e94947c5015): `ZonedDateTime.plusDays`, preserving local time and using zone gap/overlap rules. Retained all TC-SCHED-01..10 and corrected old midnight expectations. |
| D06-04 catalog | Both compatibility and explicit commits validate canonical format/existence. Unknown valid-format IDs return NOT_FOUND without orphan writes; read failures and corrupt progress stay typed errors. |
| D06-05 DAO integration | Actual merge conflicted in `ReviewRepository` and docs index; UserDao merged automatically. Kept every current develop primitive plus the new conditional transaction and ID/before-box consistency checks. DATA-07 is unmerged; its future aggregation integration remains explicitly pending. |
| D06-06 UI guard | Documented FLASH-02/03 handoff; no debounce or card idempotency added to the DAO. AC-08 remains open. |
| D06-07 evidence | Full local checks pass on the recorded input tree. CI run links are maintained in the PR description. Connected/device checks are blocked by absent hardware. |
| D06-08 lifecycle | Original multi-instance callback limitation is retained. Race fixtures open instances before seeding; app composition uses one user DB instance. Reopen preserves committed state. |

## Compatibility boundary

Legacy `recordReview(id, result, timestamp)` is a default domain adapter; it reads
an expected snapshot, checks box compatibility and timestamp replay, maps the
updated progress and calls the canonical compare-and-commit method. It cannot
encode a caller's earlier snapshot because the old signature has no such argument.
Use `RecordReviewUseCase` for read/schedule/commit, or hold the explicit snapshot
for stronger caller-controlled concurrency. Both paths have rollback and mixed
race tests; there is no legacy persistence bypass or automatic retry.

Catalog and userdata remain separate files. Catalog existence is checked before
userdata's transaction; no cross-file transaction is claimed. Entire expected
progress is compared, including saved flag, counts and nullable timestamps.
Updated history must retain first-learned timestamp/saved flag, increment count
once and match the review timestamp. DAO verifies expected/event/progress IDs and
starting/outcome boxes. No schema, tokenizer, asset, migration or user-data reset.

## Executed verification

Evidence directory: [fix-20261106](evidence/data-06/fix-20261106/).
`verification.json` records toolchain, input hashes, counts, artifacts and limits;
`tested-inputs.json` identifies tracked code/config/fixture inputs.

- Focused integration: **94/94** tests passed before the scheduler correction.
- Calendar-time red test: **8/8 failed** against the old midnight scheduler.
  Failures and JUnit XML are retained under `before/`; the same eight cases pass
  in the final suite, including Berlin 23/25-hour DST transitions, gaps/overlaps,
  Bangkok 14:30/23:55/00:05 and box-5 +30 days.
- Final full suite: **343/343 Android/JVM tests passed**, 0 failures/errors/skips.
  Includes **42 native SQLite atomic review executions** (21 cases × API26/35),
  6 domain review tests, all scheduler/DAO/repository/pack/search/Quiz regression.
- **159 Python unittests passed** (112 vocabulary + 8 catalog + 39 contract), plus
  source/credit/ingestion/QC/corpus/accepted editorial/Unicode workflow checks.
  Used fresh DATA-06 output directories because previous CI-proof directories are
  intentionally immutable; actual local commands and logs are recorded.
- App + instrumentation APK assembly and lint passed: **0 errors, 6 warnings**.
- One initial full-suite failure was the Quiz test's hardcoded repository error
  count after adding CONFLICT. Replaced it with `RepoError.values().length + 2`;
  propagation of every error remains asserted. Final full run is green.

Command (JDK17, checksum-verified Gradle8.9, compileSdk35):

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2 --console=plain
```

The successful local command used the same Gradle distribution directly with
machine-local SDK/proxy configuration; no project workaround was introduced.

### SQL rollback and state checks

Faults are real SQLite `RAISE(ABORT)` triggers in UUID-named test copies. Tests
compare every column of ReviewEvent/UserWordProgress before and after failure.
The following results hold through both legacy and use-case APIs:

| Injected failure | Before | After |
| --- | --- | --- |
| INSERT ReviewEvent | Existing progress: box3, due1234, count7, learned111, last222, mistakes3, saved=true; no event | Identical progress, no event |
| INSERT new UserWordProgress | No progress/event | No progress/event; inserted event rolled back |
| UPDATE existing UserWordProgress | Same seeded existing row, no event | Identical row; inserted event rolled back |

Mixed legacy/new callers on two DB instances use a barrier after reading the same
snapshot: exactly one success and one CONFLICT, one event and one count increment.
Both existing-row and first-insert races are covered. Unknown catalog IDs, closed
catalog, invalid stored box, mismatched IDs and tampered history cannot write.
A persisted dueAt is checked unchanged after DB reopen and a different timezone's
use case runs for another word. Current allProgress/quiz reads remain consistent
with commits and rollbacks; this is not a claim that DATA-07 aggregation ran.

## Remaining gates

- **Connected instrumentation BLOCKED**: `adb-devices.txt` shows no attached device;
  `/dev/kvm` is unavailable. Building an instrumentation APK is not device evidence.
- **FLASH-02/03 PENDING**: 600 ms/per-card guard, three taps 150 ms apart, rotation,
  retry and queue behavior; advance only on commit success, reload on conflict.
- **DATA-07 integration PENDING** until its separate branch is updated; no changes
  were pushed to it. Preserve `progressSnapshot` and atomic commit together then.
- **Reviewer/LEARN-01 owner approval PENDING**. The scheduler correction is isolated
  for review; no sign-off or blanket AC-07/AC-08/NFR device certification recorded.

Device command (API26+):

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.user.AtomicReviewInstrumentedTest
```

Retain device/model/API/build/commit, raw reports and logs. Host reopen tests do not
certify physical reboot, process-kill durability or release migration acceptance.
