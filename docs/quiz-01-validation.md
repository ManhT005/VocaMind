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
- `QuizQuestion` retains vocabulary POS/context and requires four distinct option
  IDs and meaning keys, one referenced correct ID, and an explicit MeaningNormalizer.
  It rejects keys inconsistent with normalized option text and a correct key that
  differs from the normalized target meaning. The unchecked three-argument
  constructor has been removed. Use `pool.createQuestion(stableId, options, correctId)`
  to construct from the pool snapshot with its exact normalizer.
  QUIZ-02 must still ensure distractors are semantically unambiguous; string
  inequality alone does not prove single-sense correctness.
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
  additionally honor pool membership and target count; pool.createQuestion enforces
  membership and normalized meaning mapping for each question.

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

## Issue #19 — correct meaning validation (2026-11-05)

The regression `rejectsCorrectOptionWithWrongTargetMeaning` was executed via
Android Studio Gradle on the original constructor: **1 failed, 0 passed**,
with `expected IllegalArgumentException to be thrown, but nothing was thrown`.
It supplied four distinct valid options and marked a distractor as correct.

The fix makes normalization mandatory at question construction. Every option key
must match its display meaning, and the selected correct key must match the target.
QuizPool now supplies a factory using the same injected normalizer and its own
vocabulary snapshot. The seeded fake uses this factory. Repository test data now
uses a target meaning consistent with its correct answer.

Additional tests cover null/blank normalizers, forged correct/distractor keys,
NFC/NFD, whitespace, case, an explicit ß→ss casefold vector, normalized duplicates,
unknown pool IDs, and successful scoring after normalized construction. Existing
stableId dedup/conflict and repeated-meaning pool targets 5/7/10 remain covered.
The test adapter is still not a production full Unicode implementation.

Verification through Android Studio Gradle:
- Before fix: focused regression above failed because no exception was thrown.
- After fix: `:app:testDebugUnitTest :app:assembleDebug` completed successfully;
  **195 passed, 0 skipped, 0 failed** on the current working tree.
- `git diff --check`: PASS (Git emitted line-ending warnings for unrelated files).
- No commit/push or new CI run was performed, so no CI link for this fix is
  available yet. These are local verification results, not reviewer sign-off.
Historical sections below describe their original commits; the earlier handoff
of mapping validation to QUIZ-02 is superseded by this fix. Production sampling,
full Unicode adapter and semantic distractor quality still belong to QUIZ-02.
No end-to-end AC-10 acceptance is claimed.

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

## Generator test fix — 2026-11-02

Code commit: [`a2048412cbab7d3ef611f22d92d7372d9516de7d`](https://github.com/ManhT005/VocaMind/commit/a2048412cbab7d3ef611f22d92d7372d9516de7d).
Plan: [quiz-01-fix-plan.md](plans/quiz-01-fix-plan.md).
Branch: `fix/quiz-01-generator-test`, based on develop
`fff380127b0808b203165e649e22eaf714027afb`.
PR: [#16](https://github.com/ManhT005/VocaMind/pull/16).

The earlier verification section records the original QUIZ-01 implementation
merged by PR #6 (`37f14ca3cdb9086e9960fef988290c0839d5aa3e`); those results are
historical and are not reused as evidence for this fix.

### Reproduction before the fix

After extracting the unchanged inline fake into a private helper and adding
REG-01, the following command was actually run against the old selection logic:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.quiz.QuizDomainTest.seededFakeHandlesFiveIdsWithRepeatedMeaning'
```

Result: **1 test, 1 failure, 0 skipped**, BUILD FAILED. JUnit XML recorded
`java.lang.IllegalArgumentException: distinct option IDs and meanings required`
from `QuizQuestion` via the fake. The pool was READY with five distinct IDs and
meanings A,A,B,C,D in that fixed order, and target count 5. Sampling all five IDs
guarantees the old distractor loop encounters duplicate A keys for some targets.
This was a real failing run, not an assertion that catches the exception.

That diagnostic run used the preinstalled Corretto JDK **22.0.2** before JDK 17
was available. All post-fix checks below use portable Temurin **17.0.20.1+1**,
Gradle wrapper **8.9** and the project's existing Android SDK settings.
No Gradle/SDK versions or dependencies were changed. Portable verification tools
remain outside the committed change under `.tools/`.

### Fix and regression matrix

The fake remains a private test helper. It seeds `usedMeaningKeys` with the
correct key, selects only unseen distractor keys from QuizPool and stops at four
options. It still uses the supplied Random, null guards and EMPTY/INVALID_INPUT
outcomes. Production source and normalization behavior are unchanged.

| Case | Test | Required behavior |
| --- | --- | --- |
| REG-01 | `seededFakeHandlesFiveIdsWithRepeatedMeaning` | 5 IDs, A,A,B,C,D; READY, target/output 5 |
| REG-02/03 | `seededFakePreservesIdTargetWithRepeatedMeanings` | 7/12 IDs with four meanings; READY, output 7/10 |
| REG-04/05 | `seededFakeReturnsEmptyForInsufficientPools` | 4 IDs/3 meanings or 3 IDs; EMPTY, zero questions, no session |
| REG-06 | `seededFakeRepeatsQuestionOrderForSameSeed` | Same repeated-meaning pool and seed yield the same question ID order |
| REG-07 | `seededFakeRejectsNullInputs` | Null pool or Random yields INVALID_INPUT, including both null and insufficient pool/null Random |

Independent output assertions check exact question count, unique stableIds,
pool membership/payload, four distinct option IDs and meaningKeys, and exactly
one correct option whose key/text match the question vocabulary in the pool.
They do not duplicate the distractor selection algorithm or require different
questions to have different correct meanings. Existing model/session/result
and generator contract tests remain in place.

### New verification

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.quiz.*'
.\gradlew.bat testDebugUnitTest assembleDebug
git diff --check
```

- Focused Gradle run: **BUILD SUCCESSFUL**, Quiz JUnit XML `tests=15`,
  `failures=0`, `errors=0`, `skipped=0` (2026-11-02).
- Full `testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL** (1m 9s).
  Fresh JUnit XML totals: **77 tests, 0 failures, 0 errors, 0 skipped**;
  Quiz=15, catalog=22, user DB=16, Leitner=24. Debug APK built successfully.
- `git diff --check`: PASS. Only the test class and Quiz documentation changed.
- CI is verified on the pushed head via [PR #16 checks](https://github.com/ManhT005/VocaMind/pull/16/checks),
  separately from these local results; a green plan-commit run is not evidence
  for this fix. Reviewer Trường has not signed off.

### Remaining scope

This proves the QUIZ-01 test double's contract behavior only. Production sampling,
option shuffle, full Unicode casefold, semantic distractor quality and correct
meaning mapping validation remain QUIZ-02; UI/debounce/lifecycle/persistence
remain QUIZ-03/integration. No emulator is required for this pure domain test fix.
No acceptance of the complete AC-10 feature or reviewer approval is claimed.
