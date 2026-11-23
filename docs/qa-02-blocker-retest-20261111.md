# QA-02 retest blocker ngày 11/11/2026

Tiếp nối báo cáo continuation; không phải sign-off hoặc QA-02 Done. Head0442777 cùng diff local được lưu trong mỗi run. Bảo toàn các evidence fail trước đó.

## Quyết định thiết bị

Người dùng đã chấp thuận Small Phone API30 hiện có thay LDPlayer cho cả nghiệm thu và benchmark; xem [quyết định](evidence/qa-02/device-decision-20261111.md). Không giữ blocker thiếu thiết bị cho cấu hình này trong các lượt tiếp theo. Không đổi SRS/ngưỡng hoặc quyền nội dung/reviewer.

## Hành trình và persistence

`build/qa02/api30-approved-reboot-retest/`: journey1/1 PASS50.678s; force-stop/reopen1/1 PASS4.542s; reboot/reopen1/1 PASS16.429s. Mỗi invocation có start/success status records; host exit0. Source digest69b36aa3296d7ec29fabc4a4ab30c83b8a8a0878c52dada632bab6476b87c39a. App SHA970216a5fcdd53b9e95dce97205c5e745001f06fd8e91de9d6593f8ea33fbfe0; test SHAb1fff198faa06ba99a17bad21cb05151003a47cd1ff90031219d34e443f63e00; catalog SHA41bcfacfcebdc42c8059c95f396c738fdabab62c4cbd8a255499e9c446502709.

Boot ID9d483fdd-db8b-4fbe-9ea4-1809f20af7f0→91c28ee3-6a83-41c7-a1be-366d5b61d02e. Snapshot equality assertions passed; no seed/reset between journey/reopen/reboot. Offline flags/effective radio/default-network checks passed before phases; no claim of uninterrupted isolation during boot. Network restore accepted=true. Defaults5/10/OFF/20 asserted before quota10 setup. Video/logcat/logical snapshots remain local and still require human review.

Two harness corrections: timestamp normal journey rating/answer after completed scroll/click so the next legitimate action waits≥650ms from completion (product600ms rule unchanged); record reboot request/result and wait for boot-ID change even if adbd disconnected before reply. Strict boot/offline assertions remain. Host harness4/4 PASS. Temporary debug logs were removed; no production diff retained from diagnosis.

Historical Today F04 did not reproduce in two later journeys that reached Quiz and in this full successful run. Root cause of original pack-list error remains unverified; record NOT_REPRODUCED_RETEST_PASS rather than claiming a product fix. First timing retest passed journey/reopen but reboot harness stopped at adbd disconnect; kept separately at api30-approved-timing-retest. Earlier diagnostic stopped at answer-lock; does not certify full journey.

## Benchmark on approved API30

Raw `build/qa02/api30-approved-benchmark-01/`, collector source `build/qa02/blocker-retest/collect-benchmark.py`. DATA09 release-based app/test build successful77 tasks; aapt package/debuggable verification, installed APK SHA comparison, source diff, inventory and fixture dataset hash recorded. Measurement uses existing startup_sample/fts_analysis validators, not altered aggregation.

First-data startup405ms; ten cold reopens305/291/315/308/308/313/303/1042/385/284ms. All11 valid with correct MainActivity, Status ok, TotalTime and Displayed corroboration; no launcher exclusion or replacement sample. This measures launch→initial Home display, not database-ready completion.

FTS dataset5000,20 warmups+100 samples/workload, DAO MATCH+entity materialization (excludes typing/debounce/render, asset opening/domain mapping). Hits1/1/100/0/5000. Maxima1.380472/1.369697/2.434672/1.553103/38.634472ms: all mandatory samples<50ms. Broad mean26.8161723ms: target<15ms NOT MET. Instrumentation1/1 PASS; exit0; raw preserved. Network restoration accepted=true.

B02 technical retest meets mandatory thresholds on this approved configuration/run, subject to evidence review B05. Historical LDPlayer FAIL remains; this is not a production optimization or same-device before/after result. No DAO change, result cap, reduced corpus or narrowed workload was used. A target miss is reported separately and not promoted to mandatory failure.

## CI and additional controlled retests

Both runs have artifact digests matching GitHub SHA256. Logs prove checkout merge e2c9ccbbed25bdad6f92b641703872fd6e0be664 for PR run42954377536 and exact0442777 for push run42954369143. Build toolchain/JVM is Temurin17.0.21. Each has596 JUnit tests,0 failures/errors/skips, including FTS20 and benchmark2. These tests precede the new local test-only additions.

Discovery per API26/API35 per workflow:10 independent cases PASS; suite twice each discovers11, passes10, skips1. The skip is failureArtifactProbe guarded by ui02FailureProbe; separately enabled probe intentionally fails1 test and verifies artifacts. It is not a skipped business case or product failure. API35 row interaction regression is verified; F01 FIXED_RETESTED for0442777, not full QA02 acceptance. Assertions cover synthetic pack order/visibility/details/IPA, activation persistence, no-active CTA, saved/catalog invariance, real EN-VI FTS, query/reset/retry/stale completion/lifecycle.

Current API30 retests:39 SQLite contracts+1 triple-tap PASS;8 independent UI components PASS;20 upgrade contracts PASS. Controlled02:5/5 PASS,0 skip:empty Progress/history;no active pack;fourth FORGOT from declared prior3-mistakes setup enables leech then HARD clears;REMEMBERED clears declared existing leech4;SQLite abort-trigger review failure keeps card/flipped state and full logical userdata unchanged, explicit UI recovery commits exactly1 event. These are controlled fixtures, not another continuous E2E. Raw source/APK hashes,logs,video and snapshots retained per-run; public summary linked below.

## Stop snapshot requested by user

User requested stop testing, commit and push on the existing branch. No new tests started after that request. Public evidence: evidence/qa-02/blocker-retest-20261111/summary.json. Raw APKs/internal corpus/logs/video remain ignored locally.

Privacy decision: user explicitly chose to retain Android backup and document the exception pending PO confirmation. Backup Manager observed disabled, with Google Backup transport selected; app allowBackup remains true. No backup transmission was triggered. F03 and NFR15 remain pending; no manifest change made.

Still open:remaining planner/UI quota/due/paused/overlap cases beyond covered contracts;Quiz pool<4 and actual double-tap UI gaps;Attribution external-link failure/return;remaining accessibility/device cases;privacy PO confirmation;release artifact/rights scope;reviewer raw evidence inspection and B05. B03 rights remains unsigned. Mandatory benchmark passed only the recorded approved API30 run;target<15ms broad query not met;historical failures retained. No self-approval or QA02 Done. Commit/push is explicitly authorized by the latest user instruction; no PR edit/comment/review or merge authorized.
