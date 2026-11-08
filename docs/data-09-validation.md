# DATA-09 — DB & FTS performance

## Phạm vi và căn cứ

SRS Baseline v1.1: NFR-VM-02/03/05, AC-01/05. Bản phân công: Nhân thực hiện,
Thi review, D12–D13, 6 giờ, phụ thuộc DATA-05 và DATA-06. Base nhánh:
`develop@0afcdd6609cd505ed488f0be454f35f2906b6ac2`.

Không thay query, scheduler hay transaction sản phẩm. DATA-09 bổ sung phép đo,
đối chiếu artifact và evidence; không sửa APK/catalog production hoặc cấp quyền
phát hành. Dataset FTS synthetic vẫn chỉ thuộc test APK.

| Gate | Ngưỡng và phạm vi |
| --- | --- |
| Startup | Launch → first app frame màn Home <1.500ms; ≥10 cold starts |
| FTS | Mỗi query <50ms bắt buộc; mean <15ms là mục tiêu riêng |
| FTS reference | Helio G85 / RAM 3GB / Android 10, 5.000 mục từ |
| APK | ≤25.000.000 byte, diễn giải MB theo hệ thập phân bảo thủ; báo cả MiB |

Quy ước startup: mọi mẫu hợp lệ đều phải <1.500ms. Không thay bằng mean/p95 để
bỏ qua outlier. Lần đầu với dữ liệu mới được ghi riêng, rồi đo 10 cold reopens.

## Cấu trúc triển khai

- `tools/validation/data09_performance.py`: inventory, startup, FTS, summary.
- `tools/validation/data09_apk_size.py`: reuse kiểm tra asset/checksum của
  `tools/catalog/package_qa.py`, đọc SQLite để kiểm số từ/pack và phân tích ZIP.
- `tools/validation/test_data09_performance.py`: kiểm tra boundary, malformed
  evidence, launch không cold, thiếu mẫu, qualification, artifact mismatch và flow collector.
- DATA-05 `FtsSearchBenchmarkContract`: giữ nguyên 20 warmup, 100 samples và cả
  năm workload. Thêm hash dataset, fingerprint và protocol metadata ngoài timer.
- Device wrapper export toàn bộ JSON bằng instrumentation status khi có
  `data09Report=true`, trước khi assert latency. Không phụ thuộc dòng logcat dài
  có thể bị cắt; raw evidence vẫn được giữ nếu ngưỡng 50ms làm test fail.
- `data09` build type opt-in: init từ release, `debuggable=false`, ký debug chỉ để
  cài QA, package `com.vocamind.app.data09validation`. Không dùng app người dùng.
- `data09Qa=true`: overlay corpus QA nội bộ qua pipeline VOC-DATA-06 hiện có.
  Generated assets khai báo producer/output và dependency cho merge/lint tasks.

Không thêm module benchmark hay dependency runtime. Python collector dùng thư
viện chuẩn và các tool QA đã có trong repo.

## Build và xác minh artifact

Yêu cầu JDK 17, Android SDK 35, Python theo môi trường CI của dự án. Thêm ADB và
apkanalyzer của Android SDK vào PATH hoặc truyền đường dẫn tuyệt đối.

```sh
python -m unittest discover -s tools/validation -p 'test_data09*.py' -v
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./gradlew -Pdata09Profile=true -Pdata09Qa=true assembleData09 assembleData09AndroidTest lintData09
python tools/validation/data09_apk_size.py \
  --apk app/build/outputs/apk/data09/app-data09.apk \
  --variant data09 --asset-profile internal-qa \
  --output build/data09-apk-size.json
```

Lệnh không có `data09Qa=true` tạo bản diagnostic dùng fixture main hiện tại
(4 từ). Phải dùng `--asset-profile fixture` cho bản đó. Không trộn hai profile.
`catalogQa` và `data09Profile` không được bật cùng lúc.

