# QUIZ-03 — Kế hoạch Quiz UI, session và lưu kết quả

**Owner:** Vương Quang Thành. **Reviewer:** Lê Mạnh Trường.

**Phân công gốc:** 7 giờ, D8–D9 Tuần 2; ưu tiên High, tính năng P0. **Phụ thuộc:** QUIZ-02, DATA-02.

**Nhánh:** `feature/quiz-03-ui-session`. **Base develop đã kiểm tra:** `8209ef75bc882ca10367abd40900e44e537571cb`.

**Trạng thái:** Đã triển khai UI/session và idempotent persistence; host tests đã chạy. Đang bổ sung device/CI evidence và chờ reviewer/data-owner xác nhận. Chi tiết hiện hành: [QUIZ-03 validation](../quiz-03-validation.md). Chưa đánh dấu Done thay người review.

## 1. Căn cứ

- SRS VocaMind Baseline v1.1: FR-VM-034..040, đặc biệt 036/038/039; BR-08/11; UC-06; AC-10; NFR-VM-10.
- Bản Excel phân công đã gửi, QUIZ-03: tiến trình N câu, bốn lựa chọn, khóa sau click, phản hồi text/icon, Next, xác nhận thoát, result, finalize một lần và chỉ lưu QuizAttempt khi hoàn tất. Owner Thành, reviewer Trường, dependency QUIZ-02/DATA-02.
- Google Sheets live chưa được xác minh; không coi bản Excel là bằng chứng mọi cập nhật mới của Sheet.
- Đã đọc source thực tế: GenerateQuiz.execute(Random), QuizRepository.activeSnapshot/saveAttempt, QuizSession.answer/next/abandon và QuizResult. SRS Android v1.0 chỉ dùng tham khảo lịch sử, không thêm chức năng ngoài baseline v1.1.

## 2. Mục tiêu và giới hạn

Hoàn thiện luồng người dùng: mở Quiz → sinh bài từ pack active → trả lời từng câu → phản hồi → Next → hoàn tất → kết quả và một bản ghi lịch sử. Thoát bài chưa hoàn tất phải xác nhận và không lưu completed attempt.

Giữ generator/Unicode normalizer/accepted distractor policy của QUIZ-02; không tự tạo pool khác hoặc fallback unrestricted. Không bổ sung AI, TTS, tính giờ thi, bảng xếp hạng, sửa Leitner hoặc làm lại Progress UI.

Đầu ra: QuizFragment/XML; QuizViewModel và view state; composition factory/use case wiring; completion persistence có idempotency; unit/Room/UI tests; validation và handoff.

## 3. API và nguyên tắc phải giữ

- GenerateQuiz là blocking; chạy trên worker, mỗi lần bắt đầu bài mới đọc snapshot mới. Phân biệt RepoError với READY/EMPTY/INVALID_INPUT; không đổi lỗi DB/policy thành “thiếu từ”.
- Câu hỏi/POS/context/options lấy nguyên từ generator; UI không shuffle lại, không so đáp án bằng text hoặc index cố định. Render đủ POS/context cần thiết để không làm mất ngữ cảnh phân biệt sense đã được policy duyệt.
- QuizSession bất biến: sau transition phải giữ Transition.getSession() mới. Một event handler trên main thread xử lý theo snapshot hiện tại; không giữ closure snapshot cũ để chấm điểm.
- answer(questionId, optionId) chỉ nhận lần hợp lệ đầu tiên. next() chỉ khi câu đã trả lời. Câu cuối chuyển COMPLETED ngay; không cần một click thừa để đủ điều kiện hoàn tất.
- QuizResult chỉ có ở COMPLETED; scorePercent lấy theo domain, không tự tính công thức khác tại UI. Bài bỏ dở không có result hoàn chỉnh.

## 4. Thiết kế UI và view state

| State | Hiển thị và thao tác |
|---|---|
| LOADING | Đang tạo bài; Start/retry không tạo hai yêu cầu chồng nhau. |
| EMPTY | Thông báo thiếu từ/nghĩa phù hợp, hướng dẫn chọn/kích hoạt gói. Không tạo bài giả hoặc tự activate. |
| ERROR | Lỗi đọc/policy/invalid input rõ ràng; Retry phù hợp, không coi là completed/empty. |
| ANSWERING | Câu i/N, lemma + POS/context, bốn option; chưa có đáp án thì Next disabled. |
| ANSWERED | Khóa cả bốn option; phản hồi đúng/sai bằng text/icon, không chỉ màu; Next cho câu chưa phải cuối. |
| SAVING | Bài hoàn tất, đang xác nhận lưu; khóa finalize/restart trùng, vẫn bảo toàn result trong state. |
| SAVE_ERROR | Hiển thị kết quả và lỗi lưu; không nói “đã lưu”; retry dùng cùng completion key. |
| RESULT | Tổng câu, đúng/sai, điểm; trạng thái lưu đã xác nhận; có hành động bắt đầu bài mới/quay lại. |

