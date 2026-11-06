# SEARCH-01 validation và bàn giao

Phụ trách: Trường. Reviewer dự kiến: Thành. Phụ thuộc: DATA-05.
Trạng thái bàn giao: có triển khai và evidence local để review; **không tự đánh dấu Hoàn thành**.
AC-05 / NFR-VM-03: **HOLD** do thiếu benchmark thiết bị tham chiếu và kiểm thử UI trên thiết bị.

## Nền tích hợp và SHA

Đã đọc README, Android setup, workflow CI, DATA-05 validation/fix handoff, file
`SEARCH-01-yeu-cau-thuc-hien.md` người dùng cung cấp và trích trực tiếp SRS v1.1
DOCX: FR-VM-011..014, UC-02, DEC-05, AC-05, NFR-VM-03. Không sửa SRS/phân công.
Không tìm thấy AGENTS.md áp dụng trong repository/chuỗi thư mục cha.

Working tree ban đầu sạch trên develop. `git fetch origin` xác nhận develop và
origin/develop cùng `8209ef75bc882ca10367abd40900e44e537571cb`; không có nhánh
SEARCH-01 có sẵn. Tạo `feature/search-01-search-ui` từ nền này.

SHA code được kiểm thử: **67c06d4aca3b237d7eb8565dfbb410b5acafd674**.
Commit sau SHA này chỉ bổ sung tài liệu/evidence. Manifest và kết quả nằm tại
[evidence/search-01](evidence/search-01/); `verification.json` và `tested-inputs.json`
ghi phạm vi input, kết quả từng suite, hash APK/fixture và môi trường.
Terminal logs chuẩn hóa UTF-8/newline và bỏ padding cuối dòng để qua diff check;
raw samples JSON và kết quả JUnit được giữ nguyên nội dung.

## Thay đổi và phần tái sử dụng

- Điểm vào Home → Vocabulary dùng một SearchView và RecyclerView. Row hiển thị
  lemma/meaningVi, không đọc progress hoặc lọc theo active pack.
- SearchView → SearchViewModel → **SearchVocabularyUseCase của DATA-05** →
  **VocabularySearchRepository / RoomVocabularySearchRepository** → FTS4 hiện hành.
  Giữ parser VocabularySearchQuery, normalization, dấu tiếng Việt, stableId TEXT,
  schema/tokenizer/asset và contract trả mọi match. UI không import DAO/Room.
- Debounce **600 ms theo DATA-05 fix handoff**. Scheduler được inject. Mỗi input,
  kể cả clear, tokenless, invalid, retry đều tăng generation ngay; callback chỉ
  được apply khi owner còn sống và ticket hiện hành. Không so query text để
  xác định identity. Không ghi normalized query ngược vào SearchView.
- NoInput / Loading (gồm thời gian debounce) / Content / NoMatch / Error có text.
  Input mới bỏ snapshot cũ ngay. ListAdapter dùng stableId TEXT; row mang ticket,
  canOpen kiểm tra ticket và membership trong snapshot hiện hành trước navigation.
  Không bật RecyclerView long stable IDs hoặc parse hex thành long.
- Process-scoped SearchComposition khởi tạo CatalogProvider/SearchProvider trên
  worker, tái sử dụng worker DATA-05. Future cancellation được truyền vào future
  use case để bỏ work chưa bắt đầu; generation vẫn là điều kiện đúng đắn kể cả
  SQLite đang chạy hoặc cancellation thất bại. Không tạo worker/provider mỗi ký tự.
- SavedStateHandle giữ raw query qua recreate/process restoration; Back giữ
  ViewModel. Listener/adapter gỡ theo view lifecycle, observer dùng view owner.
  onCleared hủy debounce/invalidate callbacks; feature không đóng shared catalog.
  Catalog/worker sống đến hết process, không có teardown từ screen.
