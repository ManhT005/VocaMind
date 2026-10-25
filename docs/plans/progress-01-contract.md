# PROGRESS-01 — Kế hoạch xây dựng Progress contract

**Dự án:** VocaMind

**Task:** PROGRESS-01 — Progress contract; Domain; ưu tiên High; tính năng P0 FEAT-08

**Owner:** Vương Quang Thành. **Reviewer theo phân công:** Lê Mạnh Trường. **Phối hợp data:** Bùi Vĩ Nhân.

**Ước lượng trong bảng phân công:** 4 giờ; D2 của Tuần 1. Đây là ước lượng gốc, không xác nhận task đã bắt đầu/hoàn thành ở ngày đó.

**Nhánh:** `feature/progress-01-contract`; base `develop` tại commit `37f14ca3cdb9086e9960fef988290c0839d5aa3e`.

**Trạng thái ngày 25/10/2026:** Chỉ lập kế hoạch. Chưa viết source/test nghiệp vụ PROGRESS-01, chưa thực hiện build/test cho task này.

## 1. Căn cứ trực tiếp

| Tài liệu | Nội dung dùng lập kế hoạch |
|---|---|
| `VocaMind — Phân công .xlsx`, Phân công, PROGRESS-01 | ProgressSummary/QuizHistory models và use case contract; total learned; box 0..5; Learning/Reviewing/Mastered; view state/labels và lịch sử Quiz. Dependency ghi `-`; không cần chờ DB thật mới thiết kế. |
| Cùng bảng, DATA-07 | Nhân xây query tổng hợp và lịch sử từ userdata.db, COUNT DISTINCT vocabularyId; repository trả model thống nhất, UI không tự tính lại. DATA-07 phụ thuộc DATA-02/03. |
| Cùng bảng, PROGRESS-02 | Thành xây UI và GetProgress use case từ ProgressRepository; phụ thuộc PROGRESS-01 và DATA-07. |
| `VocaMind_SRS_Baseline_v1.1.docx`, FR-VM-041..044 | Bốn chỉ số, sáu trạng thái, lịch sử/empty state, giải thích Mastered. |
| SRS BR-09, UC-07, AC-11, DEC-15 | Learning 0–1, Reviewing 2–3, Mastered 4–5; tổng box = số progress duy nhất; Mastered không phải chứng chỉ. |
| SRS §7.1–7.4; FR-VM-038/039 | UserWordProgress và QuizAttempt thuộc userdata.db; chỉ bài hoàn tất được lưu; logical IDs, bảo toàn dữ liệu. |

SRS Android v1.0 chỉ là tài liệu lịch sử; không đưa chỉ số/chức năng của bản cũ vào MVP v1.1 khi chưa có thay đổi phạm vi.

## 2. Mục tiêu và đầu ra

Định nghĩa bộ dữ liệu và hành vi để Nhân làm DATA-07 và Thành làm PROGRESS-02 mà không tự diễn giải lại chỉ số hoặc tạo model trùng nhau.

Đầu ra dự kiến:

1. Domain model `ProgressSummary`, `QuizHistoryItem`, `ProgressSnapshot` bất biến, có invariant rõ.
2. Contract `ProgressRepository` và `GetProgressUseCase`/kết quả đọc dữ liệu; chưa triển khai Room hoặc use case IO thật.
3. View-state contract và nhãn chuẩn; phân biệt loading, empty, content, error.
4. Fixture, local unit tests cho model/invariants/trạng thái và contract test double.
5. Tài liệu mapping/handoff, ghi kết quả thực thi sau khi chạy; không điền sẵn PASS.

Tên/helper class có thể tinh gọn khi code; không tạo framework generic hoặc dependency mới để phục vụ các model này.

## 3. Phân định phạm vi

| Làm trong PROGRESS-01 | Bàn giao cho task sau |
|---|---|
| Chốt định nghĩa và model thống kê | DATA-07: SQL/DAO, query/transaction, mapper và đối chiếu DB fixture |
| Chốt lịch sử và validation/sort contract | DATA/QUIZ-03: ghi QuizAttempt đúng một lần khi hoàn tất |
| Repository/use case interface, fake cho test | DATA-04/07 và PROGRESS-02: implementation đọc dữ liệu thật, scheduling/background/lifecycle |
| View state/labels/empty/error behavior | PROGRESS-02: Fragment/ViewModel, card/chart/list, resource/localization và UI binding |
| Unit test thuần Java | Room/instrumented/UI/performance/E2E tests |

