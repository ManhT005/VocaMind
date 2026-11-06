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
Different lemmas can still be synonymous; content QA identifies restrictions and
QUIZ-02 enforces the explicit corpus policy described below. QUIZ-03 must display
POS/context. Assigned reviewer approval remains outstanding.

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
identifying new synonyms across different lemmas remains a content responsibility;
the generator must also enforce the already accepted stable-ID restrictions.

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

## Q1 corpus policy integration — 2026-11-06

Before this correction, fetched/pulled origin and merged develop `46d6c18`
(PACK-02) into `feature/quiz-02-generation`; integration merge `9aa3f88` was clean.
The original review target remains `b3c6503`; validation below concerns the local
correction on top of that integration, not the original head's CI result.

`QuizRepository.activeSnapshot()` now supplies immutable active vocabulary and
an explicit `QuizDistractorPolicy`. `GenerateQuiz(repository)` consumes this
snapshot on every invocation. The Room constructor requires a policy loader;
the real `RepositoryProvider` validates the required `database/quiz-policy.json`
against all vocabulary in the installed catalog before returning the snapshot.
Missing/malformed policy, unknown pair references and projection mismatches map
to `RepoError.INVALID_DATA`, including when there are no active packs. There is
no permissive missing-file fallback and no policy IO in the domain.

The catalog tooling exports a companion bound to both the source canonical
dataset SHA-256 and the runtime vocabulary projection. Export verifies the
canonical digest; runtime recomputes the projection digest because Room omits
editorial provenance. The projection includes ID/lemma/POS/meaning/context for
all catalog rows, not just currently active words. See
[catalog policy contract](../tools/catalog/README.md#quiz-policy-companion).

The bundled catalog remains the original four-word synthetic fixture, with an
explicit empty restriction list. Its book noun/verb pair still makes generation
fail the existing same-lemma rule when all four words are active; this correction
does not weaken that rule or replace catalog content. The accepted 150-word
corpus and its three pairs are exercised directly and through real Room tests.
When real catalog packaging is integrated, export its companion from the same
validated handoff; a companion from another catalog will be rejected.

For each sampled target the generator first excludes all alternative-sense keys
of its lemma, then filters forbidden entry IDs from every other bucket. Only
nonempty safe buckets participate in sampling, and representative entries are
chosen from their filtered contents. This handles a bucket containing both a
forbidden entry and an allowed entry with the same meaning. `createQuestion`
also checks forbidden target/option IDs before validating the question.
Fewer than three safe buckets yields `INVALID_INPUT` with
`AMBIGUOUS_DISTRACTORS: <stableId>` and no questions; N is never reduced.

New regressions cover original accepted pairs in both directions, three minimal
four-word pools and safe five-word pools over 60 seeds each, 1,000 seeds over the
full corpus (including seed 60), mixed safe/forbidden buckets, snapshot/policy
immutability, changed policy between invocations, real Room active-pack mapping,
no-write snapshots, invalid policy, and the default app composition on APIs
26/35. Python tests verify the checked-in companion files against their source
data and reject real content without policy or with a mismatched dataset.

### Q1 final local verification

Toolchain: Temurin 17.0.20.1+1, Gradle 8.9, existing offline SDK/dependency cache.
`GRADLE_USER_HOME=C:/Users/thanh/.gradle`; `TEMP` and `TMP` use
`C:/vm-quiz02-tmp` to avoid Windows native SQLite path-length failures in the
longer PACK-02 regression names. An earlier run with workspace `build/tmp` hit
`SQLITE_CANTOPEN`; the same tests passed with the short TEMP directory, without
altering the database implementation or skipping cases.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.quiz.*' --tests 'com.vocamind.app.data.repository.RepositoryIntegrationTest' --tests 'com.vocamind.app.data.catalog.QuizPolicyCompositionTest' --offline --no-daemon --max-workers=2 --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --no-daemon --max-workers=2 --console=plain
python -m unittest discover -s tools/catalog -p 'test_*.py' -v
git diff --check
```

- Focused run: **90 tests, 0 failures, 0 errors, 0 skipped**.
- Final full run: **261 tests, 0 failures, 0 errors, 0 skipped**, including the
  integrated PACK-02 tests, Unicode conformance and Room regressions.
- Accepted full-corpus regression: 1,000 seeds on each of Robolectric APIs 26
  and 35; no forbidden pairs. The unrestricted seed-60 control reproduces the
  original finding, demonstrating the fixture still exposes the defect.
- Catalog Python suite: **8 tests, all passed**, including three new policy
  exporter tests. Both committed companions match their canonical inputs.
- Debug APK and instrumentation APK: **built successfully**.
- Lint: **0 errors / 6 existing warnings** (dependency version, two overdraws,
  two unused strings and missing application icon).
- `git diff --check`: **PASS**.

Final Gradle command: **BUILD SUCCESSFUL in 4m 9s**. Local logs are
`build/quiz02-focused.log`, `build/quiz02-final.log`, and
`build/quiz02-python.log`; fresh JUnit XML and lint reports are under
`app/build/test-results/testDebugUnitTest` and `app/build/reports`.
These results were collected locally before publication. Remote CI on the
correction, physical-device execution and assigned reviewer approval are not
claimed by this record.
