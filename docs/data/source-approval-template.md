# Phiếu duyệt nguồn — VOC-DATA-01

Trạng thái: **PENDING — chưa có người ký**. Tạo phiếu riêng cho mỗi snapshot;
không dùng phiếu trống này làm artifact `approval`.

| Trường | Người duyệt cần điền |
| --- | --- |
| Source ID / snapshot ID | |
| Artifact + SHA-256 được duyệt | |
| License route, evidence đã đọc | |
| Phạm vi dữ liệu được đóng gói | |
| Attribution offline, thay đổi, ShareAlike | |
| Ngoại lệ và nội dung loại | |
| Quyết định | approve / reject |
| Người duyệt / vai trò | Tên thực / PO hoặc Content Lead |
| Ngày duyệt | YYYY-MM-DD |
| Biên bản xác nhận | |

Review kỹ thuật của Trường ghi riêng; không thay sign-off của PO/Content Lead
theo SRS baseline BR-12, DATA-06, AC-12 và DEC-09.

Sau xác nhận thực: lưu phiếu hoàn chỉnh, thêm artifact role `approval` với
URL biên bản HTTPS, path, bytes và SHA-256. Điền `review.reviewer`, `role`
(`PO` hoặc `Content Lead`), `decision=approved`, `reviewedAt`, `snapshotId`.
Điền thêm `review.snapshotSha256` bằng `snapshotContentSha256` từ báo cáo audit
JSON sau khi người duyệt kiểm tra đúng nội dung. Digest phiên bản 2 bao gồm source
identity, giấy phép, scope, evidence, obligations, redistribution và metadata/artifact
snapshot, trừ phiếu approval và cờ workflow để tránh hash vòng. Nếu thay quyền hoặc
dữ liệu dù giữ nguyên
snapshot ID, cần phê duyệt lại; không tự cập nhật digest để bỏ qua review.
Chỉ bỏ blockers, đặt `packagingStatus=approved`, `pipelineEligible=true` khi
mọi điều kiện đạt. Gate kiểm tra metadata và byte integrity; nhóm xác thực
người ký và nội dung phê duyệt. Không tự khai tên/vai trò thay người duyệt.
