# QUIZ-01 — Kế hoạch xây dựng Quiz domain model

**Dự án:** VocaMind

**Owner:** Vương Quang Thành

**Reviewer theo phân công:** Lê Mạnh Trường

**Nhóm:** Domain; ưu tiên High; thuộc tính năng P0 FEAT-07

**Ước lượng gốc:** 5 giờ, D1–D2 của Tuần 1

**Nhánh:** `feature/quiz-01-domain-model`

**Base:** `develop`, tại thời điểm lập kế hoạch: `4df8d979f395d646d220bb420f50c35cbf124340`

**Trạng thái:** Kế hoạch triển khai; chưa viết model, generator hoặc test Java; chưa chạy build cho QUIZ-01.

## 1. Căn cứ

- Bảng `VocaMind — Phân công`, sheet Phân công, QUIZ-01: định nghĩa QuizQuestion/Option/Session/Result và contract generator thuần domain, không UI/Room; unique stableId, 1 đúng + 3 nhiễu; session state và answer locking; unit test invariants và pool <4.
- SRS Baseline v1.1: FR-VM-034..040; BR-08; UC-06; AC-10; DEC-07. BR-11/NFR-VM-08 liên quan chống chạm trùng, cần phân định trách nhiệm với QUIZ-03.
- Foundation hiện có: Java 17, minSdk 26, compile/target SDK 35, JUnit 4.13.2, Clean MVVM, Android CI chạy unit test và assembleDebug. Data/domain hiện là placeholder; QuizFragment chưa có nghiệp vụ.
- VOC-DATA-02 trên nhánh riêng là đầu vào tham khảo cho stableId, single-sense và meaning normalization. QUIZ-01 dùng vocabulary domain tối thiểu, không phụ thuộc Python tooling hoặc đòi merge toàn bộ contract trước khi viết model.

## 2. Mục tiêu và đầu ra

Tạo bộ model Java thuần có ràng buộc rõ để QUIZ-02 viết generator, QUIZ-03 xây UI/session và Nhân nối persistence mà không phải tự định nghĩa lại dữ liệu.

Đầu ra dự kiến:

1. QuizVocabulary và QuizPool: input domain, kiểm tra khả năng tạo bài.
2. QuizOption, QuizQuestion: lựa chọn và câu hỏi hợp lệ.
3. QuizSession, QuizResult: vòng đời phiên, khóa đáp án, kết quả hoàn tất.
4. QuizGenerator interface và kết quả trả về có phân biệt ready/không đủ dữ liệu/lỗi đầu vào.
5. Local JUnit tests và tài liệu handoff/truy vết yêu cầu.

## 3. Ranh giới với task sau

| Trong QUIZ-01 | Thuộc task sau |
|---|---|
| Model, invariants, contract đầu vào/đầu ra | QUIZ-02: thuật toán chọn câu/nhiễu và shuffle |
| Kiểm tra pool <4 ID hoặc <4 nghĩa | DATA-04/QUIZ-02: lấy pool thật từ các pack active |
| Khóa câu sau câu trả lời hợp lệ đầu tiên | QUIZ-03: nút UI, phản hồi, debounce 600 ms bằng thời gian monotonic |
| Quy định trạng thái, bỏ dở không có result | QUIZ-03: dialog xác nhận thoát và xử lý lifecycle |
| Result chỉ có sau khi hoàn tất, điểm đúng | DATA/QUIZ-03: ghi một QuizAttempt bằng cơ chế finalize/persistence phù hợp |
| JUnit thuần domain | UI/instrumented/Room tests và nghiệm thu toàn Quiz |

Không đổi Gradle/dependency, UI/navigation, DAO/schema hay thuật toán Leitner trong phạm vi này. Nếu cần dependency mới phải có lý do riêng; không thêm framework cho model.

## 4. Thiết kế đề xuất

### 4.1 Package và dependency direction

```text
app/src/main/java/com/vocamind/app/domain/quiz/
app/src/test/java/com/vocamind/app/domain/quiz/
```

