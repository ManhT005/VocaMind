# DATA-08 — Catalog upgrade / rollback validation

## 1. Phạm vi và nền triển khai

| Hạng mục | Giá trị |
| --- | --- |
| Task / Người thực hiện / Reviewer | DATA-08 / Nhân / Thi |
| Nhánh | `feature/data-08-catalog-upgrade-rollback` |
| Base | `origin/develop` tại `993e70a07a05c50c5de8099edcdf50b92c91c5e9` |
| Căn cứ | SRS Baseline v1.1, bảng phân công đính kèm và [plan](plans/data-08-catalog-upgrade-rollback.md) |
| Truy vết | FR-VM-003/010, AC-13, NFR-VM-06, §7.4, DEC-04/05/13 |
| Schema production trên nhánh | CatalogDatabase **2**, UserDatabase **1** — giữ nguyên |
| Kết luận | Catalog implementation và host regression đã kiểm chứng; chưa đóng toàn bộ gate nghiệm thu |

Không tăng schema để có test, không thêm bản sao migration khác production,
không sửa asset/schema JSON/quyền phát hành của sản phẩm. Thời gian thực hiện
theo đầu ra; 8 giờ trong bảng phân công không phải giới hạn cắt kiểm thử.

## 2. Cấu trúc và hành vi đã triển khai

`data/catalog/upgrade/` chứa coordinator, generation/store, validator,
reference checker và `CatalogUpgradeException`. Điểm nối nằm ở `core/`;
domain không nhận DAO, Room entity hoặc đường dẫn DB.

- `CatalogStartupCoordinator.bootstrap()` chạy trước khi phát dependency cho
  consumer. Single-flight/pin theo app-private root giữ cùng generation suốt
  process; APK mới được xét ở lần khởi động tiếp theo, không hot-swap DB đang mở.
- Copy bundle vào staging cùng filesystem, kiểm tra rồi đóng Room validation,
  chuyển staging thành generation bằng `ATOMIC_MOVE`. Ghi **một** AtomicFile
  `active.json` gồm `format`, `active`, `previous`, `phase`.
- File bytes được sync, thư mục được sync, publication record được sync và đọc
  lại để phát hiện trường hợp AtomicFile chỉ log lỗi. Native directory fsync trên
  Android có NIO fallback thực trên host; lỗi cả hai đường được trả ra.
- Chỉ phát consumer khi `PENDING → reopen/probe → COMMITTED` thành công.
  Recovery đọc persisted state; candidate hỏng chỉ quay về previous đã xác minh.
  Nếu không có bản hợp lệ thì trả safe error, giữ userdata cho retry.
- Bundle gốc ở `<noBackupFilesDir>/catalog-generations/<digest>/bundle/`.
  SQLite runtime ở `runtime/catalog.db`; Room không mở bundle gốc để ghi WAL.
  DB, credits và quiz policy đều đọc cùng generation.
- Bundle trùng được so hash và dùng lại, không tạo/copy generation trùng.
  Runtime copy hiện hỏng sẽ dẫn tới fallback/safe error; chưa tự dựng lại runtime.
- Validator kiểm file set, checksum, manifest/gate/profile, Room schema/identity,
  SQLite integrity/FK, metadata counts/contentVersion, FTS rowid/text và MATCH
  Anh/Vi, nguồn credits theo sourceId/snapshotDate và projection của quiz policy.
  Canonical-source digest vẫn là provenance do pipeline hiện có xác minh;
  runtime không tái tạo canonical JSON từ SQLite.
- Reference checker chỉ đọc userdata, kiểm cả `UserWordProgress`, `ReviewEvent`,
  `UserPack`. Removal của từ chỉ lưu, lịch sử review hoặc pack paused đều bị chặn.
  Rollback về bản thiếu ID mới cũng bị chặn, không xóa tiến độ để vượt kiểm tra.
  Raw SQLite reads của userdata/legacy dùng corruption handler riêng vì handler
  mặc định Android có thể xóa file ngay cả khi mở read-only; lỗi ở đây chỉ được
  trả về, không dùng default deletion handler.

Attribution factory chuyển khởi tạo provider sang serial worker. ViewModel giữ
constructor use case cũ và thêm supplier để lazy bootstrap; lỗi tiếp tục đi vào
state ERROR/retry hiện có. Packs factory đã có lazy worker nên tái sử dụng.

Legacy `databases/catalog.db` chỉ được nhận khi schema 2 và nội dung bốn bảng
nghiệp vụ tương đương asset tin cậy. Legacy schema 1 hoặc nội dung khác không
có companion xác minh được sẽ bị hoãn/safe error, file cũ giữ nguyên. Đây là
giới hạn có chủ đích, chưa phải importer cho mọi phiên bản APK cũ.

