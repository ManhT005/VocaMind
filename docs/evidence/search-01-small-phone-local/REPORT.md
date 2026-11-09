# SEARCH-01 — QA Small Phone ngày 07/11/2026

Đã chạy QA chức năng và benchmark tham khảo trên **AVD Small_Phone hiện có**.
Không phát hiện lỗi chức năng trong phạm vi đã chạy. **AC-05/NFR-VM-03 vẫn HOLD**:
emulator không phải Helio G85/3 GB/Android 10 và không chứng minh ngưỡng thiết bị SRS.

Head kiểm tra: **1aad5653c801224316e1bdaec096b2a9b7582607**, nhánh
`feature/search-01-search-ui`. Code SEARCH-01 vẫn là
`67c06d4aca3b237d7eb8565dfbb410b5acafd674`; các commit tiếp theo là tài liệu.
Đã đọc lại handoff hiện hành, file yêu cầu SEARCH-01 và SRS DOCX v1.1:
FR-VM-011..014, UC-02, AC-05, NFR-VM-03. Không thay SRS hoặc phân công.

## Môi trường và bảo toàn dữ liệu

| Thuộc tính | Quan sát thực tế |
|---|---|
| Android Studio | `C:\Program Files\Android\Android Studio\bin\studio64.exe` đang chạy |
| SDK/ADB | `C:\Users\LMT\AppData\Local\Android\Sdk`, khớp local.properties và tiến trình adb.exe của phiên Studio |
| AVD | Small Phone / `Small_Phone`, có sẵn trước phiên kiểm thử; không tạo AVD mới |
| Emulator | 37.2.12.0; serial emulator-5554; WHPX; SwiftShader |
| Android | 11 / API30, x86; sdk_gphone_x86; hardware ranchu |
| Build | google/sdk_gphone_x86/generic_x86_arm:11/RSR1.201013.001/6903271:user/release-keys |
| RAM | config.ini 1024 MB, **emulator tự nâng lên 2048 MB** theo startup log; guest MemTotal 2.028.236 kB |
| Màn hình | 720 × 1280 px, density 320, khoảng 360 × 640 dp; đã xoay landscape/portrait |
| Host/build | Windows, JDK Temurin17, Gradle8.9, SDK35; debug APK |

AVD ban đầu chưa chạy. Khởi động AVD đó ở nền bằng `-avd Small_Phone -no-window
-no-snapshot-save -no-boot-anim`; snapshot cũ không tương thích renderer nên
emulator cold boot, **không wipe dữ liệu**. App gốc vẫn có sẵn sau boot.

Không cài đè `com.vocamind.app`. `qa.init.gradle` local chỉ đổi applicationId,
testApplicationId, build output và thêm harness instrumentation **ở thư mục
evidence này**. Không sửa app/src, Gradle source hoặc test source đã track.

- Package QA: `com.vocamind.app.search01qa`.
- Package mới cho first launch offline: `com.vocamind.app.search01qa.offline`.
- Build output: `build/search01-small-phone-apk` và `build/search01-small-phone-offline-apk`.
- Snapshot/hash **6 file database/WAL/SHM app gốc trước/sau giống hoàn toàn**;
  APK path app gốc không đổi. Xem original-app-before.json và original-app-after-final.json.
- Font scale, autorotate, user rotation, IME và Wi-Fi đã trả về thiết lập ban đầu
  (settings-after.json). Emulator và package QA được giữ lại để kiểm tra tiếp.

## Kết quả đã thực sự chạy

Các lượt cuối: **21 instrumentation invocations, 17 phương thức test khác nhau**.
Không cộng các lần chạy lại thành test case độc lập. Ngoài ra có ADB UI smoke
với tap/text/rotation thật và kiểm tra ảnh/hierarchy; không gọi các lượt này là
thao tác người dùng thực hiện bằng tay.

| Lượt | Kết quả | Evidence |
|---|---|---|
| Instrumentation có sẵn: SearchScreen 2, FtsSearch 9, benchmark 1 | **12 PASS** | existing-instrumentation.txt |
| Harness local: real catalog/state/typing/navigation, injected race/error/list | **5 PASS** | local-ui-final.txt |
| Chạy lại 3 ca ở font scale 1.5 | **3 PASS** | large-font-150.txt |
| First launch package mới, offline → search → detail → Back/recreate/IME | **1 PASS** | offline-first-launch.txt |
| ADB tap/type → detail đúng ID → Back → landscape → portrait → clear | **PASS** | manual-actions.json, manual-01..08 XML/PNG |

