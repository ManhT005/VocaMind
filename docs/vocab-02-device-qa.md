# VOCAB-02 — focused emulator QA sau review

> **Recheck 07/11/2026 — source binding chưa được xác minh:** Hash `VocabularyDeviceQa.java` trong metadata lịch sử không khớp bản source được commit, không giải thích được bằng LF/CRLF/BOM hay biến đổi EOF thường gặp. Các kết quả PASS bên dưới là evidence của lượt chạy lịch sử, chưa chứng minh đúng bản probe đang lưu. Xem [probe-binding-review.json](validation/vocab-02/device-9f2dfd9-20261107/probe-binding-review.json) và [kết quả tích hợp/recheck](vocab-02-recheck-993e70a.md). Metadata/log cũ được giữ nguyên; chưa chạy lại emulator trong recheck này. Chưa dùng bản ghi này để chốt Done.

Ngày chạy: **07/11/2026, Asia/Saigon**. Phụ trách: Lê Mạnh Trường; reviewer: Vương Quang Thành.
Theo vocab-02-focused-fix-plan.md do người dùng cung cấp. Không sửa SRS/phân công, không tự Done/merge.

## Phiên bản, model và cách ly dữ liệu

- Head được kiểm tra: **9f2dfd9a50b3af4b23ec7943d315f6a709442311**; code tích hợp **a99e742**.
- Dùng model **Small_Phone hiện có**, emulator-5554, Android **11/API30**, sdk_gphone_x86,
  720×1280 px, density320. Không tạo model khác và không wipe AVD.
- Test install riêng **com.vocamind.app.vocab02qa**, debug suffix qua external init script;
  namespace/classes/assets và source nghiệp vụ giữ nguyên. Không xóa/sửa dữ liệu com.vocamind.app.
- APK app QA SHA256: **A898CAED532A40B8640A9CD07B76197C7D1D4F3C3F58AEFF98C74E62B3BFCB83**.
  Đây là APK suffix-QA được build đúng source trên, không nhầm với hash APK package gốc ở lượt host.
- External instrumentation probe và init script được giữ trong evidence; không thêm sourceSet/test hook
  vào app/src hoặc build.gradle. `git diff --exit-code 9f2dfd9 -- app` PASS sau QA.
- Fixture catalog là asset debug đóng gói của head này. Từ mới **90562a98e8686267**, từ có progress
  **d17314a48504e2ac**, pack **pack_toeic_basic**. Một row học/history synthetic được seed chỉ trong
  userdata QA: box3, dueAt1800000000000, learnedAt1700000000000, lastReviewedAt1700001000000,
  reviewCount9, consecutiveMistakes3, leech=true, isSaved=false; một ReviewEvent fixture đã biết.

[Metadata/hashes](validation/vocab-02/device-9f2dfd9-20261107/device-evidence.json),
[probe](validation/vocab-02/device-9f2dfd9-20261107/VocabularyDeviceQa.java),
[init script](validation/vocab-02/device-9f2dfd9-20261107/qa.init.gradle).

## Kết quả bốn nhóm bắt buộc

| Case | Điều kiện/thao tác thực tế | Kết quả / evidence |
| --- | --- | --- |
| **V02-A** | Airplane Mode trước first launch QA; Wi-Fi/data disabled. Home → Gói → word row → detail bằng UI; so lemma/POS/meaning/exampleEN/VI/IPA và stableId argument, Back về list. Cuộn tới ví dụ Việt trên Small_Phone. | **PASS emulator API30**. phase-a.log và phase-h.log; V02-A-detail-offline.png, V02-A-vietnamese-example-scrolled.png. Asset test có null IPA: đúng “Chưa có phiên âm”. |
| **V02-B** | Save từ chưa học, chờ Bỏ lưu, Back/reopen; adb force-stop → launcher relaunch; reboot offline → kiểm tra true. Unsave, force-stop/relaunch và reboot offline thứ hai → false. | **PASS emulator API30**. phase-a/b/c/d/e.log; screenshot saved/unsaved-force-stop, saved/unsaved-reboot. DB giữ đúng row box0/dueSave/timestamps null/counters0, không tạo review ngoài fixture hoặc UserPack. |
| **V02-C** | UI Save/Unsave từ đã có progress synthetic; đối chiếu toàn bộ learning fields và history trước–sau; snapshot tất cả bảng catalog gồm FTS/BLOB trước UI và sau các thao tác. | **PASS emulator API30**. phase-e.log; V02-C-learning-history.txt và V02-C-catalog.txt. Chỉ isSaved đổi rồi về false; learning fields/history/catalog bằng baseline. |
| **V02-D** | Chạm Save ba lần cùng lượt, assert nút disabled ngay; request rotation khi pending và confirmed. Request rotation lúc loading indicator còn visible, đợi detail/flag đúng sau recreation. Trên device: injected ViewModel/real layout giữ pending bằng latch, giả lập ambiguous write failure, read failure, Retry. | **PASS emulator API30**. phase-a/f/h.log, V02-D-hooks.txt, V02-D-loading-confirmed.txt. Retry đọc true đã persisted trong fake, writeCalls=1, không replay/đảo flag. Stable landscape screenshots được chụp sau animation. |

