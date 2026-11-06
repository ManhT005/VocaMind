# QUIZ-02 generation validation and handoff

Implementation date: 2026-11-06. Branch: `feature/quiz-02-generation`.
Develop base: `4959cf6d29314d7105f4285edd76e3169d912958`.
The existing remote planning commit `bee7d92697eb74d8c717b841ce0c8bfa8b43bf1f`
is preserved as the implementation parent.
Scope follows the supplied `quiz-02-generation.md`; QUIZ-01 contracts are retained.

## Implementation

- `UnicodeMeaningNormalizer`: pinned Unicode 16.0 NFC, explicit Unicode White_Space trim, Unicode
  16.0.0 full default casefold C/F. Preserves accents and internal spaces;
  rejects null/empty/blank. Locale independent, code point iteration.
- `DefaultQuizGenerator`: stableId sorted snapshot, sorted meaning buckets,
  supplied Random only, sample without replacement, min(10, unique IDs), three
  distinct distractor buckets, representative entry sampling and option shuffle.
  Every question goes through `QuizPool.createQuestion`; READY goes through the
  existing session validation. Same snapshot/seed gives identical complete output
  regardless of input order. No global random, mutable input, clock, network or IO.
- `GenerateQuiz.execute(Random)`: blocking, caller must use a worker executor.
  Returns `RepoResult<QuizGenerationOutcome>` and reads a fresh active snapshot on
  every valid invocation. Storage errors retain their original RepoError;
  invalid pool payloads return INVALID_INPUT. Null Random returns INVALID_INPUT
  without querying storage. Insufficient valid snapshots return EMPTY with reason.
  Production mapping remains the existing RoomQuizRepository/UserDataMapper.

Example for QUIZ-03:

```java
// Run on worker executor. Inject/reuse Random or supply a seed for reproduction.
RepoResult<QuizGenerationOutcome> result = new GenerateQuiz(quizRepository)
        .execute(new Random(42));
// Handle repository failure first, then READY / EMPTY / INVALID_INPUT.
// READY carries immutable questions, vocabulary POS/context and correctOptionId.
// Submit option IDs to QuizSession; never infer correctness from option index.
```

## Content ambiguity policy

The generator excludes every alternative meaning associated with the target's
normalized lemma in this snapshot, even if POS/context could distinguish senses.
If a sampled target has fewer than three remaining distractor meanings, return
INVALID_INPUT with `AMBIGUOUS_DISTRACTORS: <stableId>`; never shrink N or manufacture
options. The fixture covers same lemma/different POS with missing context:
four meanings cannot safely generate; adding another unrelated meaning permits
generation while excluding the alternate sense. Different stable IDs sharing a
meaning remain eligible questions and do not reduce N.

This is a conservative, reviewable policy, not semantic equivalence detection.
Different lemmas can still be synonymous; content QA must assess distractors and
QUIZ-03 must display POS/context. Assigned reviewer approval remains outstanding.

## Unicode provenance and limits

Official source, license, downloaded-byte SHA256 and deterministic generation
script are in [tools/unicode](../tools/unicode/README.md). Regeneration checks
pinned checksums before emitting Java. No extra APK dependency or runtime file is
required; the generated source contains the full Unicode license notice.

Tests check all 1,557 official C/F mappings, identity of unmapped supplementary
characters, NFC/NFD Vietnamese, Unicode trim/NBSP, case, sharp S, sigma, default
dotted I, supplementary case pairs, accent preservation, internal spaces,
compatibility characters and null/blank rejection. Android API 26/35 also run
representative production-normalizer vectors through Robolectric.

The review follow-up reproduced **664 failing transformations across 19,965
rows** with the JDK 17 platform NFC adapter. Example: official row 15,106,
column 3, `105D2 0307` must compose to `105C9`. This is a real pre-fix probe;
the adapter's prior platform-version caveat is now resolved in production.

`UnicodeNfc` now uses pinned Unicode 16 canonical decomposition, combining
classes and composition-exclusion tables, with algorithmic Hangul. It implements
recursive decomposition, stable ordering and blocked composition with no platform
Normalizer/ICU dependency. Generated data: 2,081 canonical decompositions, 934
nonzero combining classes, 961 compositions. Compatibility decomposition is not
performed. The generator verifies downloaded-byte checksums for all inputs.

