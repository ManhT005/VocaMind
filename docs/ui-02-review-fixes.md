# UI-02 — Review corrections after ed81bf4

Scope: R1 network cleanup, R2 fail-closed offline evidence, and evidence-driven investigation of the two Discovery CI failures. No production Java, catalog, search semantics/debounce, acceptance assertion, or release gate was changed. This document supersedes the initial local-only CI status in `ui-02-local-validation.md`.

## Observed CI at reviewed head

Exact head: `ed81bf4289659281efd021b1c887362334f77599`, [run 42579456168](https://github.com/ManhT005/VocaMind/actions/runs/42579456168).

| Job | Actual outcome and evidence |
| --- | --- |
| Build & Test | SUCCESS on Linux; keep separate from the earlier full Windows host failures |
| Quiz API28 | SUCCESS; job and required checks remain unchanged |
| Discovery API26 | FAILURE before instrumentation: job log traceback at runner line68, `Could not establish offline state`. Artifact13656179163 has environment/APK install/source manifests but no offline.json or test results. Old runner did not retain the failing flags, so the exact unsatisfied flag/timing is unknown |
| Discovery API35 | Ten independent cases and both suites succeeded (each suite10 pass/1 skip). The intentional probe returned exactly `UI02_EXPECTED_ARTIFACT_PROBE`, but its retrieved archive contained only a15239-byte screenshot; no in-app logcat/metadata. External log was `adb: device offline`, external screen empty. Probe correctly rejected, job FAILURE. This is not attributed to R1/R2 |

Downloaded original metadata, raw logs and ZIPs are retained locally in `build/ui02-review/ci-ed81bf4/` with their original run/job/artifact IDs. API35 data cannot distinguish an app-side collection exception from interrupted transport; neither is claimed as a proven root cause. The corrections below preserve per-stage and per-transfer evidence to remove that ambiguity on the next execution.

## R1 — Restore on every normal exit path

- `ui02_network.py` owns a narrow context manager. It snapshots settings/effective Wi-Fi read-only before any mutation; all network mutations, offline verification, evidence writes, test execution and early returns occur inside its try/finally.
- Shell return codes are checked; timeouts propagate through cleanup. Cleanup attempts every radio/setting even if one command fails, then verifies original raw values and effective Wi-Fi/data policy before reporting success in `network-restore.json`.
- Wi-Fi0/1/2/3 are supported with independent `dumpsys wifi` state. Wi-Fi2 is restored enabled and its exact flag restored; absent keys are restored using `settings delete`, not by writing the string null. An absent data policy is accepted only with one unambiguous `mUserDataEnabled` observation; unknown/contradictory initial state fails before mutation.
- A cleanup failure is surfaced. When there was already a primary failure, it is retained and the cleanup failure is attached as a note rather than replacing it.

## R2 — Actual offline gate

- Radio flags must be airplane1/Wi-Fi0/mobile-data0; effective Wi-Fi must be disabled.
- `dumpsys connectivity` must contain exactly one `Active default network: none`. Active, missing, duplicated or ambiguous evidence is rejected even when flags look offline.
- Conditional polling uses a deadline; no test starts until the conjunction holds. Raw connectivity/Wi-Fi dumps plus observations are stored before each instrumentation group. No Ethernet/VPN is silently disabled to manufacture a passing gate.
- `DiscoveryHarness` checks all three flags and the same unique default-network condition before each Activity launch, including reopen. This runs in the instrumentation harness, not application production code.

## G1 — Failure evidence stays mandatory

- Each app-side artifact stage records its own error; metadata is written even if screenshot/logcat collection fails. It records the pre-teardown phase, network dump and artifact errors. The descriptor has one owning AutoCloseInputStream; it is not also registered as a second resource to close.
- `ui02_artifacts.py` rejects incomplete TAR streams and screenshot-only evidence. Read-only transfer retries have a deadline; every attempt is retained in an isolated directory, so partial archives cannot be combined into a false pass. Output directories must be new/empty to reject stale evidence. Instrumentation itself is never rerun by this mechanism.
- Acceptance still requires the exact expected failure marker, exactly one failure, complete PNG, real threadtime logcat, matching test/revision metadata and no collection errors. Missing/invalid evidence still fails. The default suite still skips the opt-in probe; it is not counted as an ordinary pass.

## Regression and local evidence

- `python -X utf8 -m unittest discover -s tools/validation -p 'test_ui02*.py' -v`:25 host-only fake-ADB tests PASS. Includes real runner-main control flow with Wi-Fi2, setup timeout, active-network rejection, settings0/1/2/3/null, evidence-write failure, primary+restore failure, contradictory flags and incomplete/offline artifact transfers. These are not Android integration passes.
- Focused Java host tests: SearchViewModelTest + SearchVocabularyUseCaseTest =28 PASS,0 failures/errors/skips, JDK17 with TEMP `C:\tvm`. XML/logs under `build/ui02-review/focused-host-*`.
- Discovery app/test APK build and lint PASS. QA app/test APK build and lint PASS; logs and exit files under `build/ui02-review/`.
- Local Android API30: ten cases separately, two complete selected suites (10 pass/1 opt-in skip each), expected-failure probe accepted with full artifacts. Network restore is verified against the captured original state. Evidence is `build/ui02-review/local-api30/`; this is the exact dirty source manifest in that folder, not a claim of API26/35 execution.
- QA profile repeat evidence is `build/ui02-review/qa-independent/`, `qa-repeat-1/`, `qa-repeat-2/`. Internal QA artifacts remain local and are not added to public CI upload paths.
- CI runs the runner regression tests in a separately labelled **host only** step before each Discovery APK build. Existing Build & Test/Quiz checks are preserved.

## Observed rerun at a6b907f

[Run 42584868935](https://github.com/ManhT005/VocaMind/actions/runs/42584868935), exact head `a6b907f24e6a39852e90e17d4e836d3da565f371`:

- Build & Test and Quiz: SUCCESS.
- Discovery API35: SUCCESS. Downloaded artifact13658822294 confirms all ten independent methods, both suites10 pass/1 skip, expected-failure probe with full verified artifacts, and network restoration accepted with original/observed flags0/1/1. This is Android evidence, not inferred from a green build.
- Discovery API26: FAILURE before instrumentation. New checked execution exposes `svc wifi disable` exit137 with empty stdout; cleanup also records `svc wifi enable` exit137. Final raw/effective state matches the original0/1/1, but failed cleanup commands still reject the run. No failure is silently ignored. Raw logs and artifact13659555575 are in `build/ui02-review/ci-fixed/`.
- The cause of exit137 is not established by those artifacts. The next revision adds best-effort logcat/kernel/memory/process diagnostics **after restoration attempts**, preserves the primary exception and command return codes, and tests that ordering. It does not replace or ignore the failing command to manufacture offline evidence.

## New-head CI verification

The corrected feature commit must run the full declared API26/API35 acceptance sequence. No new-head success is assumed from local API30, mock tests, or the reviewed head's Linux host success. Raw new-run metadata/jobs/artifacts are collected into `build/ui02-review/ci-fixed/` and the follow-up handoff records the exact tested commit, run ID, counts, pre-launch dumps and verified restore/probe evidence. Until those executions are observed, this CI gate remains open.

Manual QA, AC-05 reference-device benchmark and content/release gates remain separate. No PR, approval, merge, SRS/assignment update or task-completion claim is part of these corrections.
