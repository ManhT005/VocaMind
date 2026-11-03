# DATA-05 — FTS4 search

## 1. Phân công và căn cứ

| Hạng mục | Nội dung |
| --- | --- |
| Tuần / Ngày | 2 / D6–D7 |
| Task / Nhóm | DATA-05 / Data — FTS4 search |
| Người thực hiện / Reviewer | Nhân, Trường / Thi |
| Ước lượng / Ưu tiên | 7 giờ / Critical |
| Phụ thuộc chính thức | DATA-01, DATA-04 |
| Yêu cầu | FR-VM-011..014; AC-05; NFR-VM-03; ADR-03; SRS §7.3 DATA-05 |
| Nguồn | VocaMind_SRS_Baseline_v1.1.docx và VocaMind — Phân công.xlsx đính kèm phiên này |
| Nhánh | feature/data-05-fts4-search |
| Nền | origin/develop tại c2ea69e |

Task DATA-05 trong Excel là triển khai tìm kiếm; mã SRS §7.3 DATA-05 là yêu cầu
xác thực asset/schema/FTS/checksum. Không đồng nhất hai mã.

## 2. Phụ thuộc hiện có và phạm vi

DATA-01 đã có catalog prepack, FTS4, domain models và mapper trên develop.
DATA-04 tổng thể chưa có branch/implementation đầy đủ trên remote khi kiểm tra.
DATA-05 bổ sung một search repository contract hẹp, tái sử dụng CatalogVocabulary
và CatalogMapper, không sửa CatalogRepository để tránh phá fake/consumer hiện có.
Cần owner DATA-04/Thi review contract và nối với repository tổng thể sau này;
không tuyên bố đã hoàn thành DATA-04.

Phạm vi: DAO FTS4, mapping integer rowid → VocabularyEntry → stableId TEXT,
repository Result/list domain, use case chạy background executor, functional tests
trên asset thật và công cụ benchmark có mẫu thô. Không sửa schema/index/tokenizer,
asset sản phẩm, UI, debounce hoặc state generation của SEARCH-01/02.

## 3. Cấu trúc dự kiến/triển khai

```text
app/src/main/java/com/vocamind/app/
├── domain/catalog/
│   ├── VocabularySearchQuery.java
│   ├── VocabularySearchResult.java
│   ├── VocabularySearchRepository.java
│   └── SearchVocabularyUseCase.java
├── data/catalog/
│   ├── CatalogDao.java                    # Thêm query MATCH sản phẩm
│   ├── FtsMatchExpression.java             # Grammar FTS4 an toàn từ tokens
│   └── RoomVocabularySearchRepository.java # Entity → domain qua mapper hiện có
└── core/SearchProvider.java                # Sở hữu executor, không sở hữu catalog DB
```

Test contract chung ở src/catalogTest/java; wrapper JVM ở src/test,
instrumentation ở src/androidTest. Benchmark asset chỉ ở src/catalogTest/assets,
không nằm trong app/src/main/assets. Tooling trong tools/catalog.

## 4. Quy tắc tìm kiếm

- NFC và lowercase Locale.ROOT, giữ dấu Việt; tách Unicode letter/digit/marks
  thành token. Không hứa tìm không dấu tương đương có dấu.
- Mỗi token là literal prefix, tất cả token phải khớp (implicit AND). Grammar FTS4
  là `"token*"`, không đưa raw user text thành SQL hoặc toán tử MATCH.
- Ví dụ `ngâ hàn` → `"ngâ*" "hàn*"`; `bank OR airport` tìm ba token literal,
  không trở thành điều kiện OR để mở rộng kết quả.
- Dấu câu là separator; null/blank/chỉ punctuation → empty success, không gọi DB.
- Guard kỹ thuật: tối đa 256 code points và 32 tokens; input vượt giới hạn trả
  INVALID_QUERY, không âm thầm truncate. Đây là quyết định triển khai cần Thi/Trường
  review, không phải giới hạn văn bản được SRS quy định.
- SQL MATCH trên lemma/normalizedLemma/meaningVi, join `f.rowid=v.rowid` số nguyên.
  Kết quả domain chỉ chứa stableId, không expose rowid.
- Không join PackWord nên một từ trong nhiều pack không nhân kết quả; hai sense
  khác stableId vẫn là hai mục từ. Ordering deterministic theo normalizedLemma, id.
- Không đặt limit ngầm; caller/UI có thể cần hợp đồng paging ở task sau nếu kho lớn.
- Result phân biệt empty/no-match với INVALID_QUERY, READ_FAILED, INVALID_DATA,
  EXECUTOR_UNAVAILABLE. Lỗi không biến thành kết quả rỗng thành công.