`am instrument` có thể trả exit code ADB 0 kể cả test thất bại; kết luận trên
được kiểm tra bằng dòng `OK (N tests)` và instrumentation status, không chỉ exit code.
Host 406 tests/CI/lint trước đây **không chạy lại** trong lượt QA emulator này.
Hai cặp APK QA được build thành công; SHA APK nằm trong installed-artifacts*.json.

| Tình huống / SRS | Kết quả và giới hạn |
|---|---|
| EN `bank`, uppercase `BANK` | PASS, catalog thật → ID 5aecfbf5b9a23aee |
| VI `ngân hàng`, uppercase, NFC/NFD | PASS bằng SearchView.setQuery trên UI thread, giữ nguyên raw text, cùng ID/nghĩa; không giả định ADB input text hỗ trợ Unicode |
| FR-VM-011, 014; FTS4 rowid/stableId | PASS instrumentation DATA-05 9 ca: EN/VI, senses, batch/membership, rowid đổi nhưng stableId giữ nguyên |
| Typing nhanh b → ba → bank → book | PASS, 5 vòng; Loading không giữ kết quả query trước, cuối cùng book với 2 nghĩa |
| A → B → A trên catalog thật | PASS, bank → book → bank với debounce/query thật |
| Completion A cũ/lỗi B trả sau A mới | PASS **UI với futures không cancel được được inject trong harness**, không gán thành SQLite thực sự trả ngược thứ tự |
| Clear khi debounce | PASS trên catalog thật, đợi 900 ms vẫn NoInput; ảnh manual-08-clear.png |
| Clear khi request đang in-flight | PASS với request được giữ pending bằng harness; callback cũ không hồi sinh Content |
| NoInput / Loading / Content / NoMatch | PASS trên catalog thật; Loading ảnh manual-09-after-input.png, Content manual-10-settled.png, NoMatch 03-no-match.png |
| Invalid query >256 code points | PASS Error có text; không biến thành NoMatch; reset được |
| Read error và Retry | PASS **READ_FAILED mô phỏng ở boundary VM**, nút Thử lại thực sự chạy request mới và phục hồi; 09/10-injected*.png. Không phá DB để tạo lỗi thật |
| Chọn result → receiver | PASS instrumentation và tap ADB trên row thật; preview đúng stableId, packId vắng; 07-detail-preview.png và manual-04-detail.png |
| Back/query restoration/recreation | PASS trên emulator, raw query và kết quả giữ lại |
| Rotation vật lý qua settings | PASS landscape → portrait, query bank/meaning còn đúng; manual-06/07 XML/PNG |
| Bàn phím mềm | PASS, WindowInsets xác nhận IME hiện; ảnh 06-keyboard-bank.png và ADB manual-03-keyboard-bank.png cho thấy row/reset còn truy cập được |
| Offline | PASS first launch package mới, airplane_mode_on=1, wifi_on=0, **Active default network: none** trước cài/launch; mở preview/Back vẫn hoạt động |
| Màn hình nhỏ/font 150% | PASS kiểm tra ảnh: search/reset/retry/preview đọc được, keyboard không che row bank; large-font-150/06,07,09,10*.png |
| Nghĩa dài và cuộn | PASS **snapshot 80 hàng synthetic chỉ trong ViewModel**, không sửa catalog; RecyclerView có vùng cuộn, đi tới holder79; ảnh 11/12-long-list*.png ở font mặc định và 150% |
| Không ghi dữ liệu / không activate | PASS logical snapshot catalog/userdata trước/sau trong SearchScreen instrumentation; app gốc cũng được kiểm chứng bằng hash trước/sau |

**VOCAB-02:** receiver vẫn là preview lemma/nghĩa/ID và Back, chưa full detail/Save.
Không merge/cherry-pick nhánh VOCAB-02 và không triển khai thêm feature đó.
Contract read-failure khi đóng DB có được chạy riêng trong FtsSearch instrumentation;
chưa nối lỗi repository thật đó với luồng UI Error/Retry trong cùng một ca.

## Offline và các lượt hiệu chỉnh harness

Airplane Mode ban đầu đã bật **nhưng Wi-Fi vẫn bật**. Không dùng riêng biểu tượng
máy bay làm bằng chứng offline. Sau `svc wifi disable`, dump connectivity ngay
lập tức vẫn có mạng chuyển tiếp; dump `connectivity-offline-before-fresh-install.txt`
sau khi ổn định ghi **Active default network: none**. Sau đó mới cài package
offline chưa tồn tại và chạy ca first launch. Package này không được clear/uninstall.

