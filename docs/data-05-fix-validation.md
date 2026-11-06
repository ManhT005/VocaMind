# DATA-05 — PR #23 integration fix (06/11/2026)

PR: [#23](https://github.com/ManhT005/VocaMind/pull/23), branch
`feature/data-05-fts4-search`. Implements the DATA-05 Fix Bug Report dated
05/11/2026. This is Android search DATA-05, distinct from SRS §7.3 asset validation.

## Changes and preserved contracts

Merged `develop` **45db6049b3d8885bfab5570e4e70e03b0cf04511** into the original
search head **20826782df28258bf4cc46929202bf40eae7d0c3**. Actual conflicts were
`CatalogDao.java` and `docs/README.md`. The resolution retains:

- `readWordCount` plus the validated `wordCount` wrapper, batch hydration,
  memberships, unique pack candidates and all other DATA-03 DAO methods.
- Production `searchVocabulary` and legacy `matchFixture`, both joining FTS4
  INTEGER rowid to `VocabularyEntry.rowid`; application consumers get TEXT stableId.
- DATA-04 repositories, PACK-01/02 implementation, current Gradle/Robolectric
  configuration and every existing CI step from develop. CI additionally runs
  `lintDebug` and uploads lint reports.

Added `searchIdsComposeWithFoundationBatchAndMembershipQueries` to the shared
functional contract. On API26/35 it checks that two `book` senses hydrate through
the batch API, survive duplicated input, retain memberships across four packs,
and coexist with legacy MATCH and validated word counts. The same contract is
compiled into instrumentation tests for subsequent device execution.

No schema, tokenizer, product asset, result projection or result limit change.
`SearchProvider` still borrows the application-owned catalog and owns only its
worker; UI receives the domain use case, not a DAO.

## Verification

Evidence: [fix-20261106](evidence/data-05/fix-20261106/).
Current Android execution results are recorded in `verification.json` alongside
the build log, JUnit summaries, raw host benchmark samples and APK hashes.

Local merged-input run: **287/287 Android/JVM tests passed**, no failures, errors
or skips. This includes 18 FTS functional runs, 6 domain search tests, 2 host
benchmark runs, 32 DAO foundation tests and 46 repository integration tests.
App and instrumentation APK assembly passed. Lint passed with **0 errors and
6 warnings**; the full report is retained. APK inspection confirms the 5,000-word
fixture and metadata are only in the instrumentation APK; the application APK
contains the unchanged product catalog.

Environment: full JDK17 (Temurin 17.0.21+9), checksum-verified Gradle 8.9, compile
SDK35, AGP-selected build-tools 34.0.0, local Python 3.12.14. The initial attempt
stopped because the environment only had a JRE without `jlink`; the successful
rerun used a full JDK, with no project configuration workaround. The
`tested-inputs.json` digest ties results to 318 tracked source/config/fixture files;
documentation/evidence additions do not alter those tested inputs.

Both new host benchmark distributions are retained with latency assertions off
and `referenceHardwareVerified=false`. Even when host samples fall below 50 ms,
they do not certify device performance or establish an improvement over another
host/environment. Original branch outlier samples remain unchanged.

Executed all Python run steps from the merged CI workflow through pinned Unicode
verification: **159 unittest tests passed** (112 vocabulary, 8 catalog, 39
contract), plus the ingestion/QC/corpus reproducibility, expected release-rejection,
accepted editorial handoff and credit checks. See
[python-results.json](evidence/data-05/fix-20261106/python-results.json).

Regenerated the benchmark fixture twice; both runs reproduced SHA-256
`5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`.
No fixture, schema or product asset diff resulted.

Host SQLite structural triage of the five workloads confirms 1/1/100/0/5000 hits,
FTS virtual-index access, rowid primary-key lookups and a temporary B-tree for
ordering. See [query-plan.json](evidence/data-05/fix-20261106/query-plan.json).
This does not establish why Android profiling has outliers. No speculative
optimization or truncation of valid matches was introduced.

## Acceptance gates and handoff

| Plan item | Outcome |
| --- | --- |
| D05-01 develop integration | Conflicts resolved; DAO APIs preserved; combined regression added |
| D05-05 merged regression | Local build/test evidence in `verification.json`; GitHub CI must be checked on the pushed head |
| D05-02 reference-device benchmark | **BLOCKED:** no connected device; no verified Helio G85 / 3 GB / Android 10 inventory or approved substitute |
| D05-03 performance investigation | Host query-plan triage recorded; optimization awaits comparable device baseline; NFR remains open |
| D05-04 SEARCH-01/02 integration | **PENDING:** UI/ViewModel implementation and E2E evidence are a separate handoff |
| D05-06 production corpus | Synthetic benchmark remains test-only; does not certify production vocabulary quotas/content |

Reference-device owner must record model/chipset/RAM/API/build/conditions, fixture
hash, exact tested commit, full test log and all raw samples. Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.FtsSearchInstrumentedTest,com.vocamind.app.data.catalog.FtsSearchBenchmarkInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.data05EnforceLatency=true
```

Keep 20 warmups, 100 samples and all five workloads. Any max >=50 ms fails the
mandatory gate; report the <15 ms target separately. The flag alone does not
verify hardware. Preserve outliers and inventory rather than relabel host data.

SEARCH-01/02 must own the 600 ms debounce and request-generation guard; test rapid
`bank` → `book` → blank with completions deliberately returned out of order.
Render Loading/Empty/NoMatch/Error/Recover distinctly, discard stale callbacks,
avoid `.get()`/`.join()` on the main thread, and open detail using stableId TEXT.
Also verify offline navigation, back and recreation.

**AC-05 / NFR-VM-03 remain HOLD** until approved device latency and UI evidence
exist. Code integration, local test success and CI success do not close those gates.
No reviewer approval or production acceptance has been recorded by this fix.