Chỉ Java standard library phù hợp Android API 26. Không import android.*, androidx.*, Room, LiveData, Fragment hoặc lớp data/feature. Dùng class bất biến, final fields, defensive copy cho collection; không để bên gọi sửa trực tiếp danh sách đáp án/câu hỏi. Tránh API collection chỉ có ở Java mới mà Android API 26 không hỗ trợ nếu chưa có desugaring tương ứng.

### 4.2 Model và trách nhiệm

| Thành phần | Dữ liệu/trách nhiệm chính |
|---|---|
| QuizVocabulary | stableId, lemma, pos, nghĩa Việt; metadata ngữ cảnh tối thiểu nếu cần phân biệt sense. Đây là domain projection, không phải Room entity. |
| QuizPool | Snapshot vocabulary đã qua boundary, dedup stableId, phát hiện bản ghi cùng ID nhưng nội dung xung đột; đánh giá số từ/nghĩa hợp lệ và target question count. |
| QuizOption | ID lựa chọn duy nhất trong câu, nghĩa hiển thị và khóa nghĩa để so sánh. UI submit ID, không submit text hoặc boolean tự khai là đúng. |
| QuizQuestion | ID câu gắn với vocabulary stableId, prompt/ngữ cảnh, 4 option, correctOptionId trỏ đúng một option; các meaningKey khác nhau từng đôi. |
| QuizSession | Snapshot câu hỏi, vị trí hiện tại, đáp án đã khóa, trạng thái IN_PROGRESS/COMPLETED/ABANDONED; transition hợp lệ và result chỉ khi hoàn tất. |
| QuizResult | Tổng câu, số đúng/sai và điểm phần trăm; hoàn tất thì mới tạo. Thời gian hoàn tất nếu chứa trong model phải được truyền từ caller, không đọc đồng hồ ngầm. |
| QuizGenerator | Interface nhận pool domain và nguồn randomness có thể seed; trả generation outcome. Không truy vấn DB hoặc ghi QuizAttempt. |
| Generation outcome | READY với danh sách câu; EMPTY với lý do không đủ từ/nghĩa; invalid/conflicting input được thể hiện rõ, không tráo thành “không có từ”. |

Tên/helper class cụ thể có thể tinh gọn khi triển khai; phải giữ ranh giới và behavior trong kế hoạch. Không cần tạo mỗi enum/trạng thái thành một framework.

### 4.3 Quy tắc pool và số câu

- Boundary upstream chỉ cung cấp vocabulary của pack active. Input model không tự bật pack hoặc truy cập UserPack. Một collection không chứng minh được pack active; cần ghi precondition và test repository ở task nối dữ liệu.
- stableId 16 hex, không rỗng; cùng ID cùng nội dung tính một. Cùng ID có dữ liệu mâu thuẫn phải báo invalid input, không âm thầm giữ first/last.
- Chỉ tạo được Quiz khi có ≥4 stableId hợp lệ và ≥4 meaningKey khác nhau.
- Đủ điều kiện: target count = min(10, số stableId hợp lệ), không phải số nghĩa phân biệt. Hai ID có nghĩa Việt giống nhau vẫn có thể nằm trong pool; generator phải tránh dùng chúng làm nhiễu lẫn nhau.
- Không yêu cầu mọi câu trong bài có correct meaning khác nhau; SRS yêu cầu không lặp stableId và bốn lựa chọn trong mỗi câu khác nghĩa. Không tự thêm quy tắc làm bài ngắn hơn SRS.
- Pool <4 hoặc nghĩa <4 trả EMPTY có lý do, không tạo session rỗng hoặc nhân bản câu để đủ số lượng.

### 4.4 Normalization và nguy cơ đa nghĩa

- BR-08 yêu cầu NFC + trim + full Unicode casefold cho meaningKey, giữ dấu tiếng Việt. Java toLowerCase(Locale.ROOT) không tương đương full casefold.
- Trước khi code model, chốt boundary của MeaningNormalizer/meaningKey: core model so sánh key đã chuẩn hóa; unit test có normalizer/test double xác định. Implementation production/adapter phải qua vector casefold trước khi generator thật được nghiệm thu ở QUIZ-02. Không ghi test của fake thành bằng chứng production normalizer đúng.
- meaningKey là dữ liệu dẫn xuất cho domain, không tự sửa canonical contract/Room schema chỉ để thêm field.
- Cùng lemma có thể có nhiều POS/sense. Model cần mang POS/ngữ cảnh để generator/UI có thể tạo prompt không mơ hồ. Cách hiển thị và chọn nhiễu cho trường hợp đa nghĩa được chốt ở QUIZ-02/03; không tuyên bố chỉ khác chuỗi nghĩa là đủ chứng minh đúng một đáp án ngữ nghĩa.

