# UI-02 — Local implementation and review handoff

Status: local implementation and device evidence prepared; open acceptance gates below. **Not marked Complete**.

Publication follow-up: the user subsequently authorized commit and push of the feature branch. No PR or merge is authorized. The validation snapshot below describes the pre-commit baseline plus recorded dirty source digest; it is not relabelled as a device execution of the published commit. Only this handoff note changed after that snapshot. Manual QA has not been performed independently; visual inspection was limited to captured screenshots. Assignment and SRS remain unchanged.

## Baseline and dependency audit

- Clean working tree initially on develop. `git fetch origin develop` succeeded; local HEAD and origin/develop both `c3454afeedecbe53fa7aaed664707e5108d7a9ca`. Created `feature/ui-02-discovery-instrumented-tests` from that fetched ref; no existing local/remote branch with this name.
- No tracked AGENTS.md found; read root README, docs index, Android setup, task attachment and relevant SRS DOCX paragraphs (UC-02, FR-VM-005..014, AC-03/04/05, NFR-VM-13). SRS content was read directly from OOXML, not inferred from task labels.
- PACK-02: merge #29 `46d6c18`; `PacksViewModelFactory` composes GetPacks/GetPackWords/SetPackActive over RoomPackRepository; UserDao persists active state and original activatedAt.
- VOCAB-02: merge #32 `c874380`; VocabularyDetailFragment renders lemma/pos/meaningVi/exampleEn/exampleVi/IPA and uses GetVocabularyDetail/ToggleSaved over RoomVocabularyRepository. Pack route and catalog search route remain distinct.
- SEARCH-02: merge #48 `c3454af`, implementation `829e4a0`; SearchViewModel has 600ms debounce, generation guards, saved raw query, validation, retry/reset. VocabularyFragment renders all five statuses and clears adapters with view lifecycle. No dependency inferred from document names or assignment percentage.

## Requirement → existing coverage → added coverage

Aliases: **J** = `DiscoveryJourneyInstrumentedTest` (real UI → production VM → use case → repository → Room/FTS); **S** = `DiscoverySearchStateInstrumentedTest` (controlled future at existing VM search boundary, real ViewModel/debounce/rendering); **Q** = `DiscoveryQaInstrumentedTest` (real QA integration). Classes live in `app/src/androidTest/java/com/vocamind/app/feature/discovery/`.

