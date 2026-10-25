# Kế hoạch DATA-01 — CatalogDatabase

## 1. Mục tiêu và căn cứ

**Dự án:** VocaMind — ứng dụng Android học từ vựng tiếng Anh ngoại tuyến, Java + Room + Clean MVVM.
**Người thực hiện:** Nhân. **Reviewer:** Thi. **Ưu tiên:** Critical.
**Lịch theo phân công:** Tuần 1, D1–D2. **Ước lượng:** 8 giờ.

Kế hoạch này được lập từ ba tài liệu người dùng cung cấp:

1. `VocaMind_SRS_Baseline_v1.1.docx`: căn cứ nghiệp vụ và dữ liệu chính, đặc biệt §7.1–7.4, §9.1, FR-VM-002/005/006/008/014, AC-02/04, ADR-01/03, DEC-04/05/10/14.
2. `Đặc Tả SRS Cho VocaMind.docx`: tài liệu nguồn; tham khảo luồng UC-01/02/09, kiểm thử TC-DATA-04, TC-INT-01/04 và các mô tả kỹ thuật liên quan. Khi khác biệt với quyết định đã chốt ở baseline v1.1, kế hoạch dùng v1.1 và ghi rõ giới hạn nghiệm thu.
3. `VocaMind — Phân công.xlsx`, sheet **Phân công**, dòng **DATA-01**: xác định người làm, thời gian, phạm vi và đầu ra; các dòng ARC-01, DATA-03, VOC-DATA-02 và VOC-DATA-06 xác định phụ thuộc và bàn giao.

Lưu ý mã: **task DATA-01 trong Excel** là CatalogDatabase; **SRS §7.3 DATA-01** là yêu cầu về nguồn học liệu. Hai mã không phải cùng một công việc. Tương tự, **SRS §7.3 DATA-05** là yêu cầu xác thực asset; **task DATA-05** là triển khai tìm kiếm FTS4.

## 2. Kết quả cần đạt

Tạo nền tảng catalog chỉ đọc ở tầng nghiệp vụ, được đóng gói dưới dạng asset `catalog.db`, mở bằng Room và phục vụ việc đọc gói học, mục từ, thông tin nguồn qua định danh ổn định.

Đầu ra gồm:

- Năm Entity: `ContentSource`, `VocabularyEntry`, `VocabularyFts`, `LearningPack`, `PackWord`.
- `CatalogDatabase`, cấu hình tạo dependency phù hợp ARC-01 và nạp bằng `createFromAsset`.
- Room schema JSON do compiler xuất; asset tương thích schema đó.
- Asset fixture có ít nhất 4 gói và dữ liệu truy vấn được; có metadata nguồn phù hợp dữ liệu thực sự sử dụng.
- Model/mapper và giao diện đọc tối thiểu để kiểm chứng catalog; phối hợp với DATA-03, không viết trùng tầng DAO.
- Kiểm thử integration mở asset thực, kiểm tra schema và liên kết FTS rowid → mục từ → stableId.
- Hướng dẫn tái tạo asset, checksum/version và báo cáo kiểm thử cho Thi review.

## 3. Phạm vi và giới hạn nghiệm thu

### Trong DATA-01

- Thiết kế schema theo baseline; tách vật lý catalog khỏi userdata.
- Nạp asset ở lần mở đầu, đọc lại bản sao đã tồn tại ở lần mở tiếp theo.
- Đọc catalog bằng stableId, giữ thứ tự `PackWord.orderNo`.
- Chuẩn bị cấu trúc FTS4 và kiểm chứng mapping cơ bản trên SQLite/Android thật.
- Chỉ công khai thao tác đọc catalog cho nghiệp vụ; thực thi I/O ngoài main thread.

### Bàn giao sang task khác

| Công việc | Task chịu trách nhiệm theo bảng phân công |
|---|---|
| UserDatabase và dữ liệu tiến độ | DATA-02 |
| DAO đầy đủ cho catalog/userdata, due/new/progress/review/quiz | DATA-03 |
| Repository contracts và implementation đầy đủ | DATA-04 |
| Tìm kiếm FTS4 Anh/Vi hoàn chỉnh và benchmark | DATA-05 |
| Quy tắc canonical schema/stableId và fixture hợp lệ/không hợp lệ | VOC-DATA-02, phối hợp với DATA-01 |
| Pipeline nhập và chuẩn hóa học liệu | VOC-DATA-03/04 |
| Catalog MVP đủ dữ liệu, QA và license | VOC-DATA-06 |
| Kiểm thử nâng cấp staged replacement/rollback | DATA-08 |
| UI danh sách gói, chi tiết từ và Attribution | Các task feature tương ứng |

