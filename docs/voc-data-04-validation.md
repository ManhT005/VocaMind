# VOC-DATA-04 — Normalization + quality gates

Ngày triển khai: 05/11/2026. Owner theo kế hoạch: Vương Quang Thành; reviewer: Trường.
Nhánh: `feature/voc-data-04-quality-gates`, base `develop` / `752cce4`.
PR: [#27](https://github.com/ManhT005/VocaMind/pull/27), target `develop`.
Trạng thái: **technical checks pass; chưa đóng nghiệm thu dữ liệu MVP**.

## Căn cứ và đầu vào

- [Kế hoạch người dùng cung cấp](plans/voc-data-04-normalization-quality-gates.md),
  lưu nguyên nội dung từ `VOC-DATA04_PLAN.md`.
- Base chứa ledger VOC-DATA-01, contract VOC-DATA-02 v1.0.0 và ingestion
  VOC-DATA-03, gồm verified component adapters. Không tìm thấy AGENTS.md.
- Đã chạy lại ingestion trên `fixtures/ingestion/verified/config.json`:
  4 record, 4 pack, 16 membership; manifest thành công, component provenance
  được đối chiếu nội dung, baseline chưa có (`baselineCompared=false`).
- Canonical envelope, packWords, identityRegistry, sourceMappings và các checksum
  dùng nguyên contract hiện có. Consumer VOC-DATA-06 nhận envelope cùng QC
  manifest; việc owner consumer xác nhận nghiệm thu vẫn pending.

## Thay đổi

`tools/vocabulary/quality.py` thêm CLI sau ingestion. Reuse T1, POS aliases,
stableId, meaningKey và validator; kiểm tra record, duplicate, provenance/license,
pack và review nghĩa/ví dụ. QC replay ingestion để xác minh lại ledger và snapshot
thực tế, không chỉ tin licenseCode hoặc approvalStatus trong canonical.

Review sidecar gắn SHA-256 vào từng normalized entry và toàn dataset (kể cả pack
context), yêu cầu ngày/người review cùng quyết định single sense, dùng đúng nghĩa,
dịch tương ứng và phân biệt đáp án. Thay wording giữ ID nhưng cần review mới.
Fixture decisions chỉ là dữ liệu test tổng hợp, không đại diện phê duyệt của người thật.

Output/report có thứ tự ổn định; lỗi blocking trả 1 và không xuất validated output
hay completion manifest. Dataset lỗi không được biến thành pass bằng việc loại
record âm thầm. Chạy lại phải dùng thư mục mới. IPA null hợp lệ, chuỗi IPA rỗng
vẫn fail contract; ví dụ ngoài 5–20 từ và dấu hiệu nhiều nghĩa chỉ là warning.

Không sửa Android, schema, thuật toán ID, ingestion hay quota release. Hướng dẫn
CLI, review format, chính sách reject và handoff: [QUALITY.md](../tools/vocabulary/QUALITY.md).

## Kiểm chứng

Môi trường local: Python embedded 3.13.9, jsonschema 4.26.0. Python hệ thống và
`py` không khả dụng; dùng `.tools/python/python.exe`. Để chạy script trực tiếp
trên bản embedded có sys.path cô lập, dùng `runpy` và chèn `tools/vocabulary`
vào sys.path; đây là điều chỉnh cách chạy local, không sửa pipeline production.

| Check | Kết quả |
| --- | --- |
| Vocabulary: `python -m unittest discover -s tools/vocabulary -p 'test_*.py' -v` | 106 pass, gồm 25 test QC và 10 test corpus |
| Contract: `python -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v` | 39 pass |
| Catalog: `python -m unittest discover -s tools/catalog -p 'test_*.py' -v` | 5 pass |
| QC-01…19 | Bao phủ trong `test_quality.py`, thêm custody, stale review và pack context |
| QC chạy CLI hai lần cùng input/config/review | Cả 6 file giống byte-for-byte |
| Canonical sau QC | Pass development contract; fixture bị chặn release |
| Thiếu review | Exit 1; không canonical, validated JSONL hay manifest |
| `git diff --check` | Pass |

[comparison.json](evidence/voc-data-04/comparison.json) lưu hash 6 file.
[Fixture report](evidence/voc-data-04/fixture-qc-report.json) có snapshot/config/
tool/review/input checksums và 4 nghĩa phân biệt mỗi pack.
[Unreviewed report](evidence/voc-data-04/unreviewed-qc-report.json) chứng minh fail
closed. Log các suite được lưu cùng thư mục evidence. Dòng BLOCKED trong negative
tests là kết quả mong đợi, không phải suite thất bại.

CI hiện có tự discover các test mới. Workflow bổ sung hai lần QC, so sánh toàn
bộ output, validate canonical, negative run thiếu review và upload artifacts.
**[Android CI #90 — SUCCESS](https://github.com/ManhT005/VocaMind/actions/runs/42029581859)**
đã xác minh cho implementation head `caa59a830365808eae8f12465862bb5fc90a684e`.
Job `127252295417` checkout merge ref `923b0931a16c6d9f9be017bd9801f7f28d7cd5bc`,
ghép head trên với develop `752cce4d2170f58cd138ead3b9bf1c90a35e1527`.
140 Python tests, source/credit/contract gates, ingestion/QC reproducibility và
negative publication đều pass; Android unit tests, `assembleDebug` và
`assembleDebugAndroidTest` đều BUILD SUCCESSFUL. Build instrumentation APK
không thay instrumentation tests trên thiết bị.

Artifact `13415741246` chứa reports/APKs/ingestion và QC proof, SHA-256 zip
`98281fed7469cd1ed8833aa33d1e1861636f2ec73ba131bd4e229bfd3b4eefa3`.
Đây là evidence cho implementation commit nêu rõ ở trên; mọi commit tiếp theo
được kiểm tra riêng trong PR, không gán CI cũ sang head mới.

## Những mục nghiệm thu còn mở

- VOC-DATA-01: quyền, corpus snapshot và phê duyệt nguồn thật; không tự bỏ hold.
- VOC-DATA-03: các quyết định adapter, identity custody và baseline tại
  [acceptance checklist](voc-data-03-acceptance-checklist.md) vẫn do owner xác nhận.
- Review nghĩa/ví dụ trên corpus MVP thật bởi reviewer có trách nhiệm; evidence
  fixture không thay thế bước này và không chứng minh AC-04/10/12 toàn sản phẩm.
- VOC-DATA-05/06 xác nhận handoff, enrichment và asset/release validation.
- Đã mở PR #27 vào develop và request review `Letruong18` (Trường).
  Review thực tế vẫn pending; không có xác nhận review nào được tự tạo.

## Nội dung PR chuẩn bị

Title: `feat(vocabulary): add normalization and quality gates for VOC-DATA-04`

Base: `develop`. QC chặn publish canonical khi record/pack/rights/review không đạt,
giữ stableId và provenance contract hiện hữu. Bổ sung review sidecar có checksum,
reports deterministic, fixture evidence, 25 test QC và 10 test corpus. Validation:
106 vocabulary + 39 contract + 5 catalog tests pass; 6 artifacts QC tái lập giống hệt;
unreviewed fixture exit 1 không publish. Nguồn/corpus thật, semantic approval và
review Trường còn pending; không tuyên bố task hoặc MVP đã được nghiệm thu.

## Rà soát hoàn thiện

Bổ sung xử lý lỗi ghi completion marker: dọn success payload, trả 1 và lưu report
failed khi filesystem còn ghi được; malformed manifest fail closed. Thêm consumer
API `quality.load_validated` kiểm tra đủ artifact/hash/mode/contract, test tích hợp
builder hiện hữu trên database synthetic tạm thời, và production-codepath test
với quyền synthetic cô lập (gồm thu hồi approval). Không thay approval nguồn thật.

[Checklist DoD và bàn giao](voc-data-04-acceptance-checklist.md) ghi rõ owner,
đầu vào còn thiếu và các bước thực tế để nghiệm thu corpus production.

## Corpus biên tập đã bổ sung

[Hồ sơ corpus](data/voc-data-04/README.md) có **150 mục nghĩa và 150 cặp ví dụ EN/VI**
được soạn với hỗ trợ AI, không trích nghĩa/ví dụ từ NGSL/Kaikki/Tatoeba. Bốn pack có
40/40/40/30 membership và tương ứng 40/40/40/30 meaning key phân biệt. IPA null.
135 headword có tham chiếu lexical vào snapshot NGSL đã lưu, 15 headword được chọn
biên tập; cả 150 mục có provenance riêng cho nghĩa, ví dụ và POS biên tập.
585 component references được đối chiếu key/nội dung bằng adapter hiện hữu.

TSV gốc, staging JSONL, canonical preview, identity proposals, bảng review 150 dòng,
review sidecar pending và manifest candidate được lưu versioned. **Không còn chỉ
có fixture 4 từ**. Proposal có stableId=null; canonical preview dùng proposed ID
để kiểm thử liên kết, không cấp ID phát hành hoặc sửa registry hiện hữu.

Mechanical QC trên toàn corpus: development contract pass, không có cảnh báo
length/single-sense heuristic. Production QC trả 1: 150 CONTENT_REVIEW_REQUIRED
và 2 SOURCE_NOT_APPROVED; gate thật chặn ingestion trước parse. Đây là kết quả
đã kiểm chứng, không phải giả định phê duyệt. Candidate manifest không phải
production completion marker; consumer hiện hữu từ chối đọc candidate.

10 test corpus gồm completeness, component lineage, immutable/deterministic
snapshot, content-edit/ID invariance, source checksum, output boundary, quota,
pending reviews và việc không thể publish. CI chạy lại exporter hai lần, diff
cả 9 file với nhau và với snapshot, đồng thời kiểm tra actual ingestion bị chặn.
150 Python tests liên quan (106 vocabulary + 39 contract + 5 catalog) đã pass local;
evidence corpus và log được bổ sung trong docs/evidence/voc-data-04/.
