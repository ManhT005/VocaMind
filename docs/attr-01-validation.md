# ATTR-01 — Offline attribution validation

Current review status: **R1 and R2 implemented and tested; user authorized push
and update of PR #15 after preview; Nhân/Thành review pending**. The user authorized implementation of the
companion proposal on 2026-11-02. This is not team or source approval. The app
supports offline authors/credit; the bundled fixture still lacks verified authors.
AC-12 and release remain BLOCKED by missing metadata, source/snapshot approval
and the synthetic catalog.
This document does not change assignment status, approve licenses or authorize
merging/releasing. The original baseline results below remain historical evidence
for `4271ed7`; new review results are recorded separately.

## R2 implementation and preview, 2026-11-02

Tested code commit: `da6881fcd42f36f030251e933af90bf1da22d598`. R1 commits
remain included. Working tree was clean before implementation; remote PR still
pointed at `0955c9b`, with two local commits ahead before R2 work. The user's
instruction to implement the proposal is recorded as authorization; no statement
that Nhân/Thành has already approved it is made.

[Contract v1](data/content-credits-contract.md): ContentCredits parses the local
versioned companion asset on the worker and joins exact source/snapshot pairs
inside the existing repository, for both query paths. Immutable domain authors
and optional notice reach the existing use case/ViewModel and wrapping UI fields.
Missing metadata stays visibly missing; malformed/read errors remain retryable.
No Room schema, catalog.db, ledger, SRS, assignment or userdata code changed.

The production companion contains only the actual `project_fixture` pair with
an empty authors array and null notice. No authors were guessed. Synthetic
authors and notices appear only in tests, never as release-approved material.

| New checks executed | Result / evidence |
| --- | --- |
| `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain` | PASS: **96 host executions**, no failures/errors/skips; debug and test APK built. [Log](evidence/attr-01-review-r2/host-final.txt), [suite counts](evidence/attr-01-review-r2/unit-results.json). |
| Lint | PASS, 0 errors, same 2 existing warnings. [XML](evidence/attr-01-review-r2/lint-results.xml). |
| `gradlew.bat connectedDebugAndroidTest --console=plain` | PASS: **15 device tests**, Small_Phone API 30 (13 data, 1 rendering, 1 navigation/lifecycle). [Log](evidence/attr-01-review-r2/device-final.txt), [JUnit](evidence/attr-01-review-r2/instrumentation.xml). |
| Offline state before install/run | Airplane=1, Wi-Fi=0, data=0. [Device state](evidence/attr-01-review-r2/device.txt), [UI screenshot](evidence/attr-01-review-r2/screen.png). |
| Existing v1 DB compatibility | Shared host/device test copies catalog with legacy repository, closes/reopens it, enriches via both APIs and asserts database bytes identical after close. No schema upgrade/reset required. |
| Credit correctness/resilience | Exact-pair and multi-source parser tests; wrong source/date, duplicate pair, malformed fields/version; immutable authors. Host/device rendering test checks two independently expected synthetic author/notice values, including long text. Data test covers read-error/retry. Shared navigation test checks missing author transparency across recreation/orientation/Back. |
| `python tools/catalog/check_credits.py` | PASS for pair consistency, explicitly reports missing authors and no license approval. [Output](evidence/attr-01-review-r2/credits-check.txt). Added to CI. |
| `python -m unittest discover -s tools/catalog -p 'test_*.py' -v` | PASS: **2 tests** for validator, duplicate/unknown/stale pair and missing authors. [Output](evidence/attr-01-review-r2/python-tests.txt). Added to CI. |
| `python tools/catalog/check_credits.py --release` | Expected failure, exit 1: fixture lacks verified authors. [Output](evidence/attr-01-review-r2/release-blocked.txt). This is not a failed debug build and does not approve licensing. |

New debug APK SHA-256:
`370e05a19e48ece80ef145311deec60738a205ded900a9f4ce37b21fc21b6f6e`.
Companion SHA-256:
`5cc8429f101e1da80848418cc0ab1ac0cea04f78d85d67a8c41a60242c53161c`.
Catalog.db remains `e88f46f4a10e835d97ab03a87a534ca345321d7cf7738569f49524abda1d4104`.

