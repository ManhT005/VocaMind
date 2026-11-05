# VOCAB-01 — validation và handoff

Phụ trách: Trường. Reviewer: Thành. Chờ review; không tự đánh dấu Hoàn thành.
Đã đọc SRS v1.1 và yêu cầu VOCAB-01 ngày 06/11/2026; không sửa SRS/phân công.

## Baseline / SHA

Working tree ban đầu sạch trên feature/pack-02-activation, nhánh này được bảo toàn.
Fetch origin thành công. Tạo feature/vocab-01-vocabulary-list từ develop mới nhất:
6420377a7f59b62fd801046aa626f593d66e0918.

**SHA code/tests đã kiểm thử: b09d08fa6f2f4da2a98d460abfa57e4de39b091e.**
Commit tài liệu/evidence sau đó không thay đổi code/tests.
### Lưu ý tích hợp PACK-02

Theo kết quả mô phỏng ghép do reviewer cung cấp, với nhánh feature/pack-02-activation tại 0512417 có conflict ở:

- app/src/main/java/com/vocamind/app/feature/packs/AsyncListViewModel.java
- app/src/main/java/com/vocamind/app/feature/packs/PackDetailFragment.java
- app/src/main/res/values/strings.xml
- docs/README.md

PACK-02 chưa nằm trong develop đã fetch ở baseline trên. Đây là lưu ý tích hợp giữa hai feature branches, không phải conflict hiện tại của VOCAB-01 với develop. Lượt cập nhật tài liệu này không thực hiện merge hoặc chạy lại mô phỏng.

Nhánh merge sau phải giữ generation/refresh và mutation/write guard của PACK-02; đồng thời giữ PackWordAdapter, row navigation, reload và strings của VOCAB-01. Không chọn toàn bộ một phía để giải quyết conflict. docs/README.md cần giữ liên kết tài liệu của cả hai task.

PacksViewModelFactory được Git auto-merge trong mô phỏng nhưng vẫn cần kiểm tra cả hai hành vi sau tích hợp: activate/pause và reconcile sau lỗi ghi của PACK-02; browsing read-only, missing arguments, progress reload và navigation stableId của VOCAB-01. Chạy lại tests của cả hai feature và full unit/build/lint trên SHA tích hợp; evidence hiện tại chỉ áp dụng cho SHA VOCAB-01 đã ghi, chưa xác minh tổ hợp hai feature.

## Triển khai / quyết định

Một danh sách duy nhất trong PackDetailFragment của PACK-01; adapter được tách thành PackWordAdapter dùng ListAdapter/DiffUtil. Không thêm danh sách vào VocabularyFragment hoặc query/mapping progress mới.

Boundary GetVocabularyByPack được đáp ứng bằng API GetPackWords.execute(packId) → PackRepository.getPackWords → RoomPackRepository hiện có; ghi chú trong JavaDoc thay vì thêm delegate dư thừa. Domain giữ Java thuần/StudyItem. Giữ query ORDER BY PackWord.orderNo, VocabularyEntry.id và map progress theo stableId TEXT; UI không sort.

Giữ lemma/POS/meaningVi, thêm IPA. Null hoặc legacy blank dùng đúng “Chưa có phiên âm”; blank là phòng vệ hiển thị cho gap dữ liệu legacy, không sửa catalog. Bind lại mọi text và click target. DiffUtil so identity bằng stableId TEXT và contents bằng trường hiển thị; không parse số/FTS rowid. Row có ripple, focus, minHeight 48dp.

Policy nhãn đề xuất để Thành xác nhận: null progress hoặc learnedAt=null → “Chưa có lượt ôn”, kể cả saved-only; nhất quán learned count hiện có. learnedAt!=null: box0 “Học lại”, box1..5 “Cấp ôn N”. Không sửa timestamp/counters/saved hoặc định nghĩa thống kê.

Giữ loading/content/empty/error/retry hiện có. Missing arguments không crash Fragment/factory. onResume refresh từ repository khi Back/mở lại; generation loại kết quả cũ đang chờ main queue. I/O chạy serial worker, DB đóng theo ViewModel owner. Observer theo view lifecycle; adapter không capture Fragment. PREVENT_WHEN_EMPTY trì hoãn restoration đến khi có dữ liệu; scroll restoration trên thiết bị cần QA.

Inactive/paused vẫn browse được. List/receiver chỉ đọc, không activate hay tạo progress.

## Kiểm chứng thực chạy

Windows/JDK17: C:/Users/LMT/.jdks/vocamind-temurin-17.
GRADLE_USER_HOME=C:/Users/LMT/.gradle.
Robolectric SQLite native API26/35 là host tests, không phải thiết bị.

Lệnh focused:
.\gradlew.bat testDebugUnitTest --tests '*VocabularyListTest' --tests '*RepositoryIntegrationTest' --offline --console=plain

Lệnh full tại SHA kiểm thử:
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain

- Focused PASS trên tiền thân SHA cuối; full run gồm toàn bộ focused classes phiên bản cuối.
- Full **211 tests, 0 failures, 0 errors, 0 skipped**, 20 suites; BUILD SUCCESSFUL.
- assembleDebug, assembleDebugAndroidTest PASS. Build test APK không phải chạy instrumentation.
- lintDebug PASS: 0 errors/6 warnings; xem report local. Không nâng dependencies ngoài scope.
- git diff --check PASS.
- Các lỗi ở lượt đầu (snapshot BLOB của FTS, child FragmentManager trong test, lint DiffUtil) đã sửa trước lượt cuối.
- Evidence: [validation/vocab-01](validation/vocab-01/), gồm local-evidence.json (SHA, suite counts/XML hashes/APK hashes), build.log, connected.log và XML 2 lớp trọng tâm.
- Report local: app/build/reports/tests/testDebugUnitTest/index.html và app/build/reports/lint-results-debug.html.
- APK local: app/build/outputs/apk/debug/app-debug.apk; app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk.