Không sửa Room schema/DAO, QuizSession/QuizResult, thuật toán Leitner, navigation, Fragment hoặc Gradle/dependency trong bước triển khai dự kiến của task này. Nếu phát hiện xung đột cần ghi riêng, không tự mở rộng phạm vi.

## 4. Hiện trạng đã kiểm tra và điểm tích hợp

- `develop` đã có Quiz domain và `QuizResult` với questionCount/correctCount/wrongCount/scorePercent; result không có attempt ID hoặc completedAt. Không dùng QuizResult của phiên đang chạy làm lịch sử.
- Đã đọc nhánh `feature/data-02-user-database` để tham khảo: QuizAttempt dùng `long id` tự tăng, `long completedAt`, `int questionCount/correctCount/scorePercent`; UserWordProgress dùng String vocabularyId PK, int box, nullable learnedAt và isSaved. Đây là nhánh chưa giả định đã merge/được nghiệm thu.
- DAO hiện tại trên nhánh data có `quizzes()` ORDER BY id và chưa có query tổng hợp DATA-07. Query theo completedAt và snapshot consistency là việc DATA-07, không tự đổi DAO ở PROGRESS-01.
- `setSaved` của nhánh data chỉ cập nhật progress đã tồn tại, có ghi chú cần review cách tạo saved-only row. Cần giữ định nghĩa đếm theo SRS ở mục 5, không tự đưa filter learnedAt vào query.
- Foundation Java 17, API 26+, JUnit 4.13.2 đã có. Không khẳng định toolchain cloud hoặc CI của PROGRESS-01 đã chạy.

## 5. Định nghĩa chỉ số — bắt buộc theo SRS

### 5.1 ProgressSummary

| Giá trị | Định nghĩa |
|---|---|
| totalLearned | COUNT(DISTINCT UserWordProgress.vocabularyId), theo FR-VM-042 và checklist phân công. |
| boxCounts[0..5] | Số vocabularyId duy nhất ở đúng mỗi box; một từ thuộc một box tại thời điểm snapshot. |
| learningCount | box0 + box1 |
| reviewingCount | box2 + box3 |
| masteredCount | box4 + box5 |

Invariants:

```text
boxCounts có đúng 6 phần tử, mọi count >= 0
sum(boxCounts[0..5]) = totalLearned
learningCount + reviewingCount + masteredCount = totalLearned
```

Quyết định kỹ thuật đề xuất: count dùng `long`, box index dùng `int`. Kiểm tra tràn số khi cộng; không wrap hoặc clamp âm. totalLearned có thể nhận riêng từ boundary để đối chiếu tổng box; các nhóm được tính từ box, không nhận ba giá trị độc lập dễ lệch.

Không đếm ReviewEvent, reviewCount hoặc PackWord làm tổng từ học. Không nhân số từ khi cùng vocabularyId ở nhiều pack; không tự lọc theo pack active, isSaved hoặc learnedAt vì SRS hiện không yêu cầu filter cho màn tổng quan. Pause pack không xóa progress và không tự làm giảm thống kê toàn bộ userdata.

**Điểm cần Nhân/Thành xác nhận khi review:** theo literal SRS, mọi row UserWordProgress đóng góp vào tổng, kể cả row có learnedAt=null nếu nó tồn tại. Tạo row chỉ để lưu bookmark sẽ ảnh hưởng chỉ số này. PROGRESS-01 giữ invariant SRS; cách tạo saved-only row thuộc data/save feature, hoặc phải có quyết định thay đổi định nghĩa được đồng bộ SRS/task. Không âm thầm “sửa” bằng WHERE learnedAt IS NOT NULL.

### 5.2 Nhãn và giải thích

| Giá trị | Nhãn chuẩn |
|---|---|
| totalLearned | Tổng từ đã học |
| box 0 | Học lại |
| box 1..5 | Cấp ôn 1 .. Cấp ôn 5 |
| Learning | Learning — nhóm box 0–1 |
| Reviewing | Reviewing — nhóm box 2–3 |
| Mastered | Mastered — nhóm box 4–5 |