| ID / SRS | Existing coverage and gap | Added method and actual assertions |
| --- | --- | --- |
| UI02-01 / UC-02, AC-03 | PackActivationInstrumentedTest.offlineFirstLaunchCtaActivateVocabularyBackPauseAndConfirmedRotation covers pack entry but mixes mutable install assumptions and manually created holders | J.offlinePackOrderVisibleItemsDetailsBothIpaAndNoWrites: offline launch; visible pack card; each attached list row at predefined order; detail stableId; no UserPack/progress creation; selected runtime catalog logical rows equal the separate prepack oracle, without warming app catalog startup before UI |
| UI02-02 / FR-VM-007/010 | Existing pack persistenceProbe depends on external driver/state; recreation alone does not prove Activity close/launch | J.activateCloseLaunchPauseCloseLaunchPreservesLearningAndSaved: touch activate; active UI and UserPack(packId); Activity close then fresh launch; active retained |
| UI02-03 / AC-03 | RepositoryIntegrationTest.pack02PauseSharedAndExclusiveDueAndNewWordsThenReopenPreservesUserdata already protects planner selection; reuse domain evidence | Same J method: touch pause; close/launch inactive; activatedAt and saved/box/due/learnedAt/lastReviewedAt/mistakes/reviewCount preserved; catalog unchanged. No full Today/planner expansion |
| UI02-04 / UC-02 | Existing pack CTA checks are useful but tied to first-launch/mixed test | J.allInactiveTodayCtaOpensDiscoveryWithoutActivation: independently reset dedicated userdata; visible Today and Explore taps; populated pack list; no UserPack or progress writes |
| UI02-05 / AC-04 | Existing SearchScreenContract and PackActivation test create/bind holders or navigate directly; retained as contract regression, not claimed as user journey | J.offlinePackOrderVisibleItemsDetailsBothIpaAndNoWrites: scroll actual list, touch visible row; assert route stableId/packId and all detail fields; visible Back to same pack/list count |
| UI02-06 / FR-VM-008, DEC-14 | Existing adapter placeholder assertions do not prove both detail rendering branches | Same J method: every fixture detail; explicit existence of non-null/null IPA; exact IPA or literal `Chưa có phiên âm`; visible EN/VI example text |
| UI02-07 / BR-02, AC-04 | RepositoryIntegrationTest.vocab02SaveUnsaveReopenOnlyChangesFlagAndCatalogIsReadOnly and vocab02FirstSaveReopenUnsaveAndBaselinePoolsAndCounts remain reused; old SearchScreenContract bypasses user item navigation | J.saveBackReopenCloseLaunchUnsavePreservesCatalogAndLearning: known unsaved reviewed row; touch Save; Back/reopen; Activity close/launch/reopen; touch Unsave; exact stableId progress flags; all review fields and actual runtime catalog snapshot preserved |
| UI02-08 / AC-05, FR-VM-011/014 | Existing SearchScreenContract verifies EN/VI and stableId through real FTS but uses created holder | J.offlineEnglishVietnameseRealFtsVisibleResultDetailResetNoMatch: BANK / NGÂN HÀNG offline; exact result count/meaning and clicked route stableId; duplicate book senses choose verb stableId. Q.qaPackVisibleWordAndEnglishVietnameseSearchOpenExactDetails checks QA EN/VI exact ordered IDs and visible result → detail |
| UI02-09 / SEARCH-02 | SearchViewModelTest.clearWhitespaceTokenlessInvalidateRunningAndPending and debounce600AndImmediateInvalidation reused for precise scheduling, no duplicate host tests | J search method: whitespace/reset shows input guidance and hides list/loading. S.whitespaceAndResetPendingNeverSubmitFts: spy request count zero for whitespace; input and visible Reset before real debounce; subsequent submitted query proves abandoned pending input never executes |
| UI02-10 / FR-VM-012 | Existing VM errorsAreDistinctFromNoMatchRetryUsesCurrentInput protects contract | J search method and S.loadingContentNoMatchAndAtoBtoARejectsOldSuccessAndError: unmatched query shows no-match message; previous list and retry hidden |
| UI02-11 / SEARCH-02 | SearchViewModelTest.aToBToAUsesGenerationNotQueryEquality and outOfOrderContentAndOldErrorCannotStopNewLoading retained | S.loadingContentNoMatchAndAtoBtoARejectsOldSuccessAndError: real loading visible for held requests; uncancellable A/B/A futures; late success/error rejected; current content/no-match and visible meaning asserted |
| UI02-12 / NFR-VM-10 | SearchReadRecoveryContract.realRoomReadFailureShowsErrorAndRetryRecoversCurrentQuery reuses its UUID-owned Room DB, never production DB; retained host/device contract | S.retryCurrentQueryErrorEditInvalidAndResetAreDistinct: read error message; no previous list; visible Retry resubmits exact current query; success; query edit; invalid input has no retry; visible Reset returns input guidance; spy counts prevent loops |
| UI02-13 / SEARCH-02 | SearchViewModelTest.disposedOwnerIgnoresLateCompletionAndCancelsDebounce and savedRawQueryRestoresAndNavigationUsesOpaqueId reused | S.resetRunningRejectsLateCompletionAndLateError; S.whitespaceAndResetPendingNeverSubmitFts; S.backWhileRunningAndRecreateKeepsCurrentQueryAndDropsAbandonedRead. Late callbacks cannot restore cleared data/error; Back destroys old search view; recreation retains current query's results. J search recreation also uses production SavedStateHandle factory |
| UI02-14 / NFR-VM-13 | No previous isolated Discovery execution sequence | run_ui02_device.py: each new case separately with clean exact dedicated package, selected J/S suite twice clean, per-test known reset; no method-order dependency; assertions and counts below are device evidence only |
| UI02-15 / NFR-VM-13 | No verified Discovery failure pipeline | J.failureArtifactProbe is opt-in via ui02FailureProbe=true, skipped by ordinary suite. Outer rule captures before Activity teardown; skipped assumptions produce no failure artifact. External driver captures post-run screen/logs/metadata if instrumentation dies before the rule can run. Driver expects exactly one intentional failure plus nonempty logcat, valid PNG, matching metadata, JUnit XML and HTML. Probe failure is never reported as an ordinary pass |