**Không đánh đồng hai mốc:** Excel yêu cầu DATA-01 mở asset thật, schema hợp lệ, ≥4 gói và fixture truy vấn được. SRS AC-02 ở cấp MVP còn yêu cầu ≥150 stableId duy nhất và tối thiểu 40/40/40/30 membership mỗi gói. Nếu DATA-01 mới có fixture nhỏ, chỉ ghi nhận hoàn thành nền tảng DATA-01; **AC-02 toàn phần còn chờ VOC-DATA-06**. Không tự tạo nghĩa/ví dụ hoặc gán giấy phép chưa xác minh để đạt số lượng.

## 4. Phụ thuộc và phối hợp

### Điều kiện bắt đầu

- **Phụ thuộc chính thức: ARC-01**, theo cột Phụ thuộc của Excel.
- Checkout đang làm phải chứa Android foundation: Java, minSdk 26, Room, cấu trúc Clean MVVM và cơ chế Factory/DI.
- Chạy baseline build trước khi sửa để phân biệt lỗi sẵn có với lỗi phát sinh.
- Kiểm tra branch, trạng thái Git, phiên bản JDK/Gradle/AGP/Room từ dự án; không tự chọn phiên bản mới thay cấu hình nhóm.

Trong tệp Excel được gửi, ARC-01 được đánh dấu hoàn thành; vẫn phải xác minh code đã có trong checkout. Không suy ra môi trường build sẵn sàng chỉ từ trạng thái bảng.

### Phối hợp cần thiết

- **Thi:** xác nhận cấu trúc data layer, cách tạo dependency, quy trình build/test và review cuối task.
- **Thành:** phối hợp chốt fields và stableId của VOC-DATA-02. Excel đặt VOC-DATA-02 phụ thuộc DATA-01, vì vậy DATA-01 bắt đầu từ schema baseline, bàn giao schema sớm để tránh tạo phụ thuộc vòng.
- **Thi và Nhân ở DATA-03:** thống nhất DAO đọc tối thiểu nào thuộc commit DATA-01 và phần mở rộng nào làm sau.
- **Trường/nhóm nội dung:** cung cấp fixture hợp lệ hoặc dữ liệu mẫu do nhóm tạo có ghi nguồn rõ ràng; không chặn schema foundation trong lúc chờ catalog sản xuất.

## 5. Thiết kế dữ liệu phải tuân thủ

### 5.1. Schema theo SRS §7.2

| Entity | Thuộc tính/ràng buộc lõi theo SRS |
|---|---|
| ContentSource | `id`, `title`, `url`, `licenseCode`, `licenseUrl`, `snapshotDate` |
| VocabularyEntry | `id` là **stableId TEXT PK**; `lemma`, `normalizedLemma`, `pos`, `meaningVi`, `exampleEn`, `exampleVi`, `ipa`, `sourceId`, `contentVersion` |
| VocabularyFts | FTS4 lập chỉ mục `lemma`, `normalizedLemma`, `meaningVi`; nối bằng rowid số nguyên nội bộ |
| LearningPack | `id`, `title`, `description`, `category`, `orderNo`, `version` |
| PackWord | Khóa chính ghép `(packId, vocabularyId)`; `orderNo`; quan hệ nhiều–nhiều giữa gói và từ |

- `meaningVi`, `exampleEn`, `exampleVi` NOT NULL cho bộ MVP đã QA; kiểm tra riêng chuỗi rỗng vì NOT NULL không ngăn được giá trị rỗng.
- `ipa` được phép null. Nhãn “Chưa có phiên âm” thuộc UI, không ghi chuỗi placeholder vào cột IPA.
- Kiểu Java, tên cột, index, foreign key nội bộ và chính sách nullable còn lại phải được ghi trong thiết kế và thống nhất với canonical contract.
- Không đổi khóa chính VocabularyEntry thành rowid chỉ để thuận tiện cho FTS.
- Không dùng `WITHOUT ROWID` cho VocabularyEntry vì mapping FTS cần SQLite rowid nội bộ.

### 5.2. Quy tắc định danh

Theo BR-01 và mô tả baseline:

`stableId = 16 ký tự hex đầu của SHA-256(lemma + "_" + pos + "_" + senseIndex)`.

- Một VocabularyEntry đại diện một nét nghĩa.
- stableId không đổi khi sửa cách diễn đạt nghĩa hoặc ví dụ.
- Chuẩn hóa đầu vào và cách quản lý senseIndex phải được chốt với VOC-DATA-02, không tự đánh lại số theo thứ tự import.
- Kiểm tra trùng ID; không âm thầm ghi đè khi gặp collision hoặc record mâu thuẫn.
- `vocabularyId` trong PackWord tham chiếu `VocabularyEntry.id`.

