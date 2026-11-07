# VOCAB-02 — validation và handoff

**Device QA 07/11/2026:** đã chạy bốn nhóm V02-A..D trên model Small_Phone/API30,
gồm force-stop và hai reboot offline. Xem [focused emulator evidence](vocab-02-device-qa.md).
Các ghi chú device NOT RUN bên dưới là trạng thái lịch sử trước lượt QA này.

**Tích hợp mới nhất:** develop `8209ef7` với DATA-06/DATA-07; xem
[validation sau resolve hiện hành](vocab-02-data0607-integration.md). Các số liệu trên base45db604 là lịch sử.

**Bản hiện hành:** first-save được sửa tại `016373c` theo contract due-now do người dùng chốt;
xem [first-save validation/evidence](vocab-02-first-save-validation.md). Các dòng BLOCKED dưới đây là lịch sử trước quyết định.

**Sau review 2482a4e:** đã tích hợp develop/Quiz policy tại `7be46fe`, xem
[review follow-up và evidence sau resolve](vocab-02-review-followup.md). R1 first-save còn BLOCKED.

**UI hiện hành:** code/tests `894db81`, xem [UI refresh và evidence mới](vocab-02-ui-validation.md).
Các SHA/kết quả bên dưới là validation ban đầu tại `582e412`, được giữ làm lịch sử.

Phụ trách: Trường. Reviewer: Thành. Phụ thuộc VOCAB-01.
Trạng thái: bản triển khai độc lập để review; **chưa đủ Done/AC-04**, không thay phân công/SRS.

## Baseline và triển khai

Working tree ban đầu sạch trên feature/pack-02-activation tại 4959cf6; giữ nguyên nhánh đó.
Fetch origin thành công, tạo feature/vocab-02-vocabulary-detail từ develop mới nhất
`46d6c18` (đã tích hợp PACK-02). Không commit/push develop hoặc main.
Không tìm thấy AGENTS.md trong repository/các thư mục cha đã kiểm tra.
Đã đọc README, setup/architecture, yêu cầu VOCAB-02 bên ngoài repo, các mục liên quan của
SRS Baseline v1.1 (FR-008/009/010/017/042, BR-02, UC-02, AC-04, DEC-14/15), source và handoff.

**SHA code/tests kiểm thử: `582e41205d71ee395030fecf478271f848c54f08`.**
Tài liệu/evidence commit sau không thay đổi code/tests.

Kế thừa một VocabularyDetailFragment, destination vocabularyDetailFragment, helper
arguments(packId, stableId), vocabularyId/packId bắt buộc TEXT và Back về pack của VOCAB-01.
Không trim/parse ID; kiểm tra membership qua read-only CatalogRepository.
Không dựng lại list; PackDetailFragment hiện có refresh khi resume.

GetVocabularyDetail và ToggleSaved điều phối qua VocabularyRepository; RoomVocabularyRepository
ghép catalog với userdata, không expose entity cho UI. RepositoryProvider/PacksViewModelFactory
mở DB lazy trên serial worker, đóng sau work khi ViewModel cleared. Fragment không gọi DAO/Room.

UI hiện lemma/POS/meaningVi/exampleEn/exampleVi/IPA, null hoặc legacy blank IPA dùng chính xác
“Chưa có phiên âm”. Meaning/example thiếu hiện “Chưa có dữ liệu”, không sinh nội dung hoặc sửa catalog.
ScrollView/multiline, targets min48dp, pending/error live-region và nhãn Lưu/Bỏ lưu rõ ràng.

Save dùng command trạng thái đích, transaction UPDATE riêng isSaved, affected count=1 mới success.
Không dùng stale progress/upsert; review hiện có đọc/cập nhật trong transaction nên giữ flag.
Không có post-commit reread có thể gây báo rollback giả: committed UPDATE là xác nhận.
Retry luôn đọc persisted state, không phát lại mutation. Double tap/pending chặn ghi chồng;
generation/owner lifecycle loại callback cũ; resume/reopen đọc lại state.

