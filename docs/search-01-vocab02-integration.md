# SEARCH-01 tích hợp VOCAB-02 và kiểm chứng local

**Hiệu năng sau báo cáo này:** xem [điều tra/tối ưu 5.000 kết quả](search-01-performance-validation.md).
Bản tối ưu local có evidence source riêng, 519 host tests/4 QA tests PASS; AC-05/NFR-VM-03 vẫn HOLD.
Các SHA, số đo và 517 tests bên dưới là lịch sử bản tích hợp trước tối ưu.

Ngày 08/11/2026. Phụ trách Trường, reviewer Thành. Bản này để người dùng kiểm tra
và đề xuất tiếp. Sau kiểm chứng local, người dùng yêu cầu commit/push lên nhánh
SEARCH-01; **không tạo PR hoặc merge lên develop/main**.
Không tự đánh dấu SEARCH-01 Hoàn thành. AC-05/NFR-VM-03 vẫn **HOLD**.

## Source và thay đổi có sẵn

- Branch: `feature/search-01-search-ui`. HEAD lúc chạy kiểm chứng là
  `371d9eca5e084cc7e8ca57721a4efe78e64ccf3c` cùng phần merge local.
  Code nay nằm trong merge commit **`7cf9519fa476e10f604ad88c5fbef668228169f2`**;
  tree của commit này khớp chính xác tree đã kiểm chứng dưới đây.
- Fetch xác nhận VOCAB-02 đã nằm trong develop
  `78b9add1f5b628408013de034fb7b7651f382856`, qua PR #32 và các lần tích hợp sau.
  Không cherry-pick nhánh VOCAB-02 khác; source detail đã merge khớp source nhánh đó.
- Đã hòa develop vào working tree bằng `git merge --no-commit --no-ff origin/develop`.
  Bốn conflict ở build.gradle, VocabularyDetailFragment, strings.xml và docs index
  đã resolve, giữ cả source sets/resources của các feature trên develop.
  Merge local đã được hoàn tất tại commit7cf9519 trước lượt publish này; không còn unmerged paths.
- Tree source kiểm chứng (Git tree, không phải commit):
  **`463e424998e23b0f89f6da21b1d15b77e5e33306`**.
  Manifest Git blob IDs: [tested-inputs.json](evidence/search-01-vocab02-local/tested-inputs.json).
  Tài liệu/evidence bổ sung sau đó không thay code đã build/chạy.
- Working tree ban đầu có thay đổi **của người dùng** ở
  `gradle/wrapper/gradle-wrapper.properties` sang Gradle8.14.6. Giữ nguyên byte,
  hash SHA-256 `d2f49c3066b70477ab982a78495f20c04f46d0054e0653428d484f68664de8b5`.
  Không đưa thay đổi đó vào index. Lệnh kiểm chứng dùng executable Gradle8.9
  đã cache thay vì sửa wrapper hoặc ngầm dùng bản 8.14.6.

## Hành vi cuối

Search → detail hiện dùng **full VocabularyDetailFragment/ViewModel của VOCAB-02**:
lemma/POS/nghĩa, ví dụ Anh–Việt, IPA placeholder, Lưu/Bỏ lưu, retry và Back.
Đã bỏ preview, CatalogDetailViewModel/factory và lookup preview không còn dùng.

Contract explicit tại domain/repository:

| Entry point | Contract |
|---|---|
| Pack | `getDetail(packId, stableId)` / `setSaved(packId, stableId, target)` giữ membership validation; null/blank pack không được coi là pack hợp lệ |
| Catalog search | `getCatalogDetail(stableId)` / `setCatalogSaved(stableId, target)` lookup thẳng stableId; không chọn pack mặc định hoặc lọc active/paused |
| Use case | `GetVocabularyDetail.executeCatalog` và `ToggleSaved.executeCatalog` tái sử dụng repository của VOCAB-02 |
| Navigation | `searchArguments(stableId)` không có packId; `arguments(packId, stableId)` giữ tương thích; stableId TEXT16hex, không parse thành số |

Factory phân biệt route catalog khi packId vắng, truyền đúng use case vào cùng
VocabularyDetailViewModel. Catalog lookup dùng domain repository trên worker,
không có DAO/Room trong UI. Save reuse đúng transaction/policy VOCAB-02: target
idempotent; first-save tạo due-now progress với learning timestamps null và
reviewCount0; **chỉ khi bấm Lưu**. Bỏ lưu không xóa progress. Không tự activate pack.

Search engine/parser/FTS4 và generation/debounce600ms giữ nguyên từ DATA-05/SEARCH-01.
Search/read/navigation không ghi business state; schema/bootstrap DB được tách
khỏi snapshot kiểm tra. Detail model đóng tài nguyên riêng theo serial worker,
không đóng SearchProvider/catalog process-scoped đang đọc.

