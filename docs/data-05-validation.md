# DATA-05 — FTS4 search

> Current integration follow-up: [PR #23 fix validation](data-05-fix-validation.md).
> Sections 4–8 below retain the original branch's test/benchmark evidence; the
> 149-test total and host samples are historical, not acceptance of the merged head.

## 1. Căn cứ, nhánh và phạm vi

D6–D7, 7 giờ, Nhân/Trường thực hiện, Thi review, Critical. Căn cứ trực tiếp hai
file đính kèm VocaMind_SRS_Baseline_v1.1.docx và VocaMind — Phân công.xlsx:
FR-VM-011..014, AC-05, NFR-VM-03, ADR-03 và SRS §7.3 DATA-05.
Xem [kế hoạch](plans/data-05-fts4-search.md).

Nhánh feature/data-05-fts4-search ban đầu dựa trên develop c2ea69e. Ngày 06/11/2026,
nhánh đã ghép develop 45db6049b3d8885bfab5570e4e70e03b0cf04511, gồm DAO foundation,
DATA-04 repositories và PACK-01/02. Giữ search boundary hẹp tương thích
model/mapper catalog, không đổi CatalogRepository hoặc consumer/fake hiện có.
Kết quả kiểm tra sau ghép và các gate còn thiếu nằm ở tài liệu follow-up phía trên.

## 2. Implementation

- VocabularySearchQuery/Result/Repository và SearchVocabularyUseCase thuộc
  domain/catalog, chỉ import Java, không Room/Android.
- CatalogDao.searchVocabulary là MATCH trên FTS4 hiện có, join INTEGER rowid với
  VocabularyEntry.rowid; trả Entity nội bộ, repository map sang CatalogVocabulary.
- FtsMatchExpression dựng literal token-prefix đúng grammar FTS4 `"token*"`.
  Không nối raw input vào SQL, không cho input thành OR/NEAR/column selectors.
- NFC + lowercase ROOT, giữ dấu, separator Unicode/punctuation. Null/blank/chỉ
  punctuation là empty success không gọi DB; không hỗ trợ accent-insensitive lookup.
- Mỗi token prefix phải khớp; không phải substring giữa từ hay fuzzy search.
  Input >256 code points hoặc >32 tokens trả INVALID_QUERY, không truncate.
- Result immutable, unique stableIds; hai sense khác ID vẫn được giữ. Không join
  pack membership, không expose FTS rowid/Entity ra UI, không limit ngầm.
- Use case submit đọc vào executor; SearchProvider sở hữu single worker và dùng
  CatalogDatabase do application owner giữ. Catalog database không bị provider đóng.
- Empty/no-match, INVALID_QUERY, READ_FAILED, INVALID_DATA và EXECUTOR_UNAVAILABLE
  là trạng thái phân biệt. Không bắt lỗi rồi trả danh sách rỗng như thành công.

Không đổi Entity, Room schema/version/tokenizer, asset sản phẩm, UI hoặc các luồng
ghi người học. Method matchFixture cũ giữ tương thích với tests DATA-01.

## 3. Tích hợp SEARCH-01/02

```java
// Composition root/core Factory tạo provider; không inject CatalogDatabase vào UI.
// Dùng lại provider trong scope của owner, inject search() vào ViewModel.
SearchProvider searchProvider = new SearchProvider(applicationOwnedCatalogDatabase);
CompletableFuture<VocabularySearchResult> request = searchProvider.search().execute(input);
// ViewModel nhận completion, kiểm tra request generation, post ViewState lên UI.
// Không gọi request.get()/join() trên UI thread.
```

Trường tiếp tục debounce và latest-query guard ở ViewModel, render empty/loading/
content/error, mở detail bằng stableId. getQuery trả text token đã chuẩn hóa, không
phải request generation ID. Cancel một Future không được coi là interrupt query
SQLite đang chạy; caller phải bỏ callback cũ/đã dispose. close() dùng graceful
shutdown; new work bị từ chối, queued/in-flight reads cần catalog còn sống.

## 4. Functional tests

SearchVocabularyUseCaseTest có 6 ca JUnit: normalize Unicode và operator-like text,
blank không schedule/read, background executor thật và caller không block, invalid
input/rejected executor, read failure, immutable/dedup result.

FtsSearchContract có 8 ca × API26/35 = 16 lượt native SQLite: English/Vietnamese
cùng stableId, prefix/case/NFD, hai sense, blank/no-match/operators, cả ba indexed
columns, rowid được đổi trong test copy nhưng stableId vẫn giữ, DB failure và reopen.
Integration mở asset thật của app qua createFromAsset; không thay query bằng mock.

Ca repack rowid dùng DELETE/INSERT FTS row trong UUID test DB để tái lập index cho
rowid mới; không giả định UPDATE alias rowid của FTS4 tự rebuild posting index.
Mọi mutation ở test copy, không đụng app/src/main/assets hoặc userdata.db.

Hai wrapper AndroidJUnit4 sẵn sàng chạy trên thiết bị, nhưng **chưa chạy** trong
phiên này. Host native SQLite không được coi là kết quả thiết bị thật.

## 5. Benchmark 5.000 từ

Tool [build_search_benchmark_fixture.py](../tools/catalog/build_search_benchmark_fixture.py)
copy schema/Room metadata từ prepack hiện có, seed 5.000 từ synthetic với stableId
SHA-256 16 hex và rowid số nguyên khác nhau. Asset cuối được đóng connection,
kiểm integrity/foreign keys/mapping và replace ở thư mục test riêng.

Dataset: app/src/catalogTest/assets/benchmark/catalog-5000.db, 1.704.936 bytes,
SHA-256 `5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`.
Hai lần generate cho cùng checksum. Đây là data test, không phải học liệu được QA
hoặc duyệt license và không chứa quota pack của bộ MVP.

Benchmark mở chính tệp prepack bằng Room createFromInputStream từ test resources/
instrumentation assets; không seed 5.000 hàng giữa lúc đo. Asset ứng dụng nhỏ vẫn
được kiểm riêng qua createFromAsset. Mỗi query warmup 20 lượt, đo 100 lượt; giữ
raw ns, mean/p50/p95/max. Timer bao gồm DAO query + Entity materialization, không
bao gồm mở asset/executor queue/query preparation/domain mapping/UI render.
Có 5 workload: rare English (1 hit), Vietnamese (1), prefix (100), no-match (0),
broad prefix (5.000). Kiểm số hit ngoài đoạn timed cho mọi sample.

**Kết quả chỉ là host profiling**, không xác nhận thiết bị Helio G85/RAM3GB/Android10.
Một số outlier >50ms; broad prefix có mean >15ms. Không xóa sample, không đổi ngưỡng,
không công bố NFR đạt dựa trên test xanh. Host tests chỉ xác minh benchmark runner
và dữ liệu hợp lệ; latency assertions mặc định tắt và được ghi rõ trong JSON.
Raw files và bảng số đo ở docs/evidence/data-05/ và phần kết quả cuối báo cáo.

### Chạy trên thiết bị tham chiếu

Xác minh và lưu model/chipset/RAM/Android thực của thiết bị trước. Chạy instrumentation
bằng Android Studio hoặc runner có kết nối điện thoại; cloud hiện không cung cấp
thiết bị Helio G85. Wrapper không tự suy chipset chính xác từ Build.HARDWARE.

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.FtsSearchInstrumentedTest,com.vocamind.app.data.catalog.FtsSearchBenchmarkInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.data05EnforceLatency=true
```

Windows dùng .\gradlew.bat. Khi bật, runner assert **max <50ms** cho mỗi workload;
mean <15ms được ghi là target, không sửa expected để qua test. Flag không tự chứng
minh máy là thiết bị tham chiếu: JSON vẫn referenceHardwareVerified=false cho tới
khi có inventory/evidence bên ngoài được reviewer xác nhận. Nếu gate fail, giữ
report và xử lý/query/paging contract với nhóm; không chuyển thành pass/skip.

JSON cũng nằm trong files/data05-fts-benchmark-api<API>.json của app test context,
và dòng DATA05_BENCHMARK_JSON trong runner output. Có thể lấy trên debug build:

```bash
adb shell run-as com.vocamind.app cat files/data05-fts-benchmark-api29.json
```

Không công bố AC-05/NFR-VM-03 hoàn thành khi còn thiếu phép đo thiết bị tham chiếu.

## 6. Chạy lại local

```bash
source /workspace/toolchains/activate.sh
python3 tools/catalog/build_search_benchmark_fixture.py
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2 --console=plain
```

source chỉ áp dụng toolchain cloud phiên này; máy cá nhân dùng JDK17/SDK của mình.
Benchmark DB đã được lưu trong test resources nên build thông thường không cần
Python. Regenerate chỉ khi chủ đích refresh schema/fixture, review checksum diff.
Report: app/build/reports/tests/testDebugUnitTest, app/build/test-results/testDebugUnitTest,
app/build/reports/lint-results-debug.xml. Không có diff schema/asset sản phẩm.

## 7. Bàn giao và giới hạn

- Nhánh bàn giao: feature/data-05-fts4-search; chưa merge vào develop/main; Thi chưa review.
- DATA-04 đã có trong develop được ghép; SEARCH-01/02 vẫn là handoff cần triển khai/
  kiểm tra riêng. Không sửa trạng thái Excel thay nhóm.
- 5.000-word benchmark synthetic không thay VOC-DATA-06 production catalog.
- Hiệu năng reference device chưa được đo/đạt; outlier host >50ms được giữ nguyên.
- Không có connected instrumentation evidence hoặc UI typing/render validation.

## 8. Kết quả lịch sử của nhánh trước lần sửa 06/11/2026

BUILD SUCCESSFUL: **149 executed, 149 passed, 0 failures/errors/skipped**.
24 lượt mới = 6 domain + 8 functional cases × API26/35 + 1 benchmark runner × API26/35.
Hai lượt benchmark pass chỉ là runner/fixture đúng, không phải latency gate pass.
App/test APK build thành công. lintDebug: 0 errors, 1 warning MissingApplicationIcon
có sẵn ở AndroidManifest (không đổi trong task). Kiểm tra ZIP xác nhận fixture5k
chỉ ở instrumentation APK, không trong app APK; asset sản phẩm không thay đổi.

| Host API | Workload / hits | Mean ms | P95 ms | Max ms |
| --- | --- | ---: | ---: | ---: |
| 26 | benchword04999 / 1 | 0.693 | 0.963 | 1.357 |
| 26 | nghĩa kiểm thử 04999 / 1 | 0.810 | 1.327 | 3.677 |
| 26 | benchword000 / 100 | 3.674 | 5.988 | 10.453 |
| 26 | notpresentbenchmark / 0 | 0.754 | 2.793 | 13.716 |
| 26 | benchword / 5000 | 27.747 | 37.382 | 150.382 |
| 35 | benchword04999 / 1 | 0.750 | 1.254 | 3.978 |
| 35 | nghĩa kiểm thử 04999 / 1 | 0.470 | 0.705 | 1.033 |
| 35 | benchword000 / 100 | 2.370 | 2.282 | 107.148 |
| 35 | notpresentbenchmark / 0 | 0.332 | 0.683 | 0.959 |
| 35 | benchword / 5000 | 20.545 | 25.925 | 109.741 |

Raw samples: [API26](evidence/data-05/benchmark-host-api26.json),
[API35](evidence/data-05/benchmark-host-api35.json).
Từng suite/hash: [unit-results.json](evidence/data-05/unit-results.json).
Không có xác nhận thiết bị tham chiếu; các số đo trên không đóng AC-05/NFR-VM-03.
