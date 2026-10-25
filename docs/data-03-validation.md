# DATA-03 — DAO foundation: implementation and validation

Ngày xác minh: 2026-10-25. Nhánh `feature/data-03-dao-foundation`, nền DATA-02
`89225c2`, plan commit `b87a4b4`. Thực hiện theo
[plan](plans/data-03-dao-foundation.md), hai SRS và dòng DATA-03 trong Excel đã
được đọc lại tại thư mục cha. Người thực hiện theo phân công: Thi, Nhân;
reviewer: Đạo. Chưa có xác nhận review của Đạo.

## 1. Kết quả triển khai

| Thành phần | Primitive / hành vi |
| --- | --- |
| CatalogDao | Giữ packs/words/vocabulary/sources/count/FTS fixture; thêm validation ID, nullable read-one, batch vocabulary và membership projection |
| PackMembership | Giữ cả packId/vocabularyId/packOrderNo/wordOrderNo; membership trùng từ giữa các pack là có chủ đích |
| Unique catalog pool | `uniqueVocabularyIdsForPacks` chọn occurrence đầu theo packOrderNo, packId, wordOrderNo, vocabularyId; stableId không lặp |
| UserDao | Active packs; activate/pause; batch progress/existing IDs; due; new IDs; đọc box 0..5; daily first-learned count; history có range/limit |
| DaoInputs | ID/limit/range validation, khử trùng input, chia batch 900 IDs để còn chỗ cho bind now/box trên SQLite API 26 |
| UserPlanningSnapshot | Read transaction cùng userdata: active packs, progress, preference và learned count; không tuyên bố transaction xuyên hai DB |
| Review transaction | Giữ recordReview có sẵn, kiểm tra null/ID trước dùng; test lỗi SQL bước update sau insert chứng minh rollback cả hai |

Không thay schema/version/index, không regenerate asset. CatalogDatabase và
UserDatabase vẫn hai tệp riêng; không thêm catalog business write DAO hoặc
cross-DB foreign key/JOIN. Query SQL được Room compiler kiểm tra khi build.

## 2. Contract bàn giao DATA-04/06/07

- DAO và projection nằm trong data, đánh dấu worker-thread; repository map sang
  domain. Feature/domain source guard cấm reference data implementation/Room.
  Không thêm wiring UI nhận DAO, không thêm repository tổng thể của DATA-04.
- `membershipsForPacks` nhận pack IDs caller đã lấy từ `activePacks`; không tự
  đọc userdata. Phải dùng unique catalog IDs làm eligible input cho due/new/Quiz.
  Primitive không tự biết caller truyền paused/orphan IDs; DATA-04 chịu trách nhiệm
  phối hợp. Test harness hai DB chứng minh active-only khi composition đúng.
- `dueProgress(eligibleIds, now, limit)` bao gồm dueAt bằng now và box 0, sắp
  dueAt ASC/vocabularyId ASC. Lọc IDs và thời gian trong SQL; merge toàn batch,
  áp global limit sau sorting. Không để paused/orphan chiếm cửa sổ trước lọc.
- `newVocabularyIds(orderedEligibleIds, limit)` loại mọi ID có progress, kể cả
  box 0 hoặc learnedAt null. Khử trùng, giữ thứ tự catalog rồi mới lấy limit.
  Caller tính quota; limit phải dương, quota=0 trả empty ở use case, không gọi
  DAO với limit=0. Không limit raw membership trước filtering/dedup.
- Batch progress/existing/due/box/new sử dụng read transaction trong userdata
  để các chunk không đọc các thời điểm khác nhau. Catalog nghiệp vụ bất biến;
  lifecycle swap catalog sau này thuộc DATA-04/08.
- `learnedCount(start,end)` đếm PK vocabularyId có learnedAt trong `[start,end)`,
  toàn userdata; không đếm ReviewEvent. Caller tính ranh giới ngày theo timezone
  và quyết định quota, không hard-code 24 giờ trong DAO.
- `reviewHistory(id,start,end,limit)` và `quizHistory(start,end,limit)` có thứ tự
  timestamp DESC/id DESC, range nửa mở. Không DISTINCT history vì nhiều event
  cho cùng vocabularyId là hợp lệ. `reviews()`/`quizzes()` cũ giữ order ID để
  không phá consumer cũ; repository mới dùng primitive history có thời gian.
