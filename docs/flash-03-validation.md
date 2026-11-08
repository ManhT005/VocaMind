# FLASH-03 — Chống duplicate review

## Căn cứ và phạm vi

- SRS Baseline v1.1: FR-VM-025, BR-11, NFR-VM-08 và phần duplicate review của AC-08.
- Bảng phân công: FLASH-03, Nhân thực hiện, Đạo review, phụ thuộc FLASH-02; ownership L2 với hỗ trợ L4.
- Base: `develop` tại `0afcdd6609cd505ed488f0be454f35f2906b6ac2`.
- Giữ luồng hiện có: Fragment → FlashcardViewModel → ReviewWriter → RecordReviewUseCase → LeitnerScheduler → RoomReviewRepository → transaction DATA-06.

## Hành vi triển khai

`FlashcardViewModel.rate(presentationId, rating)` chỉ nhận rating khi thẻ hiện tại
đã lật, không đang lưu và ID khớp lượt đang hiển thị. Khi nhận, ViewModel ghi
mốc monotonic và bật `saving` trước khi publish state/enqueue worker. Cả ba nút
rating được disable ngay qua state hiện có.

Các thao tác dưới 600ms kể từ lần nhận hợp lệ trước bị bỏ qua, kể cả khi DB ghi
xong rất nhanh, đã chuyển thẻ hoặc lần ghi trước thất bại. Đúng 600ms được nhận
nếu các điều kiện còn lại hợp lệ. Input bị bỏ qua không kéo dài cửa sổ debounce.
`SystemClock.elapsedRealtime` được inject từ factory qua `LongSupplier`; `Clock`
nghiệp vụ dùng tính lịch và thời điểm review không thay đổi.

Mỗi lượt hiển thị có một `presentationId` trong ViewModel. Fragment capture ID
trong listener của state đã render. Commit thành công chuyển queue và tăng ID;
retry tải lại phiên cũng không tái sử dụng ID cũ. Vì vậy lệnh cũ không chấm nhầm
thẻ kế tiếp, kể cả sau 600ms. Relearn cùng `stableId` có ID mới và vẫn chấm được.

Ghi thất bại giữ nguyên thẻ, mặt thẻ và ID; không advance, không enqueue relearn,
không tự retry. Người học có thể chọn lại sau cửa sổ debounce. Conflict vẫn được
báo như FLASH-02. Không sửa scheduler, Room schema hoặc transaction DATA-06.

Guard thuộc một ViewModel/phiên học và được giữ qua thay đổi cấu hình. Task này
không thêm cơ chế exactly-once bền vững qua process death hoặc nhiều phiên đồng thời.

## Kiểm thử tự động

| Kiểm thử | Bằng chứng |
| --- | --- |
| Commit nhanh, tap tại 0/150/300ms, thẻ sau đã lật | Một review, không ghi progress thẻ sau |
| Mốc 599/600ms | 599 bị chặn, 600 được nhận; tap bị chặn không gia hạn cửa sổ |
| Commit kéo dài hơn 600ms | `saving` tiếp tục chặn; rating đầu tiên được giữ |
| Lệnh cũ sau commit hoặc reload | Không enqueue worker, không chấm thẻ mới |
| Rating trước flip/null/ID sai | Không ghi và không khởi động debounce |
| Lỗi ghi và retry | Giữ occurrence; không relearn khi lỗi; retry hợp lệ ghi một lần |
| Relearn cùng từ | Occurrence mới được chấm; FORGOT lần sau không gây loop |
| Transaction và scheduler hiện có | Regression AtomicReviewTest, RecordReviewUseCaseTest, LearningFlowIntegrationTest |

JUnit và tổng hợp toàn bộ suite nằm tại [evidence/flash-03](evidence/flash-03).

### Lệnh chạy

Yêu cầu JDK 17 và Android SDK 35, theo cấu hình Gradle của dự án:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleDebugAndroidTest lintDebug -Pflash03DeviceIsolation=true
```

Test thiết bị dùng gói riêng `com.vocamind.app.flash03validation`, kiểm tra dữ
liệu ban đầu rỗng và không xóa lịch sử người dùng. Dùng cài đặt QA mới cho mỗi
lần chạy; test chủ động thất bại nếu còn lịch sử từ lần trước.

```sh
./gradlew connectedDebugAndroidTest -Pflash03DeviceIsolation=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.feature.flashcard.FlashcardDuplicateReviewInstrumentedTest
```

Test Espresso gộp ba tap vào một ViewAction để không có idle-wait giữa các lệnh
Espresso riêng lẻ. Tap dùng MotionEvent tại cùng vị trí nút rating, cách 150ms
(cho phép trễ scheduling tối đa 50ms, ghi log tag `FLASH03`). Test kiểm tra event,
`reviewCount`, box, mistakes, timestamps/dueAt, thẻ kế tiếp, và giữ cùng ViewModel,
occurrence/mặt thẻ sau `ActivityScenario.recreate()`.

## Kết quả chạy

- JVM/Robolectric/Room: **508 tests, 51 suites; 0 failures/errors/skipped**.
- Riêng FlashcardViewModelTest: 22; LearningFlowIntegrationTest: 7;
  FlashcardLayoutTest: 2; AtomicReviewTest: 42, đều đạt.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: **PASS** (lint: 0 errors, 13 warnings tại các phần không sửa).
- JDK 17, Gradle 8.9, compileSdk 35; test thiết bị nhắm API 26 (minSdk).
- Thiết bị: **chưa xác nhận PASS**. Emulator API 26 boot được bằng software emulation
  (host không có `/dev/kvm`), nhưng `pm install -r -t` timeout trước khi có thể
  chạy instrumentation. Test APK đã biên dịch thành công; không coi việc build
  test APK hoặc JVM test là bằng chứng Espresso chạy trên thiết bị.
- Log lần thử: [device-attempt.txt](evidence/flash-03/device-attempt.txt).
  Cần chạy lại lệnh `connectedDebugAndroidTest` ở trên trên thiết bị/emulator
  có đủ tài nguyên trước khi đóng phần device acceptance của FLASH-03.