Limits: API 26 device smoke NOT RUN (no installed image); API 26/35 host tests
are not device execution. No physical device, TalkBack/font scaling or real
approved multi-source catalog tested. Multi-author device UI uses a fake repository;
data enrichment is tested separately against a reopened isolated v1 DB. Source
approval, real credit metadata and >=150 eligible words remain outstanding.
Existing 46 vocabulary Python tests were not rerun because those inputs/code did
not change. GitHub CI for local R1/R2 commits has NOT RUN because user preview
is required before push. Prior evidence directories remain bound to prior SHAs.

Reviewed publication scope: `ExternalLinks` regression/fix, companion storage/domain/UI,
new validator/CI steps/tests, documentation/evidence. The user has now authorized
pushing this feature branch to existing PR #15 and requesting Thành review again.
GitHub CI results at the published head will be reported in the PR description;
the local evidence above stays bound to its tested code commit.
Do not merge or mark the task complete. **AC-12/release stays BLOCKED.**

## R1 review-fix record (before R2 authorization)

Read the entire supplied FIX_PLAN_ATTR01_PR15.md, including the replacement
conclusion in section 8. Fetched PR branch/develop and checked PR head through
GitHub: still `0955c9b`, with a clean working tree before edits.

R1 code commit: `c800ebd8192dd4a914788c0a63e3f82eb5f3c22d`.
`ExternalLinks.open()` now normalizes only the scheme before dispatch.
Regression `normalizesSchemeForBrowserFilterWithoutChangingCaseSensitiveComponents`
dispatches uppercase/mixed-case HTTP/HTTPS and lowercase HTTPS, matches a lowercase
browser IntentFilter and asserts preserved path/query/fragment. It **failed on old
code at filter matching** for `HTTPS://example.com/License?Key=AbC#Terms`, then
passed after the fix. Existing invalid URL and simulated handler failure tests
remain included. New evidence lives under `evidence/attr-01-review-r1/`; old
logs/screenshots under `evidence/attr-01/` are not reassigned to this commit.

R2 has no existing authors/credit field in storage, domain or mapping. The
[concrete contract proposal](plans/attr-01-fix-plan.md) recommends a local
versioned companion asset keyed by `(sourceId, snapshotDate)`, authors plus
optional creditText, enriched through the current repository. It specifies
existing-install compatibility, missing/mismatched metadata behavior and tests.
**Proposal only: not implemented or approved.** Await Nhân/Thành confirmation
and evidence-backed credit; do not infer authors or change DB/schema/assets.

Publication is pending the user's requested preview. No push, PR update or
re-review request has been made for these local changes. CI for the new local
head has therefore **NOT RUN**. Both R1 and R2 require reviewer reassessment.

R1 checks executed with Temurin JDK 17 and Gradle wrapper 8.9:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.feature.attribution.ExternalLinksTest.normalizesSchemeForBrowserFilterWithoutChangingCaseSensitiveComponents' --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.feature.attribution.*' --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

- The first command ran before the one-line production fix, using baseline
  `0955c9b` plus the new regression: expected **1 test failed** at filter match.
  See [red log](evidence/attr-01-review-r1/r1-before.txt) and
  [failure XML](evidence/attr-01-review-r1/r1-before.xml).
- After fixing R1, the filtered Attribution suite passed. The subsequent full
  run on `c800ebd` passed **86 host executions**, including 14 attribution
  executions on Robolectric API 26/35. See [suite counts](evidence/attr-01-review-r1/unit-results.json)
  and [build log](evidence/attr-01-review-r1/r1-full.txt).
- Debug APK and test APK built successfully. Lint succeeded with the same two
  existing warnings (dependency update, application icon) and no errors.
- Connected run on Small_Phone API 30 passed **12 tests** (11 catalog, one
  attribution/navigation/recreation) with airplane/Wi-Fi/mobile-data state
  `1/0/0` before the run. See [connected log](evidence/attr-01-review-r1/r1-device.txt),
  [JUnit XML](evidence/attr-01-review-r1/instrumentation-results.xml) and
  [device state](evidence/attr-01-review-r1/device.txt). This is a new run on
  `c800ebd`, distinct from the original PR evidence.
- Debug APK SHA-256:
  `901e23dc5b4467ea6af0242d85f288a940b9b445194eda78a7703b7df9e0d254`.