**7 functional instrumentation phases PASS (a,b,c,d,e,f,h)**. Phase g chỉ export evidence, không tính
thành một acceptance case. Không ghi 408 host tests thành device tests.

IPA-present được kiểm tra **bằng synthetic render fixture trên emulator**, dùng cùng renderer với
IPA `/test-fixture/`, không ghi vào catalog, không coi đó là IPA/QA nội dung thật. Null IPA và toàn bộ
field/route của học liệu asset được kiểm tra qua luồng app thật. Failure/retry ở phase f là injected
worker/model + renderer trên device; không giả vờ đã tái tạo disk failure. Rollback/race/storage thật
đã có [host integration evidence](vocab-02-data0607-integration.md), không tạo uncontrolled disk failure.

## Persistence thật và bằng chứng reboot

Boot IDs của cùng emulator trong lượt retest cuối:

1. Trước reboot: `a06086ae-dded-4813-98ed-15120be81df1`.
2. Reboot sau saved: `5b1b6b02-c37d-47c7-9beb-b5843e8a0af8`.
3. Reboot sau unsaved: `0235ae56-92cd-4635-8532-58657a12fe79`.

Đây là `adb reboot`, không phải Activity.recreate/Room reopen. Mỗi phase sau reboot assert
airplane_mode_on=1 trước mở UI. Force-stop dùng đúng package QA, rồi am start launcher activity
và test navigation thực để kiểm tra flag persisted. Không yêu cầu giữ mutation nếu kill trước commit.

## Ảnh và log tiêu biểu

- [Detail offline](validation/vocab-02/device-9f2dfd9-20261107/V02-A-detail-offline.png)
- [Ví dụ Việt cuộn được](validation/vocab-02/device-9f2dfd9-20261107/V02-A-vietnamese-example-scrolled.png)
- [Saved sau reboot](validation/vocab-02/device-9f2dfd9-20261107/V02-B-saved-reboot.png)
- [Unsaved sau reboot](validation/vocab-02/device-9f2dfd9-20261107/V02-B-unsaved-reboot.png)
- [Loading rotation ổn định](validation/vocab-02/device-9f2dfd9-20261107/V02-D-loading-rotation-stable.png)
- [Write error/Retry test hook](validation/vocab-02/device-9f2dfd9-20261107/V02-D-write-error.png)

Mọi PNG ở thư mục này là screenshot emulator thực qua UiAutomation, không phải ảnh Robolectric.
Chúng vẫn là emulator/test install, không phải thiết bị vật lý. Đã xem ảnh detail, saved-reboot,
error, landscape ổn định và ví dụ Việt cuộn; không phát hiện clipping làm mất nội dung cuộn được.

Lượt probe ban đầu fail phép so catalog vì baseline multiline qua SharedPreferences XML có thêm
whitespace cuối. Sửa **probe**, lưu/đọc baseline nguyên byte UTF-8 trong file và giữ nguyên assertion
so toàn catalog; reset riêng package QA để chạy lại toàn bộ a..f. Log fail giữ riêng
phase-e-probe-serialization-failed.log. Không bỏ assertion, không sửa production để tăng PASS.
Xuất file Android API30 phải qua instrumentation copy vào internal files rồi run-as tar vì shell không
được đọc trực tiếp external app directory. Không dùng quyền root hoặc đọc dữ liệu app người dùng.

## Build, host và CI

Build QA bằng JDK17/Gradle8.9, `--offline` với init script; assembleDebug/assembleDebugAndroidTest PASS.
Probe core a..f và extension h/export có hash test APK riêng trong metadata; thay đổi chỉ ở external
probe, không ở code app. Export không làm biến đổi PNG và baseline file.

Production source không đổi so với head đã review. Giữ kết quả host **408 tests/39 suites PASS**,
build hai APK và lint **0 errors/11 warnings** tại a99e742; không tuyên bố đã chạy lại chúng chỉ vì
thêm docs/probe evidence. CI head 9f2dfd9 được đọc trực tiếp từ GitHub: hai checks Build & Test
**SUCCESS**, có URLs trong ci-reviewed-head.json. CI của commit docs/evidence mới theo dõi riêng.

Không còn lỗi production được tái hiện trong phạm vi follow-up; không cần commit sửa app chỉ để có fix.
Contract due-now, planner/stats/schema và review CONFLICT policy giữ nguyên. API26/35 emulator,
thiết bị vật lý và TalkBack toàn diện **NOT RUN** trong lượt này; không mở rộng thành release gate toàn app.
Chờ Thành nghiệm thu PR #32; không tự đánh dấu Done hoặc merge.
