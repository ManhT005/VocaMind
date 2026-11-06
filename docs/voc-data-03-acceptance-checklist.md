# VOC-DATA-03 — Nghiệm thu ingestion và bàn giao

Cập nhật: 05/11/2026. PR: [#18](https://github.com/ManhT005/VocaMind/pull/18).
Owner thực hiện: Trường; reviewer: Nhân. VOC-DATA-03 là ingestion học liệu,
khác task DATA-03 (DAO foundation) và SRS §7.3 DATA-03 (QA học liệu).

**Trạng thái: implementation candidate; chưa đủ điều kiện đóng task.**
Các mục pending dưới đây cần quyết định có người xác nhận, không được suy ra
approval từ CI xanh, trạng thái dependency trên sheet hoặc fixture hợp lệ.

## 1. Căn cứ và phạm vi nghiệm thu

Đối chiếu trực tiếp ngày 05/11/2026 với
[SRS baseline v1.1](https://docs.google.com/document/d/1rNqKaQGttr3DtK2OhIHOPYzUsJN2gVrv)
và [Phân công](https://docs.google.com/spreadsheets/d/1Zx4QWaSalYk2-KWoaRRxwL6Hd-vv-sa2Lv9dBDxzQFg).
Baseline v1.1 là chuẩn tắc. Task yêu cầu snapshot đã duyệt → normalize → canonical
→ reject report → intermediate tái lập, có snapshot/version/checksum và provenance.
Sheet hiện ghi Trường thực hiện, Nhân review, Đang làm 40%; tài liệu này không sửa
trạng thái đó và không thay đổi SRS.

Đầu vào orchestration hiện tại là `reviewed-jsonl-v1`: staging đã có sense,
nghĩa Việt và ví dụ EN/VI. Production yêu cầu chính staging này là data artifact
được duyệt; approval của wordlist không tự cấp quyền cho nghĩa/bản dịch ghép thêm.
NGSL/TSL/Kaikki readers dùng để đối chiếu component với artifact, không tự chọn
sense/ghép bản dịch. Tatoeba reader nhận linked-pair JSONL, chưa trích xuất trực
tiếp upstream sentences/links TSV. Chuỗi ETL rộng hơn trong SRS gốc chưa hoàn tất.

| Yêu cầu SRS v1.1 | PR #18 chứng minh | Phần còn lại / bên tiếp nhận |
| --- | --- | --- |
| §7.3 DATA-01: nguồn được kiểm soát | Gate, inventory, checksum, chặn hold/exclude trước parse; component readers | VOC-DATA-01: snapshot thật, hồ sơ quyền và sign-off; chưa import thật |
| §7.3 DATA-02: lemma/POS, stableId, single sense, ví dụ | Normalize/map staging, registry lookup, kiểm tra key/text provenance | Nhân chốt adapter; Trường chuẩn bị staging đã duyệt; chưa tự enrichment từ dump thô |
| §7.3 DATA-03: nghĩa không rỗng, ID duy nhất, ≥4 nghĩa/pack | Canonical validation và negative tests trên fixture | VOC-DATA-04: Thành QA nghĩa/ví dụ trên corpus thật, Trường review |
| §7.3 DATA-04: ≥150 stableId, pack 40/40/40/30 | Chưa đạt; fixture không được tính vào quota | VOC-DATA-06: Thi build/QA và báo distinct/membership, Nhân review |
| §7.3 DATA-05: SQLite/FTS/Room/checksum | Intermediate được validator kiểm tra; PR không build asset | DATA-01/DATA-05 và VOC-DATA-06 xác thực asset tích hợp |
| §7.3 DATA-06, BR-12, AC-12: ledger, attribution, duyệt nguồn | Chặn nguồn không được phép; không tự sinh approval | VOC-DATA-01 + Content Lead/PO ký theo snapshot; ATTR-01/VOC-DATA-06 chứng minh attribution offline |

Chặn một nguồn chưa duyệt là hành vi đúng của pipeline. Điều đó không chứng minh
AC-12 sản phẩm đã đạt; cũng không tự tạo lỗi implementation của core ingestion.

## 2. Ba quyết định phải có trước khi đóng task

Đây là phương án bàn giao để các owner xác nhận, chưa phải biên bản đã ký.
Ghi quyết định bằng review/commit được liên kết, gồm người xác nhận và phiên bản.

| ID | Phương án cụ thể | Người xác nhận / trạng thái |
| --- | --- | --- |
| ING-R01: adapter | Nghiệm thu PR như core ingestion từ staging reviewed-jsonl-v1 và các component readers đã liệt kê. Upstream extraction/join/sense selection còn thiếu phải được ghi thành công việc có owner trước khi tuyên bố ETL nguồn thô hoàn tất. Nếu bắt buộc raw adapter trong VOC-DATA-03, bổ sung implementation và tests trước đóng task | Nhân chốt phạm vi; Trường tiếp nhận adapter đầu vào. **Pending** |
| ING-R02: identity registry | Trường đề xuất thêm/retire sense trong một registry version mới; Thành (VOC-DATA-02) quản lý contract và review ý nghĩa identity; Nhân review stableId/mapping. Không tái dùng index đã retire, không đổi ID khi chỉ sửa wording; registry chưa duyệt không đưa vào config production | Thành/Nhân xác nhận custody; Trường xác nhận quy trình. **Pending** |
| ING-R03: release baseline | Thi (VOC-DATA-06/integration) lưu baseline đã được duyệt, checksum và phiên bản trong một thư mục versioned riêng dưới docs/data/releases/<contentVersion>/. Registry đã duyệt và manifest cùng hồ sơ QA/quyền phải đi kèm; lần sau truyền canonical baseline đó qua config.baseline. Không dùng fixture hoặc ghi đè phiên bản cũ | Thi/Nhân xác nhận nơi lưu và custody trước release đầu. **Pending; chưa tạo baseline** |

Nếu chưa có baseline phát hành, giữ `baselineCompared=false`. Sau release đầu,
mọi batch kế tiếp phải so baseline đã duyệt; thiếu baseline là blocker bàn giao,
không dùng kết quả fixture để bỏ qua. Pipeline chỉ đọc registry/baseline và không
tự cấp senseIndex, duyệt nguồn hoặc xuất bản baseline.

## 3. Gate kỹ thuật trước merge

Develop `82d438c` đã được tích hợp qua `c35e480`; conflict docs/README.md đã giữ
đồng thời liên kết DATA-03 và VOC-DATA-03. Kiểm tra lại base/head khi merge.

| Gate | Cách kiểm chứng |
| --- | --- |
| Integration | Không còn unmerged paths; giữ docs DAO/ingestion, cấu hình Gradle/CI và fixtures schema v2 từ develop |
| Sources | Frozen inventory audit, source gate tests, credit consistency; nguồn hold/exclude bị chặn trước parse |
| Python | Toàn bộ vocabulary/ingestion, canonical contract và catalog suites; hiện 115 methods, không dùng số 110 cũ làm ngưỡng cố định |
| Reproducibility | CLI verified fixture chạy hai thư mục mới, so byte đúng cả 4 artifacts; validator development pass và release phải từ chối fixture với exit 1 |
| DATA-03 regression | Full Android unit tests gồm DaoFoundationTest, CatalogAssetTest, UserDatabaseTest và DataBoundaryTest; không chỉ compile fixtures schema v2 |
| Android build | testDebugUnitTest, assembleDebug, assembleDebugAndroidTest trên code tích hợp; test APK build không thay device test |
| Head CI | Android CI của commit PR cuối cùng success; lưu reports/APKs và ingestion proof vào artifact cùng run |

Workflow CI chạy lại toàn bộ gates trên head mới. Fixture proof được giữ ở
`build/vocabulary-ingestion/ci-proof/`; CI upload cả hai lượt, logs và comparison.
Kết quả lần sửa mới được ghi tại [validation](voc-data-03-validation.md), tách khỏi
evidence lịch sử ngày 03/11 và follow-up trước đó.

## 4. Điều kiện merge, đóng task và release

- [ ] Nhân xác nhận ING-R01 và review implementation; không còn blocking review.
- [ ] ING-R02/03 có owner tiếp nhận và quyết định truy vết được; chưa giả lập sign-off.
- [ ] CI commit cuối xanh, diff đúng scope và develop vẫn tích hợp được.
- [ ] Batch snapshot thật được duyệt chạy thành công, truy vết từng component;
  nếu chỉ nghiệm thu core bằng fixture thì ghi rõ partial, task vẫn chưa Done.
- [ ] Khi đóng task: bàn giao config, registry version, rejects/normalization,
  canonical + manifest + checksum, proof hai lượt và decision links cho VOC-DATA-04.

Merge implementation candidate không tự đóng VOC-DATA-03 hoặc AC-12. Release còn
phụ thuộc QA nội dung, ≥150 stableId/4 pack, asset/FTS/Room, attribution offline,
sign-off nguồn và các acceptance gates sản phẩm. Không đánh dấu sheet Hoàn thành
hoặc tự merge từ hồ sơ này.