- API 26 **device** smoke: NOT RUN; installed system images are API 30, 37.0,
  37.2, and the only configured AVD is Small_Phone API 30. Robolectric API 26
  results are host tests, not device evidence. No new SDK image was installed.
- R2 authors/credit, mismatch and legacy-upgrade tests: NOT RUN, pending contract
  confirmation and implementation. New R1 browser filter checks run on the host;
  no new device test of uppercase URLs or online page loading is claimed.
- Python source governance tests were not rerun: no source governance, asset,
  schema or Python code changed. Prior results remain bound to the original run.

## Baseline and requirements

- Date: 2026-11-02; Windows, Temurin JDK 17, Gradle wrapper 8.9.
- Initial working tree was clean on `develop`; fetched `origin/develop` at
  `fff380127b0808b203165e649e22eaf714027afb`, then created
  `feature/attr-01-offline-attribution` from that ref.
- No `AGENTS.md` found in the repository or its ancestor directories. Read root
  README, docs index, Android foundation/setup, CI and catalog/source handoff docs.
- Read supplied `ATTR-01-yeu-cau-thuc-hien.md` (SHA-256
  `b7338bb59736054f62788987d25ea0d133081d989e1372b3a740d66f1e4bcb49`) and
  SRS v1.1 (`bc2d687bb1a70f28fe0e4566f77d07dc877d08269a6265c361a06fb816c0c7ec`).
  Traces: FR-VM-045/046, BR-12, UC-09, AC-12, NFR-VM-12, DEC-09.
- Tested implementation commit: `4271ed70fc1368d7b0185646e8da7188139e7019`.
  Subsequent evidence/documentation commit changes no app or test code.

## Implementation

Home exposes Attribution before pack activation or learning. Java/XML/ViewBinding
renders source title, source URL, license code, license URL and snapshot date from
`CatalogRepository.getSources()` through `GetContentSources`. Existing entity,
mapper, Room repository and schema are reused without catalog changes.

`AttributionViewModel` publishes loading/content/empty/error, supports retry,
deduplicates concurrent loads and retains results through rotation. The core
factory owns a serial executor and CatalogProvider; Room queries/asset opening
and ordered cleanup run on the worker. The Fragment never calls Room or a DAO.
LiveData observes the view lifecycle; no view binding is retained after destruction.

Scrollable wrapping text preserves long fields. Buttons use the existing theme
and minimum 48dp touch target. Missing values remain visible as missing information;
an empty catalog explicitly does not confirm copyright compliance. External links
accept valid http/https URLs only, reject credentials and malformed addresses, and
handle unavailable/restricted handlers with a toast while preserving the screen.
No network permission, network library, WebView, connectivity gate or metadata
fetch was added.

## Executed checks

