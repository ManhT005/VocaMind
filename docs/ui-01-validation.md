# UI-01 — Polish và resilience

Ngày thực hiện: 09/11/2026 (Asia/Saigon). Base: `develop` tại `c5d0ad6`.
Nhánh: `feature/ui-01-polish-resilience`. Commit triển khai là commit chứa bản validation
này; fingerprint source được lưu trước commit trong `code-review-source-manifest.json`.
Kế hoạch nguồn: [ui-01-polish-resilience.md](plans/ui-01-polish-resilience.md).

WEEK-2-GATE: **đã đạt theo xác nhận trực tiếp của Thành trong phiên làm việc**:
“week2 gate la all task tuan 2 da lam done roi nhe”. Đây là xác nhận dependency;
không thay thế kết quả kiểm thử UI-01, CI của nhánh mới hoặc approval của reviewer.

## Kết luận rà soát code/kỹ thuật

Theo phạm vi Thành xác nhận ở lượt rà soát cuối (xét code và kỹ thuật; nghiệm thu kiểm thử
để sau): **đủ điều kiện Done phần triển khai UI-01** sau khi xử lý bổ sung F07.
Không phát hiện blocker kỹ thuật còn tồn tại trong các thay đổi đã rà soát.

- Layout/scroll và resource khớp binding; insets không cộng lặp; không thêm state nghiệp vụ
  song song với ViewModel hoặc thao tác ghi trong render.
- Giữ occurrence/debounce/saving lock Flashcard, SavedStateHandle/contract Quiz; không đổi
  schema, catalog production, scheduler/quota hay repository/domain.
- Observer theo view lifecycle; adapter được detach; callback diff của danh sách gói kiểm
  tra đúng view và state hiện hành trước khi hiển thị.
- `code-review-final-checks.txt`: compile/assembleDebug và lint **PASS**; 14 regression tập
  trung (Pack activation/header + layout API26/35) **PASS**, bao gồm lỗi F07 tái hiện trước sửa.
  Đây là kiểm chứng kỹ thuật cho phần vừa sửa, không phải chạy lại matrix nghiệm thu.
- Review quy trình và QA/acceptance vẫn là các bước bàn giao riêng. Các ca
  còn pending/52 lỗi host đã ghi ở dưới không được đổi thành PASS để chốt triển khai.

Snapshot mới nhất: `code-review-source-manifest.json`. Các báo cáo 74 test và smoke/reboot
API28 trước F07 vẫn gắn với `source-manifest.json` cũ; không gán lại hash APK cũ cho code mới.

## Findings và thay đổi

| ID | Tình huống / bằng chứng | Expected → actual trước sửa | Fix / regression | Mức độ / yêu cầu |
|---|---|---|---|---|
| F01 | Flashcard, 568×240dp, font200%, mặt sau dài, có thông báo lưu lỗi; inflate/measure/layout với cả API26/35 | Thẻ và ratings vẫn tiếp cận được → vùng thẻ co dưới 48dp khi footer chiếm chiều cao | Toàn màn cuộn, bỏ scroll lồng, thẻ tối thiểu 240dp, nhãn ratings wrap; về đầu nội dung khi đổi occurrence. `ResilienceLayoutTest`, guard FLASH-03 và `FlashcardFragmentTest` | High, AC-06 / NFR-VM-09 |
| F02 | Chi tiết gói, title dài, màn 320×480dp/font200%; regression header trước sửa | Scroll danh sách phải giải phóng được vùng header → header cố định không tham gia nested scroll | AppBarLayout/CoordinatorLayout cho header cuộn, giữ RecyclerView tái sử dụng hàng; detach adapter khi view bị hủy. `longPackHeaderDoesNotCollapseTheWordList` và tests kích hoạt gói | Medium, NFR-VM-09 |
| F03 | MainActivity insets listener chỉ lấy systemBars/cutout | Nội dung tránh bàn phím → thiếu xử lý IME khi app vẽ edge-to-edge | Hợp insets IME/systemBars/cutout (lấy max, không cộng lặp), `adjustResize`; `MainActivityInsetsTest` kiểm mở/đóng IME | Medium, NFR-VM-09; cần kiểm IME thực tế |
| F04 | Flashcard có `ipa=null` | Cùng placeholder chuẩn với detail → TextView IPA bị ẩn | Dùng `word_ipa_missing`, kiểm render không ghi review | Low, contract IPA |
| F05 | Quiz lỗi đọc/restore hoặc lưu chưa xác nhận | Thông báo cho người học → lộ mã lỗi nội bộ trong status/result | Chuỗi resources hướng dẫn thử lại, bỏ mã lỗi khỏi UI; giữ error trong state và contract retrySave. Regression lỗi đọc/retry không ghi dữ liệu | Low, NFR-VM-10 |
| F06 | Chạy build và lint cùng profile fixture `quiz03DeviceIsolation` | Lint phải chờ assets QA → Gradle báo implicit dependency ở `generateDebugLintReportModel` | Khai báo `builtBy` cho assets và dependency rõ cho các task lint trong nhánh profile kiểm thử; build/lint QA PASS | Test infrastructure; giữ prepare catalog test assets |
| F07 | Rà soát Material1.13 `canDragView`, danh sách đã nested-scroll rồi chuyển ERROR, title dài | Retry/header vẫn tiếp cận được → RecyclerView INVISIBLE làm Material từ chối kéo header; regression gesture FAIL cả API26/35 trước sửa | Mở header cho loading/empty/error; sau diff commit giữ viewport rỗng VISIBLE để vẫn nested-scroll, chỉ hiện đúng view/state mới nhất. Guard không hiện hàng cũ khi chờ diff. Gesture/header/activation regression và build/lint PASS | High, NFR-VM-09/10, UI resilience |

