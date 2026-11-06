# Device QA results (automated emulator assertions)

Publication binding: these source/APK fingerprints match integrated source commit
`63f1edf5c5aedeb83ee7024c0b813b6984568df0`; see [publication.json](publication.json).
The original uncommitted collection context is retained below.

All results below use the uncommitted source fingerprint and APK hashes identified
in environment.md. These are real API30 emulator checks, separate from 227 host
test executions. No manual reviewer sign-off is implied.

| Case | Result | Evidence |
| --- | --- | --- |
| E01 | PASS | environment.md, source-manifest.json, device/environment.json, local-evidence.json |
| M01 / E02 | PASS | device/M01-M03-offline-navigation.txt; airplane mode enabled before first Activity; empty UserPack asserted after Today/CTA/browse |
| M02 | PASS | Same instrumentation log checks pending/double-tap/persisted active and vocabulary stableId/Back; device/M02-active-list.png+xml and M02-active-today.png+xml verify active badge and no CTA |
| M03 | PASS | device/M03-paused-today.png+xml, M02-M03-active-paused-navigation.json; pause clears active badge and restores CTA |
| M04 / E03 active | PASS | device/prepare-true.txt, relaunch-true.txt, reboot-true.txt; true-before.json exactly equals true-relaunch.json and true-reboot.json across every userdata table |
| M04 / E03 paused | PASS | Corresponding false-*.txt/json; same equality check after force-stop/relaunch and a second offline reboot |
| M05 / E04 | PASS | device/M05-pending-rotation.txt: rotation during held loading and held write, Activity recreation, exactly one mutation, final repository state; confirmed rotation also in M01-M03 log |
| M06 / E04 | PASS | device/result.json records real PID4263 → absent → PID4441 after HOME/am kill/launcher restore; M06-before-hierarchy.xml, M06-restored-hierarchy.xml/png and restored.json prove restored detail and no userdata mutation |
| M07 | PASS for active source boundary | device/M07-exclusive-shared.txt: private/shared UUID fixture on device, distinct new/due source, preserved progress/history/saved/firstAt=0; not a full planner/session claim |
| M08 | PASS for injected error/reconcile | device/M08-error-reconcile.txt: writer failure changes no state; persisted write + injected reread failure reconciles through read-only retry, write count unchanged |
| Existing device regression | PASS | connected report XML and pack02-integrated-connected.txt: 18 tests, 0 failures/errors/skips |
| E05 | Local evidence collected | Historical evidence kept separately; approval/task acceptance remains for owners/reviewer |

PACK-02 targeted phase logs contain **11 instrumentation executions**, of which
10 are functional probes and one (`M06-stage`) only stages a task. Each final
targeted log has `OK (1 test)`. Actual M06 acceptance comes from the external shell
process-death assertions, not the stage test or ActivityScenario.recreate.
Together with existing device regression, the final logs contain 29 successful
instrumentation executions; this does not hide earlier harness failures.

Earlier attempts are in earlier-harness-attempts/: initial queue/lifecycle timeout,
test executor shutdown ordering error, and SQLITE_BUSY from parallel fixture
seeding. All were test-harness issues corrected without production delays/reset.
The phase driver initially found no PID after instrumentation exited; that did
not count as process-death acceptance, so M06 was repeated using the independent
shell driver. UI smoke after Gradle cleanup initially found no app; same APK was
reinstalled only into the QA AVD and the roundtrip assertions then passed.

**NOT RUN:** physical device, on-device API26/API35+ matrix, TalkBack/font-scale
acceptance, real uncontrolled disk-failure injection, old-catalog migration,
full Today planner/session behavior, remote CI for this uncommitted integration,
Thành approval and full AC-03/09/13 acceptance. Reasons: not part of this isolated
API30 run, missing corresponding device/feature, or requires reviewer/publication.