Không xóa generation cũ ngay sau publish. Chưa triển khai garbage collection,
network download, đa process hoặc công cụ rollback khi consumer đang hoạt động.

## 3. Fixture và kiểm thử

Shared contract: `app/src/catalogTest/java/com/vocamind/app/data/catalog/upgrade/`.
Host wrapper nằm trong `src/test`, AndroidJUnit4 wrapper trong `src/androidTest`,
đúng source sets đang có. Không thêm dependency/build framework mới.

A là asset fixture thật: 4 từ, 4 pack; B đổi description/pack version và
contentVersion thành 1.0.1, cùng catalog schema 2. Sidecars được cập nhật trong
thư mục test riêng. B có hash khác nhau giữa SDK do SQLite runtime tạo fixture;
hash thực và digest snapshot được ghi theo SDK trong evidence.

Seed cả năm bảng với pack active/paused, progress box 0/1/2/5, saved-only,
learned, leech/mistakes, review, quiz và preference khác mặc định. So sánh snapshot
từng ô gồm tên bảng/cột, kiểu, giá trị và NULL; không chỉ so COUNT hoặc hash file
userdata đang có WAL. Fixture này chưa phủ riêng từng box 3/4; bộ DATA-07 hiện có
kiểm tra phân bố box 0..5 trong regression.

| Nhóm | Kiểm chứng thực tế |
| --- | --- |
| First install / T01 | Bundle mở thật; catalog không tự tạo userdata; mở UserDatabase sau đó có mặc định 5/10/OFF/20 |
| Upgrade / rollback / T02, T11, T18 | B có nội dung mới; rollback A; năm bảng và bundle bytes giữ nguyên, reopen userdata không đổi |
| Idempotence / T03 | Cùng digest không staging hoặc reset dữ liệu |
| Legacy / T04 | Import schema 2 tương đương; schema khác bị từ chối, original giữ nguyên |
| Validation / T05, T06 | Metadata/credits/policy malformed, schema 99 và mất FTS posting bị chặn; có kiểm lý do rejection |
| References / T07, T12 | Chặn removal của vocabulary, saved-only, review-only, paused pack và rollback có reference mới |
| Failure / T08 | Inject IOException tại sáu boundary; thêm lỗi stream sau partial copy; không half-generation |
| Recovery / T09 | Simulated Error tại sáu boundary; coordinator mới đọc persisted selection; PENDING hợp lệ finalize, candidate hỏng fallback |
| Concurrent / T10 | Hai coordinator đồng thời chọn cùng một generation |
| Corrupt userdata / T14 | Safe error, bytes hỏng giữ nguyên, không recreate/reset |
| Profile / T16 | Candidate thật 150 từ qua QA context; bị chặn ở profile thường; releaseAuthorized vẫn false |
| UI / T17 | Regression màn Attribution và các ViewModel; sửa main-thread bootstrap được phát hiện bởi screen test |

Exception/Error injection và coordinator mới **không phải** kill process, mất điện,
thiết bị đầy ổ đĩa hay reboot thật. Test không công bố đã mô phỏng mọi syscall
failure bên trong AtomicFile/SQLite.

## 4. Kết quả chạy local

Lệnh từ root với JDK 17, compile SDK 35:

```bash
bash gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2 --console=plain
```

Kết quả và hashes nằm trong [verification.json](evidence/data-08/verification.json),
[unit-results.json](evidence/data-08/unit-results.json) và
[fixture-snapshots.json](evidence/data-08/fixture-snapshots.json).
DATA-08 có 19 case × SDK 26/35 = **38 lượt**; toàn regression **456 lượt**.
Đây là Robolectric native SQLite; APK instrumentation build được, chưa chạy trên thiết bị.

| Kiểm tra | Kết quả cuối |
| --- | --- |
| Unit/regression | 456 pass, 0 fail/error/skip |
| DATA-08 trong regression | 38 pass, SDK 26/35 |
| assembleDebug / assembleDebugAndroidTest | BUILD SUCCESSFUL |
| assembleCatalogQa (`-PcatalogQa=true`) | BUILD SUCCESSFUL, asset QA 150 từ, manifest vẫn releaseAuthorized=false |
| lintDebug | 0 lỗi, 10 warning bằng baseline trước DATA-08 |
| Asset và schema JSON production | Hash/bytes khớp base; không đổi |

Lệnh build QA dùng pipeline đang có:

```bash
bash gradlew :app:assembleCatalogQa -PcatalogQa=true --max-workers=2 --console=plain
```

Các lỗi gặp và đã sửa: Android API không hỗ trợ cờ directory-open được dùng ban
đầu; RoomDatabase không implements AutoCloseable; host Os bridge không mở được
directory; screen Attribution bootstrap trên main thread; fixture review dùng
rating không thuộc contract. Báo cáo cuối được lấy sau khi sửa và chạy lại.

