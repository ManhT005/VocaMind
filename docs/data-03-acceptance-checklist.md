# DATA-03 — Checklist nghiệm thu và bàn giao DAO foundation

Ngày cập nhật: 01/11/2026. Nhánh: `feature/data-03-dao-foundation`.
Người thực hiện theo phân công: Thi, Nhân. Reviewer: Đạo.

Tài liệu này tiếp nhận checklist được cung cấp tại
`app/build/DATA-03_Acceptance_Checklist_VocaMind.md`. Bản theo dõi bằng Git nằm
trong `docs` để không mất khi chạy Gradle clean. DATA-03 ở đây là DAO foundation,
khác VOC-DATA-03 ingestion và DATA-03 QA học liệu trong SRS §7.3.

**Trạng thái: kỹ thuật PASS sau tích hợp, sẵn sàng review; chưa có reviewer sign-off.**
Ô kỹ thuật chỉ ghi việc người triển khai đã kiểm chứng; không thay approval của
Đạo, CI trên PR hoặc nghiệm thu toàn sản phẩm. Không tự điền tên người ký.

## 1. Base và phạm vi

- Feature cũ: `d7becce2f86f4073d816ea363642021b04e03fe7`.
- Base tích hợp: `origin/develop` tại `fff380127b0808b203165e649e22eaf714027afb`.
- Merge giữ lịch sử: `77bf900da510a267e7c903b6cf464c775d4d9900`.
- DATA-01/02 đã có trong base; giữ toàn bộ Learn/Quiz/VOC-DATA trên develop.
- Hai conflict mục lục README được giải quyết bằng cách giữ liên kết cả hai nhánh.
- Không đổi Room schema/index/version, catalog asset, userdata hay thuật toán Learn.
- Delta hoàn thiện: test batch/order/rollback; Gradle chuẩn bị SDK Robolectric;
  CI build instrumentation APK và lưu report/APK ngay cả khi test lỗi; hồ sơ bàn giao.

Xem [validation](data-03-validation.md) và [plan](plans/data-03-dao-foundation.md).
Kết quả test và artifact được ghi ở §4 sau khi thực thi.

## 2. Gating checklist

| ID | Điều kiện | Bằng chứng / trạng thái hiện tại |
|---|---|---|
| G-01 | Base DATA-01/02 và diff đúng scope | Đã merge develop tại `77bf900`; kiểm tra diff từ `fff3801` |
| G-02 | Catalog read, null/empty, order, source | `catalogOrderEmptyNullableAndAttribution` và test tie/batch mới |
| G-03 | Membership giữ liên kết, pool unique vocabularyId | `membershipDuplicatesAreIntentionalButVocabularyPoolIsUnique`, `catalogMergesBatchesBeforeOrderingAndDeduplicatingCandidates` |
| G-04 | User active/progress/box/saved/history | DAO suite và DATA-02 regression |
| G-05 | Due gồm equality/box 0; new không có progress; lọc trước limit | Active/no-active/new tests; batch global-limit test |
| G-06 | Reject invalid input, empty không full scan, DB errors truyền lên | `invalidAndEmptyInputsCannotBecomeFullTableQueries`, `closedDatabaseErrorsAreNotSilentlyReturnedAsEmpty` |
| G-07 | >999 IDs, dedup và merge/sort trước limit | `largeIdBatchesMergeBeforeLimitAndStayUnique`; catalog cross-batch test |
| G-08 | Review event + progress nguyên tử | SQL-trigger rollback cho cả UPDATE existing progress và INSERT first progress |
| G-09 | Data/Room không lộ vào UI/domain; worker-thread | `DataBoundaryTest`, DAO annotations và worker-thread DAO fixtures |
| G-10 | Schema/asset/dual DB không đổi | Diff với develop không có `app/schemas` hoặc `app/src/main/assets` |
| G-11 | Regression, app và test APK trên code tích hợp | Local PASS: 105/105 JUnit, build hai APK; CI sau push chưa có |
| G-12 | Reviewer/handoff | Contract R-01…R-07 ở §3; Đạo chưa xác nhận |
| G-13 | PR → develop, checks xanh, blocking comments đóng | Chưa tạo PR/push trong đợt commit local này |

## 3. Contract đề xuất để reviewer chốt

### R-01 — Thứ tự nhiều pack

