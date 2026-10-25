# Kế hoạch VOC-DATA-01 — Research external vocabulary sources

## 1. Thông tin task

| Hạng mục | Nội dung |
| --- | --- |
| Dự án | VocaMind |
| Task / Lịch | VOC-DATA-01 / D1 |
| Thực hiện / Reviewer | Thi, Nhân / Trường |
| Ước lượng / Ưu tiên | 5 giờ / Critical |
| Phụ thuộc chính thức | Không có trong dòng backlog |
| Mục tiêu | Ledger có evidence và quyết định cho mọi nguồn; chặn nguồn chưa rõ quyền |

## 2. Căn cứ và nguyên tắc

Backlog người dùng: SRS §7.3 DATA-01/06; BR-12; AC-12; NFR-VM-12; DEC-09.
Không có toàn văn SRS trong checkout; không khẳng định đã đối chiếu toàn bộ.
Phân biệt VOC-DATA-01 nghiên cứu nguồn với DATA-01 CatalogDatabase.
Giữ định dạng tài liệu lowercase kebab-case, plan trong `docs/plans/`, báo cáo
xác minh riêng, liên kết từ README và documentation index.

## 3. Phạm vi và đầu ra

1. Khảo sát NGSL/TSL, Kaikki ViWiktionary, Tatoeba text từ trang chính thức.
2. Ledger: URL, license, evidence, quyết định, đóng gói và attribution.
3. Registry máy đọc, contract snapshot và SHA-256; thiếu quyền thì fail closed.
4. Loại FVDP và Tatoeba audio; test không cho đổi cờ để vượt exclusion.
5. CLI kiểm tra registry và kiểm tra từng snapshot trước import; unit tests, CI.

Không triển khai ETL nội dung, UI attribution hay catalog MVP. Không đánh dấu
nguồn đã duyệt khi chưa có snapshot/review. Snapshot chưa thu nhận phải để null.

## 4. Các bước thực hiện — 5 giờ

| Bước | Giờ | Đầu ra |
| --- | ---: | --- |
| Đối chiếu repo, backlog và nguồn chính thức | 1 | Evidence, giới hạn khảo sát |
| Lập ledger và registry | 1 | Sáu quyết định có lý do và obligations |
| Contract snapshot, license và attribution | 1 | Điều kiện mở gate, artifact roles |
| Triển khai gate và CI | 1 | CLI chỉ đọc; exit 1 khi không đủ điều kiện |
| Kiểm thử, validation và bàn giao | 1 | Kết quả thật, DoD và blocker còn lại |

## 5. Thiết kế triển khai

Registry canonical: `docs/data/source-registry.json`, schemaVersion 1.
Nhánh triển khai: `feature/voc-data-01-external-sources`, nền checkout `89225c2`.
`tools/vocabulary/source_gate.py` dùng Python standard library, không tải file,
không sửa registry, không cập nhật trạng thái review. `--source ID` kiểm tra
approval, license allowlist, review, snapshot, role và hash mọi artifact.
Path artifact phải nằm trong repository, sau resolve cả symlink.
Lệnh audit mặc định dùng trong CI kiểm tra snapshot của mọi source được khai báo
eligible; không chấp nhận chỉ đổi cờ rồi bỏ qua kiểm tra file/hash.

Snapshot có id, upstreamVersion, upstreamDate, retrievedAt UTC, artifacts.
Mỗi artifact có role, URL HTTPS, path tương đối, bytes và sha256. Các role bắt
buộc: data, license-evidence, attribution; Tatoeba thêm sentence-metadata, links.
Các file này cần nội dung thực được review; role và hash không tự chứng minh
ngữ nghĩa metadata đúng. Thêm license route mới phải sửa allowlist và review.

## 6. Kiểm thử và truy vết

| Kiểm tra | Kết quả yêu cầu | Căn cứ backlog |
| --- | --- | --- |
| Registry | Đủ sáu ID duy nhất, evidence và decision | DATA-01/06 |
| Pending / unknown | Không import nếu chưa duyệt hoặc chưa rõ quyền | BR-12; AC-12 |
| Excluded | FVDP/audio không được mở gate | DEC-09 |
| Snapshot | Hash/bytes sai, thiếu evidence, path vượt repo đều bị chặn | NFR-VM-12 |
| Positive fixture | Snapshot tổng hợp đủ điều kiện được gate chấp nhận | Contract kỹ thuật |

Lệnh và kết quả thực tế nằm ở báo cáo validation. Positive fixture không phải
phê duyệt nguồn thật. Không chạy Android build vì không đổi app/Gradle/asset.

## 7. Definition of Done và bàn giao

- [x] Ledger, registry, evidence URL và quyết định cho sáu nguồn.
- [x] Định nghĩa snapshot/checksum/quyền đóng gói/attribution.
- [x] CLI fail closed và kiểm thử; thêm bước kiểm tra vào cấu hình CI.
- [ ] Thu nhận raw snapshot, lưu legal evidence bất biến và checksum thực tế.
- [ ] Chốt license route Kaikki, attribution cấp record và review của Trường.
- [ ] ETL tương lai gọi gate và kiểm tra provenance từng record trước import.

Task nghiên cứu và gate đã có implementation; acquisition và nghiệm thu sản phẩm
còn pending. Chi tiết xem [validation](../voc-data-01-validation.md) và
[ledger](../voc-data-01-source-ledger.md).