Lượt local-ui-instrumentation.txt đầu có 5 PASS nhưng chụp ảnh chưa chờ frame,
một số ảnh có thể thuộc màn trước. Đã thêm chờ frame **trong harness local** và
assertion IME rồi chạy lại local-ui-final.txt; ảnh trong báo cáo lấy từ lượt cuối.
manual probe lần đầu có assertion ô rỗng thất bại vì UIAutomator xuất hint trong
thuộc tính text; hierarchy thực tế là NoInput. Đã sửa assertion harness nhận hint,
giữ manual-actions-first-attempt.json và chạy lại thành công. Đây không phải
thay đổi source để che lỗi sản phẩm. Lệnh build offline lần đầu cũng sai quoting
PowerShell; build-offline-package-final.txt là lượt sửa lệnh thành công.

## Benchmark DATA-05 trên emulator

Dùng **FtsSearchBenchmarkInstrumentedTest hiện có**, không viết engine/benchmark
thay thế. Fixture test-only 5.000 từ, SHA-256:
`5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`.
20 warmups và 100 raw samples cho **mỗi** workload, tổng **500 samples**;
không bỏ outlier, không giới hạn/truncate matches. App APK không chứa benchmark fixture.

Scope: **DAO MATCH + entity materialization**, loại trừ asset opening,
expression preparation, executor queue, domain mapping, debounce và UI.
`latencyAssertionsEnabled=false` là lượt telemetry emulator, không công bố
benchmark nghiệm thu PASS. `referenceHardwareVerified=false` được giữ nguyên.

| Workload | Matches | Mean ms | p95 ms | Max ms |
|---|---:|---:|---:|---:|
| benchword04999 | 1 | 0.7639 | 1.4963 | 3.6076 |
| nghĩa kiểm thử 04999 | 1 | 0.6460 | 1.3534 | 5.3362 |
| benchword000 | 100 | 1.2793 | 1.8302 | 4.5050 |
| notpresentbenchmark | 0 | 0.3953 | 0.9459 | 1.1680 |
| benchword | 5000 | 39.8897 | 42.8040 | 44.3659 |

Tất cả samples trong **lượt emulator này** dưới 50 ms; broad-query mean vượt
15 ms. Cả hai quan sát đều **không thay thế** đánh giá trên Helio G85/3 GB/Android10.
Raw JSON gốc: data05-fts-benchmark-api30.json. Source SHA, APK hashes, fixture hashes
và inventory đầy đủ: verification.json, installed-artifacts*.json, environment.json.

Tách phase của UI dùng trace PID QA **7120**, không lẫn log app gốc:

| Phase, ms | Request generation5 | Request generation11 |
|---|---:|---:|
| Debounce thực | 602.2770 | 600.2014 |
| Queue worker | 0.3215 | 0.2360 |
| Repository query + entity/domain hydration, catalog 4 từ | 19.1285 | 2.5867 |
| Submit → main delivery | 20.1500 | 3.3107 |
| Content bind → post | 41.2643 | 33.7695 |

Submit→main bao gồm queue/query nên **không cộng lặp** các số trên. Bind→post
không phải GPU frame presented. Repository trace có domain mapping nên không
đồng nhất với scope DAO benchmark. Dữ liệu UI chỉ 4 từ, không dùng để nhận đạt
ngưỡng 5.000 từ. Xem manual-phase-logcat.txt và phase-summary.json.

## Phần còn lại và bước tiếp theo

- **FAIL sản phẩm: chưa phát hiện** trong các ca ở trên; không suy ra đã kiểm thử
  mọi thiết bị, mọi race hoặc đầy đủ UC-02/AC-04.
- **NOT RUN:** UI Error/Retry phát sinh từ read failure thật của repository; 5.000 hàng
  qua toàn bộ UI thật; font/thiết bị ngoài cấu hình đã nêu; handset vật lý;
  full VOCAB-02 detail/Save; host/full lint/CI chạy lại trong phiên này.
- **HOLD:** benchmark đúng Helio G85/3 GB/Android10 hoặc cấu hình thay thế được
  chấp thuận. Cần chạy lại harness đúng SHA/fixture, giữ raw samples/inventory,
  đánh giá ngưỡng bắt buộc max <50 ms và mục tiêu <15 ms riêng biệt.
- Không có đề xuất sửa chức năng từ các ca đã chạy. Tiếp tục nghiệm thu phần
  performance trên cấu hình SRS và tích hợp receiver khi VOCAB-02 được review/merge
  theo quy trình riêng; không tự làm trong lượt QA này.

Khi kết thúc phiên QA: HEAD không đổi, tracked source/index không đổi; chỉ thêm
thư mục evidence local này. Trong phiên QA không commit, push, tạo PR hoặc merge.
Sau đó người dùng yêu cầu commit/push evidence; việc xuất bản tài liệu không
thay SHA code đã kiểm thử hoặc đóng gate hiệu năng, không bao gồm tạo PR/merge.
