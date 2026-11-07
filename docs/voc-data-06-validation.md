# VOC-DATA-06 — Catalog candidate validation

For the current installed-app QA profile, see [VOC-DATA-06 QA validation](voc-data-06-qa-validation.md). This report records the original candidate-only baseline.

Date: 2026-11-07. Owner implementation: Thi; intended reviewer: Nhân.

Base: `8209ef75bc882ca10367abd40900e44e537571cb` (`origin/develop`, fetched before
creating `feature/voc-data-06-release-catalog`). See the
[implementation plan](plans/voc-data-06-release-catalog.md).

## Result and scope

The accepted editorial snapshot now builds into an independent, verified SQLite
candidate with 150 distinct vocabulary IDs, 150 memberships and pack counts
40/40/40/30. All accepted wording, IDs, sense tuples, source provenance and Quiz
restrictions remain unchanged. The builder uses the existing Room export; no
schema or user-data implementation changes were made.

Candidate builds are in `build/catalog-release/voc-data-06-a` and
`build/catalog-release/voc-data-06-b`. Their complete six-file bundles are byte
identical on the recorded SQLite engine. The candidate is also generated
separately for test resources/test APK assets. It is absent from the application
APK. Packaged main assets still contain the original four-word synthetic fixture.

**Release remains BLOCKED.** Source acceptance is for project use, represented as
`PROJECT-USE-OWNER-ACCEPTED`; it is not a public distribution license. The dossier
identifies an approver but no verified author list. Credits preserve the accepted
notice and explicitly use `authors=[]`. Technical checks do not approve rights,
physical reference-device performance, new-catalog UI acceptance or DATA-08
catalog upgrade/rollback with userdata preservation.

Remote CI and peer review remain pending until this branch is pushed/reviewed.
This task does not publish a release, open a PR or update the assignment workbook.
Evidence logs normalize local paths, hostnames and trailing whitespace; candidate
metadata/manifests retain their original bytes.

## Input and artifact identity

| Item | Verified value |
| --- | --- |
| Accepted input | `docs/data/voc-data-04/accepted-v1` |
| Canonical dataset SHA-256 | `c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8` |
| Editorial staging SHA-256 | `b588844e1f1228acaeed139efe08a5d9d53dcb5a9da8e4f056a23bea3ed959c9` |
| Room export | `app/schemas/com.vocamind.app.data.catalog.CatalogDatabase/2.json` |
| Room schema version / identity | `2` / `6704c4cc4f3db1ef916af009d7de4b98` |
| Quiz exclusions | research/study, home/house, customer/client; symmetric, across packs |

[Metadata](evidence/voc-data-06/20261107-local/catalog.metadata.json) records the
actual DB checksum, SQLite version, logical SQL digest and every accepted input
hash. The [manifest](evidence/voc-data-06/20261107-local/manifest.json) refers to
the staged candidate bundle; the evidence directory contains reports/companions,
not a distributable database bundle.

## Implementation and failure gates

`tools/catalog/build_release.py` requires the verified production handoff,
release contract, reproduced frozen acceptance and matching QC review/config
bindings. It creates tables, indices, FTS4 and Room setup queries from compiler
export, inserts deterministically in a transaction and verifies the actual SQL
projection. Both build and subsequent `--verify-dir` compare database rows with
the accepted canonical data.

EN/VI phrase queries cover every vocabulary entry (300 queries). SQL checks
separately measure distinct vocabulary IDs and memberships, distinct meanings per
pack, complete example pairs, nullable IPA, integer FTS rowid to TEXT stableId
mapping, integrity and foreign keys. No host timing is used as an NFR acceptance.

The output directory must be new and below `build/catalog-release/`. All six
files are staged and verified before one directory rename. The manifest is the
last file written. Input, SQLite, write or rename failures cannot leave a
successful candidate or overwrite an existing directory/main asset.

Tests reject missing/tampered/fixture/pending QC, stale/missing Quiz policy,
source hold/pending review/revoked acceptance, 149 words, missing packs, blank
meaning/example, duplicate stableId/order, rewritten accepted wording and
invented license metadata. Rehashed companion/report/FTS corruption also fails.
The fixture builder and its original safety guard remain intact.

Gradle generates the test-only candidate from fresh ingestion and QC. Host and
instrumented tests share `ReleaseCatalogContract`, open a dedicated test DB
through Room, and exercise the real repository, FTS, credit and Quiz readers.
CI now builds two candidates, compares them, verifies the bundle, checks main
asset/schema changes and retains candidate artifacts.

## Executed checks