### 5.3. Quy tắc FTS4

Luồng bắt buộc theo FR-VM-014 và DEC-05:

`VocabularyFts.rowid (INTEGER) → VocabularyEntry.rowid (INTEGER) → VocabularyEntry.id (TEXT stableId)`.

- Không nối trực tiếp FTS rowid với TEXT stableId.
- Không lưu rowid làm định danh tiến độ hay contract của tầng domain.
- Chọn cách khai báo FTS tương thích phiên bản Room của ARC-01, sau đó kiểm chứng schema được compiler sinh và asset thực. Không mặc định annotation external-content bất kỳ sẽ tương thích với Entity có khóa chính TEXT.
- Nếu Room có giới hạn về cách liên kết FTS, làm POC nhỏ trước, thống nhất với Thi; giữ nguyên stableId TEXT PK và ghi rõ phương án nội bộ. Không bỏ kiểm tra schema để vượt lỗi.
- Khi tạo asset, phải populate/rebuild chỉ mục phù hợp; bảng FTS tồn tại chưa chứng minh chỉ mục có dữ liệu.

### 5.4. Chỉ đọc và vòng đời

- `createFromAsset` tạo bản sao có thể ghi trong vùng dữ liệu ứng dụng; bản thân API này không thực thi chế độ read-only.
- Bảo đảm **nghiệp vụ chỉ đọc** bằng ranh giới API, không expose thao tác ghi catalog cho UI/use case; việc tạo asset diễn ra build-time.
- CatalogDatabase không chứa UserPack, tiến độ hoặc lịch sử học; không tạo foreign key xuyên hai database.
- Không dùng `allowMainThreadQueries()` cho luồng sản phẩm.
- Không coi tăng version hoặc `createFromAsset` là cơ chế tự cập nhật bản sao đã có. Thiết kế phải để ngỏ luồng stage → validate → close → atomic swap → rollback theo DEC-04, triển khai/kiểm chứng đầy đủ ở task liên quan.
- Không tự xóa userdata khi catalog lỗi. DATA-01 trả lỗi qua convention đã thống nhất ở ARC-01, không mở rộng sang triển khai màn lỗi UI.

## 6. Các bước thực hiện — tổng 8 giờ

| Bước | Công việc | Thời gian | Đầu ra kiểm chứng được |
|---|---|---:|---|
| 1 | Kiểm tra ARC-01, baseline build, chốt schema và ranh giới DAO | 1 giờ | Ghi chú thiết kế + baseline build result |
| 2 | Tạo 5 Entity, cấu hình export schema, POC mapping FTS với TEXT PK | 2 giờ | Room compile được, schema JSON thật, phương án FTS hợp lệ |
| 3 | Tạo CatalogDatabase/Factory và quy trình tạo asset fixture từ schema | 2 giờ | Asset ≥4 gói mở được bằng createFromAsset |
| 4 | Thêm giao diện đọc tối thiểu, model/mapper, query sample có thứ tự | 1 giờ | Đọc pack/từ/nguồn theo contract; không lộ rowid ra domain |
| 5 | Chạy integration test, kiểm tra checksum/version, hoàn thiện tài liệu và review | 2 giờ | Test report, asset metadata, PR bàn giao Thi |

**D1:** bước 1–2 và bắt đầu bước 3; bàn giao schema sớm cho Thành.
**D2:** hoàn tất bước 3–5, phối hợp Thi để DATA-03 có thể tiếp tục.

Đây là ước lượng của task trong Excel. Nếu POC FTS/schema hoặc foundation gây phát sinh vượt 8 giờ, báo Thi với lỗi và phương án cụ thể; không âm thầm cắt integration test.

## 7. Quy trình tạo và xác thực asset

1. Compile Entity bằng Room compiler của chính dự án, xuất schema JSON.
2. Tạo SQLite asset từ schema đã xuất hoặc từ database Room tạo thật trên Android; quy trình cần tái lập và ghi rõ công cụ/phiên bản.
3. Nạp nguồn và fixture đã duyệt; giữ liên kết ContentSource, VocabularyEntry, LearningPack và PackWord hợp lệ.
4. Tạo/populate FTS4 với rowid khớp VocabularyEntry; kiểm tra truy vấn mẫu Anh/Vi.
5. Kiểm tra version và Room metadata bằng schema thật. **Không hard-code identity_hash minh họa, không chỉ sửa hash để che sai DDL.**
6. Đóng kết nối và checkpoint nếu quy trình dùng WAL; asset bàn giao không phụ thuộc tệp `-wal`/`-shm` còn sót.
7. Chạy SQLite integrity check và kiểm tra foreign key nếu có khai báo; tính SHA-256 cho tệp asset cuối cùng, lưu kèm version theo quy ước nhóm.
8. Đặt asset vào đường dẫn đã thống nhất, chạy integration test trên cài đặt/database thử nghiệm sạch.

