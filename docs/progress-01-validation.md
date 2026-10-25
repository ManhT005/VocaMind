# PROGRESS-01 — Kết quả kiểm chứng

Ngày: 25/10/2026. Nhánh: `feature/progress-01-contract`.
Base develop: `37f14ca3cdb9086e9960fef988290c0839d5aa3e`.

Đã triển khai model/contract/view state và local tests theo
[kế hoạch](plans/progress-01-contract.md). Chi tiết API, nguồn tài liệu, giới hạn
đối chiếu SRS và mapping nằm trong [handoff](progress-01-handoff.md).

## Môi trường thực thi

- Windows / PowerShell, Gradle Wrapper 8.9, cấu hình Android giữ nguyên.
- JVM thực chạy: Eclipse Temurin **21.0.7**, từ cache JDK có sẵn.
  Source/target của project vẫn là Java 17. Chưa chạy JDK 17 CI cho task này.
- `java` chưa nằm trong PATH mặc định; chỉ đặt JAVA_HOME cho tiến trình kiểm tra.
- Lần thử Gradle trong sandbox bị chặn ghi lock ở `C:\.gradle`, rồi ở cache
  `C:\Users\thanh\.gradle`. Lần chạy được cấp quyền dùng cache có sẵn đã thành công;
  không đổi dependency/version để vượt lỗi môi trường.

Các lệnh Gradle thành công dùng:

```powershell
$env:JAVA_HOME='C:\Users\thanh\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:GRADLE_USER_HOME='C:\Users\thanh\.gradle'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.progress.*' --tests 'com.vocamind.app.feature.progress.*'
.\gradlew.bat testDebugUnitTest assembleDebug
```

## Kết quả thực chạy

| Kiểm tra | Kết quả |
| --- | --- |
| Focused Android Gradle Progress tests | BUILD SUCCESSFUL, 1m 21s; 18 tests |
| Toàn bộ `testDebugUnitTest assembleDebug` | BUILD SUCCESSFUL, 50s; 28 tests; APK debug được tạo |
| `ProgressDomainTest` | tests=11, failures=0, errors=0, skipped=0 |
| `ProgressViewStateTest` | tests=7, failures=0, errors=0, skipped=0 |
| Hồi quy `QuizDomainTest` | tests=10, failures=0, errors=0, skipped=0 |
| `git diff --check` | PASS; kiểm tra whitespace riêng cho source/test mới cũng không có lỗi |
| Domain dependency scan | Chỉ dùng Java standard library; không import Android/AndroidX/data/feature |

Đã đọc XML của lần chạy cuối tại
`app/build/test-results/testDebugUnitTest/TEST-*.xml`, timestamp
`2026-10-25T10:23:06` (UTC); không dùng report cũ hoặc NO-SOURCE làm bằng chứng.
Report HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

Coverage hành vi đã kiểm chứng:

- Summary 0; fixture [2,3,4,5,6,7] → total 27, nhóm 5/9/13; từng biên box.
- Null, số box sai, count/total âm, tổng lệch, count vượt int và tràn long;
  defensive copy hai chiều của array.
- History ID/time/count/score sai; mọi tổ hợp correct 0..N với N=4..10,
  gồm 0%, 100%, 17%, 29%; wrongCount dẫn xuất.
- Sort theo thời gian rồi ID, giá trị Long.MAX_VALUE, history >10 record,
  không sửa thứ tự input; reject null/duplicate ID; list read-only.
- Success/failure payload riêng; fake repository/use case đọc lại khi retry;
  invalid persisted score → INVALID_DATA, không bị chuyển thành empty.
- LOADING không có số liệu giả; EMPTY thành công; CONTENT với một hoặc cả hai
  section có dữ liệu; ERROR giữ typed failure, cho retry.
- Box/group labels, empty/error copy và Mastered disclaimer trong view contract.

## Checklist bàn giao

- [x] Model summary/history/snapshot bất biến, có invariant và validation.
- [x] Repository/use case interface và test double, không phụ thuộc DB thật.
- [x] View states/section empty/retry/labels được đặc tả và test.
- [x] Focused tests, full regression và debug build thành công bằng Android Gradle.
- [x] Không sửa schema/DAO/Quiz/Leitner/UI/Gradle và không thêm filter learnedAt/pack.
- [x] Handoff rõ trách nhiệm DATA-07 / PROGRESS-02 và saved-only semantics.
- [ ] Đối chiếu trực tiếp SRS baseline v1.1 khi có file/link đúng phiên bản.
- [ ] Nhân xác nhận mapping/aggregation và policy saved-only row; Trường review.
- [ ] PR vào develop và xử lý phản hồi review.

Phần implementation local đã hoàn thành; chưa coi toàn bộ quy trình Done của nhóm
đã hoàn tất. Tại thời điểm kiểm chứng trước commit/push, chưa tạo PR/merge
hoặc ghi trạng thái Done vào bảng phân công.
Không có DB integration, device/UI/E2E/performance test hoặc CI run trong task này;
AC-11 end-to-end cần DATA-07 và PROGRESS-02.