Đề xuất dùng màn Quiz hiện tại với các vùng state, không thêm Activity/framework navigation. Nút chính ≥48dp; nội dung dài cuộn được, không che đáp án/nút. Result có thể cùng Fragment, nhưng không tự lưu lần nữa khi render hoặc quay lại màn hình.

## 5. Chống chạm trùng và xác nhận thoát

- Inject monotonic clock, production dùng elapsedRealtime; không dùng wall clock cho debounce. Bỏ qua answer tap dưới 600 ms kể từ answer hợp lệ trước, kể cả qua câu kế tiếp. Tap không hợp lệ không cập nhật mốc. Khóa mỗi câu vẫn do domain, không chỉ dựa vào enabled của View.
- Không tự động Next ngay sau answer; giữ phản hồi đủ rõ để người dùng chủ động tiếp tục. Câu cuối hiển thị feedback/result theo state và lưu một lần.
- Toolbar Back và system Back cùng một đường xử lý. IN_PROGRESS cần dialog: Tiếp tục / Thoát bài. Hủy dialog giữ session; xác nhận mới abandon và rời màn; không ghi attempt.
- COMPLETED/SAVING/SAVE_ERROR không được gọi abandon để biến bài đã hoàn tất thành bỏ dở. Rời màn không reset session token hoặc phát lại lệnh lưu; retry/recovery dùng completion protocol ở mục 6.

## 6. Lưu kết quả đúng một lần — điểm phải giải quyết trước code UI

Hiện QuizRepository.saveAttempt(result, completedAt) chỉ insert và chưa nhận idempotency key. Một boolean trong ViewModel chỉ chặn tap/rotation, không đủ chống ghi trùng khi commit thành công nhưng callback mất hoặc process bị dừng. Không hứa exactly-once chỉ bằng cờ RAM.

Thiết kế đề xuất:

1. Cấp completionKey/session token một lần khi tạo session; giữ qua rotation/process restoration. completedAt lấy một lần khi nhận answer cuối hợp lệ, không đổi mỗi lần retry.
2. Bổ sung contract saveAttemptOnce(key, result, completedAt): cùng key và payload trả lại cùng attemptId; cùng key nhưng payload khác trả lỗi rõ, không ghi đè.
3. Idempotency được bảo đảm bằng unique key trong userdata và transaction, không SELECT rồi INSERT ngoài transaction. Phương án ưu tiên: cột completionKey nullable + unique index trong QuizAttempt; legacy rows giữ NULL. Nhân review mapping/migration; không đổi schema chỉ bằng sửa annotation.
4. Nếu schema tăng version, cung cấp migration không phá dữ liệu, giữ mọi attempt/progress hiện có, cập nhật schema export và test nâng cấp. Không destructive fallback. Giữ API cũ nếu caller khác còn dùng; đường production QUIZ-03 dùng giao thức idempotent.
5. CompletionKey không phải stableId của một từ. Không dùng điểm/thời gian để dedup vì hai bài độc lập có thể cùng kết quả. Key không phụ thuộc API mạng.
6. Khi write lỗi không rõ commit đã xảy ra chưa, retry cùng key để reconcile; không insert lại với key mới, không tự chấm lại bài. Result chỉ xác nhận “đã lưu” khi repository trả thành công/attemptId.

Đây là hợp đồng kỹ thuật đề xuất để đáp ứng finalize một lần, không phải một trường SRS đã có sẵn. Chốt với data owner trước implementation. Nếu cần phương án lưu key bằng bảng riêng, phải có semantics tương đương và test; không triển khai hai cơ chế song song.

## 7. Lifecycle và dữ liệu phiên

- Activity/Fragment-scoped QuizViewModel phù hợp navigation giữ session khi rotation; observer theo view lifecycle, không giữ View/binding trong model.
- SavedStateHandle hoặc snapshot presentation tối thiểu giữ session token, questions/options đã xáo trộn, câu hiện tại, answers, completion timestamp và trạng thái lưu. Không để domain phụ thuộc Parcelable/Android; restore phải qua validation, không deserialize rồi bỏ qua invariant.
- Không generate lại khi xoay hoặc khi có callback cũ. Generation token loại late results sau restart/clear; write được xử lý theo key, không dựa callback render để quyết định có insert.
- Restore completed session phải reconcile theo key thay vì tạo attempt mới. State hỏng/không thể phục hồi phải báo rõ; không dựng bài mặc định hoặc ghi kết quả chưa hoàn tất.
- Snapshot gói/câu hỏi giữ cho phiên đang làm; activation thay đổi áp dụng khi bắt đầu bài mới. Không tự thay câu giữa phiên hoặc hủy bài vì pack pause ở màn khác.

## 8. Thứ tự triển khai và ước lượng

| Bước | Việc làm | Giờ dự kiến |
|---|---|---|
| 1 | Chốt state/Back/debounce và completion contract với data owner | 0.75 |
| 2 | Xây QuizViewModel/coordinator, transitions và lifecycle snapshot | 1.5 |
| 3 | Xây XML/Fragment, bốn options, feedback, Next, result/empty/error | 1.75 |
| 4 | Nối idempotent completion và migration/data tests cần thiết | 1.25 |
| 5 | Regression, UI/device tests, build và validation | 1.75 |
| Tổng | Ước lượng task gốc, cần kiểm tra lại khi chốt schema | 7.0 |

