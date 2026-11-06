# VOCAB-02 — first-save acceptance và handoff

**Sau DATA-06/DATA-07:** xem [kết quả tích hợp hiện hành](vocab-02-data0607-integration.md)
trên develop8209ef7; validation 287 tests bên dưới là lịch sử trước lần tích hợp đó.

Ngày: 06/11/2026. Phụ trách Trường; reviewer Thành. Chưa tự đánh dấu task Hoàn thành.
Base feature trước sửa: `1fb3005`, đã chứa develop `45db604` và Quiz policy PR #31.
Working tree ban đầu sạch. Không sửa SRS, phân công, schema hoặc các query planner/thống kê.

**SHA code/tests: 016373c37fc0f39d35f9ae3f9ea010be46dbda59.**

## Quyết định và thay đổi

Người dùng chốt phương án first-save due-now trong phiên làm việc;
[contract hiện hành](vocab-02-saved-only-decision.md) thay các đề xuất PENDING trước đó.
Đây là quyết định kỹ thuật, không trình bày thành giá trị khởi tạo do SRS quy định.

RoomVocabularyRepository giữ targeted UPDATE cho row có sẵn. Nếu target=true mà chưa có row,
UserDao INSERT OR IGNORE progress box0/dueAt=Save time/isSaved=true/timestamps học null/counters0/leech=false.
Tất cả trong một transaction, không ghi đè stale progress. Insert bị ignore mà không xác nhận được
flag bằng UPDATE thì fail/rollback. Unsave missing-row là verified no-op false; không insert.
Không có post-commit reread gây rollback giả. Đã bỏ SAVED_POLICY_REQUIRED và thông báo UI blocker cũ.
Retry cho read/write failure vẫn chỉ đọc lại persisted state.

Giữ wiring Quiz policy đã resolve. Clock injectable trong data repository để test đúng Save time;
production dùng System.currentTimeMillis trên worker. Không thêm dependency/network permission.

Hệ quả được kiểm tra: thêm row làm tổng/box0 tăng 1; row rời new pool; due chỉ eligible khi pack active,
deduplicate từ chung hai pack; Save không tạo lịch sử, timestamps học hoặc tăng learned-today/pack learnedCount.
Unsave không đưa từ trở lại new hoặc xóa due/progress.

## Kiểm chứng

Windows/JDK17, Gradle8.9, Robolectric API26/35, SQLite native. Code/tests không đổi trong commit docs sau đó.

```powershell
.\gradlew.bat testDebugUnitTest --tests '*RepositoryIntegrationTest' --tests '*VocabularyDetailTest' --tests '*VocabularyListTest' --tests '*QuizPolicyCompositionTest' --offline --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
git diff --check
```

Focused PASS ở tiền thân code/tests; full run tại SHA trên gồm các test mới cuối cùng.
Kết quả: **287 tests / 31 suites, 0 failures, 0 errors, 0 skipped**; BUILD SUCCESSFUL.
assembleDebug và assembleDebugAndroidTest PASS. lintDebug **0 errors / 11 warnings**
(các warnings UI/API28 heading/overdraw và nền cũ đã ghi ở lượt UI, không có lint error mới).
git diff --check PASS. ADB kiểm tra lại trả danh sách rỗng; [adb evidence](validation/vocab-02/first-save/adb-devices.txt).
[Evidence counts/hashes](validation/vocab-02/first-save/local-evidence.json),
[build log](validation/vocab-02/first-save/build.log),
[lint](validation/vocab-02/first-save/lint-results.txt), XML các suite trọng tâm cùng thư mục.

| Acceptance | Test / evidence |
| --- | --- |
| Userdata trống → Save → reopen true → Unsave → reopen false | QuizPolicyCompositionTest.firstSaveThroughProductionCompositionPersistsAcrossReopen dùng real CatalogProvider/RepositoryProvider/ToggleSaved, API26/35 |
| UI first-save, pending/rotation, Back/reopen và Unsave | VocabularyListTest.realGraphDispatchReceiveBackAndRecreation không seed progress, kiểm tra persisted box0/timestamps null/count0 |
| Save lặp không trùng/reset | vocab02FirstSaveReopenUnsaveAndBaselinePoolsAndCounts: clock DAY rồi 2×DAY sau reopen; dueAt vẫn DAY và một row |
| Tổng/box0 +1, learned-today/pack learnedCount chưa tăng | Cùng test: COUNT DISTINCT/SUM box0, allProgress/progressByBox và learnedCount=0; không thêm production filter |
| New/due/active/paused/shared | Cùng test: new loại SHARED; dueAt-1 chưa due, dueAt đủ hạn; inactive không eligible, A/B active dedup một từ, pause A vẫn B, pause cả hai không due |
| First-save/first-review concurrent | vocab02FirstSaveConcurrentWithFirstReviewAcrossConnections: 4 stableIds, 2 Room connections trên cùng file, 2 threads/latch; giữ saved, scheduler outcome, timestamps/counters/history |
| Catalog bất biến / existing progress giữ nguyên | Snapshot trong first-save và tests cũ vocab02SaveUnsaveReopenOnlyChangesFlagAndCatalogIsReadOnly; existing concurrency regression giữ nguyên |
| Write rollback / không success giả | vocab02FirstSaveRollbackAndIgnoredInsertCannotReportSuccess: trigger RAISE ABORT/IGNORE, toàn userdata snapshot giữ nguyên, retry sau bỏ trigger persist thật |
| Invalid ID/membership không tạo orphan | vocab02InvalidFirstSaveNeverCreatesOrphansOrChangesDatabases |
| Quiz policy sau tích hợp | QuizPolicyCompositionTest cùng provider first-save/review/save rồi GenerateQuiz vẫn fail-closed đúng fixture; AcceptedQuizPolicyTest và full regression |

Không có Progress Room adapter DATA-07 trong source hiện tại; kiểm tra total/box theo literal SQL baseline
trên DB thật và các primitive hiện có, không coi đó là UI Progress đã được nghiệm thu.

Offline host/local DB PASS qua --offline; **device Airplane Mode/force-stop/reboot/TalkBack NOT RUN**.
DB reopen và host recreation không thay thế process restart trên thiết bị. Không sửa asset thật hoặc bịa content QA.

## Hướng dẫn kiểm tra

1. Cài APK mới ở app/build/outputs/apk/debug/app-debug.apk. Với userdata chưa có progress,
   Home → Gói học → chọn từ → Lưu; phải chuyển sang Bỏ lưu, không còn thông báo chờ hoàn thiện.
2. Back/mở lại vẫn Bỏ lưu; chọn Bỏ lưu rồi mở lại phải hiện Lưu. Không cần activate pack để Save.
3. Chỉ Save chưa tăng thanh “đã học” của gói; tổng progress/box0 tăng theo contract.
4. Khi pack active, từ chỉ lưu thuộc due pool ở Save time; khi chưa active/đã pause thì không eligible.
   Màn Today/Flashcard chưa nối full review trong task này; dùng acceptance repository để kiểm chứng pool/scheduler.
5. Với thiết bị API26 và API mới, bổ sung offline first launch, app restart, lifecycle/TalkBack QA cùng SHA riêng.

P1 đã được sửa và có acceptance host API26/35, chờ reviewer xác nhận trên head mới.
AC-04 có evidence detail + Save/Unsave từ chưa học + catalog bất biến/reopen ở host;
chưa tự nghiệm thu toàn task khi device/content QA còn thiếu. Không tạo PR/merge.