Thông điệp bắt buộc: “Mastered là trạng thái ôn tập trong ứng dụng, không phải chứng nhận năng lực ngoại ngữ.” Nội dung/label được ghi trong view contract; domain không phụ thuộc Android resource hay locale. PROGRESS-02 đưa nhãn vào resource và bind lên UI.

## 6. Lịch sử Quiz và snapshot

### 6.1 QuizHistoryItem

| Field đề xuất | Kiểu | Quy tắc |
|---|---|---|
| attemptId | long | >0 cho record đã lưu, unique trong snapshot; khớp PK trên nhánh DATA-02 đang tham khảo. |
| completedAtEpochMillis | long | >=0, thời điểm hoàn tất dạng epoch milliseconds; không là chuỗi ngày đã format. |
| questionCount | int | 4..10 theo SRS Quiz. |
| correctCount | int | 0..questionCount. |
| wrongCount | int, dẫn xuất | questionCount - correctCount. |
| scorePercent | int | round(correctCount * 100.0 / questionCount), 0..100. |

Boundary phải kiểm tra score lưu trong QuizAttempt khớp công thức. Không hiển thị điểm sai rồi tự sửa một phía để giấu lỗi dữ liệu. Xử lý lỗi là error contract, không giả lịch sử rỗng. Nếu tái sử dụng helper tính điểm, giữ cùng công thức của QuizResult và không chỉnh semantics QUIZ-01 trong task này.

Chỉ dùng QuizAttempt hoàn tất. Bài abandon không phải history record. ID/thời điểm do persistence/caller có thẩm quyền cung cấp, model không tự tạo ID hoặc đọc đồng hồ. Định dạng ngày giờ theo timezone/locale tại UI thuộc PROGRESS-02.

Quy ước kỹ thuật đề xuất: lịch sử trả mới nhất trước theo completedAt DESC, tie-break attemptId DESC. Không dùng ID thay thời gian như một giả định nghiệp vụ. Dùng comparator tránh phép trừ dễ overflow. Không pagination/giới hạn 10 bản ghi âm thầm trong contract v1; nếu cần phân trang phải bổ sung contract rõ.

### 6.2 ProgressSnapshot

Gồm ProgressSummary và danh sách QuizHistoryItem đã validate, không null, defensive copy/read-only, không trùng attemptId. Danh sách rỗng hợp lệ. Cần thứ tự ổn định; đề xuất snapshot chuẩn hóa thứ tự một lần, repository vẫn query theo cùng quy ước, UI không tự sort/tính lại.

Nhân chịu trách nhiệm đọc dữ liệu từ cùng snapshot logic để tránh total và box lệch khi review được ghi đồng thời. Empty database là summary toàn 0 và history rỗng. Model không thể chứng minh COUNT DISTINCT hoặc tính nhất quán transaction từ số đếm được truyền vào; bằng chứng đó thuộc DATA-07 integration tests.

## 7. Contract đọc dữ liệu và view state

### 7.1 Boundary domain

- ProgressRepository contract đọc một ProgressSnapshot hoặc trả failure có loại lỗi rõ.
- GetProgressUseCase contract gọi boundary và trả success/failure; implementation IO/scheduling thực tế nối ở PROGRESS-02/data task, không gọi DB trong domain model.
- Dùng fake/stub trong test; không thêm Room, LiveData, Rx hoặc coroutine dependency.
- Synchronous contract nếu chọn phải ghi rõ caller chạy ngoài main thread; không tự tạo thread hoặc giả rằng trả model nhanh nghĩa là query được phép trên UI thread. Việc refresh/lifecycle ở PROGRESS-02.
- Chọn một kiểu success/failure nhỏ phù hợp convention repo, không tạo hệ Result framework riêng. Cần phân biệt không có dữ liệu hợp lệ với đọc dữ liệu lỗi/invariant sai.

### 7.2 View-state contract

Đề xuất Java plain view-state ở presentation package `feature/progress`, không đưa label/UI lifecycle vào domain.

