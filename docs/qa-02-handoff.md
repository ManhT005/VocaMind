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

1. Require green CI on the pushed head, especially both Discovery API35 checks. Baseline run `42610694082` failed the first Discovery case; the local machine has no installed API35 image. Prior installation failed for insufficient disk space.
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
