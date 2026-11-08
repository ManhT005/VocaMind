# LEARN-04 validation

## Scope

Integration tests for the whole learning flow, without UI: Today plan -> flashcard session ->
rating -> atomic transaction -> relearn once -> finish. `LearningFlowIntegrationTest` uses real
file-backed `catalog.db` and `userdata.db` (Robolectric native SQLite), the production
`PlanToday`, `FlashcardViewModel`, `RecordReviewUseCase` and `RoomReviewRepository`, and a
test clock that only moves when told to (UTC).

## Cases

| Test | Checks |
| --- | --- |
| fullSessionDueOrderRatingsRelearnOnceAndFinish | Only active-pack words, PackWord order; REMEMBERED -> box 1, HARD -> box 0, FORGOT -> 1 mistake; one ReviewEvent per rating in order with increasing timestamps; dueAt = review time + 1 day; FORGOT word returns once after two other cards; second FORGOT does not requeue; FINISHED; replanning the same day gives nothing to study |
| doubleTapWritesExactlyOneEventAndNextCardWorks | Three rapid taps -> 1 event; next card rates normally |
| ratingBeforeFlipWritesNothing | No worker task, no event |
| dueWordsComeBeforeNewWordsOnTheNextDay | Next day: due words first (dueAt ASC), then new words |
| repeatedForgotAcrossDaysTurnsLeechAndRememberedClearsIt | 2 -> 3 -> 4 mistakes across days, leech set on the 4th with the hint flag, HARD clears mistakes and leech |
| pausedPackWordsAreNeverPlanned | Pausing the pack gives NO_ACTIVE_PACK |

## Notes

- Rollback of a failed transaction stays covered by `AtomicReviewTest` (DATA-06); here a failed
  save is covered with fakes in `FlashcardViewModelTest`.
- The 600ms debounce and the Espresso triple-tap check belong to FLASH-03; this suite covers
  the single-write guarantee at ViewModel level.
- `./gradlew testDebugUnitTest assembleDebug` passes. Lint and device tests were not run.