| State | Nội dung và behavior |
|---|---|
| LOADING | Chưa có kết quả đọc; không tạo snapshot 0 để giả là empty. |
| EMPTY | Đọc thành công, totalLearned=0 và history rỗng; sáu box là 0, có thông điệp bắt đầu học. |
| CONTENT | Có progress hoặc history; nếu một section rỗng thì section đó hiển thị empty riêng. |
| ERROR | Đọc thất bại/dữ liệu không hợp lệ; có error/retry contract, không tự reset DB hoặc đổi thành empty. |

Một người có thể có history Quiz nhưng chưa có progress flashcard, hoặc có progress mà chưa làm Quiz. Không yêu cầu hai phần cùng có dữ liệu mới hiển thị CONTENT. Refresh giữ dữ liệu cũ/loading/error chi tiết là behavior UI sẽ nối ở PROGRESS-02; PROGRESS-01 chốt invariants và payload tối thiểu.

## 8. File/package dự kiến khi triển khai

```text
app/src/main/java/com/vocamind/app/domain/progress/
  ProgressSummary.java
  QuizHistoryItem.java
  ProgressSnapshot.java
  ProgressRepository.java
  GetProgressUseCase.java
  [kết quả success/failure tối thiểu nếu cần]
app/src/main/java/com/vocamind/app/feature/progress/
  ProgressViewState.java
app/src/test/java/com/vocamind/app/domain/progress/
  [model/contract tests]
app/src/test/java/com/vocamind/app/feature/progress/
  [view-state contract tests]
docs/progress-01-validation.md  [chỉ tạo/điền kết quả sau khi chạy]
```

Domain chỉ Java standard library; không android.*, androidx.*, data.* hoặc feature.*. Immutable class, defensive copy, collection API tương thích minSdk 26; không phụ thuộc mới ngoài JUnit hiện có. View state cũng có thể unit test bằng Java thuần, chưa gắn Fragment/ViewModel.

## 9. Thứ tự thực hiện và ước lượng

| Bước | Công việc | Đầu ra | Giờ |
|---|---|---|---|
| 1 | Chốt semantics đếm, history mapping, empty/error và nhãn | Contract/fixture và điểm phối hợp Nhân | 0.5 |
| 2 | Xây model summary/history/snapshot, invariants và defensive copy | Model Java thuần và test theo từng behavior | 1.25 |
| 3 | Khai báo repository/use case/view-state contracts và fake | Boundary thống nhất cho DATA-07/PROGRESS-02 | 0.75 |
| 4 | Chạy test, build, kiểm tra regression Quiz và cập nhật handoff | Báo cáo thực thi, checklist review | 1.5 |
| Tổng | Theo ước lượng phân công | Chưa bao gồm setup toolchain hoặc giải quyết xung đột scope | 4.0 |

Nếu saved-only semantics hoặc lỗi baseline làm phát sinh việc ngoài contract, ghi rõ và điều chỉnh ước lượng với nhóm; không thay định nghĩa SRS để chạy qua test.

## 10. Kế hoạch kiểm thử

| Nhóm | Ca cần chạy | Mong đợi |
|---|---|---|
| Summary rỗng | 6 box bằng 0, total 0 | Summary hợp lệ; ba nhóm bằng 0. |
| Đủ sáu box | counts=[2,3,4,5,6,7], total=27 | Learning=5, Reviewing=9, Mastered=13; tổng 27. |
| Biên nhóm | Chỉ box0, chỉ box1, chỉ box2/3, chỉ box4/5 | Không xếp nhầm box0 vào Mastered hoặc bỏ box0. |
| Input sai | Null, thiếu/thừa 6 box, count/total âm, tổng lệch, tràn long | Reject; không clamp hoặc tạo dữ liệu 0. |
| Bất biến | Sửa array/list đầu vào sau tạo; sửa collection trả về | Snapshot không bị thay đổi từ bên ngoài. |
| Quiz history | Count 3/11, correct<0/>total, score lệch, ID/time sai | Reject đúng lỗi dữ liệu. |
| Điểm | 0/N, N/N, 1/6, 2/7 | 0%,100%,17%,29%; đúng cùng quy tắc QuizResult. |
| Thứ tự history | Unsorted input, timestamp bằng nhau, epoch lớn | DESC completedAt rồi DESC attemptId, không overflow; duplicate attemptId reject. |
| Section rỗng | Không progress/có Quiz; có progress/không Quiz; cả hai rỗng | CONTENT/CONTENT/EMPTY tương ứng; không mất section còn dữ liệu. |
| Boundary/state | Fake success rỗng/có dữ liệu, fake failure, loading/error | Phân biệt EMPTY/CONTENT/ERROR, không suy ra empty từ lỗi. |
| Nhãn | 0 và 1..5, các nhóm, Mastered disclaimer | Hợp đồng nhãn đúng DEC-15/BR-09; chưa tuyên bố UI đã hiển thị. |