Giữ thứ tự `(packOrderNo, packId, wordOrderNo, vocabularyId)`.
`membershipsForPacks` giữ mọi liên kết; `uniqueVocabularyIdsForPacks` lấy occurrence
đầu cho mỗi vocabularyId **sau khi merge tất cả batch**. Cả SQL và merge đều có
tie-breaker. Không lấy thứ tự danh sách pack đầu vào làm thứ tự nghiệp vụ.
Đạo review: **pending**.

### R-02 — Active/due/new và quota

DATA-04 đọc active pack IDs từ userdata rồi lấy eligible vocabulary IDs từ catalog;
DAO không tự JOIN hai DB. Không active/only-paused → pool rỗng. Orphan progress
không thuộc eligible IDs. Due dùng `dueAt <= now`, order `dueAt, vocabularyId`,
gồm box 0. New là ID chưa có **bất kỳ progress nào**, kể cả box 0/learnedAt null.
Dedup và lọc trước limit; limit phải dương. Quota=0 được trả empty ở use case.
`learnedCount` đếm vocabularyId theo `[dayStart,nextDayStart)`; caller tính ngày
địa phương, không dùng ReviewEvent count hoặc mặc định một ngày bằng 24 giờ.
Đạo review: **pending**. Consumer/owner tích hợp: **DATA-04**.

### R-03 — Activate/pause

`activatePack` giữ thời điểm kích hoạt đầu qua pause/reactivation; pause unknown ID
trả 0 và không tạo row. DATA-04 phải kiểm tra pack tồn tại trong catalog trước
activate. Test xác minh reactivation không ghi đè timestamp.
Đạo review: **pending**. Consumer: **DATA-04**.

### R-04 — Saved-only handoff

`setSaved` chỉ update existing progress, missing row trả 0. Không tự tạo progress
cho từ chưa học vì sẽ thay nghĩa “new” và thống kê box.
**Owner phần tiếp theo: DATA-04 và DATA-07; chưa xác nhận tiếp nhận.**
Acceptance phần tiếp theo: lưu/bỏ lưu từ chưa học không làm mất eligibility new,
không tăng learned count/box count, tồn tại sau reopen. Cần contract/schema review
trước implementation. Đạo chưa phê duyệt; mục này không được ghi là luồng saved-only
toàn sản phẩm đã hoàn thành.

### R-05 — Transaction review

`recordReview` chỉ bảo đảm event/progress all-or-nothing trong userdata. Có test SQL
failure sau insert event, cho cả progress UPDATE và INSERT. Không bao gồm debounce,
idempotency, scheduler hoặc stale/concurrent progress.
**Owner phần tiếp theo: DATA-06**; acceptance gồm chống chấm trùng, concurrency,
FORGOT retrial và tích hợp scheduler. Không coi DAO tests là AC-08 toàn tuyến.

### R-06 — Error, batching và quy mô

900 IDs/batch; validate toàn bộ input kể cả phần sau limit; merge/sort trước
global limit. Invalid → `IllegalArgumentException`; unknown → null/empty theo
contract; SQLite/closed-DB error truyền lên repository để map lỗi. Không reset DB.
`planningSnapshot` đọc toàn progress trong một userdata read transaction;
**chưa benchmark 5.000 từ**. DATA-04 chịu error mapping; DATA-05/07 đo hiệu năng
theo use case khi tích hợp. Đạo review: **pending**.

### R-07 — Schema và architecture

Giữ hai DB vật lý, stableId/packId TEXT; không FK/transaction xuyên DB.
UI/domain dùng repository/domain model; không nhận Room entity/DAO.
Không thêm migration cho query-only changes. Nếu task sau đổi schema thì cần
version/export/migration bảo toàn dữ liệu và test phù hợp.
Đạo review: **pending**. Upgrade/rollback cấp sản phẩm thuộc **DATA-08**.

## 4. Bằng chứng thực thi

Lần chạy ngày 01/11/2026 trên Windows, Temurin 17.0.20.1+1, Gradle 8.9:
**BUILD SUCCESSFUL**, 105 executed / 105 passed / 0 failures / 0 errors / 0 skipped.
Gradle chạy 7 phút 55 giây, gồm lượt tải dependency đầu; không phải benchmark DAO.

| Suite | Executed | Passed |
|---|---:|---:|
| DaoFoundationTest: 16 cases × API 26/35 | 32 | 32 |
| DataBoundaryTest | 1 | 1 |
| CatalogAssetTest | 22 | 22 |
| UserDatabaseTest | 16 | 16 |
| LeitnerSchedulerTest / SrsTest / BoundaryTest | 24 | 24 |
| QuizDomainTest | 10 | 10 |
| Tổng | **105** | **105** |

