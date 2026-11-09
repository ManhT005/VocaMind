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

- `python -X utf8 -m unittest discover -s tools/validation -p 'test_ui02*.py' -v`:33 host-only fake-ADB tests PASS. Includes real runner-main control flow with Wi-Fi2, setup timeout, active-network rejection, settings0/1/2/3/null, evidence-write failure, primary+restore failure, contradictory flags and incomplete/offline artifact transfers. These are not Android integration passes.
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

## API26 root cause established at 5c52014

[Run 42586597874](https://github.com/ManhT005/VocaMind/actions/runs/42586597874), exact head `5c5201490e1b730416d0d4b24a495c49d30c1350`, artifact13659662015:

- `network-failure-logcat.txt` contains `SecurityException: WifiService: Neither user 2000 nor current process has android.permission.CHANGE_WIFI_STATE` from `WifiCommand.run`, for both disable and enable. This explains the observed service-command failure; it is not attributed to R2 or to app search code.
- `network-failure-identity.txt` confirms UID2000(shell). The image properties confirm API26/AOSP userdebug, `ro.kernel.qemu=1`, `ro.debuggable=1`. Memory diagnostics and failed kernel-dump permissions are retained rather than discarded.
- The workflow now opts into `--ci-emulator-api` with its matrix value. `ui02_ci_device.py` checks emulator/debuggable/exact SDK before any root request. Only API26 requests `adb root`, uses a bounded ADB transport wait, and requires verified UID0. API35 keeps its original UID. Wrong SDK, non-debuggable/physical devices, failed root, root output without UID0, and wait timeout are rejected and tested.
- Normal runner invocations never request root. This is setup for a disposable CI image; no application permission/manifest or production source is changed. Network snapshot/setup/restore still occurs through the checked context after bootstrap, and the actual `Active default network: none` gate remains mandatory.
- Host regression now totals33 PASS (25 network/runner/artifact cases plus8 CI-bootstrap cases). A real API30 Wi-Fi2 restoration probe also passed: the inner driver restored flag2 with effective Wi-Fi enabled, then its outer fixture restored the original flag0/disabled state. Raw evidence: `build/ui02-review/live-wifi2-inner-v2/` and `live-wifi2-outer-v2/verification.json`. An earlier priming attempt encountered the normal enabling transition, was rejected, and its outer cleanup restored the original state; it is not counted as a pass.

## API26 navigation fixture correction at 2e206e7

[Run 42588361670](https://github.com/ManhT005/VocaMind/actions/runs/42588361670), exact head `2e206e7931e79959c7a1fb3e199b23e660575e19`, artifact13660905170:

- Bootstrap accepted UID2000?0; offline setup and final restoration accepted with no command errors. The Wi-Fi permission failure is resolved in this run.
- Nine independent cases passed; `backWhileRunningAndRecreateKeepsCurrentQueryAndDropsAbandonedRead` failed at the unchanged15s `home after back` assertion. The screenshot shows the bank query still loading in SearchView, with no IME visible. Both suites/probe were not reached in that API26 run. Build & Test, Quiz and API35 were SUCCESS.
- The test now sends the visible editor's IME Submit action first, invoking the existing production `onQueryTextSubmit` listener's `clearFocus`. It then asserts loading remains visible, request count remains1 and the held future remains incomplete, before issuing Back and retaining the Home assertion. It does not add an unconditional second Back, increase a timeout, call NavController/ViewModel, or alter the app's query/debounce semantics.
- Updated Discovery app/test APK build and lint passed; the affected method passed independently on API30 (`build/ui02-review/local-back-focus/`). The corrected method must still pass within the full API26/API35 sequences on the next commit.

## New-head CI verification

The corrected feature commit must run the full declared API26/API35 acceptance sequence. No new-head success is assumed from local API30, mock tests, or the reviewed head's Linux host success. Raw new-run metadata/jobs/artifacts are collected into `build/ui02-review/ci-fixed/` and the follow-up handoff records the exact tested commit, run ID, counts, pre-launch dumps and verified restore/probe evidence. Until those executions are observed, this CI gate remains open.

Manual QA, AC-05 reference-device benchmark and content/release gates remain separate. No PR, approval, merge, SRS/assignment update or task-completion claim is part of these corrections.