APK analyzer báo byte, MB, MiB; compressed/uncompressed size từng DB asset;
tỷ trọng nén trong APK; ABI native; APK/catalog SHA-256; số từ và số pack.
Nó reject asset set/checksum sai, ZIP entry trùng và benchmark fixture trong app APK.

`observedGate` chỉ so sánh số byte thực đo. `srsGate` dung lượng vẫn PENDING cho
fixture/internal-QA: phải đo lại đúng APK phát hành cuối cùng và ghi quyết định
review artifact. DATA-09 không tự biến quyền dùng corpus nội bộ thành quyền phát hành.

## Đo trên thiết bị

### Chuẩn bị và qualification

1. Thiết bị được chọn bằng serial tường minh. Không dùng ADB default-device.
2. Người chạy bật airplane mode và tắt Wi-Fi; collector còn đối chiếu active
   default network. Tool không tự thay đổi cấu hình thiết bị.
3. Ghi model/chipset/RAM/Android/build fingerprint, pin/nhiệt độ, điều kiện đo.
4. Reviewer xác nhận thiết bị/configuration và checksum artifact bằng sidecar
   qualification. File phải trùng fingerprint, APK hash và metric đang chạy.

Mẫu schema (điền số liệu và reference thật trước khi dùng):

```json
{
  "kind": "reference",
  "fingerprint": "REPLACE_WITH_DEVICE_FINGERPRINT",
  "apkSha256": "REPLACE_WITH_MEASURED_APK_SHA256",
  "reviewer": "REPLACE_WITH_REVIEWER",
  "evidenceRef": "REPLACE_WITH_VERIFIED_MODEL_CHIPSET_RAM_ANDROID_INVENTORY",
  "metrics": ["startup", "fts"]
}
```

`kind` có thể là `approved-substitute` khi đã có xác nhận cấu hình thay thế.
Sidecar là khai báo review được lưu nguyên trạng, không phải kết luận tự động
chipset từ `Build.HARDWARE`. Không tự tạo qualification PASS cho host/emulator.
Thiếu hoặc không khớp qualification: vẫn có raw measurements nhưng SRS gate PENDING.

### Startup

```sh
python tools/validation/data09_performance.py startup \
  --serial DEVICE_SERIAL \
  --apk app/build/outputs/apk/data09/app-data09.apk \
  --asset-profile internal-qa --runs 10 --fresh-install \
  --qualification /path/to/reviewed-device.json \
  --output-dir build/data09/device-run/startup
```

`--fresh-install` yêu cầu package QA chưa được cài; tool không tự gỡ ứng dụng hay
xóa installation đã tồn tại. Nó kiểm package từ manifest trước khi cài, rồi
`pm clear` đúng package QA mới cài để loại dữ liệu giữ lại từ lần gỡ trước.
Đo 1 first-data launch + 10 cold reopens, ghi riêng từng loại. Bỏ flag thì chỉ đo
cold reopens của installation hiện có, không đủ chứng minh first-data launch.

Mỗi lần: force-stop, kiểm PID biến mất (pidof exit 1 là bình thường), marker riêng
trong system log, `am start -W`, xác nhận `Status: ok` và `LaunchState: COLD` nếu
Android có trường này. Phải có `Displayed MainActivity` sau marker để xác nhận
frame render, không lấy host command duration/onCreate/onViewCreated làm startup.

HomeFragment hiện là màn tĩnh. Phép đo này không khẳng định catalog/userdata đã
mở xong; không trộn thời gian DB open hoặc typing/render search vào startup/FTS.
Mẫu timeout/lỗi vẫn nằm đúng vị trí, không được thay bằng lượt retry đẹp hơn.

### FTS

Sau startup, cài test APK cùng build:

```sh
adb -s DEVICE_SERIAL install -t app/build/outputs/apk/androidTest/data09/app-data09-androidTest.apk
python tools/validation/data09_performance.py fts \
  --serial DEVICE_SERIAL \
  --apk app/build/outputs/apk/data09/app-data09.apk \
  --test-apk app/build/outputs/apk/androidTest/data09/app-data09-androidTest.apk \
  --asset-profile internal-qa \
  --qualification /path/to/reviewed-device.json \
  --output-dir build/data09/device-run/fts
```