- Search route chỉ gửi vocabularyId. Receiver dùng CatalogRepository.getVocabulary
  trên worker, có Loading/Found/NotFound/Error và Back. Pack route có packId vẫn
  dùng PackWordsViewModel để validate membership. Không chọn pack mặc định,
  activate pack, tạo progress hay mở userdata từ search composition.
- Receiver search là **preview lemma/nghĩa/ID**, chưa phải full detail/save của
  VOCAB-02; không tuyên bố toàn bộ UC-02/AC-04 được nghiệm thu.

## Kiểm chứng

Windows host Intel Core i5-11400H, JDK Temurin **17.0.20.1+1**, Gradle 8.9,
AGP 8.7.3, SDK 35/build-tools 34.0.0; Python **3.13.14**.
Robolectric 4.16.1 API **26 và 35**, native SQLite cho integration/FTS.
Gradle chạy `--offline` bằng cache có sẵn; đây không phải Airplane Mode thiết bị.

```powershell
$env:JAVA_HOME='C:\Users\LMT\.jdks\vocamind-temurin-17'
$env:GRADLE_USER_HOME='C:\Users\LMT\.gradle'
$env:TEMP='C:\tvm'
$env:TMP='C:\tvm'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline
git diff --check
```

Kết quả cuối: **406 tests, 0 failure/error/skip**; gồm **26 lượt SEARCH-01**
(11 ViewModel tests × 2 API; 2 shared screen/adapter tests × 2 API).
assembleDebug và assembleDebugAndroidTest PASS; lintDebug PASS, **0 errors,
7 warnings** hiện hành. Không sửa/tắt assertion để đạt test.

Lần đầu ở TEMP mặc định thất bại SQLITE_CANTOPEN trong ProgressAggregationTest
`atomicReviewAndQuizWriterCannotTearAggregationSnapshotAcrossThreeInterleavings`.
TEMP `C:\tmp\vocamind-search01` vẫn lỗi ở catalog lookup; TEMP rất ngắn `C:\tvm`
cho full suite pass. Điều này phù hợp giới hạn đường dẫn SQLite Windows với tên
test/database dài; chưa chứng minh độc lập giới hạn chính xác. Giữ log failed
attempts `android-initial-failure.txt`, `android-short-temp-failure.txt`; không
thay source test DATA-07 hoặc coi failed attempts là PASS.

Python/data: **22 bước PASS**, gồm **159 unittest tests** (112 vocabulary,
8 catalog, 39 contract), source gate/credits, fixture reproducibility, expected
release/QC/editorial rejection, accepted handoff, pinned Unicode generation và
comparison. Runner `run_python_checks.py` ghi command/expected exit code và logs;
dependency pin từ requirements-validation.txt được cài vào build/search01-python-deps.
Lần setup đầu thiếu jsonschema; lần harness đầu chọn sai vùng output bị tool từ
chối; đã sửa harness theo output policy, không sửa các tool/data gate. Generator
chỉ đổi newline Windows; `git diff --exit-code` xác nhận không có source/data diff.

## Truy vết test

