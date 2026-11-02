# ATTR-01 — Offline attribution validation

Technical implementation is ready for review. AC-12 and release remain blocked
by source/snapshot approval and the synthetic catalog. This document does not
change assignment status, approve licenses, or authorize merging/releasing.

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
