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

## Today screen (UI part of TODAY-01)

- `TodayPlanViewModel` (activity scoped like `PacksViewModel`) reads `PlanToday` and exposes due
  count, new count and READY / DONE / NO_ACTIVE_PACK / ERROR.
- Pack state still decides loading, pack-read error and the "choose a pack" CTA (`explore`), so the
  PACK-02 contract and tests are unchanged.
- With an active pack: READY shows "Cần ôn: X từ · Từ mới: Y từ" and Start; DONE shows a
  message with Quiz and Explore buttons (`done_quiz`, `done_explore`); a plan failure shows retry.
- The counts reload on resume, so they are fresh after returning from a session.
- Removed the stale "feature under development" string.
- Tests: `TodayRenderingTest`; `PackActivationRenderingTest` now seeds a plan ViewModel.
  `./gradlew lintDebug testDebugUnitTest assembleDebug` passes. Device tests were not run.