- `assembleDebug` và `assembleDebugAndroidTest`: PASS.
- Vocabulary gate regressions: **46 tests PASS**; frozen inventory/checksum PASS.
- Schema/asset diff từ develop: rỗng; `git diff --check`: PASS.
- [Evidence JSON](data-03-acceptance-evidence.json) lưu counts, XML/APK SHA-256 và
  digest của code/build inputs thực sự được kiểm tra. Các input này không đổi sau
  lượt test; commit chứa hồ sơ này xác định phiên bản bàn giao.

Lệnh Windows (đặt `JAVA_HOME` đến JDK 17 cục bộ):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --no-daemon
py -3 -W error::ResourceWarning -m unittest discover -s tools/vocabulary -p 'test_*.py'
py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json
git diff --check
git diff --name-only origin/develop -- app/schemas app/src/main/assets
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, XML dưới
`app/build/test-results/testDebugUnitTest/`; log `app/build/data-03-acceptance.log`.
APK: `app/build/outputs/apk/debug/app-debug.apk` và
`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
Các file build không commit; CI cấu hình upload reports/APKs gắn `github.sha`.

Robolectric 4.16.1 chạy native SQLite API 26/35. SDK jars được pin đúng
DefaultSdkProvider, resolve bằng hai Gradle configurations riêng để tránh conflict
giữa hai version cùng artifactId, rồi Sync vào `build/robolectric-sdks`.
JUnit dùng `robolectric.offline=true` và `robolectric.dependency.dir`, không tải
SDK trong class initialization. Cách này xử lý đường tải runtime đã lỗi trong
[CI develop cũ](https://github.com/ManhT005/VocaMind/actions/runs/41316392831);
không tự tuyên bố CI trên commit mới đã xanh khi chưa push.

`adb devices` ngày 01/11/2026: không có thiết bị. Chưa chạy connected tests.
Build AndroidTest APK không thay thế device testing. Reboot/staged upgrade và
migration sản phẩm tiếp tục là gate integration/release của DATA-08.

## 5. Trace test DATA03-T01…T14

| ID | Test / evidence |
|---|---|
| T01 | `catalogOrderEmptyNullableAndAttribution`, `catalogMergesBatchesBeforeOrderingAndDeduplicatingCandidates` |
| T02 | `membershipDuplicatesAreIntentionalButVocabularyPoolIsUnique`, catalog cross-batch |
| T03 | `dueFiltersActiveAndOrphanBeforeGlobalLimit`, `progressBoxAndDueTieOrderingAreStable` |
| T04 | `pausedOrNoActivePacksHaveNoCandidatesAndNeverActivateAutomatically` |
| T05 | `newIsAbsenceOfAnyProgressAndLimitComesAfterDedup`, large batch |
| T06 | `quotaCountsUniqueFirstLearnedTimesNotReviewEvents` |
| T07 | `progressBoxAndDueTieOrderingAreStable`, invalid/empty inputs |
| T08 | `savedFlagDoesNotInventProgressOrResetLearning` |
| T09 | `reviewTransactionRollsBackOnRealSqlFailureAfterInsert`, `firstReviewRollsBackEventWhenNewProgressInsertFails` |
| T10 | `historyUsesTimestampThenIdAndPreservesRepeatedEvents` |
| T11 | `catalogOrderEmptyNullableAndAttribution` |
| T12 | `planningSnapshotAndFileReopenPreserveState`, `closedDatabaseErrorsAreNotSilentlyReturnedAsEmpty` |
| T13 | `largeIdBatchesMergeBeforeLimitAndStayUnique`, catalog cross-batch |
| T14 | `DataBoundaryTest.presentationAndDomainDoNotReferenceDataOrRoom` |

## 6. Chốt review / merge

- [x] Đồng bộ develop, giữ lịch sử và module đã tích hợp.
- [x] Contract/handoff và test matrix có link cụ thể; schema/asset giữ nguyên.
- [x] Regression trên code cuối và build hai APK thành công; bằng chứng ở §4.
- [ ] Push branch và có CI run trên commit PR cuối cùng.
- [ ] PR target develop, diff đúng scope, không còn blocking comments.
- [ ] Đạo xác nhận R-01…R-07 và acceptance DATA-03.
- [ ] Merge và kiểm tra CI develop; sau đó cập nhật sheet hoàn thành.

Reviewer: **chưa xác nhận**. PR/CI mới: **chưa tạo**. Không ký hoặc tạo evidence
approval thay người phụ trách. Không đánh dấu DATA-03 Done 100% trước các bước trên.