F01/F02 có log regression trước sửa trong `evidence/ui-01/`. Log baseline ban đầu dùng
renderer legacy; lần kiểm tra văn bản cuối dùng renderer **native** để có text shaping thật.
Không coi log host là ảnh/chứng minh thực nghiệm thiết bị. Không xác nhận clipping trên
mọi thiết bị chỉ từ một cấu hình đo layout.

## Audit P0

| Màn | Kết quả rà soát source / kiểm tra tái sử dụng |
|---|---|
| Home/navigation | Có ScrollView, nút navigation có nhãn/minHeight; MainActivity sửa insets. Không enqueue navigate trong render. |
| Packs / pack detail / words | Packs đã cuộn; header chi tiết sửa F02. Route dùng packId/stableId; không sửa activation contract. |
| Search | SavedStateHandle, generation và canOpen giữ nguyên; observer viewLifecycleOwner; hủy query listener và detach adapter đã có. Insets F03 áp dụng toàn app. |
| Vocabulary detail | Scroll học liệu, placeholder IPA, guard save pending và retry đọc lại trạng thái giữ nguyên. Tái sử dụng regression long text/save/recreate hiện có. |
| Today | ScrollView, state từ ViewModel, onResume refresh; không tạo session/ghi review trong render. |
| Flashcard | F01/F04; giữ occurrence ID, debounce 600ms, saving lock; chỉ gọi rate từ nút và flip từ tap, scroll để parent hủy click. |
| Quiz | Layout đã cuộn, feedback có ✓/✗ và text; giữ SavedStateHandle/confirm exit/save idempotency; F05 chỉ sửa trình bày. |
| Progress | Summary/history chung ConcatAdapter, guard generation/snapshot và detach adapter đã có; giữ sáu box, “Học lại”, Mastered disclaimer. |
| Attribution | Nội dung đã cuộn, link có validation và catch lỗi handler; dữ liệu nguồn local. |

Rà soát source không đồng nghĩa toàn bộ audit trực quan, TalkBack, dark mode hay matrix
thiết bị đã PASS. Không thay schema, catalog production assets, domain hoặc thuật toán.

## Toolchain và lệnh kiểm tra

Windows PowerShell, JDK17 `.tools/jdk17/jdk-17.0.20.1+1`, Gradle8.9,
SDK35/min26, Python `.tools/python/python.exe`, Robolectric4.16.1.

```powershell
$env:JAVA_HOME = "$PWD/.tools/jdk17/jdk-17.0.20.1+1"
$env:GRADLE_USER_HOME = 'C:/Users/thanh/.gradle'
.\gradlew.bat -I docs/evidence/ui-01/windows-short-temp.init.gradle `
  testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug `
  "-PcatalogPython=$PWD/.tools/python/python.exe" --console=plain
```

