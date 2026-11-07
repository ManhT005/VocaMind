# VOC-DATA-06 — Catalog QA trong APK chính

Ngày 07/11/2026. Nhánh `feature/voc-data-06-release-catalog`, [PR #35](https://github.com/ManhT005/VocaMind/pull/35). Owner Thi, reviewer Nhân. Trạng thái `READY FOR INTERNAL QA`, nghiệm thu task còn mở.

| Giai đoạn | Kết quả kiểm tra | Điều kiện còn lại |
| --- | --- | --- |
| A · Quyền và ghi công | Đối chiếu đủ 150 mục, gồm 135 tham chiếu lịch sử NGSL 1.2 và 15 mục không có tham chiếu NGSL. APK QA hiển thị ghi công và phạm vi nội bộ offline. Kiểm tra hồ sơ quyền đã tách khỏi gate phát hành. | PO/Content Lead xác nhận hồ sơ thật, phạm vi, chủ thể ghi công, xử lý NGSL và hash snapshot. |
| B · Snapshot | `editorial-qa-v2` / `1.0.1` bảo toàn 150 stable ID, nghĩa, cặp EN/VI, IPA nullable, membership và ba cặp loại trừ của Quiz. Bốn pack giữ quota 40/40/40/30 và có mô tả đề xuất. Các file `accepted-v1` giữ nguyên. | Duyệt bốn mô tả, chốt snapshot `accepted-v2` với quyết định có bằng chứng. |
| C · APK | Variant `catalogQa` sinh APK ứng dụng `com.vocamind.app.qa`. Trích APK xác nhận đủ sáu asset đồng bộ với manifest. Vocabulary Detail dùng luồng Room đã tích hợp từ VOCAB-02. APK debug mặc định giữ fixture bốn từ. | Ma trận thiết bị theo plan, tích hợp QUIZ-03 cho smoke UI Quiz, CI của head PR và nghiệm thu của Nhân. |

## Tái tạo và kiểm tra

Chạy từ gốc repo với JDK 17, Android SDK 35, Python 3.11+.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew -PcatalogQa=true :app:assembleCatalogQa :app:assembleCatalogQaAndroidTest :app:lintCatalogQa
python tools/catalog/package_qa.py --verify-apk app/build/outputs/apk/catalogQa/app-catalogQa.apk
python tools/catalog/package_qa.py --verify-apk app/build/outputs/apk/debug/app-debug.apk --fixture
```

Snapshot và bundle được tạo trong thư mục build bị ignore. Mỗi lần merge asset
QA xác minh lại snapshot và bundle. Nếu thư mục sinh đã chứa byte khác, pipeline
dừng để người chạy tạo lại trong workspace sạch. Kiểm tra APK đọc trực tiếp ZIP,
so khớp asset set và hash rồi chạy kiểm tra SQLite trên DB đã trích.

Trên emulator hoặc thiết bị QA với app ID QA cài mới, tắt Wi-Fi và mobile data
và xác nhận không có mạng mặc định trước khi chạy lớp instrumentation sau.

```sh
./gradlew -PcatalogQa=true :app:connectedCatalogQaAndroidTest \\
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.PackagedQaCatalogInstrumentedTest
```

Lớp test đọc asset từ APK ứng dụng đã cài qua Room, kiểm tra pack và chi tiết,
FTS EN/VI, ghi công, Quiz trước/sau activate, ID sai và dữ liệu sau mở lại DB.
Checklist smoke UI gồm điều hướng Packs → List → Detail, nghĩa/cặp ví dụ/IPA
placeholder, màn hình Ghi công, Quiz và trạng thái sau đóng/mở app. DATA-08 chịu
trách nhiệm nghiệm thu nâng cấp catalog của bản cài đang tồn tại. Màn Quiz trên
nhánh này vẫn dùng shell. Instrumentation kiểm tra Quiz ở tầng use case với
catalog đã cài. UI session và migration userdata thuộc QUIZ-03.

## Bằng chứng

- 418 Android/JVM và 178 Python pass. Lint debug và QA có 0 lỗi, lần lượt 11 và 10 cảnh báo.
- API 26 emulator cài hai APK đã ghi hash và chạy instrumentation offline 1/1 pass. Smoke UI qua Home, Packs, danh sách từ, Detail với nghĩa/cặp EN/VI/IPA placeholder và Ghi công nội bộ. [Log UI](evidence/voc-data-06/20261107-qa/device-ui-smoke.txt).
- Build lại cho kết quả byte-identical với APK QA và sáu asset. APK QA chứa 150 từ, 150 memberships, 150 cặp EN/VI, 150 IPA null và 150 hàng FTS. SQL xác nhận 300 truy vấn EN/VI, integrity `ok` và 0 vi phạm FK.
- [Log instrumentation](evidence/voc-data-06/20261107-qa/device-instrumentation.txt), [trạng thái mạng](evidence/voc-data-06/20261107-qa/device-offline.txt) và [hash APK cài trên emulator](evidence/voc-data-06/20261107-qa/device-installed-apks.txt).
- [Manifest snapshot](evidence/voc-data-06/20261107-qa/snapshot-manifest.json), [asset APK QA](evidence/voc-data-06/20261107-qa/apk-assets-verification.json), [asset APK fixture](evidence/voc-data-06/20261107-qa/fixture-apk-verification.json) và [kết quả kiểm tra](evidence/voc-data-06/20261107-qa/verification.json).
- GitHub Actions kiểm tra cả hai profile. Artifact công khai giới hạn ở APK fixture, báo cáo test và tóm tắt hash. APK QA, APK instrumentation và catalog đã sinh dùng trong phạm vi QA nội bộ.

## Gate nghiệm thu và phát hành

Chấp thuận ngày 05/11/2026 và đường hướng đóng gói ngày 07/11/2026 là căn cứ cho
QA nội bộ. [Hồ sơ quyền](licensing/voc-data-06/README.md) tách việc kiểm chứng
hồ sơ theo snapshot khỏi quyết định phát hành ứng dụng. `releaseAuthorized=false`
cho tới khi có quyết định phân phối có hiệu lực, smoke UI và thiết bị theo plan,
reviewer PASS cùng bằng chứng DATA-08/DATA-09. Giữ `accepted-v1` và schema Room
v2 làm mốc đối chiếu cho các gate đó.
