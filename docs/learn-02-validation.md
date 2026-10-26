# LEARN-02 validation

## Scope

Unit tests for `LeitnerScheduler` (LEARN-01). Pure JUnit 4, no Android
dependency. Branch `feature/learn-02-leitner-tests` is stacked on
`feature/learn-01-leitner-scheduler` and should be merged after it.

## TC-SCHED cases (`LeitnerSchedulerSrsTest`)

The case names come from the assignment sheet. Expected values follow the
assumed rules in [LEARN-01 plan](plans/learn-01-leitner-scheduler.md), so they
must be checked against SRS TC-SCHED-01..10 in review.

| Case | Checks |
| --- | --- |
| TC-SCHED-01 | New word + REMEMBERED → box 1, due tomorrow |
| TC-SCHED-02 | REMEMBERED from box 0..4 → box + 1 with interval 1/2/4/7/14 days |
| TC-SCHED-03 | REMEMBERED at box 5 stays at box 5 |
| TC-SCHED-04 | HARD keeps every box and uses that box's interval |
| TC-SCHED-05 | HARD at box 0 stays at box 0, due tomorrow |
| TC-SCHED-06 | FORGOT from any box → box 0, due tomorrow |
| TC-SCHED-07 | 3 FORGOT in a row is not leech |
| TC-SCHED-08 | 4th FORGOT in a row turns leech on; later FORGOT keeps it |
| TC-SCHED-09 | HARD/REMEMBERED reset mistakes and clear leech |
| TC-SCHED-10 | Same input gives the same result |

## Boundary tests (`LeitnerSchedulerBoundaryTest`)

- Last millisecond of a day vs. midnight fall on different calendar days.
- dueAt is always 00:00 local time.
- One instant gives different days in UTC and Asia/Ho_Chi_Minh.
- DST spring-forward and fall-back in America/New_York keep calendar days.
- A word reviewed in the evening is due from the next morning.
- Mistake counter saturates at `Integer.MAX_VALUE`.
- Interval table matches the plan; box outside 0..5 is rejected.

## Evidence

| Check | Result |
| --- | --- |
| `LeitnerSchedulerTest` + `LeitnerSchedulerSrsTest` + `LeitnerSchedulerBoundaryTest` | 24/24 pass (JUnit 4.13.2, `javac --release 17`) |
| `./gradlew testDebugUnitTest assembleDebug` | Not run locally (only JDK 25 installed); CI `Build & Test` runs it on the PR |
