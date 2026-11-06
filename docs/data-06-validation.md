# DATA-06 — Atomic review transaction

Current integration: [PR #24 fix validation](data-06-fix-validation.md), 06/11/2026.
The original 151-test evidence in `evidence/data-06/unit-results.json` is historical;
it does not certify the current integration. Original implementation/documentation:
[pre-fix head f86332b](https://github.com/ManhT005/VocaMind/tree/f86332b1d3a33b89f71ed3e1e73840f78a47a882).

| Item | Current contract |
| --- | --- |
| Scope | Android DATA-06 atomic review; FR-VM-024, BR-10, NFR-VM-07; transaction portion of AC-08 |
| Branch | `feature/data-06-atomic-review-transaction` |
| Integrated develop | `e923c993e9ba731bb2aab146e712885731e20479` (includes DATA-05 fix) |
| Domain | One `ReviewRepository`: snapshot read + compare-and-commit, deprecated DATA-04 compatibility adapter |
| Production writer | `data/repository/RoomReviewRepository`, shared by `RepositoryProvider.reviews()` and its use case factory |
| Persistence | `UserDao.recordReviewIfUnchanged`, entire progress comparison and atomic event/progress write |
| Scheduling | Calendar days preserving local time under DEC-12; timezone gap/overlap rules apply |
| New model mapping | `ReviewProgress` ↔ `WordProgress` ↔ internal Entity mapper; UI sees no Room types |

## Composition and errors

Create `RepositoryProvider` once on a worker using the application-owned catalog;
reuse that provider through its lifecycle. Its `recordReview(clock, zone)` factory
borrows the same review repository/user DB as `reviews()`. `ReviewProvider` never
opens or closes a database.

```java
// Composition root injects this domain use case into a ViewModel.
RecordReviewUseCase review = repositories.recordReview(Clock.systemUTC(), ZoneId.systemDefault());
// Execute on a worker; advance/render success only after commit returns.
ScheduleResult committed = review.execute(stableId, Rating.REMEMBERED);
```

Both APIs validate canonical ID format and catalog existence before userdata writes.
Unknown IDs are `NOT_FOUND`; failed catalog/progress reads are `READ_FAILED`;
corrupt rows are `INVALID_DATA`; stale snapshots are `CONFLICT`; commit failures
are `STORAGE` (legacy `RepoError.WRITE_FAILED`). Invalid commands fail before writes.
No failure is converted to empty/success and no automatic rating retry occurs.

The legacy `recordReview(String, ScheduleResult, long)` is deprecated, captures a
snapshot at adapter entry and uses the same compare-and-commit path. It rejects
mismatched box and duplicate/older timestamps. Its old signature cannot carry a
snapshot captured by the caller before scheduling. New flows must use the use
case, or explicitly preserve the expected snapshot until compare-and-commit.

## Verification and limits

The current run passed **343 Android/JVM tests**, **159 Python unittests**, the
workflow's data/Unicode checks, app/test APK assembly and lint (0 errors, 6 warnings).
Detailed source hashes, JUnit reports, SQL fault tests and calendar-time red/green
regression are in [fix-20261106](evidence/data-06/fix-20261106/).

No Entity/schema/version/product catalog or destructive-migration change. Existing
DAO primitives remain intact. DATA-07's aggregation methods are on its separate,
unmerged branch; that branch must integrate the canonical writer and rerun its
own snapshot aggregation tests when updated.

Connected instrumentation was not run: no attached device or `/dev/kvm` in this
environment. FLASH-02/03 must still prove 600 ms/per-card idempotency, queue rules,
rotation/retry and UI behavior. Two sequential use-case calls may legitimately
commit twice; optimistic concurrency is not a card-level debounce mechanism.
AC-08/full release acceptance and reviewer/LEARN-01 owner sign-off remain open.
