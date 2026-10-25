# VOC-DATA-01 — Implementation and validation

Ngày xác minh: 2026-10-25. Windows PowerShell, Python 3.13.5 (`py -3`).
Nhánh: `feature/voc-data-01-external-sources`, nền `89225c2`.

## 1. Căn cứ và phạm vi đã hoàn thành

Đã đối chiếu SRS cũ, baseline v1.1, Excel Phân công và plan; xem
[traceability](voc-data-01-requirements-traceability.md) với hash tài liệu gốc.
Dòng VOC-DATA-01 yêu cầu source governance; canonical schema và ingestion thuộc
VOC-DATA-02/03. Không thay app/Room/catalog asset/userdata.

- Sáu nguồn có decision/evidence/license/obligations trong [ledger](voc-data-01-source-ledger.md)
  và [registry](data/source-registry.json). FVDP và Tatoeba audio luôn exclude.
- Thu nhận raw NGSL 1.2 (2.809 dòng) và TSL 1.2 (1.250 dòng); URL redirect,
  timestamp UTC, byte count và SHA-256 thực tế lưu trong registry.
- Lưu 12 artifact tải từ upstream: CSV, source pages, legal code, Kaikki raw
  index/Wikimedia terms, Tatoeba terms/license/download index. Thêm hai notice
  attribution offline NGSL/TSL. [Capture manifest](data/evidence/voc-data-01-research-20261025/capture.json)
  không có lỗi tải. Evidence HTML không thay raw corpus.
- Công cụ capture chạy riêng, không ghi đè capture cũ hay tự duyệt nguồn.
- Gate xác minh byte/hash cả research evidence và pending snapshots; import chỉ
  được phép khi có PO/Content Lead sign-off đúng snapshot, role approval,
  legal code, attribution và dữ liệu. Tatoeba cần metadata câu và links.
- Git giữ nguyên byte evidence bằng `-text`; CI audit và tests không gọi mạng.

## 2. Kết quả kiểm chứng

| Lệnh | Kết quả |
| --- | --- |
| `py -3 -W error::ResourceWarning -m unittest discover -s tools/vocabulary -p 'test_*.py' -v` | 18 tests pass |
| `py -3 tools/vocabulary/source_gate.py` | Exit 0; inventory và checksum hợp lệ |
| `py -3 tools/vocabulary/source_gate.py --source ngsl` | Exit 1 đúng kỳ vọng, chờ PO/Content Lead và review kỹ thuật |
| `git diff --check` | Pass |

Tests: registry/exclusion, unknown/pending license, valid synthetic snapshot,
corrupt/missing artifacts, dates, path confinement, technical review không thay
PO/Content Lead, approval gắn snapshot, metadata Tatoeba, toàn bộ inventory thật,
count CSV và sáu nguồn thật vẫn bị chặn. Positive fixture không duyệt nguồn thật.

## 3. Trạng thái bàn giao

Phần nghiên cứu, ledger, source registry, snapshot/checksum contract, evidence,
quyết định include/exclude và gate của VOC-DATA-01 đã triển khai. Hồ sơ sẵn sàng
review; **chưa có sign-off thật, không đánh dấu AC-12 sản phẩm đã đạt**.

PO/Content Lead cần ký [phiếu duyệt từng snapshot](data/source-approval-template.md).
Trường review kỹ thuật. Kaikki chưa chốt license route theo edition/snapshot;
Tatoeba chưa thu nhận corpus có author/license/link metadata. Các nguồn đó bị
chặn, không tự đi tiếp pipeline. Không thu nhận raw corpus chưa đủ hồ sơ chỉ để
tăng số nguồn. Thông tin quyền không rõ được ghi blocker và chưa dùng đóng gói.

Bàn giao VOC-DATA-03: gọi gate trước import, giữ provenance cấp field/record,
kiểm tra encoding TSL và actual license từng câu Tatoeba. UI attribution offline,
QA tối thiểu 150 vocabulary IDs và release sign-off cần kiểm chứng ở task tích hợp.
Chưa chạy Android build (không đổi app), chưa chứng minh CI chạy trên GitHub
cho lần cập nhật này. Không sửa các file Word/Excel gốc.