Đường dẫn đề xuất, cần điều chỉnh theo module thực tế: `app/src/main/assets/database/catalog.db`; chuỗi `createFromAsset` tương ứng là `database/catalog.db`. Đây là đề xuất triển khai, không phải đường dẫn bắt buộc trong SRS.

## 8. Kế hoạch kiểm thử và truy vết

Các mã `DATA01-Txx` dưới đây là mã test đề xuất cho task, không phải mã có sẵn của SRS.

| Test | Thao tác và kết quả mong đợi | Căn cứ |
|---|---|---|
| DATA01-T01 | Trên DB test sạch, mở đúng asset bằng Room; thực thi query để buộc mở DB; không lỗi schema/identity hash | FR-VM-002; SRS §7.3 DATA-05; TC-INT-01 ở mức foundation |
| DATA01-T02 | Đọc ≥4 gói; đọc count từ theo membership, không nhầm số từ duy nhất toàn kho với tổng liên kết | FR-VM-005; Excel DATA-01; DEC-10 |
| DATA01-T03 | Đọc từ thuộc một gói theo orderNo; pack rỗng/ID không tồn tại cho kết quả theo contract, không crash | FR-VM-006 |
| DATA01-T04 | Đọc chi tiết bằng stableId; nghĩa/ví dụ EN-VI hợp lệ; fixture có IPA null vẫn đọc và map được | FR-VM-008; AC-04 phần dữ liệu; DEC-14 |
| DATA01-T05 | MATCH với từ Anh và nghĩa Việt có trong fixture; nối rowid trả đúng stableId và nội dung; không duplicate ngoài mong đợi | FR-VM-014; ADR-03; DEC-05; tham chiếu TC-INT-04 |
| DATA01-T06 | Kiểm tra stableId không trùng, membership không orphan, khóa ghép ngăn trùng cặp pack–word | BR-01; SRS §7.2–7.3 |
| DATA01-T07 | Đọc ContentSource tương ứng với dữ liệu, metadata đủ để tầng trên hiển thị Attribution offline | SRS §7.2; FR-VM-045; phần API attribution của Excel DATA-01 |
| DATA01-T08 | Đóng rồi mở lại cùng DB test: dữ liệu vẫn đọc đúng; không nạp lại fixture sai cách hoặc ghi đè DB mỗi lần mở | FR-VM-002; UC-01 |
| DATA01-T09 | Asset sai schema ở vị trí test riêng bị từ chối có kiểm soát; không dùng destructive fallback để che lỗi | SRS §7.3 DATA-05; DEC-13 |
| DATA01-T10 | Đối chiếu SHA-256/version của asset đã đóng với metadata bàn giao; integrity check thành công | SRS §7.3 DATA-05 |

- Các test mở DB/FTS dùng Android instrumentation với **asset SQLite thực**, không chỉ in-memory DB hoặc mock DAO.
- Dùng tên DB test riêng và cleanup DB test; không xóa dữ liệu học thật để chuẩn bị test.
- Chạy smoke trên API 26 theo foundation; lưu phiên bản Android và cấu hình thiết bị/máy ảo.
- Test mapper có thể dùng JUnit; mock mapper pass không thay thế integration test asset.
- Thêm kiểm tra số liệu ≥150 unique và quota 40/40/40/30 khi nhận asset VOC-DATA-06; cho đến lúc đó ghi **AC-02 toàn phần: chưa nghiệm thu**.
- AC-04 UI placeholder và thao tác lưu từ cần feature/userdata khác; DATA-01 chỉ xác minh phần dữ liệu đọc.

### Lệnh xác minh dự kiến

Dùng tên module/variant thật và lệnh CI của ARC-01. Với module `app`, variant `debug`, lệnh dự kiến là:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Lệnh thứ hai cần emulator/thiết bị đã kết nối. Khi đã có bộ test khác trong dự án, lọc đúng lớp test DATA-01 bằng runner để tránh phạm vi không liên quan. Lưu exit status, số test executed/pass/fail/skip và report; không xem chạy 0 test là đạt.

Các lệnh trên là **kế hoạch cần chạy**, không phải kết quả đã xác minh trong tài liệu này.

## 9. Checklist Definition of Done