- `activatePack` giữ thời điểm kích hoạt đầu tiên; pause missing ID trả 0 và
  không tạo pack. Đây là quyết định triển khai để Đạo review, không tự nhận SRS
  đã chốt semantics reactivation. Catalog ID existence được DATA-04 kiểm tra.
- `setSaved` chỉ update row hiện có, trả affected count; không tự tạo saved-only
  progress làm sai new/count/box. Luồng lưu từ chưa học còn cần contract riêng.
- Invalid ID/collection/limit/range/box/row bị từ chối; empty collections không
  trở thành truy vấn full-table. Unknown ID trả null/empty; lỗi SQLite/closed DB
  được truyền lên, không nuốt thành empty, không tự xóa/reset DB.
- `planningSnapshot` hiện đọc toàn progress: phù hợp kho MVP; chưa có benchmark
  cho kho lớn, không tuyên bố tối ưu truy vấn ở quy mô không giới hạn.

## 3. Kiểm chứng thực tế

Windows, JDK 17, SDK theo local.properties. Baseline trước sửa:
`:app:testDebugUnitTest` BUILD SUCCESSFUL (UP-TO-DATE, không công bố đó là lượt
execute mới). Sau triển khai Room compile và suite DAO riêng đã chạy thành công.
Lần kiểm tra cuối sau khi bổ sung new-ID primitive:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-17'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

Kết quả: **BUILD SUCCESSFUL**, 77 executed, 77 passed, 0 failures/errors/skipped.

| Suite | Executed | Passed | Failed / Skipped |
| --- | ---: | ---: | ---: |
| DaoFoundationTest: 14 cases × API 26/35 | 28 | 28 | 0 / 0 |
| DataBoundaryTest | 1 | 1 | 0 / 0 |
| UserDatabaseTest | 16 | 16 | 0 / 0 |
| CatalogAssetTest | 22 | 22 | 0 / 0 |
| QuizDomainTest | 10 | 10 | 0 / 0 |
| Tổng | **77** | **77** | **0 / 0** |

Report: `app/build/reports/tests/testDebugUnitTest/index.html`; XML:
`app/build/test-results/testDebugUnitTest/`. Debug app và instrumentation APK
đều build thành công; source schemas và catalog asset không có diff.

Fixture là UUID-named file-backed Room DB với synthetic content riêng. Catalog
fixture được seed bằng raw SQL chỉ trong test copy, không thay production asset.
Test suite bao phủ DATA03-T01..T13: ordering, null/empty, shared membership,
active/paused/orphan, equality now, new filtering trước limit, day boundaries,
six boxes, saved isolation, timestamp ties, SQL-trigger rollback, reopen,
closed DB và 1.005 IDs vượt một chunk. DATA03-T14 dùng source boundary test.

`adb devices` trả danh sách rỗng trong lần kiểm tra này. **Chưa chạy connected
device tests**; DAO suite mới hiện là host-native Robolectric, không có wrapper
instrumentation mới. Build instrumentation APK không chứng minh DAO suite đã
chạy trên thiết bị. Reopen không thay reboot hay migration/release acceptance.

## 4. Giới hạn và trạng thái nghiệm thu

Phần code, primitive, fixture và kiểm thử DATA-03 đã triển khai; sẵn sàng Đạo
review. Chưa giả lập review/acceptance hoặc sửa trạng thái Excel thành Hoàn thành.
Các quyết định cần xác nhận khi bàn giao: multi-pack ordering, giữ first activation
timestamp, global first-learned count và saved-only workflow chưa triển khai.

DATA-04 tiếp tục repository/mappers/error Result, caller active filtering và quota
integration. DATA-06 xử lý scheduler, stale/concurrent progress, idempotency và
double-submit; @Transaction hiện tại chỉ bảo đảm all-or-nothing. DATA-07 xây
ProgressSummary/QuizHistory aggregate. DATA-05 chịu search hoàn chỉnh. Không
công bố đã hoàn thành toàn bộ FR-VM-005..046 hoặc AC-08/09/10/11 cấp sản phẩm.