Workaround `java.io.tmpdir=C:/Windows/Temp` là cấu hình test local có sẵn, đã ghi nhận ở
QUIZ-03/PROGRESS-02; không sửa SQL hoặc kỳ vọng nghiệp vụ để bỏ qua lỗi môi trường.
Lần đầu không áp dụng workaround gặp SQLITE_CANTOPEN; đã dừng đúng test worker và giữ
log ở `host-initial-environment-failure.txt`. Không tính lần dừng đó là test PASS.

Kết quả cuối và hash nguồn/APK: xem evidence và phần cập nhật kết quả bên dưới.

### Lượt toàn bộ unit test

`host-checks.txt` / `full-unit-summary.json`: **594 tests, 542 pass, 52 fail, 0 skipped**.
Không ghi toàn bộ suite PASS. Nhóm thất bại:

- `CatalogUpgradeTest` (38), `QuizPolicyCompositionTest` (4): bootstrap bị
  `AccessDeniedException` tại `CatalogGenerationStore.syncDirectory` khi Java mở thư mục
  Windows bằng FileChannel để fsync.
- `AttributionScreenTest` (2), `PackVocabularyIntegrationTest` (2), `VocabularyListTest`
  (2), `SearchScreenTest` (4): dữ liệu/điều hướng không settle khi bootstrap catalog không
  hoàn tất trên host này. Đây là phân tích từ stack trace và chuỗi phụ thuộc, chưa phải
  chứng minh toàn suite PASS trên Linux.
- Các file catalog/domain liên quan không thay đổi trong UI-01. Finding ngoài scope
  chuyển owner DATA-08/CI: tái chạy cùng SHA trên Linux và xác nhận kết quả trước merge.
  Không thêm skip, sửa expectation hay thay fsync trong UI-01 để che lỗi.

Lần chạy này trước bước dọn resource Quiz (giữ key cũ, bỏ template mã lỗi); lượt kiểm tra
tập trung cuối kiểm tra lại phần UI trên source cuối. XML/summary ghi đủ cả các ca FAIL.

### Kiểm tra snapshot trước vòng rà soát code bổ sung F07

- `final-head-checks.txt` / `head-unit-summary.json`: **74 tests PASS, 0 fail/skip**.
  Bao gồm native geometry/text layout API26/35, IME API35, Flashcard occurrence/rotation/
  saving lock/IPA, Quiz feedback/recreate/error, Pack activation, Progress snapshot và detail dài.
- `assembleDebug`, `assembleDebugAndroidTest`: **PASS**.
- `lintDebug` trên head cuối: **PASS — 0 errors, 14 warnings**, nguyên văn ở `lint-final-head.txt`.
  Warnings gồm accessibilityHeading XML trên min26, plural candidate và cấu hình release;
  không đồng nghĩa TalkBack đã nghiệm thu trên thiết bị.
- Kiểm tra head cuối chạy tuần tự, `--no-daemon --max-workers=2`
  và `-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8` trên máy RAM8GB.
  Lượt chạy song song JVM/emulator trước đó hết native memory; log giữ ở
  `final-head-memory-failure.txt`, không được tính là PASS. Các lượt 74 test trước đó
  vẫn giữ ở `final-host-checks.txt` / `focused-unit-summary.json`.
- Profile cuối tái dùng `-Pquiz03DeviceIsolation=true`, package
  `com.vocamind.app.quiz03validation`, corpus QA 150 từ đã có sẵn; **build/lint PASS**
  (`qa-profile-final-checks.txt`). Không thay production catalog assets hay allowlist catalog.
- APK thường được fingerprint trước khi build QA ghi đè output (`normal-apk-sha256.json`).
  SHA APK QA nằm trong device evidence khi runner được thực hiện.

### Thiết bị / runner

Runner `tools/validation/ui01_device.py` kiểm tra package bằng `aapt`, yêu cầu QA install
mới, không `pm clear`/uninstall ứng dụng người dùng. Snapshot mạng được ghi **trước** mọi
thay đổi; preflight từ chối khi `dumpsys connectivity` không phản hồi. Mỗi lệnh restore
được thử riêng và lỗi restore được lưu thay vì bỏ qua.

