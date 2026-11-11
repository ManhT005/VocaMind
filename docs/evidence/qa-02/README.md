> Latest: [resume ba02a88](../../qa-02-resume-ba02a88.md), [public counts/hashes](resume-ba02a88/summary.json). Raw source/APKs/corpus/video/logs stay in ignored build/qa02. No QA02 acceptance or human signature inferred.

> Latest stop snapshot: [blocker retests](../../qa-02-blocker-retest-20261111.md). F01 verified/fixed; approved API30 journey/reopen/reboot and mandatory benchmark retest PASS; controlled5/5 PASS. Privacy/rights/sign-off and remaining P0 still open. Earlier entries are historical.

> Current-source continuation (2026-11-11): [verified results and limitations](../../qa-02-continuation-20261111.md). Both current-head CI workflows succeeded; ZIP reports remain HTTP401, so F01 is not closed. Historical pending/failing CI statements below describe earlier runs. New API30 journey blocked before Activity; B02 remains FAIL; backup policy requires review.

# QA-02 evidence index

These are supplemental emulator/host results, not physical-device acceptance.
The authoritative status is [validation](../../qa-02-validation.md) and the
[matrix](../../qa-02-requirement-test-matrix.csv).

- `sdk28-04`: final executed150-word main journey, force-stop and changed-boot-ID reboot.
- `sdk28-supplement-02`:39 native SQLite contracts and actual152/152ms input burst.
- `sdk28-components-01`:8 independent component/fault-injection cases.
- `sdk28-upgrade-01`:20 native DATA08 isolated-database contracts.
- `ci-baseline`: artifact-checksum verification,303 baseline input comparisons,
 596 unit results,API26 support,DATA08 reboot phases and the unresolved API35 failure.
- `preparation`: original inputs, exact QA APK verification and LDPlayer recovery.
- `sdk28-03`: exploratory predecessor, superseded by04; not the final matrix artifact.

Each final run has `source-hashes.json`, `summary.json` and `source-freeze.json`.
All compiled app/test inputs match their recorded run hashes and are frozen in a
private ZIP under `build/qa02/<run>/`. The supplemental host runner subsequently
gained opt-in component/upgrade modes; the recorded older hash is retained. Those
host-only differences do not change APK/test inputs or the main journey runner.

Raw commands, network/logcat, finalized video, test-only logical DB snapshots,
screenshots and source ZIPs remain local under ignored `build/qa02/`. Public reports
record hashes/paths. APKs and internal corpus are not uploaded. A reviewer must inspect
the raw evidence before sign-off. No reviewer approval is inferred from a PASS test.