## 5. Background và tích hợp

Use case trả CompletableFuture. SearchProvider tạo single background executor;
caller không đợi `.get()` trên UI thread. Input empty được trả ngay và không
schedule worker. Repository blocking chỉ chạy trên worker khi đi qua provider.

SEARCH-01 quản lý debounce, request generation và bỏ kết quả cũ, SEARCH-02 render
empty/loading/error/reset. Future cancellation không được coi là đã interrupt một
SQLite query đang chạy. Provider.close shutdown graceful, từ chối công việc mới;
owner bỏ callbacks sau dispose và giữ catalog DB sống đến khi read đang chạy xong.

## 6. Kế hoạch 7 giờ

| Bước | Công việc | Giờ |
| --- | --- | ---: |
| 1 | Đọc SRS/phân công, xác minh nền và schema/tokenizer | 0,5 |
| 2 | Query/token literal-prefix và DAO FTS4 | 1,5 |
| 3 | Domain Result/repository, async use case, factory | 1 |
| 4 | Integration Anh/Vi/empty/error/rowid và unit background tests | 1,5 |
| 5 | Prepack benchmark 5.000 từ, raw samples và runner thiết bị | 1,5 |
| 6 | Regression/build/lint, report và bàn giao Thi/Trường | 1 |

## 7. Test và benchmark matrix

| Ca | Bằng chứng |
| --- | --- |
| T01 | bank / ngân hàng trả cùng stableId từ asset thật |
| T02 | Prefix Anh/Vi, uppercase, NFC/NFD và whitespace |
| T03 | book trả hai senses khác ID; không duplicate theo pack |
| T04 | Blank/punctuation/no-match/operator-like input không gây query syntax lỗi |
| T05 | Chứng minh cả lemma, normalizedLemma và meaningVi được index |
| T06 | Đổi rowid ở bản sao test và rebuild FTS row: stableId không đổi |
| T07 | DB đóng trả READ_FAILED; blank không truy cập DB; reopen hoạt động |
| T08 | Executor thật chạy khác calling thread, Future không chặn caller |
| T09 | Invalid input, rejected worker, result immutable và duplicate IDs |
| T10 | 5.000-word prepack: rare English, Vietnamese, prefix 100 hits, no-match và broad prefix 5.000 hits |

Benchmark warmup 20 lượt/query, đo 100 lượt/query bằng nanoTime, giữ raw samples,
mean/p50/p95/max. Timing là DAO MATCH + Entity materialization; không gồm mở asset,
expression generation, executor queue, domain mapping, typing hoặc UI render.

NFR-VM-03 yêu cầu Helio G85 / RAM 3 GB / Android 10; <50 ms bắt buộc, <15 ms mục tiêu.
Host native SQLite chỉ profiling; dù số đo thấp vẫn **không đạt gate thiết bị**.
Runner instrumentation có tùy chọn assertion max<50 ms; chỉ bật sau khi xác minh
thiết bị tham chiếu và lưu inventory/evidence. Không thay threshold hoặc bỏ query
chậm để làm report đẹp. 5.000 từ synthetic chỉ là workload, không phải học liệu MVP.

## 8. Definition of Done

- [x] Search Anh/Vi/prefix/empty trả domain/stableId, không lộ Room/rowid.
- [x] Use case có executor nền và test caller không bị block.
- [x] Query FTS4 và mapping được kiểm chứng trên prepack thực.
- [x] App/test APK, regression và lint có kết quả thực.
- [x] Benchmark có raw evidence và mô tả đúng cấu hình/phạm vi timing.
- [ ] Đo/đạt <50 ms trên thiết bị tham chiếu; ghi riêng target <15 ms.
- [ ] DATA-04/Thi/Trường xác nhận boundary; SEARCH-01/02 tiếp tục UI/debounce.

Chưa được phép ghi DATA-05 hoàn tất nghiệm thu AC-05/NFR-VM-03 nếu thiếu benchmark
thiết bị tham chiếu, dù phần code/host tests đã chạy thành công. Không tự ghi nhận
review hoặc sửa trạng thái Excel thay nhóm.

## 9. Trạng thái thực tế

Phần code/host test đã triển khai tại local: 149/149 test đạt, gồm 24 lượt mới;
app/test APK build thành công và lint không có lỗi. Xem
[báo cáo](../data-05-validation.md) và [evidence](../evidence/data-05/unit-results.json).
Host profiling có outlier >50ms, broad query mean >15ms, chưa đạt/xác minh gate
thiết bị tham chiếu. Connected instrumentation, DATA-04/Thi/Trường review và UI
tích hợp còn pending. Nhánh bàn giao: feature/data-05-fts4-search; chưa merge vào develop/main.
