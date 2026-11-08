# FLASH-02 validation

## Scope

Rating flow end to end: Flashcard UI -> `FlashcardViewModel` -> `RecordReviewUseCase` ->
`LeitnerScheduler` -> atomic transaction (DATA-06) -> next card. Today now has a
"Bắt đầu học" button that opens the flashcard session.

## Behaviour

- A rating is committed on the worker before the card advances; the next card shows its front.
- While a write is in flight (`saving`) the rating buttons are disabled and further taps
  are ignored, so one card receives at most one rating. The 600ms debounce and the
  Espresso triple-tap check remain FLASH-03.
- A failed or conflicting write keeps the same card and face and shows a message; nothing is
  retried automatically (CONFLICT needs a new user decision).
- After a commit the hint shows the new box and next due date, or the leech message when the
  review turned the leech flag on.
- HARD/REMEMBERED clear leech and FORGOT counts mistakes via `LeitnerScheduler` (LEARN-01).
- Not in this task: relearn queue (TODAY-02), TTS (not P0).

## Tests

`FlashcardViewModelTest` runs the real `RecordReviewUseCase` over `FakeReviewRepository`:
each rating's box/mistakes, advance only after commit, taps while saving, failure and
conflict keep the card, 4th FORGOT sets leech, finish state, load errors, retained state.
Rollback of a failed transaction is covered by the existing `AtomicReviewTest` (DATA-06).
`./gradlew testDebugUnitTest assembleDebug` passes. Lint and device tests were not run.