adb devices -l trả danh sách rỗng.
.\gradlew.bat connectedDebugAndroidTest --offline --console=plain đã thử và FAIL: “No connected devices!”
**Instrumentation NOT RUN**. Không có screenshot thiết bị.

## Ma trận evidence / giới hạn

| Ca | Evidence / kết quả |
| --- | --- |
| T01 ordering | RepositoryIntegrationTest.packWordsKeepOrderNoAndMergeProgressByStableId: SHARED đứng trước W1 dù ID/lemma xếp sau; PASS API26/35. Adapter không sort; đối chiếu toàn UI trên thiết bị pending. |
| T02 membership | vocabularyBrowsingIsReadOnlyAcrossInactivePausedAndFileReopen: A/B có từ riêng/shared, cùng progress; PASS API26/35. |
| T03 inactive/paused | Cùng test: A chưa có UserPack, B paused vẫn đủ từ; snapshot không đổi; PASS. |
| T04/T05 status | VocabularyListTest.recycledHolderReplacesIpaStatusAndClickTargetAfterRefresh: box0..5, saved-only/learnedAt=null; PASS. Policy nhãn chờ Thành xác nhận. |
| T06 IPA/recycling | Rebind cùng holder IPA → null → blank, exact placeholder/status/click; PASS. Scroll thật trên thiết bị NOT RUN. |
| T07 input/states | RepositoryIntegrationTest: null/blank/missing/empty/closed DB; PacksViewModelTest: empty/error/retry/runtime failure; PASS. |
| T08 navigation | VocabularyListTest: ID TEXT ffffffffffffffff, cùng lemma khác ID, rebind đổi vị trí; real graph click→receive đúng ID/pack, unknown ID báo lỗi; PASS. |
| T09 lifecycle | Real graph Back/recreation giữ pack/ID; generation test loại late read; existing clearing test đóng DB sau read; PASS. Device rotation/fast pack switching stress/scroll restoration NOT RUN. |
| T10 persistence | File-backed close/open giữ learnedAt/saved; update box fixture rồi query thấy box5; PASS. App process death/reopen trên thiết bị NOT RUN. |
| T11 read-only | Snapshot mọi bảng catalog/userdata trước/sau browsing và reopen, gồm FTS BLOB; PASS. Receiver dùng cùng read query; chưa snapshot thiết bị trước/sau click. |
| T12 offline | Host graph dùng asset/local DB; Gradle/Robolectric chạy --offline thành công. Airplane Mode first launch trên thiết bị NOT RUN; Gradle offline không thay thế Airplane Mode. |
| T13 layout | wrap_content/multiline/min48dp; small screen/font scale/TalkBack và ảnh UI thiết bị NOT RUN. |
| T14 reboot | NOT RUN, không có thiết bị. Không coi DB reopen là reboot. |

Không tuyên bố AC-03/AC-13 toàn phần hoặc AC-04 detail đã đạt. Device QA và VOCAB-02 pending; không có blocker kỹ thuật cho bản local.

## Hướng dẫn reviewer

1. Cài debug APK; Trang chủ → Gói học → chọn từng pack gồm inactive/paused. Kiểm tra đúng membership/orderNo; không cần activate.
2. Mỗi hàng có lemma/POS, nghĩa và IPA. Fixture null/blank phải hiện “Chưa có phiên âm”. Production có thể không đủ ca null; dùng host fixture thay vì sửa catalog thật.
3. Null/saved-only chưa review: “Chưa có lượt ôn”; box0 đã review: “Học lại”; box1..5: “Cấp ôn N”. Chạy VocabularyListTest kiểm tra holder đổi IPA/status/ID.
4. Click hàng → receiver hiển thị vocabularyId/pack đúng và ghi rõ chờ VOCAB-02; Back về pack. ID ffffffffffffffff giữ nguyên TEXT.
5. Retry sau lỗi; pack hợp lệ rỗng khác lỗi đọc. Back/recreate/mở lại phải reload dữ liệu bền; thêm QA rotation/cuộn.
6. Khi có API26 và API mới: chạy connected tests, Airplane Mode từ first launch, force-stop/reopen, reboot và font scale; lưu evidence riêng.

## Contract VOCAB-02

- Destination R.id.vocabularyDetailFragment; receiver com.vocamind.app.feature.vocabulary.VocabularyDetailFragment.
- Bundle vocabularyId = stableId TEXT bắt buộc; packId = pack nguồn TEXT bắt buộc trong route từ list. Không trim/parse/đổi ID.
- Helper VocabularyDetailFragment.arguments(packId, stableId).
- Missing/blank arguments báo an toàn; unknown ID hoặc ID không thuộc pack báo sai mã; pack/read failure báo error. Back khả dụng, không chọn từ mặc định.
- Click lấy ID item đang bind; back stack giữ pack; recreation giữ arguments.
- Receiver kiểm tra membership qua cùng GetPackWords trên worker và hiển thị contract; chưa hiển thị full detail.
- VOCAB-02 thay receiver tối thiểu bằng nội dung/lookup detail; giữ route/arguments/Back. Search/deep-link độc lập cần thống nhất packId optional riêng.
- Chưa làm examples/detail/save/unsave; AC-04 pending.

User đã yêu cầu commit/push; không tạo PR/merge. Review Thành/CI remote còn pending.
PR nháp: [vocab-01-pr-draft.md](vocab-01-pr-draft.md).