AC-05 timing benchmark remains separate: UI timing/debounce/deadlines are **not** query latency evidence. No claim for Helio G85/3GB/Android10/5,000-entry benchmark, process death, reboot, whole NFR-VM-13 or release/content approval.

## Changes and isolation

- `app/build.gradle`: opt-in `-PdiscoveryUi=true` build type `discovery`, app ID `com.vocamind.app.ui02validation`; opt-in `-PcatalogQa=true -PdiscoveryQa=true` uses `com.vocamind.app.ui02validation.qa` instead of existing `.qa`; androidTest Espresso core 3.7.0 only. Defaults and release catalog unchanged.
- `tools/validation/prepare_ui02_fixture.py`: copies the existing synthetic prepack into ignored build outputs, adds test-only `/bæŋk/` for bank, updates checksum, writes fixture v1 inventory. Does not edit main assets, IDs, schema, ledger or source approval. Four existing packs and four words; book noun `90562a98e8686267`, book verb `d17314a48504e2ac`, bank `5aecfbf5b9a23aee`, fourth word `923d45253cc2c67c`; pack order explicitly asserted. Main catalog SHA-256 remains `7f90d0b11b14ce73932231dc7c4506bdf30af3788b66d0fde536d08fa7f753e9`.
- `DiscoveryHarness.java`: package allowlist before DELETE; only owned dedicated userdata tables; UUID oracle DB closes/deletes; app uses actual composition root and selected runtime DB. Per-test fixture checksum/assertions. Read assertions run on instrumentation thread. Condition/deadline polling and DiffUtil/animation completion; no fixed sleep, no main-thread DB/future waits. View actions target Activity's actual window and attached rows, not created holders.
- J/S/Q classes: integration and controlled evidence explicitly separated. Injection uses existing Fragment lifecycle/ViewModelStore boundary before view creation, no production seam, no direct state or result-view mutation.
- `run_ui02_device.py`: explicit serial, dedicated package only; records HEAD + source-file SHA manifest + uncommitted diff + APK hashes + fixture + device properties/settings/commands. Establishes offline, restores original network settings; separate instrumentation process/reset each run; XML/HTML from raw AndroidJUnitRunner events; artifacts via run-as tar.
- `.github/workflows/android-ci.yml`: new synthetic offline Discovery job API 26/35, selected execution/probe and always-retained reports/artifacts for 14 days. Existing Quiz and build-test gates unchanged. Does not upload internal QA corpus/APKs/screenshots.
- No production Java, navigation, search contract, debounce, data meaning or SRS changes.

## Re-run

Use JDK 17 and existing SDK; Python with repository validation dependencies installed. On this Windows workstation Python is `C:/Users/LMT/AppData/Local/Microsoft/WindowsApps/python.exe` and JDK is `C:\Users\LMT\.jdks\vocamind-temurin-17`.

