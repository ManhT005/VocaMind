# VOC-DATA-01 — Implementation and validation

Ngày xác minh: 2026-10-25. Windows PowerShell, Python 3.13.5 (`py -3`).
Nhánh: `feature/voc-data-01-external-sources`, nền `89225c2`.

## 1. Căn cứ và phạm vi đã hoàn thành

Đã đối chiếu SRS cũ, baseline v1.1, Excel Phân công và plan; xem
[traceability](voc-data-01-requirements-traceability.md) với hash tài liệu gốc.
Dòng VOC-DATA-01 yêu cầu source governance; đợt hiện tại thực hiện thêm
[plan ledger/handoff mới](plans/voc-data-01-source-ledger-and-handoff.md).
Canonical schema và ingestion thuộc
VOC-DATA-02/03. Không thay app/Room/catalog asset/userdata.

- Bảy nguồn (sáu nguồn gốc và Commons research-only) có decision/evidence/license/obligations trong [ledger](voc-data-01-source-ledger.md)
  và [registry](data/source-registry.json). FVDP và Tatoeba audio luôn exclude.
- Bốn nguồn text là candidate với audit decision hold; chưa có accepted include.
  `ngsl-12`/`tsl-12` là alias của source ID cũ, giữ nguyên provenance.
- Frozen [snapshot.json](data/snapshot.json) kiểm tra registry digest và artifact
  hash. Capture mặc định dry-run; Commons chỉ hỏi API metadata khi có `--execute`,
  không fetch media. Đợt này không tải thêm corpus hoặc metadata live.
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
| `py -3 -W error::ResourceWarning -m unittest discover -s tools/vocabulary -p 'test_*.py' -v` | 41 tests pass |
| `py -3 tools/vocabulary/source_gate.py` | Exit 0; inventory và checksum hợp lệ |
| `py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json` | Exit 0; inventory đã đóng băng khớp registry và byte hash |
| `py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json --source ngsl-12` | Exit 1 đúng kỳ vọng, nguồn candidate/hold không được import |
| `git diff --check` | Pass |

Tests: registry/exclusion, unknown/pending license, valid synthetic snapshot,
corrupt/missing artifacts, dates, path confinement, technical review không thay
PO/Content Lead, approval gắn snapshot, metadata Tatoeba, toàn bộ inventory thật,
count CSV và bảy nguồn thật vẫn bị chặn. Bổ sung acquisition bằng mock response,
lỗi tải, stale/tampered frozen inventory, alias ID và Commons chỉ gọi API một lần,
không theo media URL. Positive fixture không duyệt nguồn thật.

## 3. Trạng thái bàn giao

Phần nghiên cứu, ledger, source registry, snapshot/checksum contract, evidence,
quyết định include/exclude và gate của VOC-DATA-01 đã triển khai. Hồ sơ sẵn sàng
review; **chưa có accepted include/exclude và sign-off thật, chưa Done theo plan
mới và không đánh dấu AC-12 sản phẩm đã đạt**. Chi tiết ở [handoff](voc-data-01-handoff.md).

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

## 4. Tối ưu gate và báo cáo

- Audit và frozen check dùng chung cache hash trong một lần chạy: 14 artifact
  được hash 14 lần thay vì 28 lần. Test đếm trực tiếp lời gọi `file_digest`;
  đây là giảm I/O lặp, chưa phải benchmark thời gian xử lý corpus.
- CI bỏ lệnh audit độc lập bị trùng; một lệnh kiểm tra đầy đủ và xuất JSON.
- Cache không ghi xuống đĩa; recheck file identity/size/mtime/ctime, so sánh lại
  expected hash. Thay byte cùng kích thước hoặc sửa checksum đều bị phát hiện.
- Approval buộc khớp content digest ngoài snapshot ID, tránh dùng lại review
  cho nội dung đã thay dù ID giữ nguyên. Không tạo hoặc giả lập sign-off thật.
- Validate kiểu dữ liệu và URL HTTPS trước khi xử lý artifact/evidence để lỗi
  manifest có thông báo BLOCKED thay vì lỗi thuộc tính chưa được kiểm soát.
- `--json` báo 7 nguồn, 4.059 dòng raw theo registry, 0 nguồn eligible và blocker
  từng nguồn. Không đồng nhất số dòng raw với số từ MVP được QA.

Không đổi registry, frozen snapshot hay raw evidence ở đợt tối ưu này. Trạng thái
hold/exclude và yêu cầu duyệt vẫn giữ nguyên.

## 5. Sửa hai lỗi review

- P1: digest approval phiên bản 2 ràng buộc cả thông tin nguồn (license code/URL,
  scope, obligations, evidence, redistribution và identity) cùng snapshot.
  Test thay license bằng license khác trong allowlist, thay scope, thay nghĩa vụ
  attribution đều bị chặn với approval cũ. Digest chỉ bao snapshot ở phiên bản
  trước không còn được chấp nhận; không tự tạo sign-off thay người duyệt.
- P2: frozen inventory bắt buộc có `registrySha256` và đầy đủ artifact khớp
  registry. Test xóa digest, rút inventory còn một file, đổi registry bị chặn.
  Acquisition manifest dùng purpose riêng `source-capture` và verifier riêng;
  không thể đổi purpose để vượt frozen check, không kết hợp với import approval.
- Tổng cộng 41 tests pass. Inventory hiện có vẫn xác minh thành công; cache vẫn
  hash đúng 14 file một lần mỗi invocation. Registry/raw data không đổi, không
  tải thêm corpus. Nguồn thật vẫn hold/exclude, chờ con người review.