Lượt LDPlayer đầu chưa cài APK: `pm path` trả exit1 cho package chưa tồn tại; đã sửa runner.
Lượt kế tiếp emulator bị treo `dumpsys connectivity`, rồi cả adb shell/restore timeout,
chưa tới bước install/smoke. Log giữ ở `device-runner.txt`. Phiên bản runner lúc đó chưa
persist snapshot trước restore; đã sửa thiếu sót này. Giá trị airplane ban đầu đọc được
là 0 (thể hiện trong traceback lệnh restore); WiFi ban đầu không có artifact lưu lại.
Các lượt reboot/stop-start LDPlayer ban đầu không phục hồi được transport; log giữ ở
`emulator-recovery.txt`, `emulator-hard-recovery.txt`, `ldplayer-final-recovery.txt`.
Sau khi dừng JVM/AVD kiểm tra và mở lại LDPlayer, **đã phục hồi**: Airplane=0, WiFi=1,
default network100 và WiFi CONNECTED (`ldplayer-recovery-final.txt`). WiFi ban đầu không
có snapshot nên chỉ xác nhận trạng thái cuối hoạt động, không khẳng định restore chính
xác giá trị WiFi trước phiên. Dump chứa thông tin mạng riêng giữ ở `.tools/` được ignore;
evidence trong repo chỉ giữ summary và hash dump gốc.

Lượt AVD thử profile `.ui01validation` (`device-api28/`) có smoke FAIL ở Today: profile
mới không được validator cho phép dùng internal QA corpus. Đã bỏ profile mới và tái dùng
profile Quiz QA hiện có. `catalog-diagnostic.txt` là contract chạy không đúng fixture
profile (contract chỉ copy các file REQUIRED), không phải bằng chứng catalog production
bị lỗi trên Android. Các lượt thất bại được giữ riêng, không gộp với kết quả cuối.

### Smoke Android API28 đã chạy

AVD riêng `UI01_API28`, serial `emulator-5560`, 720×1280px/density320 = 360×640dp,
font mặc định, portrait. Package QA mới, Airplane=1, WiFi=0 và default network=none
được xác minh trước lần mở Activity đầu tiên. Locale mặc định của AVD; chuỗi app tiếng Việt.

- `device-final/smoke.txt`: **PASS**, 85.79s. 10 lượt chấm Flashcard với ba rating;
  recreate sau flip giữ occurrence/mặt thẻ; Quiz10, recreate sau feedback/result;
  DB có đúng 10 review và 1 completed Quiz; Save một từ qua UI; Progress/Attribution đọc local.
- `device-final/reopen.txt`: **PASS**, 7.294s. Force-stop rồi process mới: 10 review,
  1 Quiz10, active packs và đúng một saved flag còn nguyên.
- Fixture QA đặt dailyNewWords=10 và activate packs trong setup để đủ 10 lượt;
  mọi ghi review/Quiz/saved flag đi qua UI/factory/repository thật. Không thay quota production.
- [Video smoke](evidence/ui-01/device-final/smoke.mp4),
  [Flashcard](evidence/ui-01/device-final/ui01-flashcard.png),
  [Quiz feedback](evidence/ui-01/device-final/ui01-quiz-feedback.png),
  [Quiz result](evidence/ui-01/device-final/ui01-quiz-result.png),
  [Progress](evidence/ui-01/device-final/ui01-progress.png),
  [Attribution](evidence/ui-01/device-final/ui01-attribution.png).
  Logcat giới hạn thời gian chạy ở `device-final/logcat.txt`; không có AndroidRuntime crash.
- `device-final/device-summary.json` ghi hash cặp APK, thời gian, cấu hình và mạng gốc
  Airplane=0/WiFi=1. Restore AVD sau smoke thành công, không có lỗi restore.
- Đây là smoke trên emulator, chưa thay thế nghiệm thu cấu hình tham chiếu NFR-VM-14,
  TalkBack, API26 thiết bị, toàn matrix dark/font lớn/IME/rotate và AC-14 release size.

### Probe sau reboot AVD