## Kết quả kiểm chứng

Windows, JDK Temurin17.0.20.1, Gradle8.9, AGP8.7.3, SDK35; Python3.13 với dependency
jsonschema pin của repo trong build/search01-python-deps. TEMP/TMP=`C:\tvm` cho
Robolectric/native SQLite Windows. `--offline` chỉ mô tả Gradle cache.

```powershell
$env:JAVA_HOME='C:\Users\LMT\.jdks\vocamind-temurin-17'
$env:GRADLE_USER_HOME='C:\Users\LMT\.gradle'
$env:PYTHONPATH="$PWD\build\search01-python-deps"
$env:TEMP='C:\tvm'; $env:TMP='C:\tvm'
$gradle89='C:\Users\LMT\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat'
& $gradle89 testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline
& $gradle89 '-PcatalogQa=true' assembleCatalogQa assembleCatalogQaAndroidTest lintCatalogQa --offline
python tools/catalog/package_qa.py --verify-apk app/build/outputs/apk/catalogQa/app-catalogQa.apk
```

| Kiểm tra | Kết quả |
|---|---|
| Full Android/JVM | **517 tests / 51 suites, 0 failures/errors/skips**, API26/35 |
| Focused source tests trước full run | PASS search, vocabulary detail/list, repository |
| debug APK + instrumentation APK | PASS |
| lintDebug | 0 errors / 13 warnings |
| QA150 APK + instrumentation APK | PASS, xác minh 150 vocabulary/FTS rows, 300 EN/VI checks, integrity ok/FK0 |
| lintCatalogQa | 0 errors / 13 warnings |
| Python unittest | **199 PASS**: vocabulary129 + catalog31 + contract39 |
| Emulator instrumentation | **4/4 PASS**, gồm full QA catalog flow, real Room recovery, 5000-row UI và benchmark telemetry |
| Whitespace phần app so với develop | `git diff --check origin/develop -- app`: PASS |
| Full staged whitespace | Còn 6 trailing-whitespace lines từ evidence QUIZ-03 đã có trên develop; giữ nguyên log lịch sử, không gán thành lỗi SEARCH-01 |

Lần setup đầu prepareReleaseCatalogTests thiếu jsonschema; đã dùng deps đã cài
trong build và chạy lại. Lần recovery test đầu chọn hai senses `book` cùng lemma,
SearchView không phát text-change lần hai nên await timeout. Đã sửa **test** chọn
hai lemma khác nhau, không sửa code sản phẩm để bỏ qua lỗi. Giữ focused.txt/full.txt
của các attempts và full-final.txt của lượt PASS. Dòng QC failed trong Python
unittests là negative fixtures; các suite kết thúc OK.

Evidence tổng: [verification.json](evidence/search-01-vocab02-local/verification.json),
JUnit/XML/lint/logs tại [thư mục evidence](evidence/search-01-vocab02-local/).
Chưa chạy GitHub CI cho source chưa commit.

## Chạy trên QA hiện hành

Profile `catalogQa` hiện có của repository dùng catalog nội bộ **150 từ**, version1.0.1,
applicationId **com.vocamind.app.qa**, tên launcher **VocaMind QA**. Trên Small Phone
đầu phiên chưa cài package này; đã cài profile đó riêng. Hai app đang có
`com.vocamind.app` (fixture4 từ) và `com.vocamind.app.quiz03validation` (150 từ) được
giữ nguyên, **hash database/WAL/SHM và APK path trước/sau giống nhau**.
Không clear/uninstall hoặc migrate dữ liệu các bản đang có.

Emulator: Small_Phone có sẵn, Android11/API30, x86/ranchu, 720×1280/density320,
runtime RAM khoảng2GB. QA chạy Airplane Mode, Wi-Fi tắt và dump xác nhận
`Active default network: none`. Sau đó trả Wi-Fi về trạng thái ban đầu; emulator
được giữ chạy, bản QA đang mở ở full detail **customer / khách hàng**.

| Ca đã chạy | Mức kiểm chứng |
|---|---|
| EN/VI, uppercase/NFC/NFD trên QA150 | SearchView → VM → use case → FTS thật, kết quả có đúng stableId |
| NoMatch, A→B→A/clear trước debounce | UI thật, clear vẫn NoInput sau 900ms |
| Search result không packId → full detail | Đối chiếu lemma/nghĩa/cả hai ví dụ với row domain từ catalog thật; stableId/packId trong route được assert |
| Save/Unsave/recreate/Back/reopen | Chỉ click mới mutate; persisted saved flag đúng sau recreation/Back; test khôi phục flag ban đầu |
| Room read failure → Error → Retry | Đóng DB UUID test đã mở; repository trả READ_FAILED, UI hiện Error/retry; reopen DB rồi bấm retry chạy đúng current raw query; không đóng catalog app |
| FTS5000 → VM → RecyclerView | Fixture DATA-05 thật, đủ5000 IDs duy nhất, không truncate; cuộn tới benchword04999 và clear. DB UUID test, không thay catalog QA150 |
| Tap thủ công qua ADB | Nhập customer, tap row thật, mở full detail; ảnh 06-manual-detail.png và hierarchy được giữ |
| Không bịa pack/không autoactivate | Host repository test gồm orphan ffffffffffffffff không membership, pack paused/inactive và snapshot; route pack sai membership vẫn NOT_FOUND |

