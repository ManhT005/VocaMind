# VOCAB-02 — follow-up review 2482a4e

**Cập nhật mới:** R1 đã được triển khai tại `016373c` sau khi người dùng chốt contract due-now;
xem [first-save acceptance](vocab-02-first-save-validation.md). BLOCKED bên dưới là trạng thái lịch sử tại 1dabe27.

Ngày 06/11/2026. Phụ trách Trường, reviewer Thành.
Review đầu vào: VOCAB-02-review-2482a4e.md do người dùng cung cấp ngoài repository.
Không tạo PR/merge vào develop, không đánh dấu Done hoặc tự sửa SRS.

## I1 — đồng bộ develop và resolve conflict

Fetch origin/develop: **45db6049b3d8885bfab5570e4e70e03b0cf04511** (PR #31/QUIZ-02).
Working tree ban đầu sạch trên feature/vocab-02-vocabulary-detail tại 2482a4e.
Merge develop vào feature; conflict duy nhất ở RepositoryProvider.java.

Merge SHA: 7be46fe1d1adb0cadf564cd364859fdd2d29f027.
**SHA code/tests kiểm thử cuối: 1dabe27484e41282c02c2e5535c3b468d033d175.**

Resolve giữ:

- VocabularyRepository/RoomVocabularyRepository, vocabulary() và wiring catalog/userdata.
- Constructor RoomQuizRepository ba tham số và loader QuizPolicyAsset trên develop.
- Loader dựng toàn bộ QuizVocabulary từ catalog rồi validate companion policy;
  không thay bằng unrestricted hoặc bỏ policy khi gặp lỗi.

Đã rà auto-merge RepositoryIntegrationTest: giữ cả các ca VOCAB-02 lẫn accepted-policy
Room snapshot/generation và new Quiz regression; unrestricted chỉ dùng trong fixture test tổng quát,
không đi vào production composition. docs/README giữ links cả hai feature.

Mở rộng QuizPolicyCompositionTest.defaultUseCaseLoadsPolicyBoundToBundledCatalogAndVocabularyRepository:
cùng RepositoryProvider đọc detail, tạo review thật trong test, Save/Bỏ lưu progress có sẵn;
Quiz vẫn fail-closed với fixture đồng lemma theo policy và không làm mất saved flag.
Đây không phải acceptance first-save cho userdata trống.

Full run đầu sau merge có 279 tests, 1 failure: GenerateQuizTest đếm cứng 7 repository reads
trong khi duyệt mọi RepoError và VOCAB-02 thêm SAVED_POLICY_REQUIRED. Sửa assertion thành
RepoError.values().length + 2 (mỗi error, một invalid snapshot, một empty snapshot), giữ kiểm tra
identity từng lỗi; không sửa production Quiz policy hoặc bỏ test. Chạy lại toàn bộ tại SHA cuối.

## R1 — first-save vẫn BLOCKED, chưa sửa

Review yêu cầu: “Chốt representation/semantics của từ chỉ được lưu với người phụ trách data”
và không coi đề xuất PENDING thành quyết định đã chấp thuận. Review chưa phê duyệt representation,
giá trị ban đầu/new/due/stats hoặc CR cho FR-VM-017/042.
Đã gửi câu hỏi xin contract Thành/Nhân trong phiên làm việc; chưa nhận được quyết định tại lúc ghi tài liệu.

Không tự insert progress/sentinel, thêm filter hoặc đổi schema để vượt test. Hành vi hiện tại
userdata trống → Save trả SAVED_POLICY_REQUIRED → reopen saved=false vẫn là **FAIL FR-VM-009**.
Test quan sát blocker không được coi là nghiệm thu first-save PASS.

[Decision pending](vocab-02-saved-only-decision.md) có representation và tác động các phương án.
Đề xuất UX mới nhất trong trao đổi: Save không đồng nghĩa đã học; new vẫn giữ từ chỉ lưu,
due không chọn; pack learnedCount theo learnedAt vẫn 0. Nếu tổng/box của Progress cũng không tăng,
cần CR cho literal FR-VM-042; nếu giữ literal thì phải chấp nhận tổng/box0 tăng.
Chưa có căn cứ để chọn thay reviewer/data owner.

Sau khi có contract: triển khai conditional first-save trong transaction, preserve flag khi
first-review đồng thời, no-op unsave missing theo quyết định; acceptance userdata trống → Save
→ DB reopen true → Unsave → reopen false, first-save/first-review concurrency và new/due/
learned-today/total/box theo contract cuối. AC-04/VOCAB-02 chưa đủ nghiệm thu cho đến khi hoàn tất.

## Kiểm chứng SHA tích hợp

Windows/JDK17, Gradle8.9, Robolectric API26/35; chạy:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
git diff --check
```

Kết quả, counts và hash artifacts: [evidence](validation/vocab-02/integration-review/local-evidence.json).
[Build log](validation/vocab-02/integration-review/build.log),
[lint](validation/vocab-02/integration-review/lint-results.txt), XML trọng tâm cùng thư mục.
Kết quả cũ 245 tests tại 2482a4e không được dùng thay kết quả sau merge.

Kết quả cuối: **279 tests / 31 suites, 0 failures, 0 errors, 0 skipped**;
BUILD SUCCESSFUL. assembleDebug/assembleDebugAndroidTest PASS; lintDebug **0 errors/11 warnings**.
git diff --check PASS; origin/develop là ancestor của HEAD nên conflict I1 đã được xử lý trong nhánh.
Code/tests không đổi trong commit tài liệu/evidence sau đó. Initial failed log được giữ riêng,
không trộn với XML/kết quả cuối. P1 first-save không được tính thành acceptance PASS từ regression xanh.

Device QA vẫn **NOT RUN**: chưa có thiết bị được cung cấp/kết nối trong phiên trước;
không suy host recreation/Room reopen thành process death, Airplane Mode hoặc reboot trên thiết bị.
Không tự mở emulator hoặc thay dữ liệu production để nhận nghiệm thu.
