# VOCAB-02 — chi tiết từ và lưu cá nhân bền vững

PR #32 vào develop. Phụ trách Trường; reviewer Thành. Chưa tự nghiệm thu Done/merge.

Hoàn thiện VocabularyDetailFragment theo route stableId/packId TEXT của VOCAB-01: lemma/POS,
nghĩa/ví dụ Anh–Việt, IPA nullable/fallback, loading/error/retry, pending Save và UI light/dark.
GetVocabularyDetail/ToggleSaved qua domain repository, I/O trên worker; catalog chỉ đọc.

First-save tạo progress box0, dueAt=Save time, isSaved=true, timestamps học=null, counters0/leech=false.
Giữ nguyên baseline: tổng/box0 tăng một, từ rời new pool và eligible qua due khi pack active;
Save không tự activate, không tạo ReviewEvent hoặc tăng learned-today/pack learnedCount.
Existing progress/Unsave chỉ đổi flag, không xóa/reset dữ liệu học. Đây là contract kỹ thuật do người dùng
xác nhận, không phải bộ giá trị khởi tạo được SRS quy định sẵn.

Đã tích hợp develop **8209ef7** (DATA-06/DATA-07) tại code/tests **a99e742c67edda2ecbd0f3629f47ff551af1a972**.
Resolve RepositoryProvider giữ vocabulary()/VocabularyRepository, progress()/progressUseCase(),
recordReview(Clock, ZoneId) và Quiz policy loader PR #31. UserDao giữ cả first-save và các query
aggregation/snapshot/compare-and-commit. Không đổi schema hoặc new/due/stats queries.

Save là target command idempotent trong transaction/insert-if-absent. DATA-06 review dùng optimistic
snapshot: nếu Save đổi row giữa read/commit, review trả CONFLICT, không ghi event và không tự replay rating;
caller cần đọc lại và quyết định mới. Tests chứng minh không mất saved flag/history, lần đánh giá mới
chỉ tạo một event. Giữ policy Quiz fail-closed.

Validation tại SHA a99e742: **408 tests / 39 suites, 0 failures/errors/skipped** trên Robolectric API26/35;
assembleDebug/assembleDebugAndroidTest PASS; lint **0 errors/11 warnings**.
Full command: testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain.
Native SQLite dùng TEMP/TMP/java.io.tmpdir ngắn trên Windows. Tests gồm first-save→reopen→Unsave,
concurrency first-review, total/box0 qua DATA-07 thật, learned-today=0 khi chỉ Save, atomic rollback,
actual composition và accepted Quiz policy. Không dùng số liệu cũ 287 tests thay cho bản sau resolve.

[Validation sau resolve](vocab-02-data0607-integration.md) · [Contract saved-only](vocab-02-saved-only-decision.md)

AC-04 có host acceptance và [focused emulator QA 07/11/2026](vocab-02-device-qa.md) trên Small_Phone/API30:
V02-A..D PASS, gồm detail offline, Save/Unsave, real force-stop/relaunch, hai reboot offline,
bảo toàn existing progress/history/catalog, pending/rotation/loading và controlled error/Retry.
QA dùng package riêng và external instrumentation probe, không sửa production source.

IPA-present và lỗi đọc/ghi trên device có fixture/test hook ghi rõ, không bịa catalog QA hoặc disk failure.
Ma trận emulator API26/35, thiết bị vật lý và TalkBack toàn diện chưa chạy trong lượt này.
Không xác nhận content QA toàn catalog hoặc AC-12/13 toàn phần; chờ Thành nghiệm thu, không tự Done/merge.