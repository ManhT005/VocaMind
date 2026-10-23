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
