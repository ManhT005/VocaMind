# Draft: VOCAB-02 — chi tiết từ và lưu progress hiện có (saved-only còn blocked)

Base dự kiến: develop. Tác giả phụ trách: Trường. Reviewer: Thành; cần Nhân xác nhận data contract.
Đây là nội dung nháp local, chưa tạo PR.

Follow-up review: tích hợp develop `45db604` tại `7be46fe`, resolve RepositoryProvider giữ cả
vocabulary repository và Quiz policy loader PR #31. [Validation sau resolve](vocab-02-review-followup.md).
P1 first-save vẫn BLOCKED chờ Thành/Nhân chốt contract; chưa đủ approve tính năng hoàn chỉnh.

UI refresh tại `894db81`: thêm thanh tiêu đề/Back, nhóm nghĩa và ví dụ thành thẻ,
nội dung cuộn với nút Lưu cố định phía dưới, màu light/dark; bỏ Retry cho saved-only/not-found.
Xem [validation UI mới nhất](vocab-02-ui-validation.md) cho SHA/kết quả/ảnh host-rendered.

VOCAB-01 mới mở receiver kiểm tra ID/pack. Thay receiver đó bằng màn detail đọc lemma, POS,
nghĩa/ví dụ Anh–Việt, IPA hoặc “Chưa có phiên âm”, giữ nguyên route stableId TEXT và Back.
GetVocabularyDetail/ToggleSaved đi qua domain repository, DB mở/query/write trên worker.

Lưu/Bỏ lưu progress hiện có dùng target command idempotent và UPDATE duy nhất isSaved trong transaction,
giữ nguyên tiến độ/lịch sử; pending chặn double tap, Retry reconcile persisted state và không replay mutation.
Catalog chỉ đọc. Reopen/resume/recreation đọc đúng flag; thiếu nội dung hiện trạng thái thiếu minh bạch.

**Blocker trước nghiệm thu VOCAB-02/AC-04:** chưa có saved-only contract đã được Thành/Nhân xác nhận.
UPDATE 0 trả SAVED_POLICY_REQUIRED, không insert progress mặc định hoặc báo lưu thành công.
[Đề xuất representation/new/due/stats và first review](vocab-02-saved-only-decision.md).
Không đổi schema, planner/stats, search, Flashcard hay TTS.

Validation: full host Robolectric API26/35 + assembleDebug/assembleDebugAndroidTest/lintDebug,
243 tests, 0 failures/errors/skipped; lint 0 errors/6 warnings tại code SHA 582e412.
chi tiết SHA/kết quả/hashes tại [validation](vocab-02-validation.md).
Tests bao gồm rendering/null IPA, failure/retry, pending/recreation, file-backed persistence,
snapshot catalog/userdata và Save đồng thời review. Instrumentation/Airplane Mode/app process restart
trên thiết bị NOT RUN vì không có thiết bị. Content QA toàn bộ catalog chưa được xác nhận từ task này.

Đề nghị reviewer kiểm tra diff, chốt blocker saved-only và yêu cầu device QA trước nghiệm thu;
chưa tự đánh dấu task Hoàn thành. Không merge từ bản nháp này.