## Saved-only blocker

[Đề xuất và tác động chi tiết](vocab-02-saved-only-decision.md): **PENDING**, cần Thành/Nhân xác nhận.
Không tự tạo progress, schema/filter hay thay new/due/stats. UPDATE 0 trả SAVED_POLICY_REQUIRED;
UI báo phần lưu từ chưa có tiến độ chưa hoàn thiện, không coi là success.
Save cho progress có sẵn hoạt động; từ chưa học vẫn có nút Lưu nhưng thao tác đang BLOCKED.
Không coi hành vi tạm thời này là hoàn thành FR-VM-009.

## Lệnh và evidence

Windows, JDK17 `C:/Users/LMT/.jdks/vocamind-temurin-17`,
GRADLE_USER_HOME=`C:/Users/LMT/.gradle`. Host Robolectric API26/35, SQLite native;
test đo chữ dài dùng native graphics (legacy text metrics không đủ cho phép đo này).

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VocabularyDetailTest' --tests '*VocabularyListTest' --tests '*RepositoryIntegrationTest' --offline --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
.\gradlew.bat connectedDebugAndroidTest --offline --console=plain
git diff --check
```

Focused tests PASS ở tiền thân code/tests; full run tại SHA trên bao gồm phiên bản cuối của mọi suite.
Full run **243 tests / 24 suites, 0 failures, 0 errors, 0 skipped**; BUILD SUCCESSFUL.
assembleDebug và assembleDebugAndroidTest PASS. lintDebug PASS: **0 errors / 6 warnings**
(dependency update, overdraw, resources cũ chưa dùng và application icon hiện có; không thêm dependency).
git diff --check PASS. Không nhận các task NO-SOURCE như bằng chứng test.
Kết quả full và hashes: [evidence](validation/vocab-02/local-evidence.json),
[build log](validation/vocab-02/build.log), [lint](validation/vocab-02/lint-results.txt).
XML trọng tâm được giữ trong cùng thư mục. Report HTML local:
app/build/reports/tests/testDebugUnitTest/index.html; app/build/reports/lint-results-debug.html.
APK local: app/build/outputs/apk/debug/app-debug.apk;
app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk.

ADB không có thiết bị; connectedDebugAndroidTest đã thử, FAIL “No connected devices!”.
[Connected log](validation/vocab-02/connected.log) ở tiền thân code/tests, chỉ chứng minh không có thiết bị.
**Instrumentation NOT RUN**; build test APK không phải chạy instrumentation.

## Ma trận yêu cầu → evidence

| Ca | Test / mức kiểm chứng |
| --- | --- |
| T01/T02 rendering, nullable/blank IPA | VocabularyDetailTest.renderAllFieldsNullBlankIpaAndReplacePreviousWordWithoutInventingContent, fixture độc lập kiểm tra mọi field và placeholder; PASS host API26/35 |
| T03 argument/not-found/membership | vocab02MissingProgressIsBlockedNotFalseSuccessAndPoolsAreUntouched; VocabularyListTest.routePreservesOpaqueTextAndRejectsMissingArguments; realGraphDispatchReceiveBackAndRecreation; PASS host |
| T04 save/unsave | vocab02SaveUnsaveReopenOnlyChangesFlagAndCatalogIsReadOnly: giữ tất cả field học, toàn bộ snapshot userdata trở lại như cũ sau unsave; PASS |
| T05 từ chưa học | Test xác nhận UPDATE 0 không success, không tạo row; hành vi persist saved-only **BLOCKED** quyết định Thành/Nhân |
| T06 retry/idempotent | Repository test lặp set true; missing-row false cũng BLOCKED. pendingBlocksDoubleTapAndResumeRetryReconcilesAmbiguousCommitWithoutReplay kiểm tra retry không replay/đảo flag; PASS phần độc lập |
| T07 persistence | File-backed close/open true rồi false; real graph Back/reopen và recreation đang Save; PASS host. Process death/force-stop/reboot thiết bị **NOT RUN** |
| T08 read-only catalog | Snapshot mọi bảng catalog (gồm FTS/blob) trước/sau, userdata sau unsave bằng snapshot đầu; PASS |
| T09 concurrency | vocab02SaveConcurrentWithReviewRetainsBothAndHistory: 8 vòng 2 threads bắt đầu cùng latch, giữ flag/due/counters/leech/history; PASS. First-save tạo row/first-review **BLOCKED** policy |
| T10 failures | vocab02WriteFailureReadFailureAndIdempotentReconciliation: trigger abort UPDATE, closed userdata/catalog, retry đích và reopen. ViewModel fake ambiguous commit + read failure + reconcile; PASS |
| T11 lifecycle | Real graph rotation khi Save pending và reopen; double tap/refresh guard, generation và owner clear loại late callbacks; PASS host. Fast-switch stress nhiều từ/load rotation trên thiết bị **NOT RUN** |
| T12 shared/inactive/paused | Cùng shared stableId đọc A/B, không tự tạo UserPack; regression vocabularyBrowsingIsReadOnlyAcrossInactivePausedAndFileReopen; PASS |
| T13 new/due/stats | Blocked-save giữ toàn snapshot/pools không đổi; không có saved-only policy đã duyệt nên ảnh hưởng representation cuối **BLOCKED**, không suy từ test này thành đã đạt |
| T14 offline | Host local assets/Room và --offline PASS, không đổi manifest/network dependencies. Airplane Mode từ first launch và Save→app reopen thiết bị **NOT RUN** |
| T15 long text/small font/Back | longExamplesRemainScrollableAtLargeFontOnSmallScreen: 320×480dp, fontScale1.5, không ellipsize, content vượt viewport, target48dp; Back thật trong real graph PASS host. Screenshot/TalkBack/device visual QA **NOT RUN** |

Full regression bao gồm PACK-02, VOCAB-01, repository/review/planner/progress/quiz và các suite hiện có.
Không gán host tests thành bằng chứng thiết bị; không xác nhận content QA toàn catalog, AC-12 hoặc AC-13.

## Hướng dẫn reviewer

1. Cài debug APK, Home → Gói học → chọn pack (kể cả inactive/paused) → chọn từ.
   Đối chiếu lemma/POS/nghĩa/ví dụ và IPA với catalog. Null IPA dùng fixture host nếu catalog thật không có ca đó.
2. Với từ có progress từ luồng học hiện có: Lưu → pending → Bỏ lưu. Back/mở lại phải vẫn Bỏ lưu;
   nhấn Bỏ lưu, đóng/mở lại phải hiện Lưu. Không cần activate pack để xem detail.
3. Nếu chưa có progress, click Lưu phải báo chưa hoàn thiện, không hiển thị Bỏ lưu thành công.
   Không tự seed/sửa asset production để vượt acceptance. Host tests seed DB test độc lập để kiểm chứng Save.
4. Thử double tap, rotation trong pending, Retry sau lỗi. Retry đọc lại trạng thái, người dùng quyết định thao tác tiếp.
5. Có thiết bị API26 và API mới: chạy connected tests, Airplane Mode first launch, app force-stop/reopen,
   font lớn/màn nhỏ/TalkBack và chuyển nhanh nhiều từ; bổ sung SHA/evidence riêng.

AC-04: detail/rendering/read-only và Save existing progress có evidence host; **chưa đạt toàn phần**
do Save từ chưa học chờ quyết định/persist, cùng các kiểm chứng thiết bị chưa chạy.
Nháp PR: [vocab-02-pr-draft.md](vocab-02-pr-draft.md). Không tạo PR/merge hoặc đánh dấu task Hoàn thành.