## 5. Migration userdata thật và phần còn mở

Nhánh này vẫn schema 1. Đã chạy test production sẵn có
`UserDatabaseTest.productionMigrationPreservesLegacyHistoryAndProgress` trong
checkout tách biệt của `origin/feature/quiz-03-ui-session` tại
`f34b4ea72d81062d231ae75433c9ad4fefa1963a`: **2/2 lượt SDK 26/35 pass**.
Test gọi chính UserDatabase v2 với MIGRATION_1_2 của nhánh đó; kiểm progress,
quiz cũ/NULL completionKey và unique completion behavior.

[Migration component evidence](evidence/data-08/migration-component.json) ghi
đúng source và phạm vi. Kết quả này không thay integration sau merge: chưa có
MigrationTestHelper/schema-1→2 cùng catalog upgrade và snapshot cả năm bảng trên
nhánh DATA-08. Không chép migration hay schema 2 qua chỉ để làm gate xanh.

Để tái lập component test, checkout riêng đúng SHA trên rồi chạy:

```bash
bash gradlew :app:testDebugUnitTest \
  --tests '*UserDatabaseTest.productionMigrationPreservesLegacyHistoryAndProgress*' \
  --max-workers=2 --console=plain
```

## 6. Gate thiết bị và bàn giao

Cloud hiện không có emulator/system image hoặc `/dev/kvm`; ADB không khởi động
được do `/home/agent/.android` read-only. Không sửa permission/thư mục hệ thống
hay dùng host tests để thay bằng chứng thiết bị.

Trên máy Android Studio có thiết bị/emulator API26+ kết nối, chạy shared suite:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.upgrade.CatalogUpgradeInstrumentedTest
```

Suite tự tạo/xóa DB riêng. Chạy lại suite sau reboot không đủ chứng minh dữ liệu
của lần trước tồn tại: cần một phiên kiểm thử giữ state xuyên process, với A/B
cùng application ID, seed năm bảng, ghi logical snapshot/generation, upgrade,
force-stop/relaunch rồi reboot/relaunch và đối chiếu snapshot. Lưu model/API,
APK SHA, commands và trước/sau; lặp sau rollback. Việc này còn cần thực hiện
trên thiết bị trước khi đóng T15/AC-13.

Gate còn mở: migration tích hợp sau merge schema 2, real process/device reboot,
fault trên storage thật, legacy khác nội dung cần bộ companion cũ, và review Thi.
Không tự cập nhật bảng phân công thành hoàn thành hoặc phê duyệt release.

## 7. Rà soát bổ sung sau triển khai

Phát hiện một lỗi retry: sau PENDING bị hỏng sidecar, recovery về A thành công
nhưng thư mục B hỏng vẫn tồn tại. Retry bằng asset B hợp lệ lại kiểm thư mục B
hỏng đó và thất bại mãi. Đã mở rộng case
`pendingCorruptCandidateFallsBackToVerifiedOld` và tái hiện **2/2 lượt fail**
trên SDK 26/35 trước khi sửa, do `Catalog bundle checksum mismatch`.

Sửa bằng cách xác minh bản staging mới trước, cô lập generation không active bị
hỏng vào `rejected-<uuid>` qua atomic move, rồi đưa bản sạch vào đúng digest.
Không đụng generation active hoặc userdata; giữ bytes lỗi cho điều tra. Sync
thêm thư mục runtime/staging sau khi Room validation đóng trước publication.
Case hiện kiểm cả fallback, retry, recover lần nữa và snapshot userdata không đổi.

Đã tối ưu bước kiểm tra khi khởi động:

- Dùng lại danh sách vocabulary/pack đã đọc cho policy và các probe, thay vì
  query toàn bảng lần nữa.
- Reference checker dùng hai query userdata (UNION vocabulary references và
  pack references), đối chiếu Set trong bộ nhớ. Không còn truy vấn catalog từng
  ID/lần review, kể cả khi review history lớn.
- Dùng lại checksum DB vừa xác minh khi đối chiếu metadata, tránh đọc hash DB
  thêm lần nữa.
- Probe FTS bằng `SELECT 1 ... LIMIT 1`, không tạo list domain/entity của mọi
  kết quả. Chỉ định riêng lemma/normalizedLemma/meaningVi để một cột không vô
  tình làm probe cột khác pass.

Đây là giảm truy vấn/cấp phát dựa trên code path; chưa có benchmark startup trên
thiết bị nên không công bố mức tăng tốc theo ms hoặc phần trăm. Generation cũ,
rejected và staging còn lại sau process death chưa có garbage collection; cần
chính sách retention riêng nếu triển khai nhiều phiên bản nội dung liên tiếp.