`device-final/reboot-final-probe.txt`: **PASS**, 4.182s. Cặp APK cài đặt được đối chiếu
SHA256 trước khi reboot. Boot ID đổi từ `08f1375d-d1f6-4bd2-bded-75810697e2fe` sang
`687fc73e-83ff-4073-aebc-9a70e706883a`; process mới vẫn có 10 review, một Quiz10, active
packs và đúng một saved flag. Không có ghi review/attempt mới từ reopen.

Probe dùng `cmd connectivity airplane-mode enable`, kiểm tra default network=none trước
reboot và sau boot. Ngay sau boot: Airplane=1, `wifi_on=3`, default network=none; field
`offline_before_reassert=false` trong JSON do checker đòi chính xác `wifi_on=0`, không phải
bằng chứng có default network online. Trước instrumentation đã reassert WiFi off và xác
nhận default network=none. Không có đo mạng liên tục trong toàn khoảng boot.

`reboot-final-summary.json`, `reboot-final-offline-before.txt`, `reboot-final-after-boot.txt`
và `reboot-final-runner.txt` giữ các giá trị thực tế. Restore AVD về Airplane=0/WiFi=1
thành công; AVD riêng đã dừng sạch. Lượt `reboot-summary.json` / `reboot-runner.txt` trước đó
bị assert trạng thái WiFi và mất AVD trong lúc host thiếu RAM, không tính là PASS.

Đây là bằng chứng bảo toàn dữ liệu qua reboot trên emulator API28; U01-09 cấu hình tham
chiếu/NFR-VM-14 đầy đủ vẫn chờ QA-02, không thay bằng kết quả emulator này.

### Ràng buộc source và artifact

Các snapshot source được tạo trước commit; `source-manifest.json` ghi snapshot trước F07;
snapshot code đã rà soát
ở `code-review-source-manifest.json`. Mỗi snapshot ghi base commit, nhánh, SHA256 từng file
code thay đổi và bundle hash. `evidence-manifest.json` giữ fingerprint báo cáo cũ;
`code-review-evidence-manifest.json` bổ sung fingerprint toàn bộ evidence sau rà soát.
Cặp APK QA đã chạy nằm cục bộ ở `.tools/ui01-final-apks/` (ignore), hash khớp
`device-final/device-summary.json`. APK debug thường trên source cuối được ghi riêng trong
manifest, không dùng để suy kích thước release hoặc thay hash APK đã smoke.

## Handoff và phần nghiệm thu còn lại

Thành phối hợp QA-02/UI-02 nhận các ca chưa chạy; Trường nhận review code.
Lịch đề xuất: 10/11/2026 và hoàn tất trước bàn giao MVP; Thành chốt lịch/thiết bị tham chiếu.
Chưa gửi tin nhắn hay xin approval reviewer thay người dùng.

| Matrix | Phần cần ghi kết quả thiết bị trước nghiệm thu |
|---|---|
| U01-01 | API26 thật/emulator, 320dp, toàn bộ P0, ảnh clipping/scroll. Host API26 chỉ là regression bổ trợ. |
| U01-02 | API mới trong matrix QA, landscape/system bars/cutout/IME thật. |
| U01-03 | Dark mode, font150%/200%, EN–VI dài trên mọi màn, đủ bốn lựa chọn Quiz và disclaimer. |
| U01-04/05 | Rotate thật trong lúc ghi chậm/lỗi, trước/sau flip, answer/feedback/result/save pending; đối chiếu DB. |
| U01-06/07 | Search nhanh/clear/Back/async diff, error injection đọc/ghi; tái dùng tests feature, nghiệm thu trên thiết bị. |
| U01-08 | Airplane trước lần mở đầu, packs→search/detail→Today→Flashcard→Quiz→Progress→Attribution; cần đủ luồng, không suy từ smoke một phần. |
| U01-09 | 10 thẻ + Quiz10, reboot offline trên cấu hình tham chiếu; logcat/video và snapshot DB trước/sau. Force-stop/reopen không thay thế reboot. |
| U01-10 | Đo touch targets thực tế, TalkBack thao tác trọng yếu, focus order, scroll không flip ngoài ý muốn. |

AC-14 kích thước APK **release** ≤25MB do QA/release xác nhận riêng.
Chưa xác nhận reviewer approval hoặc sẵn sàng phát hành chỉ từ code/test local.
