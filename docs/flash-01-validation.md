# FLASH-01 validation

## Scope

Flashcard UI and its ViewModel. Route `flashcardFragment` is added to the nav graph but
is **not yet linked from Today**, and ratings are **not persisted**: `FlashcardViewModel`
only advances the card unless a `RatingListener` is given. Persisting reviews, the
double-tap guard and the relearn queue belong to FLASH-02/03 and TODAY-02.

## Behaviour

- Queue comes from `PlanToday` (due first, then new words within the daily quota).
- Front shows only lemma and POS. Tapping the card flips it.
- Back shows IPA (hidden when null), Vietnamese meaning, English and Vietnamese example.
- FORGOT / HARD / REMEMBERED are disabled until the card is flipped; `rate()` before
  flip is ignored in the ViewModel as well.
- Rating advances to the next card on its front side; the last rating gives FINISHED.
- Rating buttons have `minHeight` 56dp and the back button is 48dp.
- Face and position live in the retained ViewModel, so rotation keeps them.
- States: loading, ready, no active pack, nothing to study, finished, error + retry.

## Tests

- `FlashcardViewModelTest`: front/back, rating gating, double rating, finish, empty
  states, error + retry, state retention.
- `FlashcardLayoutTest`: rating buttons start disabled and >= 48dp, answer side hidden.
- `./gradlew testDebugUnitTest assembleDebug` passes. Lint and instrumented tests
  (rotate on device, measured touch target) were not run here.