| Case | Evidence local | Giới hạn |
|---|---|---|
| T01 EN/VI, uppercase, NFD | SearchScreenTest đi qua composition/FTS thật; FtsSearchTest regression | Catalog app là fixture 4 từ |
| T02 rapid input/debounce | debounce600AndImmediateInvalidation, virtual clock 599 + 1 ms | Không dùng host elapsed làm debounce thiết bị |
| T03 out-of-order/stale error | outOfOrderContentAndOldErrorCannotStopNewLoading | Future cố tình từ chối cancel |
| T04 A → B → A | aToBToAUsesGenerationNotQueryEquality | A cũ trả sau A mới |
| T05 clear/whitespace/tokenless | clearWhitespaceTokenlessInvalidateRunningAndPending; clearRejectsQueuedCompletionAndError | Cả completion đợi main queue và in-flight |
| T06 NoMatch/error/retry | errorsAreDistinctFromNoMatchRetryUsesCurrentInput; invalidInputDoesNotSubmitOrBecomeNoMatch | UI retry button chưa thử lỗi thật trên thiết bị |
| T07 immediate/exception/dispose | immediateExceptionalAndRejectedFuturesUseMainDelivery; disposedOwnerIgnoresLateCompletionAndCancelsDebounce | Test delivery queue điều khiển |
| T08 Back/recreate/restore | SearchScreenTest Back/recreate, savedRawQueryRestoresAndNavigationUsesOpaqueId | Process-kill thực NOT RUN |
| T09 opaque ID/refresh click | opaqueNavigationArgumentsAndReboundHolder; canOpen stale generation | ffffffffffffffff không parse số |
| T10 catalog route/no pack | SearchScreenTest search trước activation, null packId; VocabularyListTest/PackVocabularyIntegrationTest pack regression | Paused-pack device smoke NOT RUN |
| T11 invalid/unknown/read fail | detailMissingUnknownFailureAndDisposedAreSafe; pack route regression | Real device fault injection NOT RUN |
| T12 offline | Luồng local host/FTS, app không khai báo INTERNET, không dùng network trong production flow | Airplane Mode first launch trên thiết bị NOT RUN |
| T13 no writes | SearchScreenTest logical snapshot mọi table catalog/userdata trước/sau search/detail/Back/recreate; userdata existence không đổi | Asset import ban đầu là bootstrap, snapshot sau import |
| T14 dài/nhiều/keyboard | Adapter bind nghĩa dài, RecyclerView; DATA-05 broad benchmark 5.000 matches | Visual/small-screen/IME/5.000-row UI device QA NOT RUN |
| T15 latency | Host raw benchmark và phase trace được giữ | Reference-device gate HOLD |

## Độ trễ và điều kiện nghiệm thu

Debug log tag `SearchTiming` không ghi raw query. Các phase được tách:

| Phase | Phạm vi |
|---|---|
| debounce_ms | input → scheduler fire, mục tiêu schedule 600 ms |
| queue_ms | DATA-05 executor submit → worker start; task bị cancel có thể vẫn có queue record nhưng không query |
| query_hydration_ms | RoomVocabularySearchRepository.search gồm FTS/entity/domain hydration |
| submit_to_main_ms | submit từ VM → completion được delivery trên main, gồm bootstrap/queue/query/callback |
| bind_to_post_ms | observe state → diff commit và View.post; **không phải GPU frame presented** |

`latency.json` giữ raw phase trace cùng 2 báo cáo benchmark DATA-05 API26/35:
20 warmups, 100 samples/workload, 5 workload (1/1/100/0/5000 matches), không xóa
outlier. Phạm vi benchmark DATA-05 là DAO MATCH + entity materialization; khác
query_hydration_ms nói trên vì không bao gồm domain mapping. Các scope không cộng
lẫn nhau hoặc dùng thay thế cho nhau. Bootstrap cold-read có thể nằm trong
submit_to_main_ms; không thuộc query gate.

Quan sát lượt cuối trên **host**, đơn vị ms (không phải kết quả nghiệm thu thiết bị):

| Phase | Mẫu | Min–max |
|---|---:|---:|
| Queue | 6 | 0.0381–0.6991 |
| Repository query + hydration, fixture 4 từ | 6 | 0.4454–39.6861 |
| Submit → main | 6 | 0.6572–42.3251 |
| Bind → post, gồm state/reset/recreate | 20 | 3.7968–85.0848 |

Workload broad 5.000 matches của benchmark DAO: API26 p95 **25.1028**, max
**26.7569**; API35 p95 **26.1882**, max **29.6933**. Cả 1.000 raw samples
(5 workloads × 100 × 2 API) được lưu. Không dùng các số này đóng ngưỡng <50 ms
hoặc mục tiêu <15 ms trên cấu hình tham chiếu.

