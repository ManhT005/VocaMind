# VOCAB-02 — chi tiết từ và lưu cá nhân bền vững

Base: develop. Phụ trách Trường; reviewer Thành. Đây là bản nháp, chưa tạo PR.

Hoàn thiện VocabularyDetailFragment kế thừa route vocabularyId/packId TEXT của VOCAB-01:
lemma/POS/nghĩa/ví dụ Anh–Việt/IPA nullable, loading/error/retry, UI light/dark và nút Save cố định.
GetVocabularyDetail/ToggleSaved đi qua domain repository, I/O trên worker; catalog chỉ đọc.

Save từ chưa học nay persist được: tạo progress nếu chưa có với isSaved=true, box0, dueAt=Save time,
timestamps học null và counters0. Query planner/thống kê giữ nguyên: tổng/box0 tăng một, từ rời new
và có thể vào due khi pack active; Save không tự activate, không ghi ReviewEvent hoặc tăng learned-today.
Đây là [contract triển khai đã được người dùng chốt](vocab-02-saved-only-decision.md), không phải
bộ giá trị khởi tạo được SRS quy định sẵn. Unsave giữ progress/lịch sử; existing progress chỉ đổi flag.
Target command idempotent, transaction/insert-if-absent bảo vệ race với first-review; Retry đọc lại persisted state.

Đã tích hợp develop 45db604 và resolve RepositoryProvider giữ cả vocabulary repository lẫn Quiz policy
loader PR #31. Không đổi schema, new/due/stats queries, search, Flashcard hoặc TTS.

[Validation first-save mới nhất](vocab-02-first-save-validation.md): code/tests 016373c,
full regression gồm production composition/reopen, UI first-save không seed progress,
first-save/first-review trên hai DB connections, snapshot catalog, total/box0/new/due/learned-today
và trigger rollback/ignored insert. Build hai APK và lint có evidence riêng đúng SHA.
Kết quả: 287 tests/31 suites PASS, 0 failures/errors/skipped; build PASS, lint 0 errors/11 warnings.

AC-04 có acceptance host cho detail/save/unsave/reopen; device Airplane Mode/process restart/TalkBack
vẫn NOT RUN, không xác nhận QA nội dung toàn catalog hoặc AC-12/13 từ task này.
Chờ reviewer nghiệm thu; không tự đánh dấu task Hoàn thành hoặc merge.
