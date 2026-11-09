# SEARCH-01 / VOCAB-02 — regression các task liên quan

**C?p nh?t publish:** source/test ?? ki?m ch?ng t?i commit `8b06d1a8287f1571120f6889e4ba4b8616a79241`.
Regression cu?i: 46/46 instrumentation fixture4 PASS (first-launch ch?y ri?ng 1/1 PASS),
QA150 search/full detail/retry/5000 rows ch?y l?i 3/3 PASS. Ng??i d?ng y?u c?u commit/push;
kh?ng t?o PR/merge. C?c ghi ch? ch?a commit b?n d??i l? tr?ng th?i l?c thu evidence.
AC-05/NFR-VM-03 v?n HOLD.

08/11/2026, base HEAD `e12f0ee`, production source giữ nguyên bản tối ưu local trong
[báo cáo hiệu năng](search-01-performance-validation.md). Lượt này chỉ sửa một
Android instrumentation test; không sửa sản phẩm, SRS hoặc phân công. Chưa commit/push.

## Coverage và kết quả thực chạy

- Full host run gần nhất: **519 tests/51 suites PASS**, API26/35. Đã đối chiếu JUnit:
  VocabularyDetail/VocabularyList/PackVocabularyIntegration/PackActivation,
  TodayRendering/PlanToday, FlashcardViewModel/LearningFlowIntegration,
  QuizScreen/QuizViewModel/domain quiz, RepositoryIntegration, AtomicReview,
  ProgressAggregation và search/race/retry. Đây là host tests, không gọi là QA thủ công.
- QA150 bản tối ưu trước lượt này: **4/4 instrumentation PASS** gồm catalog search,
  full detail/Save/Back/recreation, Error/Retry thật, 5000-row scroll và diagnostic.
- Bổ sung instrumentation 5 class: PackActivation, QuizScreen, AtomicReview,
  ProgressAggregation, QuizCompletion (migration/idempotency).
- Lượt đầu trên QA150: **47 run /24 fail**. Các contract data dùng ID cố định fixture4,
  không tương thích catalog150; giữ toàn bộ log, không gán PASS cho lượt này.
- Build package riêng `.searchregression` bằng Gradle init script, catalog fixture4
  đúng contract, không clear/wipe dữ liệu app hiện có: **47 run /46 PASS /1 FAIL**.
  Ca first-launch thất bại vì các ca trước đã activate pack; suite này không thể coi xanh.
- Chạy riêng first-launch trên package mới `.searchfirstlaunch`: tái hiện lỗi test
  chờ `route_message` của detail preview cũ, timeout20s sau khi click word.
  Expected hiện hành là full detail VOCAB-02; đây là **test assertion lỗi thời**,
  không phải bằng chứng pack navigation hỏng. Ảnh hưởng: chặn instrumentation
  regression pack. Đề xuất/sửa: assert lemma/nghĩa, Save enabled và cả stableId/packId.
- Chỉ cập nhật assertion đó, giữ các bước activate/list/Back/rotation/pause.
  Build và chạy trên package mới `.searchfirstlaunch2`: **1/1 PASS** (4,061s).
  Như vậy có evidence PASS cho 47 ca riêng biệt qua các lượt, **không có một lượt
  full suite47 PASS sau sửa**. Không xóa/che log thất bại.

Không phát hiện lỗi sản phẩm cần sửa trong coverage đã chạy. Chưa thể khẳng định
mọi task không bị ảnh hưởng ngoài phạm vi này. Flashcard/Today có host coverage;
Today→pack được ca first-launch kiểm tra trên emulator, chưa QA thủ công toàn bộ
Flashcard session hoặc Quiz production10questions. QuizProductionFlow đòi package
và fresh-state riêng nên **NOT RUN**; không tính assumption skip thành PASS.

## Evidence và tái lập

[Thư mục evidence](evidence/search-01-performance-local/):
`cross-task-qa-instrumentation.txt` (lượt QA150 lỗi fixture),
`cross-task-fixture4.txt`, `cross-firstlaunch.txt`, `cross-firstlaunch-final.txt`,
build logs, `cross-task-source.json`, `search-regression.init.gradle` và
`search-firstlaunch2.init.gradle`. Init script chỉ đổi applicationId/assets cho
build cục bộ, không thay source/catalog production. Hai package mới giữ nguyên để
kiểm tra, không uninstall/clear. Test pack thực hiện activate/pause có chủ đích;
không tuyên bố dữ liệu QA trước/sau bất biến.

Lệnh build: cached Gradle8.9/JDK17 với `-PcatalogQa=true -I <init-script>` và
`assembleCatalogQa assembleCatalogQaAndroidTest --offline`, sau đó `adb install -r -t`.
Lệnh ca cuối:

```powershell
& $adb shell am instrument -w -r -e class 'com.vocamind.app.feature.packs.PackActivationInstrumentedTest#offlineFirstLaunchCtaActivateVocabularyBackPauseAndConfirmedRotation' com.vocamind.app.searchfirstlaunch2.test/androidx.test.runner.AndroidJUnitRunner
```

File APK tại đường dẫn QA chuẩn đã được trả về đúng APK QA150 tối ưu từ package
đang cài (app hash `b85a4ab7...`, test hash `a507991d...` trong verification trước).
Source manifest hiệu năng là snapshot trước sửa AndroidTest pack; hash test mới
nằm ở `cross-task-source.json`, không sửa lại provenance lịch sử. Full host suite
không chạy lại sau thay đổi chỉ ở AndroidTest. `git diff --check -- app`: PASS.

AC-05/NFR-VM-03 vẫn **HOLD** theo báo cáo hiệu năng. Bước tiếp theo: review thay đổi
test, chạy các nhóm first-launch trong installation riêng khi đưa vào CI/device QA,
và benchmark thiết bị tham chiếu; không đổi điều kiện sản phẩm để làm suite xanh.

Logs text ???c chu?n h?a UTF-8/LF v? b? trailing padding; gi? n?i dung l?i/k?t qu?. Raw samples JSON kh?ng ??i.
