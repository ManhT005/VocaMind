> Latest review of tested source5713ba6: [device results and limitations](qa-02-review-5713ba6-20261111.md), [requirement results](qa-02-review-5713ba6-results.csv), [findings](qa-02-review-5713ba6-findings.md). API26 full journey/reopen/reboot PASS; Realme API33 animation0x journey/reopen/physical reboot PASS, animation1x mismatch remains unresolved. TalkBack partial only; API35 full journey NOT RUN. Rights, privacy/backup, WEEK-2 binding and reviewer sign-off remain open. Earlier status paragraphs below are historical. This is documentation publication, not a fresh test run.

> CI retest5713ba6: push42990358456 and PR42990368223 both SUCCESS;599 unit tests/workflow,0 fail/error/skip; API26/API35 Discovery verified with expected probe skips/failure. F05 FIXED_RETESTED for helper scope. Full QA150 API26/API35 journeys, TalkBack, privacy, rights and human sign-off remain open. See [current report](qa-02-resume-ba02a88.md).

> Latest technical retests: [qa-02-resume-ba02a88.md](qa-02-resume-ba02a88.md). CI ba02a88 PR SUCCESS / push API35 precondition FAIL; local controller fix passed host+API30, API35/API26 CI still pending. Planner/Quiz/IME/font/Attribution coverage updated. TalkBack deferred by user; privacy/rights/sign-off remain open. Earlier sections are historical.

> Latest stop snapshot: [blocker retests](qa-02-blocker-retest-20261111.md). F01 verified/fixed; approved API30 journey/reopen/reboot and mandatory benchmark retest PASS; controlled5/5 PASS. Privacy/rights/sign-off and remaining P0 still open. Earlier entries are historical.

> Current-source continuation (2026-11-11): [verified results and limitations](qa-02-continuation-20261111.md). Both current-head CI workflows succeeded; ZIP reports remain HTTP401, so F01 is not closed. Historical pending/failing CI statements below describe earlier runs. New API30 journey blocked before Activity; B02 remains FAIL; backup policy requires review.

# QA-02 handoff — 2026-11-11

Branch: `feature/qa-02-e2e-acceptance`; PR: https://github.com/ManhT005/VocaMind/pull/55
Base: `develop`. This is a progress handoff, not QA-02 acceptance or release approval.

## Delivered

- Opt-in isolated QA02 application profile, continuous offline journey, persistence/reboot checks, supplemental runners and strict host result validation.
- Discovery row helper scrolls with an explicit offset and requires at least 90% label visibility before clicking, addressing the API35 failure in the baseline CI.
- Flashcard triple-tap injection avoids synchronous dispatch delays while retaining the 150..200ms timing assertions and transactional checks.
- FTS materialization sorts in Java using SQLite-compatible ASCII NOCASE comparison. This changes production DAO code; historical device evidence does not certify this version.
- Requirement matrix, defect log and evidence summaries are committed. Raw APKs, corpus, video and device logs remain in ignored `build/qa02/` on the original machine.

## Remaining gates

1. Require green CI on the pushed head, especially both Discovery API35 checks. Run `42952992416` reached the final pack row but timed out because the expanded app bar left less than 90% of that row visible on the compact API35 display. The helper now collapses the real scrolling app bar before positioning pack rows; a current-head CI retest is still required. The local machine has no installed API35 image.
2. Rebuild the QA02 APK with the changed DAO and rerun the full offline journey, persistence and outstanding P0 matrix cases on the approved LDPlayer substitute. Existing component results used an older APK.
3. Resolve benchmark B02: latest recorded broad FTS max 130.341ms and valid startup 2206..7856ms exceed the unchanged gates (every FTS sample <50ms; startup <1500ms). Some startup samples target the launcher and must be rejected.
4. Complete outstanding P0 cases and obtain Nhan's explicit evidence review/sign-off (B05).
5. Keep content/release B03 HOLD until the authorized owner signs the exact snapshot and channels. A code merge does not itself grant release rights.

## Continue here

- Current status and reproduction commands: [validation](qa-02-validation.md).
- Per-case results: [matrix](qa-02-requirement-test-matrix.csv).
- Owners and retest requirements: [defects](qa-02-defects.csv).
- Evidence scope and local raw paths: [evidence index](evidence/qa-02/README.md).
- Unsigned approvals: [approval request](qa-02-approval-request.md).

The newest sections at the top of validation supersede its historical sections below.
Do not infer a full PASS from component results or from an emulator-only baseline.

## Handoff verification

- Discovery runner host tests: 33 passed.
- QA02 runner host tests: 4 passed.
- Discovery app and instrumentation APKs: build passed (`assembleDiscovery assembleDiscoveryAndroidTest`, `-PdiscoveryUi=true`). This compiles the current Android tests, including QA02.
- Focused FTS unit run did not complete: native CursorWindow crashed (`Failed ashmem_create_region: File too large`, executor exit `-1073740791`) on this Windows/JDK21 environment. Do not report it as PASS; rerun on CI/JDK17. The older 22-test result in validation is historical.
- GitHub PR initially reports no merge conflict, but no submitted reviews; this is not approval to merge.
- Branch-protection details could not be read: GitHub integration returned HTTP403. Required-check/review settings remain unverified.
