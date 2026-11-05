# PACK-02 activation validation and handoff

Implementation owner: Trường. Requested reviewer: Thành. Local handoff for review,
not a change to task assignment/status or SRS acceptance status. No PR or merge.

## Baseline and tested revision

- Read the attached PACK-02 requirements and relevant SRS v1.1 clauses directly
  from the supplied DOCX (FR-VM-007/010/019/020, BR-07, UC-02/03, AC-03/09/13,
  DEC-02). No edits to either source document.
- Initial branch: `feature/voc-data-03-ingestion-pipeline`; working tree clean.
  No existing PACK-02 local branch or changes to preserve. No AGENTS.md found in
  repository or checked ancestor directories. Read README, Android setup, CI,
  PackRepository/UserDao, PACK-01 architecture and existing integration fixtures.
- Fetched develop from origin: `6420377`; created `feature/pack-02-activation`
  from that revision. PACK-01 and DATA-02 dependencies are present.
- Tested source commit: `d31944f0cfd80eb43b9e993bbbde83baddb84115`.
  Subsequent handoff commit contains documentation/evidence only.
- User's explicit instruction to commit/push overrides attachment section 9's
  earlier stop-before-push wording. Publish only the feature branch; no PR/merge.

## Implementation and API decisions

`SetPackActive.execute(packId, active)` returns `RepoResult<List<PackState>>`:
validate ID/catalog existence, perform the explicit target write, reread the
persisted list for all consumers. Clock is injected as LongSupplier and is read
only for valid activate requests. Zero is a valid first activation timestamp.
Existing repository/DAO contracts retain first activatedAt, progress, dueAt,
saved flags and review history. Pause of a valid never-activated pack creates no
row. RoomPackRepository now rejects unknown IDs for pause as well as activation.
It also propagates state reread errors without converting them to a write error.

Primary control is on PackDetailFragment. The button immediately disables while
pending, then renders confirmed persisted state. On any failure it shows a
message and a read-only Retry: reconcile first, then allow a new explicit write.
This also handles successful writes followed by failed reads without claiming
rollback or blindly toggling the opposite state. RepoError remains in view state.

PacksViewModel is Activity scoped across list/detail/Today. Its existing factory
owns a lazy RepositoryProvider and one serial worker. Lifecycle refresh uses a
generation token to suppress older results waiting for main-thread delivery;
refresh during a write relies on that write's final reread. Writes cannot overlap
through these screens. PackWordsViewModel stays fragment scoped. Each owner queues
its close after pending work; the activity's provider is not closed by a fragment.
Recreation reads state and never invokes setActive implicitly.

Today reads GetPacks through the same domain boundary. Only a successful read
without a valid catalog pack active displays Chọn gói học / Khám phá. CTA only
navigates to packsFragment. Loading/errors are distinct and cannot appear as
no-pack/completed. Active state currently displays an honest session-feature
placeholder, with no invented counts or completion claim.

Key files: domain/pack/SetPackActive.java, RoomPackRepository.java,
core/PacksViewModelFactory.java, feature/packs/{AsyncListViewModel,
PacksViewModel,PacksFragment,PackDetailFragment}.java,
feature/today/TodayFragment.java and their XML/string resources.

## Commands and results

Windows local execution on 2026-11-06, JDK 17 from
`C:\Users\LMT\.jdks\vocamind-temurin-17`, SDK 35, Gradle 8.9.

```powershell
$env:GRADLE_USER_HOME='C:\Users\LMT\.gradle'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
git diff --check
```

PASS: BUILD SUCCESSFUL on the tested commit, 215 tests across 21 JUnit XML suites,
0 failures/errors/skips. Lint succeeds with warnings recorded in evidence JSON;
no dependency upgrades introduced. Added tests: 6 use-case/ViewModel cases,
2 rendering cases run on both API 26 and 35 (4 executions), 2 integration cases
run on both API 26 and 35 (4 executions). Existing integration/pack tests also pass.
These are host tests; API 26/35 Robolectric execution is not a device result.

Reports: `app/build/test-results/testDebugUnitTest/`,
`app/build/reports/tests/testDebugUnitTest/index.html`,
`app/build/reports/lint-results-debug.html`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
Log: `build/pack02-gradle.log`.
Committed summary/hashes: [pack-02-local-evidence.json](validation/pack-02-local-evidence.json).

Python CI checks were run with Python 3.13.14 and jsonschema 4.26.0 installed in
`build/pack02-venv`, without modifying global dependencies. Commands, exit codes,
test totals and hashes are in the evidence JSON. All source gate, vocabulary,
credit, catalog, contract and ingestion checks PASS; fixture release validation
returns 1 as expected (release rejection), not a missing-file/import failure.
Ingestion proof compares four files byte-for-byte in
`build/vocabulary-ingestion/pack02-verified-proof/comparison.json`.
Earlier attempts without jsonschema and reuse of an existing proof directory
failed; the recorded final run uses installed dependencies and a fresh directory.
GitHub wrapper-validation action / remote CI: NOT RUN locally; inspect remote CI
after push. Local wrapper runs use the repository-pinned Gradle distribution.