Ảnh QA full detail/Save và 5000 rows:
[evidence](evidence/search-01-vocab02-local/02-full-detail-save.png),
[hàng cuối](evidence/search-01-vocab02-local/05-5000-last-row.png).
Test Save đã tạo một progress row due-now trong **package QA mới**, rồi trả saved=false;
row vẫn tồn tại theo policy VOCAB-02. Đây là thao tác test Lưu có chủ đích, không phải
search/navigation tự tạo progress.

## Độ trễ và gate

Harness DATA-05 hiện có: 20 warmups và100 samples/workload, tổng500 samples,
fixture SHA256 `5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`.
Không đổi fixture, assertions/workloads hoặc loại outlier. Đây là telemetry emulator
với latencyAssertionsEnabled=false, không tuyên bố nghiệm thu performance PASS.

| Matches | Mean ms | p95 ms | Max ms |
|---:|---:|---:|---:|
| 1 EN | 0.9654 | 1.6571 | 3.8686 |
| 1 VI | 0.9209 | 1.9503 | 3.8659 |
| 100 | 2.2221 | 4.0933 | 6.6847 |
| 0 | 1.0627 | 2.1269 | 7.5636 |
| 5000 | **50.2628** | **76.1241** | **99.1540** |

Lượt emulator này **có mẫu vượt50ms**, broad mean cũng vượt15ms; giữ đầy đủ raw
samples tại [JSON](evidence/search-01-vocab02-local/data05-fts-benchmark-api30.json).
Không đủ điều kiện A/B để suy ra code chậm hơn lượt cũ hoặc kết luận nguyên nhân.
Không tối ưu bằng cách cắt kết quả hoặc thay yêu cầu.

Benchmark đo DAO MATCH + entity materialization, loại asset open/queue/domain/UI.
Trace manual PID6327 tách debounce603.9103ms, queue0.5833ms,
repository query/hydration55.5148ms, submit→main57.1719ms, Content bind→post39.6008ms.
Repository trace có thể gồm mở kết nối đầu tiên và domain mapping; không dùng số đó
thay pure DAO benchmark. Submit→main chồng scope queue/query; bind→post không phải
GPU presented frame. Raw: manual-phase.txt, logcat.txt. Không cộng lặp các scope.

**AC-05/NFR-VM-03 HOLD** cho tới evidence đúng Helio G85/3GB/Android10 hoặc cấu
hình thay thế đã được chấp thuận. Thiết bị tham chiếu, GitHub CI và reviewer approval
cho bản tích hợp: NOT RUN/PENDING. Không nhận task Done từ emulator/host PASS.

## Hướng dẫn người dùng kiểm tra

1. Mở **VocaMind QA** trên Small Phone → Từ vựng. Thử `customer`, `khách hàng`,
   uppercase; chờ debounce600ms. Thử `notpresentsearchintegration`, xóa và gõ nhanh.
2. Bấm customer → đối chiếu POS/nghĩa, cuộn đọc ví dụ Anh–Việt, placeholder IPA.
   Bấm Lưu/Bỏ lưu, Back rồi mở lại; query phải giữ nguyên, saved flag đúng.
3. Pack → danh sách → detail vẫn dùng cùng receiver và membership validation.
4. Error/Retry thật và5000-result UI có instrumentation riêng, không có debug
   toggle/corrupt DB trên UI sản phẩm. Không tìm `benchword` trong catalog QA150
   để suy ra fixture bị thiếu: fixture5000 chỉ nằm trong APK test.

**SEARCH-02 handoff:** giữ raw query/SavedStateHandle, generation-per-input,
600ms decision, parser DATA-05 và retry/reset contract. Các gap real read-retry,
full detail và5000-result UI nay đã có evidence; tiếp tục hardening/accessibility
theo scope riêng, không thay engine/normalization hoặc gán debounce thành SRS.

**VOCAB-02 handoff:** cùng receiver/ViewModel/Save policy cho hai scope explicit;
pack route giữ validation, catalog route không bịa pack. Không còn preview.
Các task source/catalog release/benchmark reference vẫn theo owner hiện hành.