Collector kiểm SHA-256 APK app/test đã cài, bật `data05EnforceLatency=true` và
`data09Report=true`, giữ instrumentation output và raw JSON. Nó kiểm dataset
hash, 5.000 từ, 20 warmup, 100 mẫu mỗi workload, hit counts 1/1/100/0/5000 và
fingerprint. Query latency là DAO MATCH + materialization, không gồm mở asset,
chuẩn bị query, executor queue, mapping domain hoặc UI.

Mỗi workload có min/mean/p50/p95/max; max ≥50ms FAIL, mean ≥15ms chỉ không đạt
target. Report DATA-05 giữ `referenceHardwareVerified=false`; qualification
bên ngoài mới đối chiếu việc reviewer đã xác nhận thiết bị.

### Tổng hợp

```sh
python tools/validation/data09_performance.py summary \
  --startup build/data09/device-run/startup/report.json \
  --fts build/data09/device-run/fts/report.json \
  --apk-size build/data09-apk-size.json \
  --output build/data09/device-run/summary.json --require-pass
```

Summary ghi hash các report, từ chối trộn APK khác nhau. Thiếu evidence là
PENDING; `--require-pass` exit khác 0 nếu còn FAIL/PENDING. Collector exit 0
chỉ có nghĩa hoàn tất phép đo, không tự chứng nhận SRS khi thiếu qualification.
Output paths/run directories đã tồn tại không được ghi đè; chọn run-id mới.

## Kết quả thực tế của phiên triển khai

Evidence tại [evidence/data-09/host-validation](evidence/data-09/host-validation).

- Toàn bộ JVM/Robolectric/Room: 501 tests, 0 failures/errors/skipped.
- Python evidence collector: 18 tests, PASS (toàn bộ tools/validation: 26 tests, PASS); dữ liệu fake trong unit test chỉ
  kiểm logic collector, không phải số đo thiết bị.
- Build/lint debug và data09; app/test APK: PASS.
- Host FTS API 26/35 giữ đầy đủ raw 100 mẫu × 5 workload × 2 API. Kết quả host
  không đóng gate Helio G85 / RAM3GB / Android10.
- APK QA nội bộ được kiểm checksum, corpus và size; kết quả cuối nằm trong
  `apk-size.json`. Không dùng artifact fixture nhỏ để claim size release.
- Startup thiết bị và FTS reference-device: PENDING. ADB trong executor bị lỗi
  tạo thư mục cấu hình tại home read-only; không có inventory thiết bị tham chiếu
  đã được xác nhận. Log tại `adb-availability.txt`.
- APK phát hành cuối: PENDING. Corpus/release approval của dự án vẫn HOLD.

CI chạy Python collector tests, build profile DATA-09, xác minh QA APK và upload
báo cáo/checksum; không upload APK chứa corpus QA nội bộ. CI xanh chứng minh runner/build/integrity, không thay thế
performance acceptance trên thiết bị.

### Số đo APK QA đã xác minh

- 6.126.378 byte = 6,126378 MB = khoảng 5,843 MiB; dưới ngưỡng quan sát 25 MB.
- Catalog: 150 từ / 4 pack, phân bổ 40/40/40/30; không có native ABI trong APK.
- `catalog.db`: 155.648 byte giải nén, 39.933 byte nén trong APK.
- `srsGate` vẫn PENDING: đây là APK nội bộ ký debug, không phải APK phát hành cuối.
- Đã kiểm chuyển fixture → QA khi xóa thư mục generated, xác minh checksum và
  corpus ngay trên artifact sau build. `initial-qa-asset-mismatch.json` giữ lần
  phát hiện sai asset trước khi sửa dependency producer của Gradle.
