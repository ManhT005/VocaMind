# SEARCH-01 — điều tra và tối ưu 5.000 kết quả

**C?p nh?t publish:** source/test ?? ki?m ch?ng t?i commit `8b06d1a8287f1571120f6889e4ba4b8616a79241`.
Regression cu?i: 46/46 instrumentation fixture4 PASS (first-launch ch?y ri?ng 1/1 PASS),
QA150 search/full detail/retry/5000 rows ch?y l?i 3/3 PASS. Ng??i d?ng y?u c?u commit/push;
kh?ng t?o PR/merge. C?c ghi ch? ch?a commit b?n d??i l? tr?ng th?i l?c thu evidence.
AC-05/NFR-VM-03 v?n HOLD.

Ngày 08/11/2026. Phụ trách Trường; reviewer Thành. **AC-05/NFR-VM-03: HOLD**.
Không sửa SRS, phân công hoặc đánh dấu task Hoàn thành.

## Source và môi trường

- Nhánh `feature/search-01-search-ui`, base HEAD `e12f0ee2064b672921a976b3f80ad64227e1cd24`.
  Thay đổi tối ưu đang local, **chưa commit/push**. Vì vậy HEAD riêng lẻ không đại diện
  cho bản mới: đối chiếu [source-sha256.json](evidence/search-01-performance-local/source-sha256.json)
  (266 source/config files), SHA256 `7020a8434559aa97667eedddf58feb61350a6500656572be68fbaddb10bb5fde`.
- APK baseline lấy lại từ package QA đã cài, SHA256
  `805cf000c7df757dd3dbb397ccfb47ee800fc276cae20f57ee361fafd0fec673`, khớp bản đã
  kiểm chứng ở code commit `7cf9519fa476e10f604ad88c5fbef668228169f2`.
- APK QA tối ưu SHA256 `b85a4ab73ac5da166bcae8cee3e18274a65a11f9bf8b4362e18de2ca59590a76`;
  test APK cuối `a507991d11a0c3678a2513e0d9372340ef3ea1c176f8050bacd7aa9fa5dcaf7d`.
- AVD có sẵn Small_Phone, serial emulator-5554, sdk_gphone_x86/ranchu,
  Android 11/API30, MemTotal 2.028.236 kB, 720×1280/density320.
  Chỉ thấy emulator qua ADB cả đầu/cuối lượt; **không có Helio G85/3GB/Android10**.
  [Inventory](evidence/search-01-performance-local/inventory.json),
  [ADB cuối lượt](evidence/search-01-performance-local/devices-final.txt).
- Windows, Temurin17.0.20.1, cached Gradle8.9, AGP8.7.3. Giữ nguyên thay đổi wrapper
  Gradle8.14.6 của người dùng; hash không đổi `d2f49c3066b70477ab982a78495f20c04f46d0054e0653428d484f68664de8b5`.
  Không xóa dữ liệu app, không tạo/wipe AVD. Cài lại APK QA/test bằng `install -r -t`;
  chỉ package riêng `com.vocamind.app.qa` được cập nhật.

## Nguyên nhân và thay đổi

Đọc lại SRS baseline v1.1: NFR-VM-03 yêu cầu query FTS4 <50ms, mục tiêu <15ms,
trên Helio G85/3GB/Android10, catalog 5.000 mục từ. Thời gian query phải tách khỏi
typing/render. Handoff DATA-05 dùng DAO MATCH **cộng entity materialization** làm
phạm vi đo bảo thủ; giữ nguyên phạm vi đó khi so sánh.

Chẩn đoán trên cùng DB UUID clone, luân phiên ba biến thể mỗi round, 20 warmups
và 100 mẫu/biến thể; SQL/projection/order không đổi:

| Biến thể trước tối ưu | Mean ms | p95 ms | Max ms |
|---|---:|---:|---:|
| Room generated entity mapper | 45,196 | 54,458 | 104,184 |
| SQL + Cursor count/window fill, chưa tạo Java entity | 12,791 | 17,485 | 23,604 |
| Cursor + đủ 5.000 entity/10 trường | 37,809 | 45,856 | 69,172 |

[Raw diagnostic](evidence/search-01-performance-local/diagnostic.json).
Các scope chồng nhau, không trừ chúng để công bố thời gian SQL/hydration chính xác.
Số liệu và generated code cho thấy chi phí adapter/materialization đáng tối ưu:
Room generated mapper gọi `isNull` rồi `getText` qua compatibility statement cho
từng trường. Đường Cursor đọc trực tiếp tránh các lời gọi trung gian đó, đồng thời
preallocate list bằng count. Đây là bằng chứng về cải thiện đường đọc, **chưa xác định
được nguyên nhân của từng outlier**. SQL vẫn có temp B-tree ORDER BY; không có bằng
chứng đủ mạnh để quy toàn bộ chậm trễ cho sort hoặc thay index/schema.

