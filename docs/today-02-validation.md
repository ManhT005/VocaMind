# TODAY-02 validation

## Scope

Relearn queue for the flashcard session (`SessionQueue`, used by `FlashcardViewModel`).

## Rules

- A word rated FORGOT (after its review committed) is queued again once in the same
  session, after at least 2 other cards; with fewer cards left it goes to the end, and a
  lone card is shown again right away.
- Each word is relearned at most once per session (`relearned` set), so a second FORGOT
  does not enqueue it again and the session always ends.
- A failed or conflicting save does not queue anything: the card stays as it was.
- No new table. `dueAt` stays tomorrow because the committed FORGOT review already set it;
  the relearn card's own rating is a normal committed review through the same use case.
- Position/total grow by one when a card is queued again.

## Tests

- `SessionQueueTest`: HARD/REMEMBERED never requeue, gap of two, end-of-queue and
  single-card cases, one relearn per word, finished session rejects `advance`.
- `FlashcardViewModelTest`: FORGOT -> 2 cards -> relearn once -> finish, repeated FORGOT
  does not loop, failed save queues nothing, 4 FORGOTs over two sessions set leech.
- `./gradlew testDebugUnitTest assembleDebug` passes. Lint and device tests were not run.