`NfcConformanceTest` checks all five NFC invariants for **19,965 official rows**
(**99,825 transformations per runtime**) and identity for every scalar absent
from Part 1. `AndroidNfcConformanceTest` inherits the same tests unchanged on
Robolectric API 26/35. A production-adapter regression checks the exact post-JDK
Todhri composition above. This verifies pinned code on three test environments;
it does not claim physical-device execution. No failing vector is skipped.

The strengthened ambiguity fixture asserts INVALID_INPUT and zero questions when
three safe distractors are unavailable, and independently checks every emitted
option against alternative meanings of the target lemma when generation succeeds.
Both paths run across 60 seeds. Policy remains conservative even with POS/context;
content QA for synonyms across different lemmas remains a content responsibility.

## Validation

Toolchain: portable Temurin 17.0.20.1+1, Gradle wrapper 8.9, compile/target SDK 35,
min SDK 26. JAVA_HOME points to `.tools/jdk17/jdk-17.0.20.1+1` and
GRADLE_USER_HOME to the existing `C:/Users/thanh/.gradle` cache.

Commands:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.quiz.*' --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
git diff --check
```

Focused domain run: BUILD SUCCESSFUL. JUnit XML: 24 tests, 0 failures, 0 errors,
0 skipped (18 QUIZ-01 regression, 5 generator/normalizer, 1 orchestration).
Property test exercises 360 deterministic pool/seed combinations including skewed
meanings, membership, count, correct mappings, read-only output, input immutability
and input-order invariance. It checks answer position variation across seeds
without demanding different output for every pair of seeds.

Real Room integration adds active A/B/shared, paused C, orphan activation,
pause A, all paused, read failure and exact catalog/userdata snapshots before/after
generation on APIs 26/35. Existing repository regression remains included.

Initial implementation `testDebugUnitTest assembleDebug lintDebug`: BUILD SUCCESSFUL. JUnit
XML totals: **221 tests, 0 failures, 0 errors, 0 skipped**, including 38 real Room
repository cases across APIs 26/35. Debug APK:
`app/build/outputs/apk/debug/app-debug.apk`. Lint: **0 errors, 6 warnings** in
unchanged files (dependency update, two layout overdraws, two unused strings,
missing application icon). HTML report: `app/build/reports/lint-results-debug.html`.
`git diff --check`: PASS. Unicode regeneration with pinned checksum checks: PASS.

## Review follow-up verification

Final focused Quiz run: **30 tests, 0 failures, 0 errors, 0 skipped**.
Full `testDebugUnitTest assembleDebug lintDebug`: **BUILD SUCCESSFUL** (3m 19s).
Fresh JUnit XML totals: **227 tests, 0 failures, 0 errors, 0 skipped**.
This includes 2 JDK NFC cases and the same 2 cases on each of Android API 26/35
(4 Robolectric cases), plus all existing model and Room regression.
Lint remains 0 errors / 6 warnings in unchanged files.
`git diff --check` and checksum-verified Unicode regeneration: PASS.

APK after pinned NFC: **7,577,745 bytes**, compared with **7,545,779 bytes** for
the previously built QUIZ-02 APK using platform NFC: **31,966 bytes added**.
There is no new Gradle dependency, Android API requirement or runtime resource.
The approximately 5 MB of raw Unicode text is tooling/test input only.

Delivery PR: [#31](https://github.com/ManhT005/VocaMind/pull/31), targeting develop.
CI evidence is attached to the latest implementation head in
[PR checks](https://github.com/ManhT005/VocaMind/pull/31/checks). The earlier green
planning-commit run is not implementation evidence. CI reruns checksum-verified
Unicode generation, conformance, regression and APK assembly; inspect the latest
head's check conclusion before merging. Local evidence above is independent
of the remote check result.

Review follow-up disposition: alternative-sense options now have direct regression
assertions; the demonstrated platform NFC mismatch is removed by pinned production
normalization; implementation and validation are delivered together on the existing
PR. Assigned human sign-off remains separate from completion of these code changes.

## Delivery boundaries

No UI/layout, schema, Leitner, progress, ReviewEvent or QuizAttempt write changes.
QUIZ-03 owns display, feedback/debounce, exit confirmation and final persistence.
PR/CI delivery is recorded in the review follow-up section above.
Assigned reviewer sign-off and Nhan/Dao repository confirmation remain handoff steps;
this does not claim full AC-10 acceptance or mark external review complete.