Fixture model chỉ chứng minh phép cộng/group và hợp đồng. Test COUNT DISTINCT nhiều pack, snapshot read nhất quán và saved-only behavior phải được chạy trên DB ở DATA-07; test chữ trên màn hình thuộc PROGRESS-02.

## 11. Lệnh và bằng chứng dự kiến

Từ checkout hiện có, không cần worktree. Dùng JDK 17, Gradle Wrapper 8.9, SDK Platform 35/Build Tools theo foundation; kiểm tra môi trường thật khi triển khai, không tự đổi version project để vượt lỗi setup.

```sh
java -version
./gradlew --version
./gradlew :app:testDebugUnitTest --tests 'com.vocamind.app.domain.progress.*' --tests 'com.vocamind.app.feature.progress.*'
./gradlew testDebugUnitTest
./gradlew assembleDebug
git diff --check
git status --short
```

Xem report/XML của lần chạy hiện tại để chắc test mới thực sự chạy, ghi số tests/failures/errors/skipped. NO-SOURCE không chứng minh PROGRESS-01 đạt. Standalone Java/JUnit nếu dùng để chẩn đoán phải ghi riêng, không thay bằng chứng Android Gradle/CI.

Đây là lệnh kế hoạch, chưa được thực hiện cho PROGRESS-01. File validation chỉ ghi PASS sau khi chạy thành công; build bị chặn thì báo blocker cụ thể và phần đã kiểm tra.

## 12. Handoff và Definition of Done

| Bên nhận | Handoff |
|---|---|
| Nhân — DATA-07 | Count/box invariant, long types, QuizAttempt mapping, sort/tie-break, error và consistent snapshot contract. |
| Đạo/data boundary | Repository contract và dependency direction, không trả DAO/entity cho UI. |
| Thành — PROGRESS-02 | Model/use case/view-state, nhãn, section empty và Mastered disclaimer. |
| Trường — reviewer | Review đúng FR/BR/UC/AC, fixture/test và tính rõ ràng của nhãn/trạng thái. |

Checklist Done:

- [ ] ProgressSummary/QuizHistory/snapshot khớp FR-VM-041..044, BR-09 và DEC-15.
- [ ] Total/6 box/groups, history score/order và trạng thái được unit test thật.
- [ ] Repository/use case/view state có contract rõ, fake dùng được khi chưa có DATA-07.
- [ ] Nhãn và disclaimer được đặc tả; chưa tự coi là UI acceptance.
- [ ] Không thay schema/DAO/Quiz/Gradle ngoài phạm vi; không lọc learnedAt/active pack trái định nghĩa SRS.
- [ ] Cập nhật validation/handoff trung thực, phân biệt model tests với DB/UI/CI checks.
- [ ] PR vào develop, Trường review; Nhân xác nhận các điểm liên quan mapping/aggregation; góp ý xử lý trước khi Done.

## 13. Truy vết

| Yêu cầu | Thiết kế dự kiến | Kiểm chứng |
|---|---|---|
| FR-VM-041 | totalLearned và ba nhóm dẫn xuất | Summary/group tests; DB đối chiếu ở DATA-07. |
| FR-VM-042 / DEC-15 | 6 box và tổng distinct invariant, nhãn box0..5 | Model/label contract tests; DB/UI tests ở task sau. |
| FR-VM-043 / UC-07 | History chỉ từ completed attempts, trạng thái rỗng độc lập | History/snapshot/state tests, data/UI integration sau. |
| FR-VM-044 / BR-09 | Mastered disclaimer và không diễn giải thành chứng chỉ | Contract label check; review màn PROGRESS-02. |
| AC-11 / phân công | UI số liệu khớp DB | PROGRESS-01 cung cấp contract; không tự tuyên bố AC-11 đạt hoàn toàn khi chưa có DATA-07/PROGRESS-02. |