```powershell
$env:JAVA_HOME='C:\Users\LMT\.jdks\vocamind-temurin-17'
.\gradlew.bat -PdiscoveryUi=true -PcatalogPython=C:/Users/LMT/AppData/Local/Microsoft/WindowsApps/python.exe assembleDiscovery assembleDiscoveryAndroidTest
& 'C:/Users/LMT/AppData/Local/Microsoft/WindowsApps/python.exe' tools/validation/run_ui02_device.py --adb C:/Users/LMT/AppData/Local/Android/Sdk/platform-tools/adb.exe --serial emulator-5554 --output build/ui02/new-run
```

Runner default: ten cases separately → selected J/S classes twice → explicit expected-failure probe. Ordinary suite skips only the opt-in probe. Target one independent case with `--only 'com.vocamind.app.feature.discovery.CLASS#METHOD'`. Do not select the whole old packs package (contains external process-restoration driver methods).

QA (internal local evidence only):

```powershell
.\gradlew.bat -PcatalogQa=true -PdiscoveryQa=true -PcatalogPython=C:/Users/LMT/AppData/Local/Microsoft/WindowsApps/python.exe assembleCatalogQa assembleCatalogQaAndroidTest lintCatalogQa
& 'C:/Users/LMT/AppData/Local/Microsoft/WindowsApps/python.exe' tools/validation/run_ui02_device.py --qa --adb C:/Users/LMT/AppData/Local/Android/Sdk/platform-tools/adb.exe --serial emulator-5554 --output build/ui02/qa-new-run --only 'com.vocamind.app.feature.discovery.DiscoveryQaInstrumentedTest#qaPackVisibleWordAndEnglishVietnameseSearchOpenExactDetails'
```

All evidence remains local under ignored `build/ui02/`; see validation results below. Keep source manifests with each run; earlier failed attempts are debugging evidence, not evidence for later APKs.

## Validation results ? 2026-11-09

Authoritative device evidence: [final-acceptance summary](../build/ui02/final-acceptance/summary.json). All 13 groups accepted by the runner; this means expected outcomes, **not** that the deliberately failing probe is an ordinary test pass.

| Check | Actual result | Evidence |
| --- | --- | --- |
| Android API 30 synthetic, each new active J/S method separately | 10 pass, 0 fail, 0 skipped; every invocation starts from cleared dedicated package | `build/ui02/final-acceptance/independent-*/` (commands, raw runner, XML, HTML) |
| Selected J/S suite, clean run 1 | 10 pass, 0 fail, 1 skipped (opt-in probe) | [summary](../build/ui02/final-acceptance/summary.json), `suite-1/junit.xml`, `suite-1/report.html` |
| Selected J/S suite, clean run 2 | 10 pass, 0 fail, 1 skipped (opt-in probe) | `suite-2/junit.xml`, `suite-2/report.html` |
| Intentional assertion probe | 1 expected failure, 0 ordinary passes; valid PNG, nonempty logcat, matching metadata, XML/HTML verified; driver exit 0 | [probe XML](../build/ui02/final-acceptance/artifact-probe/junit.xml), `artifact-probe/artifacts/files/ui02-failures/failureArtifactProbe/` |
| QA catalog API 30 independent + two clean repeat runs | 1 pass / 0 fail / 0 skipped per run; 3 executions of the same supplementary QA case; driver exits 0 | [independent](../build/ui02/qa-independent/summary.json), [repeat 1](../build/ui02/qa-suite-1/summary.json), [repeat 2](../build/ui02/qa-suite-2/summary.json) |
| External collector negative control | Intentionally missing test class: 1 framework failure and expected driver exit 1; post-run PNG, logcat, metadata, XML/HTML verified. Separate from UI coverage | [verification](../build/ui02/external-fallback-probe/verification.json), `target/external-*` |
| Full host tests on final source | 522 pass, 53 fail, 0 skipped / 575; native Gradle exit 1; **not green** | [XML totals](../build/ui02/host-final/totals.json), [HTML](../build/ui02/host-final/html/index.html), `host-final/gradle.log`, `gradle-exit-code.txt` |
| Focused host regression reused by UI-02 | 114 pass, 0 fail, 0 skipped (RepositoryIntegration 64, FtsSearch 20, SearchViewModel 22, SearchVocabularyUseCase 6, SearchReadRecovery 2) | [HTML](../build/ui02/host-focused/html/index.html), `host-focused/xml/`, `host-focused.log` |
| Unmodified develop baseline diagnostic | 2 pass, 4 fail / 6 in QuizPolicyCompositionTest; same Windows directory-fsync failure reproduced | [HTML](../build/ui02/baseline-host/html/index.html), `baseline-host/gradle.log`; clean detached checkout under `build/ui02-baseline` |
| App + test APK build: debug / discovery / catalogQa | All succeeded, native exit 0; builds are separate from the device executions above | `final-debug-build-lint.log`, `final-discovery-build-lint.log`, `qa-final-build-lint.log` under `build/ui02/`; matching `*-build-exit.txt` |
| Lint debug / discovery / catalogQa | Each: 0 errors/fatal, 13 warnings; native exit 0 | `app/build/reports/lint-results-debug.*`, `lint-results-discovery.*`, `lint-results-catalogQa.*` |
| Fixture reproducibility | Two independently generated output trees have identical byte hashes | [hash comparison](../build/ui02/fixture-reproducibility.json) |
| CI | New API 26/35 synthetic execution prepared; **not triggered** because no push/PR authorized; original job blocks compared unchanged | `.github/workflows/android-ci.yml`; YAML parser not installed locally, no CI execution claimed |

