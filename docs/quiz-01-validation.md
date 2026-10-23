# QUIZ-01 implementation and handoff

## Implemented domain contract

Sources: `app/src/main/java/com/vocamind/app/domain/quiz/`.
Tests: `app/src/test/java/com/vocamind/app/domain/quiz/QuizDomainTest.java`.

- `QuizVocabulary` carries stableId, lemma, POS, Vietnamese meaning and optional
  context (empty string allowed). IDs accept 16 hex characters and canonicalize
  to lowercase. Blank required text and null context are rejected.
- `QuizPool` snapshots the active-pack projection supplied by upstream. Identical
  IDs/payloads deduplicate; conflicting payloads throw `IllegalArgumentException`.
  Null/invalid input is an error, never an empty-data signal. Availability separates
  fewer than four IDs from fewer than four meaning keys. Target is min(10, ID count)
  when ready, otherwise zero. Shared meanings do not shorten the target.
- `MeaningNormalizer` is an explicit injected boundary. Its production adapter must
  implement NFC, trim and full Unicode casefold while preserving Vietnamese accents.
  Keys are derived in memory, not added to the canonical data schema. The test fixture
  uses NFC/trim/lowercase for its limited Vietnamese vectors; it is NOT a production
  normalizer and does NOT establish full Unicode casefold compliance.
- `QuizQuestion` retains vocabulary POS/context for later prompt construction,
  requires four distinct option IDs and meaning keys, and one referenced correct ID.
  `QuizOption` accepts already normalized keys. QUIZ-02 must ensure keys correspond
  to display text, the correct key matches the vocabulary, and distractors are
  semantically unambiguous. String inequality alone does not prove single-sense correctness.
- `QuizSession` is immutable: every accepted operation returns a `Transition` with
  a new session. Rejected operations return the same snapshot and an explicit outcome.
  The caller must retain the returned session and serialize UI events against the
  latest snapshot; stale snapshots are not a concurrency/persistence lock.
- `answer(questionId, optionId)` validates the current question and option, locks
  its first valid answer, and completes immediately on answering the final question.
  `next()` requires an answer. `abandon()` is terminal and produces no result.
  Collections are defensive snapshots and exposed read-only.
- Only a completed session exposes `QuizResult`; score is
  round(correct * 100.0 / total), wrong = total - correct. There is no clock or IO.
- `QuizGenerator.generate(QuizPool, Random)` is a contract only. A seeded fake in
  tests demonstrates the boundary. `QuizGenerationOutcome` distinguishes READY,
  EMPTY with a pool availability reason, and INVALID_INPUT with an explanation.
  Ready output validates session size/unique IDs. The production generator must
  additionally honor pool membership, target count and correct meaning mapping.

Typical caller flow (on a serialized event handler):

```java
QuizSession.Transition transition = session.answer(questionId, optionId);
session = transition.getSession();
if (transition.getOutcome() == QuizSession.Outcome.ACCEPTED) {
    // Render feedback; if COMPLETED, hand result to the persistence coordinator.
}
// On the user's next action: session = session.next().getSession();
// After confirmed exit: session = session.abandon().getSession();
```

## Verification

Local date: 2026-10-23.

- Standalone compilation: JDK 21.0.7 `javac --release 17 -encoding UTF-8`, all
  Quiz production and test sources, JUnit 4.13.2 and Hamcrest 1.3: PASS.
- `org.junit.runner.JUnitCore com.vocamind.app.domain.quiz.QuizDomainTest`:
  **10 tests, 0 failures**, final run 0.413 seconds. Tests include data matrices for
  0/3/4/7/10/12 pool sizes, invalid inputs, ID conflict/dedup, NFC/case fixture keys,
  preserved accents, repeated meanings, question/session invariants, defensive
  copies, correct/wrong and duplicate/stale answers, terminal operations,
  abandonment after an answer, 0/100/17/29 percent scores, seeded fake and outcomes.
- Android Studio bundled JDK 25.0.3 cannot run Gradle 8.9: configuration fails with
  `Unsupported class file major version 69`. An existing JDK 21.0.7 is used for
  subsequent Android checks; JDK 17 CI remains the baseline verification target.

- Android Gradle focused run with JDK 21.0.7:
  `gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.quiz.*'`:
  **BUILD SUCCESSFUL**. Gradle installed the already licensed SDK Platform 35 and
  Build Tools 34.0.0 required by the existing project. No build configuration changed.
- Current Gradle XML: `app/build/test-results/testDebugUnitTest/TEST-com.vocamind.app.domain.quiz.QuizDomainTest.xml`:
  **tests=10, skipped=0, failures=0, errors=0**. A subsequent unfiltered
  `testDebugUnitTest` also executed the test task and produced these counts.
- Full `gradlew.bat testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**
  (1m 52s, 40 actionable tasks). Debug APK:
  `app/build/outputs/apk/debug/app-debug.apk`. This validates compilation and
  packaging; it does not claim device/UI acceptance.
- Earlier offline attempts failed while SDK 35 was not yet installed. They do
  not represent the final environment or a domain code failure.
- `git diff --check`: PASS; new source/test/validation files also checked for
  trailing whitespace. Domain imports contain only Java standard library types.

Standalone JUnit does not replace Android Gradle or CI evidence. CI with JDK 17
has not been run from this local implementation.

## Remaining integration and review

QUIZ-02: implement sampling/shuffle and production Unicode casefold adapter with
conformance vectors; verify meaning/POS/sense choices and pool membership.

QUIZ-03: render prompts with POS/context, retain current session snapshot, submit
IDs, handle feedback, confirm exit before abandon, and debounce across questions
using a monotonic clock (600 ms). No debounce is simulated in this model.

Data integration: supply only active-pack vocabulary and test that repository
precondition. Map completed result fields to QuizAttempt; finalize persistence
exactly once outside this immutable model. Abandoned sessions must not be saved
as completed attempts.

No UI, navigation, Gradle dependency, Room schema, DAO or Leitner changes. Review
by the assigned reviewer and a PR into `develop` remain delivery steps; this local
implementation does not claim they have occurred.