`CatalogDao.searchVocabulary` giữ signature trả `List<VocabularyEntry>`, SQL MATCH,
rowid mapping, thứ tự NOCASE/stableId và đầy đủ 10 trường. Bên trong dùng Room
`@Query` trả Cursor, lấy column indexes theo tên, đóng Cursor bằng try-with-resources
kể cả khi đọc lỗi. UI vẫn chỉ nhận domain qua repository/use case; không expose Cursor.
Không cache kết quả, cắt/paginate kết quả, đổi parser, tokenizer, schema, fixture hoặc
asset. Không thay generation/debounce/navigation/Save policy.

Regression lần đầu phát hiện Cursor legacy của Room có thể reopen DB đã đóng:
4 ca host lỗi (closed DB và real Error/Retry trên API26/35). **Giữ nguyên tests**;
thêm scalar read `SELECT 1` qua generated Room connection barrier trước Cursor để
giữ closed-owner failure và main-thread checks. Scalar read không ghi DB; chi phí
của nó **đã nằm trong benchmark bản mới**. [Log lỗi ban đầu](evidence/search-01-performance-local/validation.txt)
và [lượt cuối PASS](evidence/search-01-performance-local/validation-final.txt) đều được giữ.
Ownership vẫn phải bảo đảm owner không đóng DB giữa một query đang chạy.

## Đối chứng benchmark

Fixture DATA-05 không đổi SHA256
`5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`.
Mỗi lần giữ 5 workloads (1 EN / 1 VI / 100 / 0 / 5000 hits), 20 warmups,
100 mẫu/workload. Không loại mẫu. Chạy tuần tự **old-a → new-a → old-b → new-b**
sau khi host build/tests dừng, cùng AVD, cùng SQL/fixture/harness, không clear dữ liệu.
Không dùng host benchmark để kết luận đạt hiệu năng.

| 5.000 kết quả | Mean ms | p95 ms | Max ms | Mẫu ≥50ms |
|---|---:|---:|---:|---:|
| old-a | 45,302 | 50,991 | 109,465 | 7/100 |
| new-a | 39,734 | 47,198 | 52,977 | 3/100 |
| old-b | 42,905 | 47,702 | 53,528 | 1/100 |
| new-b | 35,854 | 39,687 | 43,145 | 0/100 |
| Gộp hai lượt cũ | 44,103 | 49,552 | 109,465 | 8/200 |
| Gộp hai lượt mới | **37,794** | **45,595** | **52,977** | **3/200** |

Mean quan sát giảm **14,3%** trong hai cặp emulator này. Lượt new-b riêng lẻ dưới50ms
không đại diện cho toàn bộ evidence; **vẫn có 3 mẫu mới vượt ngưỡng**. Mean broad
vẫn vượt mục tiêu15ms. Các workload còn lại mean khoảng0,9–1,5ms ở bản mới; xem
[comparison.json](evidence/search-01-performance-local/comparison.json) và raw `old-a/new-a/old-b/new-b-data05-fts-benchmark-api30.json`.
Đây là đối chứng emulator tuần tự, chưa phải thí nghiệm vật lý kiểm soát/random hóa
nhiệt, CPU/power và scheduling; không ngoại suy phần trăm cải thiện sang Helio G85.

Lượt baseline đầu chạy chồng host build có mean66,196ms/max158,380ms, được giữ ở
`baseline.json` nhưng **không dùng để tính mức cải thiện**. Lượt `baseline-idle.json`
độc lập và chẩn đoán cũng được giữ. Outlier bên trong mọi lượt đều còn nguyên.
Hai lượt A dùng diagnostic test APK đã build trước tối ưu; hai lượt B dùng test APK
cuối với hash được collector ghi nhận. Benchmark contract/workloads không đổi.

Đo lại chẩn đoán bản cuối: DAO mean36,661ms; Cursor đầy đủ36,126ms; Cursor count
12,503ms. [Raw](evidence/search-01-performance-local/optimized-qa-search-cost.json).
Không gộp lượt diagnostic với phân phối benchmark để tăng số mẫu hoặc làm đẹp max.

