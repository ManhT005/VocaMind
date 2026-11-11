> Latest review of tested source5713ba6: [device results and limitations](qa-02-review-5713ba6-20261111.md), [requirement results](qa-02-review-5713ba6-results.csv), [findings](qa-02-review-5713ba6-findings.md). API26 full journey/reopen/reboot PASS; Realme API33 animation0x journey/reopen/physical reboot PASS, animation1x mismatch remains unresolved. TalkBack partial only; API35 full journey NOT RUN. Rights, privacy/backup, WEEK-2 binding and reviewer sign-off remain open. Earlier status paragraphs below are historical. This is documentation publication, not a fresh test run.

# Bản nháp cập nhật PR55 — chưa gửi GitHub

Review source 5713ba6 và evidence hiện hành đã hoàn tất trong phạm vi local. CI push42990358456/PR42990368223 xanh; 599 unit tests/workflow không fail/error/skip, Discovery API26/API35 có expected guarded probe skips và intentional failure probe riêng. Đây không phải chứng nhận toàn QA-02.

QA150 journey trên API26 và Realme API33 animation0x, force-stop/reboot persistence đạt với source/APK/fixture được khóa. Mỗi thiết bị đạt10 controlled cases; API26 native SQLite ordering oracle đạt. Realme animation1x vẫn tái hiện expected FORGOT/actual HARD; retest0x không đóng nguyên nhân này. Browser error/return API26 được bổ sung; host cleanup exit1 được giữ và có verification khôi phục riêng.

Realme startup486ms lần đầu/366..400ms cold; FTS5000 broad mean31.881/max35.336ms, đạt mandatory50ms nhưng chưa đạt target15ms. Không tối ưu source hoặc thay protocol; không gán Realme thành thiết bị chuẩn SRS. Historical LDPlayer FAIL giữ nguyên; quyết định thay thế đã duyệt chỉ áp dụng cấu hình API30 được ghi trong hồ sơ.

Đề nghị reviewer disposition R01 journey synchronization, R02 runner portability và R03 recording/evidence validation trước merge nguyên trạng. Full TalkBack và API35 QA150 chưa đủ; B03 quyền snapshot, PO backup/privacy, WEEK-2 scope binding và B05 sign-off còn mở. Không đề nghị QA-02 Done/MVP acceptance tại thời điểm này.

Evidence private giữ local; không đính kèm/phát tán APK, corpus, video hoặc dữ liệu cá nhân. Đây chỉ là bản nháp, không phải approval hoặc review đã gửi.


---
Historical draft below:

Latest CI: commit5713ba6 push42990358456 and PR42990368223 both SUCCESS;599 unit tests/workflow0 fail/error/skip. API26/API35 synthetic Discovery offline helper retest verified; F05 closed within that scope. Full QA150 platform journeys and human gates remain open. Earlier candidate wording below describes the pre-push state.

# QA-02 extend controlled UI coverage and apply offline controller state

The pushed head ba02a88 has green PR CI but its push workflow failed Discovery API35 before app tests: cellular network100 remained active after settings-only offline setup. The local change applies airplane mode through ConnectivityService for setup/restoration, retains strict effective-network checks, and handles explicit unsupported-command responses on API26/27 without accepting an active network.

Added controlled UI checks for paused/no-due planner states, due-first/remaining quota/paused exclusion, shared-pack dedup with quota8 over4 unique words, Quiz empty pools0/3, actual double-tap answer locking, offline external-link error/return, actual IME/Back state, longest packaged detail at effective font130%, and measured48dp action targets. Fixtures, renderer contracts and production journeys are reported separately.

Validation:27 network host tests,8 CI-device host tests and4 QA host tests passed; API30 Discovery independent cases plus suites twice/probe passed; font130% journey/reopen/reboot passed. CI artifacts for ba02a88 contain598 passing unit tests, including FTS22 and benchmark2. The new local helper still needs API35/API26 CI; no full QA02 PASS is claimed.

Final consolidated API30 regression:12/12 controlled UI cases passed with0 skips at effective font1.3, including actual IME. Per-case logs/video/snapshots and source/APK hashes retained privately; network/configuration restoration checked. Matrix now distinguishes historical emulator rows from current approved API30 technical evidence. Only API30 AVD is configured locally; full QA150-word API26/API35 journeys remain unrun.

Mandatory benchmark thresholds passed on the explicitly approved API30 release-based run; broad FTS15ms target remains missed. TalkBack is deferred with user permission and remains NOT RUN. Android backup is retained by user choice pending PO privacy confirmation. Content rights and Nhân sign-off remain open. No merge or approval requested by this draft.