| Check | Result | Evidence |
| --- | --- | --- |
| Fresh production ingestion/QC | PASS: 150 accepted, 0 rejected/withheld | [QC report](evidence/voc-data-06/20261107-local/qc-report.json) |
| Python vocabulary regression | PASS: 112 tests | [Raw output](evidence/voc-data-06/20261107-local/vocabulary-tests.txt) |
| Canonical contract regression | PASS: 39 tests | [Raw output](evidence/voc-data-06/20261107-local/contract-tests.txt) |
| Catalog fixture/candidate/negative gates | PASS: 21 tests | [Raw output](evidence/voc-data-06/20261107-local/catalog-tests.txt) |
| Independent candidate rebuilds | PASS: all six files byte identical | [Comparison](evidence/voc-data-06/20261107-local/reproducibility.json) |
| Host Android regression | PASS: 390 tests, including 10 RC cases on API 26/35 | [Totals](evidence/voc-data-06/20261107-local/host-results.json), [RC JUnit](evidence/voc-data-06/20261107-local/TEST-com.vocamind.app.data.catalog.ReleaseCatalogTest.xml) |
| Debug app/test APK assembly, lint | PASS: lint 0 errors, 6 warnings | [Gradle output](evidence/voc-data-06/20261107-local/gradle-checks.txt), [lint XML](evidence/voc-data-06/20261107-local/lint-results-debug.xml) |
| Offline emulator integration | PASS: 71 grouped tests; isolated first-launch rerun 1/1 | [Device results](evidence/voc-data-06/20261107-local/device-results.json), [isolated JUnit](evidence/voc-data-06/20261107-local/device-isolated-first-launch.xml) |
| Packaged asset preservation | PASS: four source assets equal base and APK copies; candidate absent from app APK | [Checksums](evidence/voc-data-06/20261107-local/packaged-assets.json), [APK inspection](evidence/voc-data-06/20261107-local/apk-assets.json) |
| Remote CI / Nhân review | PENDING | Workflow configured; no remote run/review claimed |
| Rights, author ledger, release promotion | BLOCKED | [Release gate report](evidence/voc-data-06/20261107-local/release-gate-report.json) |
| Physical reference-device timing and DATA-08 upgrade/rollback | NOT RUN / BLOCKED for release | Emulator integration does not substitute for these gates |

The first local host run had three existing file-backed SQLite failures using
the long Windows temp path. All 390 host tests subsequently passed with
`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\VocaMind\.tools\tmp`; no production code or
test assertion was changed for this workaround. Local Java is JDK 22.0.2;
the configured CI toolchain remains JDK 17. Exact Python/SQLite/dependency and
device versions are recorded in [environment](evidence/voc-data-06/20261107-local/environment.json).

The initial unfiltered instrumented run executed 77 cases: 76 passed, one
pre-existing PACK-02 first-launch test failed at line 88 because earlier PACK-02
cases had activated a pack in the shared test installation. That test explicitly
requires a fresh installation and is independent of the candidate test DB.
Its failure is retained in the [initial JUnit](evidence/voc-data-06/20261107-local/device-initial-full-suite.xml).
The five new RC cases passed in that run. The final grouped run passed all 71
cases outside PACK-02, including the five RC cases. The first-launch scenario
then passed 1/1 on a fresh test installation; the other five PACK-02 cases had
already passed in the initial run. Gradle had uninstalled the test APKs, so the
isolated retry used a new installation.
See [qualified run](evidence/voc-data-06/20261107-local/gradle-checks.txt) and
[isolated first-launch run](evidence/voc-data-06/20261107-local/device-isolated-first-launch.txt).
No user device or installed production userdata was cleared.

| Execution-plan cases | Qualification |
| --- | --- |
| VD06-01..05 | PASS: production QC, distinct IDs/memberships, examples and per-pack meanings |
| VD06-06 | PASS for NULL IPA through SQL/Room; existing fixture UI placeholder regression passed; candidate UI release smoke pending |
| VD06-07..11 | PASS: Room, integrity, EN/VI FTS, stable membership/order and three symmetric exclusions |
| VD06-12..17 | PASS: negative policy/source/QC gates, canonical mutations, rebuilds and exact source/snapshot credits; author completeness remains blocked |
| VD06-18 | PASS for offline candidate Room/repository/FTS/policy and existing fixture UI; full candidate APK UI acceptance pending promotion |
| VD06-19 | PASS: Python, host and qualified instrumented regressions; initial shared-install failure retained above |
| VD06-20 | Benchmark executed on emulator without latency enforcement; physical reference-device acceptance BLOCKED |
| VD06-21 | NOT RUN: catalog upgrade/rollback is DATA-08, a release blocker |

## Reproduction

Install `requirements-validation.txt` into the selected Python environment.
Use a fresh suffix for every ingestion/QC/build output, and stop after any
nonzero native exit code in PowerShell.

```powershell
python tools/vocabulary/ingest.py --config docs/data/voc-data-04/accepted-v1/ingestion-config.json --output build/vocabulary-ingestion/rc-02
python tools/vocabulary/quality.py --input build/vocabulary-ingestion/rc-02 --config docs/data/voc-data-04/accepted-v1/ingestion-config.json --reviews docs/data/voc-data-04/accepted-v1/reviews.json --output build/vocabulary-quality/rc-02
python tools/vocabulary/accept_editorial.py --verify build/vocabulary-quality/rc-02
python tools/catalog/build_release.py --qc-dir build/vocabulary-quality/rc-02 --output-dir build/catalog-release/rc-02
python tools/catalog/build_release.py --qc-dir build/vocabulary-quality/rc-02 --verify-dir build/catalog-release/rc-02

python -m unittest discover -s tools/vocabulary -p 'test_*.py' -v
python -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v
python -m unittest discover -s tools/catalog -p 'test_*.py' -v

.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.ReleaseCatalogInstrumentedTest'
```

Local runs selected `.tools/voc-data-06/Scripts/python.exe` with
`-PcatalogPython=D:/VocaMind/.tools/voc-data-06/Scripts/python.exe`.
Emulator Wi-Fi/data were disabled and airplane mode was enabled before testing.
The 5,000-word benchmark ran without enforcing a physical-device latency gate;
it does not establish the SRS reference-device threshold.
