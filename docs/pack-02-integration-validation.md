# PACK-02 / PR #29 — local develop integration follow-up

Owner: Trường. Assigned reviewer: Thành. This is implementation/QA evidence for
review, not Thành's approval or acceptance of all AC-03/09/13.

## Revision identity and preservation

- Read `C:/Users/LMT/Downloads/pack-02-pr29-review-and-follow-up.md` including
  E01–E05 and M01–M08, repository README/setup, CI workflow, and both feature
  validation records. No AGENTS.md found in tracked files or checked ancestors.
- Initial working tree: clean on develop `4959cf6`. No existing local edits were
  stashed, overwritten or deleted. Other branches were left intact.
- Fetch confirmed develop `4959cf6d29314d7105f4285edd76e3169d912958` (VOCAB-01,
  PR #30), PACK-02 `0512417f1e5d398e09f6c9a00707b6a28c4a2543`, PR #29 open.
  Remote CI success at the old head does not validate this integrated worktree.
- Switched to `feature/pack-02-activation`, integrated `origin/develop` using
  `git merge --no-commit --no-ff origin/develop`, and resolved its conflicts.
  The merge is intentionally **pending locally**: HEAD remains `0512417` and
  MERGE_HEAD is `4959cf6`. No new commit, push, PR edit or remote merge.
- Since the user prohibits committing, there is no new tested commit SHA.
  The new evidence records both parent SHAs, hashes of tested source files,
  a combined source fingerprint, and APK/test-APK hashes to identify the actual
  integrated artifact. Do not attribute these results to pristine `0512417`.
- Historical [PACK-02 validation](pack-02-validation.md),
  [old evidence](validation/pack-02-local-evidence.json), and VOCAB-01 evidence
  are preserved separately. New run evidence belongs under
  `docs/evidence/pack-02/integration-0512417-4959cf6/`.

## Conflict resolution and auto-merge review

| File | Resolution / verification |
| --- | --- |
| AsyncListViewModel.java | PACK-02 implementation already includes the generation-based read refresh introduced by VOCAB-01. Retained its write guard, pending/error state, serial mutation/reread and stale callback suppression; no weaker read-only refresh replaces it. Both VocabularyListTest late-read and PackActivationTest read/write/error cases execute. |
| PackDetailFragment.java | Combined both model fields: Activity-scoped PacksViewModel for activation and fragment-scoped PackWordsViewModel for words. onResume refreshes both. Kept pending/disabled/error/reconcile control and replaced the old inner WordAdapter with VOCAB-01 PackWordAdapter, stableId route, list visibility and submitList behavior. |
| strings.xml | Kept both activation/Today strings and IPA/status/vocabulary route strings. No duplicate resource keys or conflict markers. |
| docs/README.md | Kept links for both features and added this follow-up record. |
| PacksViewModelFactory.java (auto-merge) | Verified injected SetPackActive and lazy provider/serial worker remain. VOCAB-01's removal of the synchronous null packId throw is deliberate: GetPackWords now validates invalid IDs in domain on the worker, letting UI expose an error without leaking an executor. |
| RepositoryIntegrationTest.java (auto-merge) | Verified both PACK-02 private/shared scope, zero timestamp/reopen/write-failure cases and VOCAB-01 read-only full-table snapshots/order/membership/reopen/invalid-ID cases remain executable. No fixture or test class was selected wholesale over the other. |

No production bug has been established in the combined code during these checks;
production changes are conflict integration, not a cosmetic follow-up fix.
Added `PackActivationInstrumentedTest` provides real ActivityScenario/NavHost,
persisted repository and isolated fixture checks, with test-only controlled
executors/failures. No artificial delay/hook was added to production.

## Host verification and CI-equivalent gates

JDK 17, Gradle 8.9, compile SDK 35; existing Gradle cache via
`GRADLE_USER_HOME=C:/Users/LMT/.gradle`.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
git diff --check
git diff --cached --check
git ls-files -u
```

Full integrated host run: **227 tests, 23 suites, zero failures/errors/skips**.
This includes both feature suites, real NavHost host tests, all repository tests,
old callback ordering, double tap, mutation guards and read reconciliation.
The new PackVocabularyIntegrationTest runs the combined CTA → pack → activate →
word detail/stableId → Back → active list/Today → pause → CTA flow on host API26/35.
Debug app/test APK builds and lint PASS. Existing on-device catalog/attribution
regression suite also PASS: **18 executions, zero failures/errors/skips**, run via
`connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=com.vocamind.app.feature.packs.PackActivationInstrumentedTest --offline --console=plain`
on the isolated AVD. PACK-02's phased cases run separately so first-launch and
external reboot/process-death setup are not conflated with ordinary suite execution.
Detailed final totals, lint issues,
commands and report hashes are in `local-evidence.json` in the new evidence folder.
Robolectric API26/35 is host execution, not emulator QA.

Python 3.13.14 + jsonschema 4.26.0 in the existing workspace test virtualenv:
source gate, vocabulary tests (112), catalog tests (5), contract tests (39), credit
consistency, ingestion, QC determinism/refusal, editorial reproducibility/refusal
and owner-accepted editorial production handoff all run according to the current
CI workflow. Expected refusal exit 1 is distinguished from failure to execute.
Four byte-for-byte directory comparisons and refused-output absence checks are
recorded. GitHub wrapper-validation action/remote CI on the new worktree:
**NOT RUN**, because nothing is published. Local wrapper builds succeed.

## Device environment and isolation (E01)

Only a disposable headless AVD named `pack02-followup`, serial `emulator-5560`,
was used. Its configuration was copied from Small_Phone but its entire data path
is `build/pack02-followup-avd`; no snapshot, disk or userdata from Small_Phone was
copied/modified. Model `sdk_gphone_x86`, Android API30; exact fingerprint and hashes
are in each run's environment JSON. No physical device, API26 or API35+ emulator run
is claimed. Production databases were not reset.

The test driver verifies the AVD name before it clears the **disposable QA app**
for first-launch runs. Earlier failed harness runs are preserved separately.
Airplane mode is enabled with `cmd connectivity airplane-mode enable`, Wi-Fi/data
disabled, and settings rechecked before launch and after reboot. An earlier direct
broadcast was refused by Android; that refusal is not a successful airplane-mode
operation. The supported connectivity command succeeded.

## Device cases (E02–E05)

Final case results and per-command logs are recorded in the run evidence folder.
Device instrumentation and host results are reported separately.

| Case | Evidence / scope |
| --- | --- |
| M01 | Fresh QA install offline before first Activity launch; Today CTA → packs → detail; asserts UserPack remains empty until explicit activate. |
| M02 | Real detail button disables immediately, repeated callback blocked, persisted active state; correct list adapter ordering, IPA/status, exact stableId passed through real NavHost to receiver and Back. |
| M03 | Pause confirms persisted state, list badge clears and Today CTA returns when all packs inactive. No implicit reactivation. |
| M04 | Test driver prepares reviewed/saved word fixture, confirms UI write, force-stops/relaunches and reboots offline for active and paused states separately. Compares every userdata table before/after, including first activatedAt, dueAt, progress, saved flag and history. |
| M05 | Device rotation while read is held, while a write is held, and after confirmation; recreate during pending. Controlled executor is injected only by instrumentation; final DB state matches exactly one target write. Word list continues to refresh onResume without superseding shared pending activation. |
| M06 | External driver stages background detail task, kills the background process, verifies PID absence/change, relaunches launcher task, checks restored destination hierarchy and unchanged userdata. Force-stop alone is not counted as saved-task process restoration. |
| M07 | UUID-named device catalog/user fixture with exclusive A/B/shared words; checks actual StudyRepository new/due boundary and retained progress/saved/history/first timestamp. This is source-boundary evidence, not a Today session/planner implementation. |
| M08 | On-device injected writer failure and actual persisted write followed by injected reread failure; Retry reads durable state, no extra write or blind reverse toggle. Real SQLite trigger rollback also remains covered by the host repository test. |

Earlier harness-only failures are retained with causes and retest results:

1. Pending test initially drained its controlled worker before asynchronous
   fragment onResume queued the newer read, then timed out. Replaced fixed sleep
   with a condition-based worker/UI drain; no production workaround.
2. Error test shut down its injected worker before Activity close, so ViewModel
   cleanup was rejected. Test now closes Activity before executor shutdown.
3. Persistence seeding opened an extra writer while its repository/Activity was
   open, provoking SQLITE_BUSY. Fixture now seeds with a single DB owner before
   launching repositories/UI; failure remains in evidence, not hidden as PASS.

## Remaining boundaries and review handoff

- API26 and a newer API on-device matrix, physical-device coverage, TalkBack,
  font-scale/small-screen visual acceptance: NOT RUN in this follow-up. Host
  API26/35 and API30 emulator do not establish that broader matrix.
- Catalog v1→v2 migration and legacy-emulator upgrade are not implemented. Fresh
  isolated QA profile uses the bundled catalog. No destructive fallback added.
- TODAY-01/02 still owns full due/new/quota/relearn planning and active-session
  refresh policy. VOCAB-02 still owns full word detail; the existing receiving
  boundary is preserved. No migration/catalog release or scope expansion.
- Trường must review the local integrated diff and decide when to commit/push.
  Thành must independently review/approve; Nhân should confirm data-specific
  acceptance as assigned. No task Done or full AC-03/09/13 sign-off is inferred.
- Before publication, rerun appropriate checks if source changes, bind evidence
  to the eventual commit, update the existing PR only when authorized, and wait
  for fresh CI/reviewer confirmation before any merge decision.
