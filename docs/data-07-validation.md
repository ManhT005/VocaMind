# DATA-07 — Progress aggregation queries

## 1. Căn cứ và phạm vi

Tuần 2, D8, 5 giờ, Nhân thực hiện / Thi review, High. Phụ thuộc DATA-02 và DATA-03.
Đối chiếu trực tiếp VocaMind_SRS_Baseline_v1.1.docx và VocaMind — Phân công.xlsx
người dùng đính kèm: FR-VM-041..043, UC-07, AC-11, DEC-15. Xem
[kế hoạch](plans/data-07-progress-aggregation-queries.md).

Branch: feature/data-07-progress-aggregation-queries. Bắt đầu từ develop c2ea69e,
merge DATA-03 e668bcb vào riêng feature branch, integration commit 84ad09d.
Không thay develop/main hoặc nhánh DATA-06. Nhánh bàn giao là
feature/data-07-progress-aggregation-queries; chưa merge vào develop/main.

## 2. Implementation

| Thành phần | Trách nhiệm |
| --- | --- |
| UserDao.distinctProgressCount | COUNT(DISTINCT vocabularyId) → long |
| UserDao.progressCountsByBox | GROUP BY box và COUNT DISTINCT → BoxProgressCount |
| UserDao.completedQuizHistory | Toàn QuizAttempt, completedAt DESC rồi id DESC |
| UserDao.progressSnapshot | @Transaction đọc cả total, groups và history |
| ProgressRows / BoxProgressCount | Kết quả nội bộ tầng data, không truyền lên UI |
| ProgressMapper | Điền sáu box, kiểm invariant, tạo ProgressSummary/QuizHistoryItem/ProgressSnapshot |
| RoomProgressRepository | Triển khai đúng ProgressRepository/ProgressReadResult hiện có |
| ProgressProvider | Ghép dependency với một UserDatabase do application owner quản lý |

Không tạo contract domain khác, không sửa UI, schema, Entity hoặc asset. Mapper
sử dụng các model immutable đã có; UI không được cộng lại counts theo quy tắc riêng.
Learning/Reviewing/Mastered dùng getter domain theo box0–1/2–3/4–5.

Tổng là toàn distinct UserWordProgress, không join catalog/UserPack nên không nhân
số từ khi một từ thuộc nhiều pack và không lọc pack active, isSaved hoặc learnedAt.
Nếu saved-only row đã có trong bảng, nó vẫn được tính theo contract PROGRESS-01;
DATA-07 không quyết định lúc nào tạo row này.

History không giới hạn ngầm, không lọc ngày ngầm. Bảng QuizAttempt chỉ nhận lượt
hoàn tất theo write contract của Quiz; DATA-07 không tự suy trạng thái session.
Score được kiểm tra với questionCount/correctCount theo QuizHistoryItem, không
sửa dữ liệu không hợp lệ hoặc tự tính score rồi thay giá trị đã lưu.

## 3. Lỗi và snapshot

- DB mở/query thất bại → READ_FAILED, không phải thành công với số 0.
- Box ngoài 0..5, total/groups không khớp, group trùng hoặc history sai invariant
  → INVALID_DATA. Không bỏ nhóm lỗi, sửa score hay xóa/reset userdata.
- Empty hợp lệ → success, 0 total, 6 box 0, history rỗng.
- Chỉ progress hoặc chỉ history → snapshot vẫn có content.
- total/group/history cùng một Room transaction. Bài kiểm tra cạnh tranh dùng
  hai DB handle, SQL callback sau câu count để bắt đầu writer trước câu GROUP BY;
  writer đổi box và thêm quiz trong transaction. Snapshot đầu vẫn giữ cả counts
  và history cũ; đọc sau writer nhận đồng thời cả hai dữ liệu mới.
- Callback/latches chỉ trong test; code sản phẩm không có test hook.

## 4. Sửa tương thích fixture phụ thuộc

Lượt baseline sau ghép DATA-03 với develop có 32 failures trong setup
DaoFoundationTest do schema catalog đã đổi. Cụ thể: ContentSource có thêm cột
attributionText, LearningPack.orderNo và (PackWord.packId, orderNo) có unique index.

Sửa giới hạn trong fixture test: INSERT source dùng tên đủ 7 cột; seed pack/word
bằng thứ tự riêng. Giữ các assertion đọc/batching/order/duplicate của DATA-03;
thêm kiểm tra thứ tự trùng bị SQLite từ chối. Không bỏ unique index, không giảm
assertion để chấp nhận fixture sai, không thay database/schema/logic sản phẩm.
Bộ DAO 32 lượt đã được chạy lại thành công sau sửa.

## 5. Hướng tích hợp PROGRESS-02

Dùng UserDatabase đã được application owner mở trên worker. Factory không mở
hoặc đóng database và không tạo thêm instance cho mỗi lần refresh.

```java
ProgressRepository repository = ProgressProvider.create(applicationOwnedUserDatabase);
GetProgressUseCase getProgress = repository::readProgress;
// Trên worker executor:
ProgressReadResult result = getProgress.execute();
// PROGRESS-02 chuyển result sang ViewState và render trên UI thread.
```

Contract đồng bộ, không tự tạo thread/subscription và không tự refresh. Caller
quản lý lifecycle và reload sau các thao tác ghi. Dao/Entity không được inject
vào Fragment/ViewModel. Lỗi phải hiển thị qua ViewState ERROR, không giả empty.

## 6. Lệnh xác minh

Tại root repository, JDK 17 và SDK theo project:

```bash
source /workspace/toolchains/activate.sh
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2 --console=plain
```

Lệnh source chỉ dành cho cloud đã chuẩn bị; máy cá nhân dùng JAVA_HOME/SDK của
mình và trên Windows có thể dùng .\gradlew.bat.

Kết quả cuối: **BUILD SUCCESSFUL, 185 executed / 185 passed / 0 failures, errors,
skipped**. Trong đó 27 lượt mới DATA-07; 158 lượt regression kế thừa đã đạt sau
sửa tương thích fixture DATA-03. App APK và instrumentation APK build thành công.
Không có diff schema/asset hoặc UI.

Tests mới:
- ProgressAggregationContract: 12 integration cases × API 26/35 = 24 lượt native SQLite.
- ProgressMapperTest: 3 JUnit cases cho long counts và invariant không hợp lệ.
- DB test là file UUID data07-*.db; cleanup chỉ DB test.

Report runner: app/build/reports/tests/testDebugUnitTest và
app/build/test-results/testDebugUnitTest. Tổng kết thực tế được ghi trong
[evidence/data-07/unit-results.json](evidence/data-07/unit-results.json).

Connected instrumentation chưa chạy; build AndroidTest APK không thay kết quả
trên thiết bị. Wrapper dùng cùng contract, có thể chạy trên thiết bị API26+:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.user.ProgressAggregationInstrumentedTest
```

## 7. Giới hạn nghiệm thu

Chỉ xác minh phần dữ liệu của AC-11, chưa xác minh UI/chart/lifecycle hay labels
hiển thị trên thiết bị; PROGRESS-02 và reviewer Thi nghiệm thu phần đó. Không sửa
trạng thái Excel hoặc ghi review thay thành viên. Không ghi nhận CI GitHub hoặc
connected tests thành công khi chỉ chạy local host.

Nhánh DATA-06 giữ riêng; khi ghép cần giữ transaction ghi review và queries đọc
DATA-07 trong UserDao. Không có destructive migration/reset hoặc thay đổi schema.
