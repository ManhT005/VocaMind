# Review VOC-DATA-02 v1.0.0

Trạng thái: **chờ review**, không điền thay xác nhận của thành viên.

| Bên | Nội dung cần xác nhận | Trạng thái |
|---|---|---|
| Thành (owner) | Single sense, nội dung bắt buộc, fixtures/quality contract | Bản triển khai đề xuất đã được soạn; chờ Thành kiểm tra/tiếp nhận |
| Nhân (reviewer) | Field/types/POS, sourceId, Room mapping, rowid và stableId, nullable IPA | Chưa review |
| Trường (ETL) | Output format, adapter POS, registry/senseIndex, provenance và khả năng cung cấp nghĩa/ví dụ | Chưa xác nhận với nguồn thực |
| Thi (integration) | ContentSource/attributionText, source snapshot artifacts, baseline/version và build catalog | Chưa review |

## Checklist để chốt

- [ ] Chấp nhận enum POS và quy tắc ASCII fold của ID; chạy vector trên implementation sẽ dùng.
- [ ] Chấp nhận envelope JSON v1, source registry và provenance nhiều nguồn.
- [ ] Chốt người quản lý sổ senseIndex và nơi lưu baseline đã phát hành.
- [ ] Nhân xác nhận mapping schema/DTO/Room; attributionText có nơi lưu offline.
- [ ] Trường thử một batch nguồn thực và báo các field không thể cung cấp; chưa có nguồn thì ghi rõ pending.
- [ ] Không đổi nghĩa nghiệp vụ của sense khi chỉ sửa wording để giữ ID.
- [ ] Registry/fixtures/tests qua trên môi trường người triển khai.
- [ ] Góp ý được xử lý, ghi commit/version, mới chuyển contract sang accepted.

Nguồn phổ biến vẫn cần evidence/license review theo BR-12/DEC-09 trước release; không yêu cầu tải toàn bộ nguồn hoặc duyệt nội dung thật chỉ để đọc contract này.

## Nhật ký quyết định

| Ngày | Vấn đề | Quyết định/Người xác nhận | Commit |
|---|---|---|---|
| Chưa ghi nhận | Review baseline v1 | Chưa có xác nhận của thành viên | — |

## Xử lý review ngày 02/11/2026

Trạng thái: **đã sửa implementation candidate; chờ review liên nhóm**.
Bảng này không thay chữ ký nghiệm thu hoặc phê duyệt data release.

| ID | Thay đổi / bằng chứng | Còn chờ |
|---|---|---|
| VD2-01 | attributionText xuyên canonical → SQLite → Room → mapper → domain/repository; test đối chiếu và offline attribution | Nhân/Thi review |
| VD2-02 | Namespace DATA-01 `pack_*` trong schema, fixtures, quota; adapter không remap ID | Xác nhận baseline phát hành; nếu đã dùng ID thử nghiệm khác cần duyệt migration |
| VD2-03 | Unique index LearningPack(orderNo), PackWord(packId,orderNo); negative tests Python và Room | Nhân review schema 2 |
| VD2-04 | CI cài jsonschema, chạy contract/fixture/pipeline tests trên PR develop; giữ source checks và Gradle | Chưa push nên chưa có kết quả CI remote |
| VD2-05 | Builder dùng canonical JSON và DDL Room compiler; tái tạo schema/asset/metadata; debug bundle cùng fixture; test checksum/FTS/null IPA/order/integrity/unique | Instrumentation trên emulator/device còn chờ |
| VD2-06 | Fetch origin/develop và ghép vào working tree, giữ foundation ngoài phạm vi | Chưa tạo merge commit |
| VD2-07 | Giữ release gate fixtureOnly, quota <150 và source approval; có regression test | Batch nguồn thật, evidence/license, snapshot, content QA và baseline thật |
| VD2-08 | CONTRACT.md ghi namespace, mapping/version, normalization, registry custody đề xuất và incompatibility | Thành/Nhân/Trường/Thi xác nhận; chưa Accepted |

### Quyết định kỹ thuật của bản sửa (chưa thay chữ ký nhóm)

- Giữ `pack_*` của DATA-01; không sửa logical UserPack IDs.
- attributionText bắt buộc lưu offline. approvalStatus/evidenceRef,
  snapshotSha256/provenance ở canonical/ETL, không phải Room entity.
- Giữ enum POS và stableId SHA-256 16 hex. NFC + ASCII fold cho lemma ID,
  full Unicode casefold cho meaningKey. Không cấp senseIndex theo vị trí array;
  sửa wording không làm đổi identity. Java adapter sao chép canonical đã validate.
- Room schema 1 → 2; contentVersion độc lập. Giữ export schema 1.
  Không thêm destructive fallback hoặc đoán attribution cũ. Chưa xác lập
  catalog nào đã được phát hành, nên **không tuyên bố migration không cần hoặc
  đã an toàn**. Catalog cài đặt schema 1 sẽ bị Room từ chối đến khi có upgrade
  path được duyệt. Nếu đã phân phối, cần staging/swap/rollback và test giữ
  UserPack/progress trước release. Không xóa userdata để sửa catalog.
- Thay fixture DATA-01 cũ bằng canonical chung chỉ là đổi dữ liệu kiểm thử,
  không coi fixture cũ là baseline production. Userdata không bị sửa.
- Registry custody đề xuất: ETL/content cấp và retire senseIndex; contract owner
  review; integration owner lưu canonical baseline và checksum. Người nhận
  trách nhiệm và nơi lưu baseline thật còn chờ xác nhận.

### Bằng chứng và liên kết

- [Validation hiện tại](VALIDATION.md)
- [Contract và quyết định integration](CONTRACT.md#integration-candidate-after-review-2026-11-02)
- [Builder và kiểm thử](../../../tools/catalog/README.md)
- Commit/PR sửa review: chưa tạo, chưa push; không điền link giả.
- Owner Thành, reviewer Nhân, ETL Trường, integration Thi: **chưa ký duyệt**.
