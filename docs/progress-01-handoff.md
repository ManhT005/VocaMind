# PROGRESS-01 — Contract và bàn giao

Ngày triển khai: 25/10/2026. Owner: Vương Quang Thành; reviewer theo phân công:
Lê Mạnh Trường; phối hợp DATA-07: Bùi Vĩ Nhân.

## Căn cứ và phạm vi

- [Kế hoạch gốc](plans/progress-01-contract.md), sao chép nguyên nội dung từ file
  người dùng cung cấp; trạng thái “chỉ lập kế hoạch” trong file là thông tin lịch sử.
- [Bảng phân công VocaMind](https://docs.google.com/spreadsheets/d/1Zx4QWaSalYk2-KWoaRRxwL6Hd-vv-sa2Lv9dBDxzQFg/edit):
  đã đọc các dòng PROGRESS-01, DATA-07 và PROGRESS-02 qua Google Drive.
- SRS baseline v1.1 chưa có trong checkout/Downloads hoặc kết quả tìm Drive.
  Các mã FR-VM-041..044, BR-09, UC-07, AC-11, DEC-15 dưới đây truy vết theo
  kế hoạch và bảng phân công, chưa tuyên bố đối chiếu độc lập toàn văn baseline.
  Tài liệu “Đặc Tả SRS Cho VocaMind” trên Drive là bản khác, không dùng thay baseline.

Chỉ thêm domain Java, presentation contract, test và tài liệu. Không thay
schema/DAO, Quiz, scheduler, Fragment/navigation hoặc Gradle/dependency.

## API cho DATA-07 / DATA-04

Package: `com.vocamind.app.domain.progress`.

| API | Quy ước |
| --- | --- |
| `ProgressSummary(long totalLearned, long[] boxCounts)` | Đúng 6 box, không âm, tổng bằng total, reject tràn `long`; clone cả đầu vào/đầu ra |
| `getLearningCount/getReviewingCount/getMasteredCount` | Lần lượt cộng box 0–1 / 2–3 / 4–5; không nhận ba tổng độc lập |
| `QuizHistoryItem(long attemptId, long completedAtEpochMillis, int questionCount, int correctCount, int scorePercent)` | ID > 0; time >= 0; 4..10 câu; correct 0..N; score đúng round(correct * 100.0 / N) |
| `ProgressSnapshot(ProgressSummary, Collection<QuizHistoryItem>)` | Reject null/item null/ID trùng; copy và expose read-only; sort time DESC, ID DESC; không giới hạn số record |
| `ProgressRepository.readProgress()` | Một lần đọc đồng bộ trả `ProgressReadResult` khác null; caller chạy ngoài main thread |
| `GetProgressUseCase.execute()` | Contract đồng bộ cho PROGRESS-02; gọi repository và giữ nguyên outcome; chưa có production implementation |
| `ProgressReadResult.success(snapshot)` | Kể cả DB rỗng: summary 0 và history rỗng là success |
| `ProgressReadResult.failure(error)` | `READ_FAILED` cho lỗi storage; `INVALID_DATA` cho mapping/invariant sai; không biến thành empty |

Getter payload không tương ứng với success/failure ném `IllegalStateException`.
Constructor/factory nhận dữ liệu không hợp lệ ném `IllegalArgumentException`.
Data adapter bắt lỗi validation khi mapping và trả `INVALID_DATA`; không tự sửa
điểm, bỏ record lỗi hoặc trả summary 0. Không bắt mọi lỗi lập trình thành lỗi đọc.

### Nguồn số liệu và mapping

- `totalLearned = COUNT(DISTINCT UserWordProgress.vocabularyId)` trên toàn userdata.
  Mỗi vocabularyId thuộc đúng một box 0..5 trong cùng snapshot logic.
  Không đếm ReviewEvent/reviewCount/PackWord và không lọc active pack, isSaved,
  learnedAt; một từ ở nhiều pack chỉ đóng góp một lần.
- DATA-07 bảo đảm transaction/read consistency khi tổng hợp total, box và history;
  model chỉ kiểm tra số đếm được đưa vào, không chứng minh SQL DISTINCT.
- Mapping theo nhánh DATA-02 được mô tả trong kế hoạch: `QuizAttempt.id` →
  attemptId, `completedAt` → completedAtEpochMillis; ba field count/score giữ nguyên.
  Chỉ lấy attempt hoàn tất; không tự tạo ID/thời gian và không dùng QuizResult
  của session đang chạy. Query `ORDER BY completedAt DESC, id DESC`.
- `wrongCount = questionCount - correctCount`; không lưu thêm một giá trị dễ lệch.
- Model chuẩn hóa thứ tự một lần; UI không tự sort/tính lại. Không pagination ẩn.

**Cần Nhân/Thành xác nhận khi review:** row bookmark có `learnedAt=null` vẫn
đóng góp vào tổng nếu được tạo trong UserWordProgress, đúng literal kế hoạch/SRS.
Không thêm `WHERE learnedAt IS NOT NULL` để né xung đột. Nếu đổi định nghĩa,
phải đồng bộ SRS/task; saved-only row policy thuộc data/save feature.

## API cho PROGRESS-02

`feature.progress.ProgressViewState` là Java thuần, chưa gắn ViewModel/Fragment.

| State | Payload và hành vi |
| --- | --- |
| LOADING | `loading()`, chưa có snapshot/error; không giả số 0 |
| EMPTY | Read success, total=0 và history rỗng; có snapshot thật và thông điệp bắt đầu học |
| CONTENT | Có progress hoặc history; `isProgressEmpty/isHistoryEmpty` cho empty từng section |
| ERROR | Có typed error, `canRetry()=true`; UI dùng `RETRY_LABEL`, `ERROR_MESSAGE` và gọi use case lại |

`fromResult(result)` ánh xạ duy nhất success/failure sang state. `hasSnapshot()`
báo khi nào đọc được payload; getter snapshot/section trên LOADING/ERROR ném
`IllegalStateException`, getter error trên state khác ERROR cũng vậy.
Refresh giữ dữ liệu cũ, hủy request, lifecycle và chống response cũ thuộc PROGRESS-02.

Ví dụ wiring sau khi DATA-07 có adapter (thực thi trong background executor):

```java
GetProgressUseCase getProgress = repository::readProgress;
ProgressViewState state = ProgressViewState.fromResult(getProgress.execute());
// Dispatch state lên main thread để render; không gọi DB trên main thread.
```

Nhãn nằm trong view contract để chuyển vào resources ở PROGRESS-02:

- Tổng từ đã học; box 0: “Học lại”; box 1..5: “Cấp ôn 1” .. “Cấp ôn 5”.
- Learning — nhóm box 0–1; Reviewing — nhóm box 2–3; Mastered — nhóm box 4–5.
- “Mastered là trạng thái ôn tập trong ứng dụng, không phải chứng nhận năng lực ngoại ngữ.”
- Empty progress và empty Quiz history có thông điệp riêng.

Chưa tuyên bố nhãn đã hiển thị trên UI, hoặc AC-11 đạt end-to-end.

## Truy vết và phần còn lại

| Yêu cầu | Đầu ra PROGRESS-01 | Cần xác nhận ở task sau |
| --- | --- | --- |
| FR-VM-041 / BR-09 | Summary, ba nhóm, test từng biên box | DATA-07 fixture query |
| FR-VM-042 / DEC-15 | 6 box, sum invariant, nhãn box 0 | DISTINCT nhiều pack và render UI |
| FR-VM-043 / UC-07 | History validation/order, empty từng section | Persistence chỉ completed, UI timezone/locale |
| FR-VM-044 / BR-09 | Mastered disclaimer | PROGRESS-02 render và review UX |
| AC-11 | Một contract dùng chung, fake repository/use case | DB/UI integration, UI khớp số liệu thật |

Test double nằm trong `ProgressViewStateTest`; không đưa fake data vào ứng dụng.
Xem [kết quả kiểm chứng](progress-01-validation.md) cho evidence thực thi.
PR vào develop, review của Trường và xác nhận mapping/aggregation của Nhân vẫn
là bước bàn giao chưa thực hiện; không coi chúng đã hoàn thành từ local tests.