PowerShell commands, with `JAVA_HOME=C:\Users\LMT\.jdks\vocamind-temurin-17`:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug connectedDebugAndroidTest --console=plain
python3.13 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json --json
python3.13 -m unittest discover -s tools/vocabulary -p 'test_*.py' -v
git diff --check
```

Python was invoked using its WindowsApps absolute executable path. Gradle/SDK,
Git mutations and Python required sandbox escalation; all were approved.

| Check | Observed result | Evidence |
| --- | --- | --- |
| Host Android tests | 84 executions, 0 failures/errors/skips; 12 attribution executions, including Robolectric API 26/35 | [Suite counts](evidence/attr-01/unit-results.json), local HTML `app/build/reports/tests/testDebugUnitTest/index.html` |
| Debug and instrumentation APK builds | Successful | [Final Gradle log](evidence/attr-01/gradle-final.txt) |
| Lint | Successful, 0 errors; 2 existing warnings: dependency update and missing application icon | [Lint XML](evidence/attr-01/lint-results-debug.xml) |
| Connected instrumentation | 12 tests, 0 failures/errors/skips (11 catalog + 1 Attribution integration) | [JUnit XML](evidence/attr-01/instrumentation-results.xml) |
| Vocabulary source audit | Successful research inventory check; 7 source records, **0 eligible**; not release approval | [Catalog reconciliation](evidence/attr-01/catalog-ledger-audit.json) and command output recorded during execution |
| Python CI tests | 46 passed | `python3.13 -m unittest discover -s tools/vocabulary -p 'test_*.py' -v` returned `Ran 46 tests ... OK` |
| Whitespace | `git diff --check` successful | Git command output |

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256
`fb3571b3dd56329823ccefccce68c094aec91b41fc5326b35b5da206356a944e`.
Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## Scenario evidence and limits

Device: `Small_Phone`, `emulator-5554`, Android 11 / API 30, 720×1280 at
320dpi (360dp width). Emulator started headlessly for this task. Before the
first installation/launch and both connected runs, airplane mode was enabled,
Wi-Fi and mobile data disabled (`1/0/0`); see [device state](evidence/attr-01/device.txt).
Final app remains installed on the emulator in this offline state.

| Scenario | Result and scope |
| --- | --- |
| ATTR-T01 | Real bundled DB fields verified by shared screen contract on device and host. Multiple distinct sources and all field rendering tested with injected fake repository on host; actual asset has one source. |
| ATTR-T02 | PASS on API 30: first launch offline, local catalog copied/read without network. Airplane icon visible in screenshots. |
| ATTR-T03 | PASS: Home entry with no pack activation or learning; isolated app install. |
| ATTR-T04 | Browser intent verified on host. On device the source button opened Chrome FirstRunActivity while offline; system Back returned to readable Attribution. [Browser tree](evidence/attr-01/browser.xml), [return tree](evidence/attr-01/browser-return.xml). Actual web page loading was not verified; Chrome onboarding/network behavior belongs to the external app. |
| ATTR-T05 | Host UI tests cover null/malformed URL and preserve source rows. Host Android tests simulate ActivityNotFoundException and SecurityException, check toast and valid browser intent. No-browser configuration was not exercised on a real device. |
| ATTR-T06 | Host ViewModel and rendering tests verify distinct loading/empty/error, retry recovery and duplicate retry suppression. |
| ATTR-T07 | Device test passes Activity recreation, orientation request, explicit Back and system Back. Portrait/landscape inspected visually; source/URL/license wrap and lower fields/buttons scroll into view. Extreme long title/URL and missing fields exercised on host; font scaling/TalkBack and physical-device layouts not verified. |
| ATTR-T08 | Use case/repository boundary, queued worker/main delivery, ViewModel cleanup and late-result suppression tested; production code has no network calls. |
| ATTR-T09 | BLOCKED for release; reconciliation below. |

Screenshots: [Portrait](evidence/attr-01/portrait.png),
[scrolled links and snapshot](evidence/attr-01/links.png),
[landscape](evidence/attr-01/landscape.png). Browser/landscape captures preceded
the final accessibility compatibility adjustment, which only moved heading
metadata from XML to ViewCompat; portrait/links were recaptured from the final APK.

## Catalog reconciliation and AC-12

[Read-only SQLite/APK audit](evidence/attr-01/catalog-ledger-audit.json) found:

| Packaged source / snapshot | Ledger comparison | Release condition |
| --- | --- | --- |
| `project_fixture` / `2026-10-25` | Not present as an approved source/snapshot in source-registry/ledger. `LicenseRef-Project-Test-Fixture` is a test label. | Synthetic integration fixture only; not approved release vocabulary. |

Catalog asset, APK asset and metadata match SHA-256
`e88f46f4a10e835d97ab03a87a534ca345321d7cf7738569f49524abda1d4104`.
There are four unique vocabulary entries. None of the researched external sources
is packaged. Registry NGSL/TSL/Kaikki/Tatoeba text/Commons remain hold; FVDP and
Tatoeba audio remain excluded. No approval, signature or legal status was created.

**AC-12 is not met.** Content/catalog owners (Thi/Nhân) and PO/Content Lead must
provide an approved per-snapshot ledger and evidence, replace the synthetic
catalog with eligible material and satisfy the required content count/QA gates
before release. Attribution UI and passing technical tests do not remove this
blocker. Existing ledger, source registry, catalog, SRS and assignment files
remain unchanged.

## Review handoff

`thanhcodeai` is a repository collaborator and PR #6 associates this GitHub
account with commit author **Quang Thành**; this identifies the assigned reviewer.
Request review when opening the feature PR into `develop`. Human review and CI
on GitHub remain separate gates. Do not self-merge or mark task complete.
