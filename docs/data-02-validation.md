# DATA-02 — UserDatabase

Implemented against VocaMind_SRS_Baseline_v1.1 §7.2, FR-VM-004/024,
ADR-01 and the DATA-02 assignment (Nhân / reviewer Thi, ARC-01 dependency).
The original supplied plan is preserved in plans/data-02-user-database.md.

Five Room entities live under `data.user.entity`, physically separate from
CatalogDatabase. `UserDatabase.open(context)` opens `userdata.db` and synchronously
validates/initializes it before returning. Call it and all DAO operations from a
worker executor; retain the returned instance in the application's data-layer
owner and close it when that owner is disposed. No UI integration is added here.
Opening errors propagate. No destructive fallback, asset copy, database deletion
or main-thread-query override is used.

The onOpen callback inserts preference id=1 using INSERT OR IGNORE directly on
the supplied connection: defaults 5 / 10 / false / 20. It never recursively opens
Room and never resets an existing preference. Concurrent opens are tested.

Implementation decisions for review:

- IDs referencing catalog are text logical IDs, without cross-database foreign keys.
- Time values are epoch milliseconds. Nullable learnedAt/lastReviewedAt preserve
  the distinction between missing learning history and a timestamp of zero.
- `setSaved` updates an existing progress row only and returns the affected count.
  Creating a saved-only row is deferred until DATA-03/07 define how it participates
  in the SRS count across boxes 0..5. Consumers must handle a zero result. No new
  saved-only box or synthetic learning timestamps are introduced.
- Rating strings are REMEMBERED/HARD/FORGOT. Quiz score is an integer rounded as
  in the existing QuizResult; counts must be 4..10 and consistent with the score.
- DAO write primitives validate box/rating/counters/preferences/quiz values.
  Entities are internal mutable Room rows, not domain models; raw SQL can bypass
  Java validation. No claim of SQLite CHECK constraints is made.
- `recordReview` inserts the event then upserts progress in one Room transaction.
  Tests fail the second step after insertion and verify rollback. Scheduler,
  duplicate submission handling and full rating semantics remain DATA-06/domain.
- No speculative query indexes are added before DATA-03 query contracts are reviewed.

Production schema starts at version 1, exported by the Room compiler into
`app/schemas/com.vocamind.app.data.user.UserDatabase/1.json`. No earlier userdata
schema exists in this checkout and no production migration is invented.
Future schema changes must increment the version, retain exported history and
register explicit data-preserving migrations. Missing migrations/hash mismatch
must fail rather than reset user data.

## Validation

Use JDK 17 and the SDK configured in local.properties:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

UserDatabaseTest uses unique database files, native SQLite and worker threads
on Robolectric API 26 and 35. It covers default values, edited preferences,
concurrent opens, full five-table field round trips, saved-flag isolation,
transaction rollback, invalid writes, missing migration and invalid identity hash.
Test cleanup deletes only UUID-named test databases.

An additional synthetic migration test creates a fixture version 2 with an
auxiliary table, then registers an explicit 2→1 migration which removes only that
table. Room validates the production v1 target schema, and all columns of all five
user tables are compared before migration, after migration and after reopening.
This exercises a data-preserving downgrade, not a released production upgrade.

Executed on Windows on 2026-10-25 with JDK 17: the command above completed
`BUILD SUCCESSFUL`. Total: 48 executed, 48 passed, 0 failed, 0 skipped:

| Suite | Executed | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: | ---: |
| UserDatabaseTest (8 cases × API 26/35) | 16 | 16 | 0 | 0 |
| CatalogAssetTest | 22 | 22 | 0 | 0 |
| QuizDomainTest | 10 | 10 | 0 | 0 |

Debug application and instrumentation APK assembly both passed. Local reports:
`app/build/reports/tests/testDebugUnitTest/index.html` and XML under
`app/build/test-results/testDebugUnitTest/`.

The initial test run exposed Robolectric's native-loader failure on the checkout's
URL-encoded space (`ADR%20NHOM%202`). The Gradle test configuration now selects
the OS temporary cache when the checkout path contains spaces; the subsequent
complete run passed on both configured APIs. The OS temporary directory must
itself have no spaces for this workaround. Production database paths are unchanged.

Outstanding acceptance evidence: a production upgrade migration when a real v2
schema exists, connected device force-stop/reboot (DATA02-T11),
and Thi's schema review. `adb devices` returned no connected devices on
2026-10-25. APK compilation does not prove instrumented execution. The userdata
tests do not establish catalog staging/rollback or full AC-13; DATA-08 owns that
integration. DATA-02 DoD remains open for these acceptance checks.