### Exact tested source, fixture and environment

- At local validation: branch `feature/ui-02-discovery-instrumented-tests`, no task commit yet. Recorded HEAD was `c3454afeedecbe53fa7aaed664707e5108d7a9ca`.
- Device/QA tested source digest: `9447e477170dd5f843345a8aa43e04ff1921aaba5877014d53e75c5e699455bb`. This is a SHA-256 of the sorted source-file SHA manifest, **not a Git commit SHA**. All final device/QA runs have the same digest, and every recorded source hash was compared with current bytes. Full final host run saved the same source manifest before executing. Documentation is finalized after tests and does not affect runtime source digest.
- [Validation manifest](../build/ui02/validation-manifest.json) records commands, native result codes, toolchain, APK SHA-256, input document checksums and report paths. `final-acceptance/environment.json`, `source-hashes.json` and `working-tree.patch` bind device evidence to exact app/test APKs and dirty tracked diff. `tested-source.zip` preserves all changed/untracked source and handoff files byte-for-byte; its inventory/hash is in the validation manifest. Earlier debug attempts are not substituted for these final runs.
- Synthetic `ui02-v1`, contentVersion 1.0.0, catalog SHA-256 `a833a04f1ee3b8a6e907d4b05cfbc42d936f47dfe71b1e06c97adacb063f8305`. Full expected entries/pack memberships: [fixture inventory](../build/ui02/fixture-v1.json). Main asset is unchanged at SHA-256 `7f90d0b11b14ce73932231dc7c4506bdf30af3788b66d0fde536d08fa7f753e9`.
- QA snapshot `editorial-qa-v2`, contentVersion 1.0.1, catalog SHA-256 `41bcfacfcebdc42c8059c95f396c738fdabab62c4cbd8a255499e9c446502709`. [QA expected IDs](../build/ui02/qa-expected.json): pack `pack_toeic_basic`, first word customer `ee46fdf826f53ce2`; EN customer ? that ID; VI kh?ch h?ng ? ordered client `a0adea31b35a7f42`, customer `ee46fdf826f53ce2`. Q verifies the ordered result IDs and opens the first exact ID. Internal QA content stays local.
- Windows host; Gradle 8.9 / AGP 8.7.3; Gradle JVM Temurin 17.0.20.1; Python 3.13.14. The runner's `java -version` field resolves PATH Java 21, while Gradle uses explicit JAVA_HOME 17; see [toolchain](../build/ui02/toolchain.json). No Gradle run on JBR 25 was accepted (initial attempt failed with class-file version 69).
- Device: `emulator-5554`, AVD Small_Phone, Android 11/API 30, x86, fingerprint `google/sdk_gphone_x86/generic_x86_arm:11/RSR1.201013.001/6903271:user/release-keys`; emulator 37.2.12.0. Final boot used `-no-snapshot -gpu swiftshader_indirect -no-audio -no-boot-anim`; [renderer log](../build/ui02/software-emulator.log). Driver records properties/animation settings, airplane/Wi-Fi/mobile-data state and restores original network settings. Final host/device clocks matched Nov 9, UTC+7. The old loaded snapshot's Nov 2 clock belongs only to earlier debug logs.
- API 26 and API 35 **device execution not run**: no corresponding local image/AVD; no physical device connected. API 30 is supplementary local QA, not a claim to have completed the planned 26/35 matrix. Newer preview images are outside this selected QA matrix.