### 4.5 Session và answer locking

Luồng dự kiến:

```text
Pool READY -> IN_PROGRESS -> trả lời câu hiện tại -> khóa câu
             -> next khi đã trả lời -> câu tiếp
             -> trả lời câu cuối -> COMPLETED -> có QuizResult
             -> bỏ dở trước khi hoàn tất -> ABANDONED -> không có QuizResult
```

- Input answer có questionId + optionId để từ chối click cũ khi UI đã chuyển câu.
- Đáp án không tồn tại hoặc questionId không khớp: không thay đổi phiên.
- Sau lần trả lời hợp lệ đầu tiên, click lại không đổi đáp án/điểm; trả outcome rõ để caller phản ứng.
- Không next khi chưa trả lời. Next/answer sau kết thúc không làm phát sinh kết quả mới.
- Quyết định đề xuất: chấm câu cuối chuyển COMPLETED ngay, không chờ thao tác thừa mới được tính kết quả. Caller chịu trách nhiệm finalize DB đúng một lần ở QUIZ-03; model bất biến không tự bảo đảm exactly-once persistence.
- Điểm: round(correctCount * 100.0 / questionCount), số sai = tổng - đúng. Không có điểm kết quả hoàn chỉnh cho bài bỏ dở.
- Domain không tự hiển thị dialog; caller chỉ gửi abandon sau khi người dùng xác nhận. Debounce 600 ms qua các câu khác nhau được nối tại QUIZ-03, không dùng System.currentTimeMillis() trong model để giả lập behavior UI.

## 5. Thứ tự triển khai và ước lượng

| Bước | Công việc | Đầu ra | Giờ dự kiến |
|---|---|---|---|
| 1 | Rà SRS, chốt semantics model/outcome/normalization boundary | Design note và bảng invariants | 0.5 |
| 2 | Xây vocabulary/pool, option/question và input guard | Model bất biến, quy tắc pool và câu hợp lệ | 1.25 |
| 3 | Xây session/result và transitions | Answer locking, completion/abandon, tính điểm | 1.25 |
| 4 | Khai báo generator contract, fake/test fixture | Contract cho QUIZ-02, không có random sampling production | 0.5 |
| 5 | Chạy test/build, sửa lỗi, tài liệu evidence/handoff | JUnit report, build result, review checklist | 1.5 |
| Tổng | Ước lượng task gốc | Chưa tính setup toolchain hoặc vướng dependency ngoài task | 5.0 |

Viết test theo từng nhóm behavior cùng lúc triển khai; bước 5 là chạy tích hợp cuối và tổng hợp evidence. Nếu vượt ước lượng vì normalizer/toolchain hoặc baseline build hỏng, ghi riêng nguyên nhân, không cắt test để báo đủ 5 giờ.

## 6. Kế hoạch kiểm thử

| Nhóm | Các ca bắt buộc | Kết quả mong đợi |
|---|---|---|
| Input | Null/blank ID, text; sai stableId; null collection/item | Reject theo contract, không tạo model lỗi |
| Pool | 0/3/4/7/10/12 ID hợp lệ | 0/0/4/7/10/10 câu mục tiêu; không đủ trả EMPTY |
| Nghĩa | ≥4 ID nhưng <4 meaningKey; trùng key khác hoa/thường/NFC | EMPTY do thiếu nghĩa; không bỏ dấu để gộp nhầm ban/bàn |
| ID | Trùng ID cùng payload; trùng ID xung đột | Dedup đúng hoặc báo invalid, không đếm tăng pool |
| Question | 3/5 options; option ID trùng; key nghĩa trùng; không có correctOptionId | Reject câu không hợp lệ |
| Session | <4/>10 câu; trùng vocabulary ID; collection bị sửa bên ngoài | Reject/defensive copy, state không bị thay đổi ngoài API |
| Answer | Đúng/sai; option lạ; questionId cũ; click lần hai | Chỉ lần hợp lệ đầu tiên khóa câu, tính đúng một lần |
| Transition | Next trước answer; abandon; answer/next sau terminal | Không nhảy trạng thái trái quy tắc, abandon không có result |
| Result | Hoàn tất, bỏ dở; 0/N, N/N, 1/6, 2/7 | Chỉ completed có result; 0%, 100%, 17%, 29% |
| Generator contract | Fake nhận pool đã chuẩn bị, seed cố định, outcome thiếu dữ liệu | Unit test không cần Room/UI/network; không giả định generator thật đã có |

