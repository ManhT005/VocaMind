# TODAY-01 validation

## Scope

Domain-level Today Planner (`PlanToday`, `TodayPlan`, `DailyQuota`). UI wiring of
`TodayFragment` is out of scope and follows with FLASH-01/02.

## Rules

- Only active packs contribute; no active pack gives `NO_ACTIVE_PACK` (CTA to pick a pack).
- Tier 1: due words (`dueAt <= now`), `dueAt ASC`, capped at `PlanToday.MAX_DUE`.
- Tier 3: never-seen words in `PackWord.orderNo` order, limited to
  `UserPreference.dailyNewWords - learnedToday` (never negative).
- De-duplicated by `stableId` across tiers and packs.
- Nothing due and no quota left gives `DONE` (suggest Quiz / Explore).
- Day boundary comes from the injected `Clock` and `ZoneId`.

## Contract change

`StudyRepository.dailyQuota(dayStart, nextDayStart)` added; implemented by
`RoomStudyRepository` from `UserPreference` and `UserDao.learnedCount`.

## Tests

`PlanTodayTest`: no pack, ordering, quota limit, quota overrun, duplicates,
not-yet-due, done state, repository failures. `./gradlew testDebugUnitTest` passes.