### Commands and open gates

Final host command (native exit 1):

```powershell
.\gradlew.bat -PcatalogPython=C:/Users/LMT/AppData/Local/Microsoft/WindowsApps/python.exe testDebugUnitTest
```

Focused host command (successful; XML/HTML copied before subsequent full host execution):

```powershell
.\gradlew.bat -PcatalogPython=C:/Users/LMT/AppData/Local/Microsoft/WindowsApps/python.exe testDebugUnitTest --tests '*SearchViewModelTest' --tests '*SearchVocabularyUseCaseTest' --tests '*SearchReadRecoveryTest' --tests '*FtsSearchTest' --tests '*RepositoryIntegrationTest'
```

The full host failures remain an open regression gate. Earlier full execution had 521 pass / 54 fail; the authoritative final execution has 522 pass / 53 fail (one ProgressAggregation failure did not repeat). Both sets are retained separately; this variability is not treated as a fix:

| Class | Failures / tests |
| --- | --- |
| QuizPolicyCompositionTest | 4 / 6 |
| CatalogUpgradeTest | 38 / 40 |
| ProgressAggregationTest | 1 / 34 |
| AttributionScreenTest | 2 / 2 |
| PackVocabularyIntegrationTest | 2 / 2 |
| VocabularyListTest | 2 / 8 |
| SearchScreenTest | 4 / 6 |

Observed failures include `CatalogGenerationStore.syncDirectory` falling back to a Windows directory FileChannel open that raises AccessDeniedException, SQLite CANTOPEN in ProgressAggregation, and downstream navigation deadlines. The unmodified baseline diagnostic reproduces four QuizPolicyComposition failures; **this does not prove that all 53 failures share one cause**. No production portability change was made under UI-02. Further host triage or a supported Linux CI run is still needed.

Earlier `cold-final-api30` suite was rejected: 5 completed passes, 1 skipped probe, then process abort during case 7 (only 6/11 completion events). Crash log points to RenderThread/Skia OpenGL `GL errors`, SIGABRT; see `cold-final-api30/full-crash-logcat.txt` and `crash-log.txt`. It has no pre-teardown screenshot because the native process died. Software GPU was used for the final accepted reruns; production code was unchanged. The new external collector was separately verified by the missing-class negative control; its image is explicitly post-run and is not attributed to the old crash.

Still open: full host green gate; API 26/35 device matrix; actual CI execution after separately authorized publication; AC-05 reference-device FTS benchmark; MVP content/release authorization; process-death/reboot evidence. Activity close/launch and recreation here are described only as those lifecycle operations. At the local validation snapshot, no push, PR, merge, main/develop commit, reviewer message or task-completion update had occurred. The later commit/push authorization above does not close these acceptance gates.