Test class dự kiến: QuizPoolTest, QuizQuestionTest, QuizSessionTest, QuizResultTest; bổ sung contract test nếu cần. Đánh giá theo behavior đã chạy, không lấy số test mục tiêu hoặc NO-SOURCE làm bằng chứng đạt.

## 7. Môi trường và lệnh kiểm chứng dự kiến

Repository yêu cầu JDK 17, Gradle Wrapper 8.9, SDK Platform 35 và Build Tools 34.0.0. Máy cloud hiện mới xác nhận có Java 21 runtime, chưa xác nhận javac/JDK 17 hoặc Android SDK; cần chuẩn bị đúng toolchain khi triển khai, không đổi version project để né lỗi setup.

Từ checkout hiện có, không cần tạo worktree:

```sh
java -version
./gradlew --version
./gradlew :app:testDebugUnitTest --tests 'com.vocamind.app.domain.quiz.*'
./gradlew testDebugUnitTest
./gradlew assembleDebug
git diff --check
git status --short
```

Kiểm tra JUnit XML/report của lần chạy hiện tại để chắc test Quiz thực sự chạy và số failed/skipped đúng. Có thể compile/run JUnit thuần Java riêng để chẩn đoán; việc đó không thay cho bằng chứng Android Gradle build. Không tuyên bố PASS cho lệnh chưa chạy. CI hiện có sẽ dùng JDK 17 khi code được commit/push và workflow chạy; chưa có run cho QUIZ-01.

## 8. File dự kiến và handoff

- Source mới chỉ trong `app/src/main/java/com/vocamind/app/domain/quiz/`.
- Tests trong `app/src/test/java/com/vocamind/app/domain/quiz/`.
- Ghi kết quả thực thi riêng tại `docs/quiz-01-validation.md` sau khi chạy, không điền trước kết quả PASS.
- Cập nhật docs index; không sửa QuizFragment hoặc gắn persistence trong task này.

Handoff:

| Bên nhận | Nội dung |
|---|---|
| Thành / QUIZ-02 | Generator interface, pool rules, target count, validated Question/Option |
| Thành / QUIZ-03 | Session transitions, answer outcome, result, trách nhiệm debounce/finalize/confirm exit |
| Trường (reviewer) | Review model/prompt nghĩa, fixtures, tests và khả năng dùng vocabulary domain |
| Nhân | Fields QuizResult cần map sang QuizAttempt; chỉ completed được lưu, không thêm DAO trong QUIZ-01 |
| Đạo | Boundary domain/repository, hợp đồng cung cấp pool; không để model Quiz phụ thuộc schema Room |

## 9. Definition of Done và kiểm soát phạm vi

- [ ] Model Java thuần, immutable/defensive copy, không Android/Room/IO.
- [ ] Invariants Question/Option, pool 0/3/4/7/10/12 và thiếu nghĩa được kiểm tra bằng test.
- [ ] Answer locking/state/result có unit test; bỏ dở không có result hoàn tất.
- [ ] Generator chỉ là contract; không nhận DAO và không ghi DB.
- [ ] Giới hạn normalization/đa nghĩa, debounce và exactly-once persistence được ghi rõ, không tuyên bố đã giải quyết ở task này.
- [ ] Các test Quiz thực sự chạy; full unit test/build có kết quả được ghi trung thực.
- [ ] PR vào develop, Trường review; góp ý được xử lý trước Done.

Hiện tại chỉ lập kế hoạch. Các checkbox trên đều chưa được xác nhận; commit/push/PR của phần triển khai chưa được thực hiện.
