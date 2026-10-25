# VOC-DATA-01 — Implementation and validation

Ngày xác minh: 2026-10-25, Windows PowerShell, Python 3.13.5 (`py -3`).
Nhánh triển khai: `feature/voc-data-01-external-sources`, tạo từ
`feature/data-02-user-database` tại `89225c2`. Giữ nguyên nền checkout hiện có;
không merge hoặc đổi lịch sử nhánh DATA-02.

## 1. Kết quả triển khai

- [Plan](plans/voc-data-01-external-sources.md) theo cấu trúc các plan hiện có:
  thông tin task, căn cứ, phạm vi, bước làm, thiết kế, kiểm thử và DoD.
- [Ledger](voc-data-01-source-ledger.md) và [registry](data/source-registry.json):
  sáu nguồn có evidence/decision; quyền chưa rõ và snapshot chưa tải được ghi rõ.
- [Gate](../tools/vocabulary/source_gate.py): kiểm tra registry; chế độ `--source`
  kiểm tra approval, supported license route, review, snapshot UTC, artifact roles,
  path confinement, bytes và SHA-256. Không tải hay đóng gói nguồn ngoài.
- Android CI được bổ sung Python, registry validation và unit tests. Chưa có
  bằng chứng chạy workflow trên GitHub; đây là kết quả local.

## 2. Kiểm chứng thực tế

| Lệnh | Kết quả |
| --- | --- |
| `py -3 -W error::ResourceWarning -m unittest discover -s tools/vocabulary -p 'test_*.py' -v` | 13 tests pass, không có ResourceWarning |
| `py -3 tools/vocabulary/source_gate.py` | Exit 0, audit đủ 6 sources; nguồn eligible phải qua kiểm tra snapshot |
| `py -3 tools/vocabulary/source_gate.py --source ngsl` | Exit 1 đúng kỳ vọng, thiếu snapshot/checksum và offline attribution/evidence |

Tests bao gồm: mọi nguồn hiện tại bị chặn, không override exclusion, ID trùng,
snapshot tổng hợp hợp lệ, sửa byte cùng kích thước gây hash sai, thiếu evidence,
path vượt repo, thiếu review và license chưa hỗ trợ. Bổ sung kiểm tra cấu trúc
registry sai, ngày sai, CI phát hiện file eligible bị mất và Tatoeba thiếu metadata/links.
Dữ liệu positive test là
tổng hợp trong thư mục tạm, không chứng minh quyền sử dụng dữ liệu thực.

## 3. Giới hạn và bàn giao

Chưa thu nhận raw snapshot hoặc lưu bản điều khoản upstream bất biến. Không có
checksum giả. Kaikki còn chờ chốt license route; Tatoeba còn chờ metadata license,
authors và links ở cấp câu. Chưa có review của Trường. Gate mới kiểm tra manifest
và byte integrity, chưa kiểm tra ngữ nghĩa license/provenance từng record.
Production ETL chưa có nên chưa thể chứng minh mọi đường import đều gọi gate.

Không thay app code, Room schema, database asset hoặc userdata; không chạy lại
Android build cho thay đổi tài liệu/tool Python. Chưa nghiệm thu AC-12 cấp sản phẩm.