Trace host dùng virtual scheduler: số wall-clock `debounce_ms` trong XML **không
phải 600 ms thực**. Virtual assertion chứng minh lịch 600 ms; trace chỉ xác nhận
có các phase. Fixture UI có **4 từ**, benchmark synthetic có **5.000 từ** chỉ ở
test assets, không thay catalog app. Host API26/35 trên CPU Windows không đại diện
Helio G85/3GB/Android10. **Không công bố đạt <50 ms hoặc <15 ms trên thiết bị**.

ADB inventory không có thiết bị. connectedDebugAndroidTest / Airplane Mode / cold
launch / touch/keyboard trên máy thật: **NOT RUN**. Benchmark thiết bị tham chiếu
hoặc thiết bị thay thế được chấp thuận: **BLOCKED**. Giữ **AC-05/NFR-VM-03 HOLD**.
DATA-05/performance owner cần inventory model/chipset/RAM/API/build, fixture hash,
exact SHA, điều kiện warm/cold và toàn bộ raw samples; dùng lệnh DATA-05 handoff
với data05EnforceLatency=true, báo riêng ngưỡng bắt buộc max <50 ms và mục tiêu <15 ms.

## Hướng dẫn thử

1. Cài app-debug.apk, Home → Vocabulary. Nhập `bank`, chờ debounce 600 ms:
   một row bank/ngân hàng. Thử `NGÂN HÀNG` hoặc NFD: cùng stableId.
2. Nhập `book`: hai nghĩa riêng. Gõ bank → book nhanh; chỉ query hiện tại có thể
   hiển thị. Nhấn X trong SearchView hoặc **Xóa tìm kiếm** khi đang loading:
   NoInput ngay, kết quả cũ không được hồi sinh.
3. Nhập từ không tồn tại để thấy NoMatch; nhập >256 code points để thấy lỗi input
   có thể sửa/xóa. Khi read failure thật xuất hiện, **Thử lại** chạy raw query
   hiện hành qua debounce mới; lỗi được mô phỏng xác định trong unit tests.
4. Click row → preview đúng stableId, không cần packId/activation. Nhấn Back:
   query/kết quả giữ lại; xoay màn hình kiểm tra raw input còn nguyên.
5. Route từ pack tiếp tục validate membership như trước. Không dùng preview để
   xác nhận full detail/save. Airplane Mode first-launch và keyboard/small-screen
   cần chạy tiếp trên thiết bị, không coi hướng dẫn này là bằng chứng đã chạy.

## Contract bàn giao

**SEARCH-02:** giữ state/generation và SavedStateHandle key `search.input`;
NoMatch chỉ success-empty của query có token. INVALID_QUERY không retry cùng
input vô hiệu; reset/sửa input. Error khác retry qua current raw query. Không
giữ preview có thể click từ generation cũ. Tăng coverage UI lỗi/retry, process
death, IME/accessibility và broad results trên thiết bị; không thay parser hay
gán 600 ms vào SRS.

**VOCAB-02:** `VocabularyDetailFragment.searchArguments(stableId)` gửi
`vocabularyId` TEXT 16 hex, **không packId**. Route pack dùng
`arguments(packId, stableId)`, tiếp tục membership validation. Null/absent packId
là catalog scope; blank packId bị reject. Không lấy pack active đầu tiên hoặc
tự activate/save. Thay preview bằng full detail qua domain lookup trên worker,
giữ not-found/read-error/Back và lifecycle guard. Nhánh VOCAB-02 tồn tại nhưng
không merge/cherry-pick feature chưa được yêu cầu; thay đổi receiver này cần
review phối hợp khi tích hợp.

**DATA-05/performance:** giữ engine/query contract; debug tracing bổ sung ở
composition/provider, không đổi benchmark assertions/workloads. Cần reference
device evidence để đóng gate. GitHub CI và reviewer Thành: **PENDING**, local
checks không thay review hoặc chứng nhận CI. Theo chỉ thị trực tiếp hiện hành,
commit/push feature được phép; không tạo PR/merge.

Bản nháp PR: [search-01-pr-draft.md](search-01-pr-draft.md).