- [ ] ARC-01 có trong checkout; baseline build được kiểm tra.
- [ ] 5 Entity khớp SRS §7.2; VocabularyEntry.id là stableId TEXT PK.
- [ ] Room schema JSON được sinh từ cấu hình build thật và quản lý theo quy ước dự án.
- [ ] CatalogDatabase và dependency factory mở được asset qua createFromAsset.
- [ ] Asset fixture có ≥4 gói; dữ liệu mẫu có nguồn, nghĩa và ví dụ hợp lệ.
- [ ] PackWord có khóa ghép, thứ tự đọc đúng; không có tham chiếu mồ côi.
- [ ] FTS rowid nối INTEGER→INTEGER; tầng ứng dụng nhận stableId TEXT.
- [ ] Không expose thao tác ghi catalog hoặc DAO trực tiếp cho UI/ViewModel.
- [ ] Không truy vấn DB trên main thread trong code sản phẩm.
- [ ] Không hard-code hash minh họa; asset/schema/version/checksum được kiểm tra.
- [ ] Integration test asset thực chạy thành công; build và test liên quan có report.
- [ ] Tài liệu tái tạo asset và phạm vi chưa nghiệm thu được ghi rõ.
- [ ] Thi review; phản hồi blocker được xử lý; bàn giao schema cho Thành và DAO foundation cho DATA-03.

**Điều kiện hoàn thành DATA-01:** đạt đầu ra foundation của Excel và không vi phạm ràng buộc SRS. Chỉ ghi AC-02 toàn phần đạt khi catalog sản xuất đáp ứng đủ số lượng/chất lượng, không dựa vào fixture nhỏ.

## 10. Rủi ro và cách xử lý

| Rủi ro | Hành động |
|---|---|
| ARC-01 hoàn thành trên bảng nhưng chưa có trong branch | Đồng bộ với Thi đúng branch/commit; không tự dựng lại foundation khác nhóm |
| Schema canonical thay đổi muộn | Gửi schema draft ngay D1; thống nhất mapping trước tạo asset; regenerate schema/asset và chạy lại test nếu đổi |
| Room FTS không tương thích cách khai báo với TEXT PK | POC sớm trong bước 2; đọc schema/compiler error, thống nhất cách ánh xạ nội bộ với Thi; không đổi stableId thành rowid |
| Hash đúng nhưng DDL/FTS sai | Xác thực bằng Room mở asset thật và query; không sửa hash đơn lẻ để vượt lỗi |
| Thiếu học liệu đã duyệt | Dùng fixture được phép cho foundation, ghi rõ chưa đạt catalog MVP; bàn giao yêu cầu cho pipeline/content |
| Cập nhật asset không đổi DB đã copy | Không hứa tự cập nhật; chuyển bài toán staged upgrade/rollback sang luồng và task tương ứng |
| Không có emulator/thiết bị | Hoàn thành compile, asset validation và mapper test; ghi instrumentation chưa chạy, chưa đóng DoD cho đến khi có evidence |

## 11. Nội dung bàn giao review

PR/tài liệu bàn giao cần ghi:

1. Vấn đề giải quyết: cung cấp catalog offline tách khỏi dữ liệu học cá nhân.
2. Các Entity, schema version, đường dẫn asset, checksum, quy trình tạo lại.
3. Quyết định kỹ thuật FTS rowid ↔ stableId và contract đọc tối thiểu.
4. Thiết bị/Android dùng kiểm thử, lệnh thực tế, report và kết quả.
5. Fixture hiện có bao nhiêu gói, bao nhiêu stableId duy nhất và membership từng gói.
6. Những phần còn chờ DATA-03/VOC-DATA-02/VOC-DATA-06; trạng thái AC-02/AC-04 theo đúng phần đã kiểm chứng.

Tài liệu này là kế hoạch triển khai DATA-01, chưa phải bằng chứng code đã hoàn thành hoặc ứng dụng đã đáp ứng SRS.

## 12. Trạng thái triển khai trong phiên này

Đã triển khai trên nhánh local `feature/data-01-catalog`, nền `develop` commit
`37f14ca3cdb9086e9960fef988290c0839d5aa3e`. Xem [báo cáo xác minh](../data-01-validation.md)
và [hướng dẫn asset](../../tools/catalog/README.md). Build APK ứng dụng/test APK
và 32 lượt test đã đạt (22 catalog API 26/35 bằng Robolectric native SQLite +
10 Quiz). Instrumentation trên thiết bị chưa chạy; Thi chưa review. Fixture chỉ
có 4 stableId dùng chung giữa 4 pack, chưa đạt AC-02 cấp MVP.