## Requirement to evidence

| Cases | Evidence and limits |
| --- | --- |
| T01, T07 | Rendering test Today CTA after successful read, hidden during loading/error/active; initial VM never writes; integration empty active source including orphan user row. |
| T02, T08, T09 | SetPackActive validation/clock tests; Room unknown/blank rejection, no-row pause, repeated activation/pause, timestamp zero and first timestamp preservation. |
| T03, T05 | File-backed Room close/reopen paused state, saved flag, dueAt, box, learned/review times and history; reactivation retains first timestamp. App relaunch on device NOT RUN. |
| T04 | Device reboot/offline persistence NOT RUN: adb devices lists no connected device/emulator. Room reopen does not establish reboot acceptance. |
| T06 | Multi-pack fixture contains exclusive A/B words plus SHARED. Pause A removes its exclusive new/due words, retains SHARED via B exactly once; pause all empties new/due; orphan contributes nothing. |
| T10 | Injected write/read failures, safe read retry, successful-write/failed-read reconciliation; Room SQLite trigger blocks activation with no row written. Arbitrary runtime device DB fault injection NOT RUN. |
| T11 | Controlled worker/main queues, old read delivered after confirmed write, repeated tap and refresh during write; one write and confirmed state retained. |
| T12 | Shared persisted snapshot and onResume refresh implemented; host rendering covers CTA and detail state. Full NavHost list/detail/Back/Today/CTA device smoke NOT RUN (no device). |
| T13 | Host detach/attach view recreation during pending operation keeps single write and confirmed label; cleared owner/late delivery tests. Real rotation during loading/write/confirmed and process restoration NOT RUN (no device). |
| T14 | No network/account required by added paths; airplane-mode first launch/activate/pause/reopen smoke NOT RUN (no device). Host execution is not evidence of airplane mode. |

## Manual review steps

Use an isolated test install/profile; do not clear real user data.

1. With no active packs, open Today. Wait for Chọn gói học and tap Khám phá.
   Merely opening Today or a pack must not activate anything.
2. Open an existing pack detail, tap Kích hoạt. During pending the button is
   disabled; after success it becomes Tạm dừng and status is Đang học.
3. Back to list: active badge reflects persisted state. Return to Today: no
   no-pack CTA or false completion; session feature remains pending implementation.
4. Pause from detail, Back, reopen detail/Today. Pause all packs: Today CTA returns.
   Reactivate: learned progress remains. Repeat taps and rotate while pending.
5. Force-stop/reopen and reboot the test device, then open offline. Confirm
   activation, progress, dueAt, saved flag and history. Repeat from first launch
   in airplane mode. Record device model/API, timestamps and evidence separately.
6. On a test DB with exclusive/shared fixture words, verify pause A retains shared
   words through active B; no duplicate stableId; all paused gives empty source.
7. Inject storage failure only on a dedicated test database. Verify error/Retry,
   no default activation, and reconciliation of any write that already persisted.

## TODAY-01/02 and review handoff

Use the existing StudyRepository/ActiveCatalogScope active-only source when
planning each new session. Do not cache a pool across activation changes without
refreshing. PACK-02 does not implement due/new priority, quotas, relearn, Flashcard,
Quiz, schema migration or catalog release. No running study-session UI exists in
this baseline; in-flight session policy remains for TODAY/FLASH owners to decide.
Do not infer AC-09, all AC-03, or upgrade/rollback/reboot AC-13 acceptance from
this handoff. Device smoke and Thành review remain pending. Task not marked complete.

## Draft PR description (not published)

Title: `feat(packs): add explicit activate/pause and Today no-pack CTA`

Target: develop. Owner: Trường. Requested reviewer: Thành (request only when PR
publication is authorized).

Pack detail now lets learners activate/pause explicitly and renders persisted
UserPack state. A shared Activity ViewModel serializes writes, disables repeated
taps and suppresses stale reads. Repository pause validates catalog existence;
first activation timestamp and all study progress remain intact.

Today displays Chọn gói học / Khám phá after a successful no-active-pack read;
the CTA only navigates. Existing active-only study sources retain shared words
through other active packs, with stableId deduplication. Planner work remains for
TODAY-01/02.

Validation: 215 host tests; debug app and instrumentation APK builds; lint;
Python CI gates; diff whitespace check. See evidence JSON and this document.
Device offline/navigation/rotation/reboot smoke NOT RUN (no connected device).
No SRS/assignment change, migration, catalog update, PR creation or merge.
