# LEARN-01 validation

## Scope

Pure Java Leitner scheduler in `com.vocamind.app.domain.learning`:

| Class | Role |
| --- | --- |
| `Rating` | REMEMBERED / HARD / FORGOT, same names as `ReviewEvent.rating` |
| `LeitnerState` | box 0..5, consecutiveMistakes ≥ 0, isLeech; `newWord()` |
| `ScheduleResult` | boxBefore/boxAfter, dueAt (epoch ms), mistakes, leech, `becameLeech()`, `toState()` |
| `LeitnerScheduler` | `schedule(state, rating, nowEpochMillis, zone)` |

No Android, Room, data or feature imports. The current time and zone are
parameters, so results are deterministic.

## Rules implemented

Rules are final for LEARN-01. Intervals follow the common 1-3-7-14-30 day
Leitner sequence; the leech threshold comes from the assignment sheet. When the
SRS is available, align FR-VM-028..033 / TC-SCHED-01..10 by changing constants.

- REMEMBERED: box + 1 (max 5), mistakes reset, leech cleared.
- HARD: box unchanged, mistakes reset, leech cleared.
- FORGOT: box 0, mistakes + 1, leech on at ≥ 4 in a row (stays on until
  HARD/REMEMBERED).
- dueAt = start of local day `today + interval(boxAfter)`; intervals in days
  for box 0..5: 1, 1, 3, 7, 14, 30.

Changing an interval or the threshold only touches constants in
`LeitnerScheduler`.

## Evidence

| Check | Result |
| --- | --- |
| `LeitnerSchedulerTest` (6 tests) | Pass (JUnit 4.13.2, compiled with `--release 17`) |
| `git grep "import android"` in `domain/learning` | No matches |
| `./gradlew testDebugUnitTest assembleDebug` | Not run locally: the machine only has JDK 25, which Gradle 8.9 cannot run. CI (`Build & Test`, Temurin 17) runs it on the PR. |

## Handoff

- LEARN-02: full TC-SCHED-01..10 suite and boundary tests (box 5, HARD at box 0,
  DST, midnight).
- DATA-06 / FLASH-02: write `ScheduleResult` into ReviewEvent
  (`boxBefore`, `boxAfter`, `nextDueAt = dueAt`) and UserWordProgress in one
  transaction.
- LEARN-03: UI hint using `isLeech()` / `becameLeech()`.