**Phạm vi:** benchmark là MATCH + order + đọc đủ entity (bản mới gồm scalar guard).
Không gồm asset open, parse, executor queue, domain mapping, debounce600ms hay render.
Trace UI được giữ riêng trong [SearchTiming logcat](evidence/search-01-performance-local/search-timing-logcat.txt);
`submit_to_main` chồng scope queue/query, `bind_to_post` không phải GPU frame.
Không lấy thời gian DAO thay cho typing-to-frame hoặc cộng lặp các scope.

## Kiểm chứng và trạng thái

| Kiểm chứng thực chạy | Kết quả |
|---|---|
| Host Android/JVM, API26/35 | **519 tests / 51 suites PASS**, 0 failed/error/skipped |
| Full projection differential test | So mọi trường với generated mapper cũ, nullable IPA, số lượng và thứ tự; PASS API26/35 |
| Build QA APK/test APK | PASS |
| lintCatalogQa | 0 errors / 13 warnings |
| Emulator QA bản cuối | **4/4 PASS**: EN/VI/uppercase/NFC/NFD, rapid/clear, full detail/stableId/Save/Back/recreation; real READ_FAILED→Error→Retry; 5000 unique rows/scroll/clear; cost diagnostic |
| Benchmark collector A/B/A/B | 4/4 chạy xong, tổng2000 samples; collector PASS không phải performance PASS |
| Gate <50ms trên emulator, mọi mẫu broad bản mới | **Chưa đạt**: 3/200 ≥50ms |
| Thiết bị SRS / approved substitute | **NOT RUN — chưa có kết nối thiết bị phù hợp** |
| AC-05 / NFR-VM-03 | **HOLD**: thiếu reference-device evidence; không đổi SRS/task status |
| Python/content pipeline, GitHub CI, physical-device QA lượt này | NOT RUN; không thay content/tools Python |

[Verification](evidence/search-01-performance-local/verification.json), JUnit XML,
lint XML, ảnh 5000 rows/detail và logs ở cùng thư mục. Tests Save chỉ thao tác có
chủ đích trong QA, trả saved flag ban đầu; progress do first-save giữ theo policy.
Không tuyên bố mọi kiểm tra UI là manual: các ca trên là instrumentation.

Lệnh local (PowerShell, từ root; dùng JDK17/cached Gradle8.9 như báo cáo tích hợp):

```powershell
$env:JAVA_HOME='C:\Users\LMT\.jdks\vocamind-temurin-17'
$env:GRADLE_USER_HOME='C:\Users\LMT\.gradle'
$env:PYTHONPATH="$PWD\build\search01-python-deps"
$env:TEMP='C:\tvm'; $env:TMP='C:\tvm'
$g='C:\Users\LMT\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat'
& $g '-PcatalogQa=true' assembleCatalogQa assembleCatalogQaAndroidTest lintCatalogQa testDebugUnitTest --offline
python docs/evidence/search-01-performance-local/run.py new-b app/build/outputs/apk/catalogQa/app-catalogQa.apk com.vocamind.app.data.catalog.FtsSearchBenchmarkInstrumentedTest
```

Lệnh runner từng lượt đã lưu `*-command.json`; script `run.py` lưu raw/inventory và
không tự coi serial là reference hardware. Bản QA tối ưu cuối vẫn cài ở `com.vocamind.app.qa`.

## Bước tiếp theo để xét gate

Kết nối thiết bị Helio G85/3GB/Android10, xác nhận model/SoC/RAM/API/build, trạng thái
nguồn/nhiệt/background load và reviewer chấp thuận inventory. Hoặc phải có quyết định
chấp nhận cấu hình thay thế trước khi dùng nó nghiệm thu. Cài QA/test APK bằng `-r -t`,
không clear dữ liệu; chạy cùng harness và fixture trên worker:

```powershell
& $adb -s SERIAL shell am instrument -w -r -e class com.vocamind.app.data.catalog.FtsSearchBenchmarkInstrumentedTest -e data05EnforceLatency true com.vocamind.app.qa.test/androidx.test.runner.AndroidJUnitRunner
```

Lưu log, raw JSON qua `exec-out run-as`, hash source/APK/fixture và inventory thiết bị.
Flag enforce chỉ bật assertion max<50ms; **không chứng minh cấu hình phần cứng**.
Nếu còn mẫu ≥50ms thì ghi FAIL ở phép đo đó và tiếp tục profiling trên thiết bị đó;
không đổi sang p95 hoặc chỉ workload ít kết quả để cho PASS. Báo mục tiêu15ms riêng.
SEARCH-02/VOCAB-02 tiếp tục dùng contract hiện có; không thay public result/navigation
hoặc full detail/Save để tối ưu đường đọc này.