Nếu migration/process recovery làm vượt ước lượng, báo điều chỉnh cụ thể; không cắt idempotency/test để giữ 7 giờ hoặc âm thầm mở rộng task. Test được viết cùng mỗi bước, không đợi cuối mới thiết kế.

## 9. Ma trận kiểm thử

| Nhóm | Ca bắt buộc | Kết quả mong đợi |
|---|---|---|
| Start/pool | 0/3/4/7/10/12 từ, thiếu nghĩa, policy/read error | EMPTY/error đúng loại; bài READY đúng N, không auto-activate. |
| Prompt/options | Đồng lemma khác POS/context, option text dài, shuffle | Giữ ngữ cảnh generator; đúng bốn options và đúng target ID. |
| Answer | Đúng/sai, ID lạ/cũ, double tap, tap <600 ms qua câu sau | Một answer hợp lệ/câu, debounce xác định bằng fake monotonic clock. |
| Next/last | Next trước answer, Next lặp, answer cuối | Không bỏ câu; COMPLETED đúng một lần sau câu cuối. |
| Score | 0/N, N/N, 1/6, 2/7 | 0/100/17/29%, wrong=total-correct. |
| Exit | Toolbar/system Back, hủy/xác nhận dialog | Hủy giữ session; bỏ dở không tạo QuizAttempt. |
| Persistence | Finalize/retry lặp, callback mất, cùng key khác payload, hai key cùng điểm | Cùng key đúng một row; hai bài khác nhau được hai row; payload conflict rõ. |
| Migration | DB cũ có history/progress → schema mới, nhiều legacy NULL key | Dữ liệu giữ nguyên, unique index đúng, không reset user data. |
| Lifecycle | Rotation khi loading/answer/feedback/saving/result, Back/restore | Không generate/shuffle lại hoặc insert trùng; state/result nhất quán. |
| Failure | Query/write fail, ambiguous commit, retry, late callback | Không fake success/empty; recovery đúng key, không replay rating. |
| Side effects | Abandon/generate vs completion; progress/catalog snapshot | Chỉ completion insert attempt; không sửa catalog/progress/ReviewEvent. |
| Device | Offline từ lần mở đầu, full 4/10 câu, exit, rotate, reopen | Luồng người dùng thực chạy ổn, evidence đúng commit/APK. |

Dùng unit tests cho coordinator/state/debounce và fake repository; Room integration cho idempotency/migration; instrumented tests cho real NavHost/Back/dialog và full flow. Không coi Robolectric, build test APK hoặc NO-SOURCE là device PASS.

## 10. File dự kiến và kiểm chứng

```text
app/src/main/java/com/vocamind/app/feature/quiz/
  QuizFragment.java, QuizViewModel.java, [state/snapshot mapper]
app/src/main/java/com/vocamind/app/core/
  [QuizViewModelFactory/composition]
app/src/main/res/layout/fragment_quiz.xml
app/src/main/res/values/strings.xml
app/src/main/java/com/vocamind/app/domain/quiz/
  [completion contract/use case tối thiểu, giữ domain hiện có]
app/src/main/java/com/vocamind/app/data/
  [idempotent attempt persistence + migration cần thiết]
app/src/test/, app/src/androidTest/
  [Quiz UI/coordinator/completion/migration tests]
docs/quiz-03-validation.md
```

Từ checkout hiện có, không cần tạo worktree; JDK17/SDK/Gradle theo foundation:

```sh
./gradlew --version
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
./gradlew connectedDebugAndroidTest
git diff --check
git status --short
```

Chỉ chạy connected khi có test device phù hợp, không reset install thật. Ghi commit, toolchain, lệnh, exit status và tests/failures/errors/skips từ report hiện tại. Device evidence ghi API, airplane mode, APK hash, luồng đã chạy. Các lệnh trên là kế hoạch, chưa được chạy cho QUIZ-03.

## 11. Done và bàn giao

- [ ] Đúng scope phân công và FR-VM-036/038/039, BR-08/11, NFR-VM-10.
- [ ] Full flow, empty/error, feedback text/icon, khóa answer, Next và result có test.
- [ ] Confirm exit hoạt động; abandon không lưu completed attempt.
- [ ] Idempotent completion/migration được kiểm chứng, không chỉ guard trong RAM.
- [ ] Lifecycle/retry không generate hoặc lưu trùng; restore theo đúng snapshot/token.
- [ ] CI đúng head xanh, host và device evidence phân biệt rõ.
- [ ] Trường review; Nhân phối hợp phần persistence/schema; góp ý xử lý trước merge/Done.

Khi bàn giao cho Progress/QA, lịch sử chỉ chứa bài đã hoàn tất; không sửa công thức thống kê để che lỗi lưu. QUIZ-03 không tự xác nhận toàn bộ release gates hoặc nghiệm thu học liệu của ứng dụng.
