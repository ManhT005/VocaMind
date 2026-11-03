# Kế hoạch DATA-02 — UserDatabase

## 1. Thông tin task

| Hạng mục | Nội dung |
|---|---|
| Dự án | VocaMind — Android, Java, Room, Clean MVVM |
| Task | DATA-02 — UserDatabase |
| Người thực hiện / Reviewer | Nhân / Thi |
| Lịch / Ước lượng | Tuần 1, D2–D3 / 7 giờ |
| Ưu tiên | Critical |
| Phụ thuộc chính thức | ARC-01 |
| Mục tiêu | Tạo userdata.db đọc/ghi, lưu bền dữ liệu cá nhân, mặc định đúng SRS, không destructive migration |

Đây là **kế hoạch**, chưa phải bằng chứng DATA-02 đã được triển khai hoặc kiểm thử. Các lệnh và tên API bên dưới là đề xuất, cần điều chỉnh theo code thực tế.

## 2. Căn cứ và nguyên tắc áp dụng

- `VocaMind_SRS_Baseline_v1.1.docx`: §7.1–7.4; FR-VM-002..004/007/009/010/024/038/041..043; AC-13; ADR-01; NFR-VM-06/07; DEC-04/06/13/15.
- `Đặc Tả SRS Cho VocaMind.docx`: tham khảo UC-01, luồng học, Quiz, tiến độ và các ca kiểm thử dữ liệu. Nếu mô tả cũ khác quyết định baseline v1.1, áp dụng baseline v1.1; ví dụ nhắc học mặc định phải OFF.
- `VocaMind — Phân công.xlsx`, sheet Phân công, dòng DATA-02 và dòng người dùng vừa xác nhận: phạm vi, thời gian, trách nhiệm, phụ thuộc và tiêu chí hoàn thành.

**Phân biệt mã:** task DATA-02 trong bảng là UserDatabase; SRS §7.3 DATA-02 là yêu cầu chuẩn hóa học liệu. Không dùng hai mã này thay thế nhau.

## 3. Phạm vi

### Phải hoàn thành

1. Năm Entity: UserPack, UserWordProgress, ReviewEvent, QuizAttempt, UserPreference.
2. UserDatabase vật lý riêng, tên `userdata.db`, đọc/ghi bằng Room.
3. Khởi tạo một bản ghi UserPreference mặc định, không ghi đè thiết lập đã có.
4. DAO/API nội bộ tối thiểu cho repository sử dụng và kiểm thử lưu bền dữ liệu.
5. Schema JSON xuất từ Room compiler; chính sách migration không phá dữ liệu.
6. Ranh giới transaction để chuẩn bị ghi ReviewEvent + UserWordProgress nguyên tử.
7. Kiểm thử mặc định, reopen, lỗi migration và bảo toàn dữ liệu; ghi rõ phần chưa chạy trên thiết bị.

### Ranh giới với task khác

| Task | Nội dung giữ ở task đó |
|---|---|
| DATA-01 | CatalogDatabase, nội dung từ vựng và nguồn, asset catalog |
| DATA-03 | DAO foundation đầy đủ: due/new, review, progress, quiz history và query theo use case |
| DATA-04 | Repository contracts/implementations tổng thể, phối hợp catalog và userdata |
| DATA-06 | Hoàn thiện transaction ghi đánh giá theo luồng học và kiểm thử lỗi |
| DATA-07 | Truy vấn tổng hợp tiến độ theo phân công |
| DATA-08 | Kiểm thử nâng cấp catalog staging/rollback và migration bảo toàn userdata |
| Feature/domain tương ứng | UI, lập lịch Leitner, quyết định khi nào lưu QuizAttempt, thống kê và nhắc học |

DATA-02 phải cung cấp nền tảng dùng được; không cần triển khai lại toàn bộ các task trên. Nếu cần DAO tối thiểu, thống nhất với Thi để DATA-03 tiếp tục trực tiếp, tránh hai implementation cạnh tranh.

## 4. Kiểm tra trước khi code — 0,5 giờ

- Xác minh ARC-01 có trong nhánh nền: Java 17, Room, cấu trúc data/domain, factory và build hoạt động.
- Kiểm tra Git status; giữ nguyên thay đổi của người khác. Không merge DATA-01 vào develop thay nhóm.
- Chọn nhánh đề xuất `feature/data-02-user-database` từ nhánh tích hợp nhóm đang dùng. Nếu DATA-01 chưa merge và cần dùng code của nó, ghi rõ nhánh chồng lên DATA-01 và base PR; DATA-02 không có phụ thuộc chính thức vào DATA-01.
- Kiểm tra implementation DATA-01 hiện có để đồng bộ package, cách tạo dependency, lỗi đọc DB và quy ước test; không sửa catalog ngoài nhu cầu được thống nhất.
- Chốt với Đạo/Thành cách biểu diễn rating, thời gian và trạng thái lưu từ chưa học trước khi đóng schema.

**Đầu ra:** nhánh làm việc, baseline build và ghi chú quyết định schema.

## 5. Thiết kế schema — 1,5 giờ

Các thuộc tính lõi dưới đây lấy từ SRS §7.2. Kiểu Java, nullable, index và cách cấp ID là quyết định triển khai cần ghi rõ khi review, không tự coi là yêu cầu đã chốt trong SRS.

| Entity | Thuộc tính lõi | Lưu ý thiết kế |
|---|---|---|
| UserPack | packId PK; activatedAt; isActive | packId là ID logic của gói catalog; isActive ∈ {0,1}; activatedAt epoch milliseconds |
| UserWordProgress | vocabularyId TEXT PK; box; dueAt; learnedAt; lastReviewedAt; consecutiveMistakes; isLeech; reviewCount; isSaved | vocabularyId chính là stableId, tuyệt đối không lưu FTS rowid; box 0..5; bộ đếm không âm |
| ReviewEvent | id; vocabularyId; rating; reviewedAt; boxBefore; boxAfter; nextDueAt | Rating thống nhất với domain REMEMBERED/HARD/FORGOT; box 0..5; thời gian epoch milliseconds |
| QuizAttempt | id; completedAt; questionCount; correctCount; scorePercent | Lưu lượt hoàn tất; correctCount không vượt questionCount; chốt kiểu/độ chính xác score với domain |
| UserPreference | id=1; dailyNewWords; dailyGoalMinutes; reminderEnabled; reminderHour | Một bản ghi cấu hình; khởi tạo mặc định 5, 10, false, 20 |

### Quy tắc bắt buộc

- `userdata.db` và `catalog.db` là **hai tệp Room riêng** theo ADR-01.
- packId/vocabularyId tham chiếu catalog theo logic; không tạo foreign key xuyên DB.
- Không dùng rowid của catalog làm khóa tiến độ; ID không đổi khi nghĩa/ví dụ thay đổi.
- Không seed gói active hoặc tiến độ học giả. Khi chưa chọn gói, UserPack có thể rỗng.
- Không ghi isSaved hoặc tiến độ vào VocabularyEntry của catalog.
- dueAt được lưu thành epoch milliseconds; quy tắc ngày lịch địa phương thuộc scheduler/domain, không tính lại lịch trong Entity/DAO.
- Không hard-code hoặc kế thừa reminderEnabled=1 từ mô tả cũ.
- Chốt rõ trạng thái “đã lưu nhưng chưa học”: learnedAt/lastReviewedAt có null không, khi nào tạo progress và cách thống kê. Không tự chọn box rồi làm lệch tổng tiến độ của FR-VM-041/042; thống nhất với contract progress và use case lưu từ.
- Index đề xuất cho dueAt, lịch sử theo vocabularyId/reviewedAt và completedAt phải phục vụ query dự kiến; chỉ thêm sau khi thống nhất DATA-03, không lập index mọi cột.
- Các invariant nghiệp vụ cần validation/test; annotation Java hoặc NOT NULL không tự bảo đảm box/rating/bộ đếm hợp lệ.

## 6. UserDatabase và khởi tạo mặc định — 1 giờ

- Tạo `UserDatabase extends RoomDatabase`, database version đầu tiên là 1 nếu chưa có schema userdata phát hành.
- Bật exportSchema và giữ schema JSON trong Git theo quy ước dự án.
- Factory dùng application context, tên `userdata.db`, không `createFromAsset` với catalog và không trộn bảng catalog.
- Không bật allowMainThreadQueries trong code sản phẩm; thực hiện I/O trên worker executor.
- Không gọi bất kỳ destructive fallback nào hoặc xóa DB khi schema/open thất bại.
- Khởi tạo preference id=1 bằng thao tác insert-if-absent sau khi DB mở, trong callback/initializer đã thống nhất. Không dùng callback truy cập đệ quy vào chính singleton đang khởi tạo.
- Chỉ báo data layer sẵn sàng sau khi bước đảm bảo preference hoàn tất, tránh race khi màn Today đọc cấu hình.
- Mặc định: dailyNewWords=5, dailyGoalMinutes=10, reminderEnabled=false, reminderHour=20.
- Khi mở lại, giữ giá trị người dùng đã chỉnh; initializer phải idempotent và an toàn khi nhiều consumer cùng yêu cầu.
- Lỗi DB được trả theo error convention cho tầng trên xử lý an toàn; không bắt lỗi rồi trả một DB trắng như thể thành công.

**Đầu ra:** mở được userdata.db, có đúng một preference, mở lần hai không reset dữ liệu.

## 7. API cho repository và transaction boundary — 1 giờ

Các tên sau chỉ là gợi ý API nội bộ, không phải yêu cầu tên hàm từ SRS:

- Đọc/ghi UserPreference; bảo đảm giá trị hiện có không bị initializer ghi đè.
- Đọc/upsert UserPack theo packId, giữ dữ liệu theo semantics activate/pause đã thống nhất.
- Đọc/ghi UserWordProgress theo stableId mà không reset các trường không liên quan. Toggle isSaved không được làm mất box, dueAt hoặc lịch sử.
- Insert/read ReviewEvent và QuizAttempt để test round-trip; chưa xây UI hay toàn bộ query lịch sử.
- Không expose Entity/DAO cho Fragment/ViewModel; tầng trên dùng domain contract qua repository/use case.
- Chuẩn bị DAO transaction trong **cùng UserDatabase** cho cặp ReviewEvent + UserWordProgress; nếu triển khai primitive này ở DATA-02, test thành công/rollback ngay. Luồng rating, scheduler và idempotency hoàn chỉnh vẫn thuộc các task học/DATA-06.
- Không cố transaction xuyên catalog và userdata; catalog chỉ được đọc riêng.
- Việc lưu QuizAttempt chỉ sau hoàn tất được điều phối bởi use case/domain, không cho rằng bảng DB tự biết một phiên đã xong.

**Đầu ra:** data primitive có thể dùng từ repository, xác định rõ entry point transaction và phần bàn giao DATA-03/06.

## 8. Chính sách migration — 1 giờ

### Nếu đây là userdata schema v1 đầu tiên

- Xuất, kiểm tra và commit schema v1; xác nhận chưa có v1 phát hành khác bị ghi đè.
- Không giả vờ đã có migration production v1→v2; chưa có thay đổi schema thì chưa có migration thật cần đăng ký.
- Để kiểm tra cơ chế non-destructive, dùng **test fixture/schema evolution riêng cho kiểm thử**, tạo DB từ schema v1, seed dữ liệu rồi chạy bước migration đã định nghĩa và so sánh trước–sau. Ghi rõ đây là thử nghiệm, không tăng production DB version hoặc thêm bảng sản phẩm chỉ để test pass.
- Nếu chưa thể có một migration đại diện hợp lệ trong phạm vi 7 giờ, vẫn kiểm tra version mismatch bị từ chối mà DB cũ không bị xóa; ghi phần migration preservation còn chờ, không đánh dấu đã đạt toàn bộ yêu cầu migration test.

### Nếu đã có userdata schema cũ thực tế

- Thu thập schema và version đã dùng; viết Migration đúng cặp version cần hỗ trợ.
- Chạy MigrationTestHelper trên SQLite thật: dữ liệu trước/sau, schema và foreign keys đều hợp lệ.
- Không xóa progress/review/quiz/preferences để đổi schema. Không dùng REPLACE theo cách làm mất cột hoặc quan hệ ngoài ý muốn.

Cả hai trường hợp đều phải thử đường dẫn lỗi: thiếu migration hoặc lỗi mở DB không được kích hoạt xóa userdata. Nâng cấp catalog v1→v2/rollback là bài kiểm tra khác ở DATA-08, không thay bằng một bài migration userdata đơn lẻ rồi công bố AC-13 đầy đủ.

## 9. Kiểm thử và bàn giao — 2 giờ

Các mã dưới đây là mã test đề xuất cho DATA-02, không phải mã có sẵn trong SRS.

| Test | Cách kiểm tra / Kết quả mong đợi | Truy vết |
|---|---|---|
| DATA02-T01 | Lần mở sạch tạo userdata.db riêng, đủ 5 bảng, Room chấp nhận schema | FR-VM-002; ADR-01 |
| DATA02-T02 | Preference có đúng id=1, 5 từ/ngày, 10 phút, reminder OFF, giờ 20 | FR-VM-004; DEC-06 |
| DATA02-T03 | Chạy initializer nhiều lần, kể cả đồng thời: một preference; sửa cấu hình rồi reopen không mất giá trị đã sửa | FR-VM-003/004 |
| DATA02-T04 | Lưu pack active/pause; đóng hoàn toàn rồi mở cùng tệp: giữ trạng thái; DB mới không tự active gói | FR-VM-007/010; DEC-02 |
| DATA02-T05 | Ghi progress, đổi isSaved: các trường box/dueAt/reviewCount còn nguyên; không sửa catalog | FR-VM-009/010 |
| DATA02-T06 | Round-trip ReviewEvent/QuizAttempt: giữ ID, stableId, rating, timestamp, số liệu đã ghi | FR-VM-024/038/043 |
| DATA02-T07 | Khi primitive transaction được triển khai: thành công ghi đủ hai bảng; inject lỗi giữa hai bước thì rollback cả hai | FR-VM-024; NFR-VM-07 |
| DATA02-T08 | Seed đủ 5 bảng, snapshot, close/reopen rồi đối chiếu từng bản ghi và trường, không chỉ COUNT | FR-VM-003/010; phần persistence của AC-13 |
| DATA02-T09 | Migration thật hoặc test-only evolution đã khai báo: dữ liệu trước/sau nguyên vẹn; schema hợp lệ | NFR-VM-06; yêu cầu migration của Excel |
| DATA02-T10 | Thiếu migration/hash không hợp lệ: lỗi được báo; tệp/dữ liệu cũ không bị tự xóa hoặc tạo trắng | DEC-13; AC-13 |
| DATA02-T11 | Thiết bị/emulator: lưu dữ liệu, force-stop/mở lại, sau đó reboot/mở lại; preference/pack/progress/history còn đúng | FR-VM-010; AC-13 |

### Cách chạy

- Unit test cho mapper/validation; integration test dùng Room trên **tệp SQLite**, không chỉ mock hoặc in-memory DB để chứng minh persistence.
- Có thể dùng Robolectric native SQLite trên API 26 và API hiện hành để kiểm tra nhanh, nhưng không xem đó là bằng chứng reboot thiết bị.
- MigrationTestHelper cần test dependencies, schemas trong test assets và cấu hình runner phù hợp; kiểm tra theo phiên bản Room đang dùng.
- Mỗi test dùng tên DB riêng; cleanup chỉ DB test, không đụng catalog.db/userdata.db đang chứa dữ liệu thật.

Từ root dự án, lệnh dự kiến với module app:

```bash
# JDK/SDK theo docs dự án. Riêng cloud đã chuẩn bị trong phiên trước:
# source /workspace/toolchains/activate.sh
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest

# Cần emulator/thiết bị Android kết nối; lọc lớp test DATA-02 khi đã có tên cụ thể.
bash ./gradlew :app:connectedDebugAndroidTest
```

Máy cloud ở lần kiểm tra trước chưa chạy được connected tests: không có /dev/kvm và adb bị chặn tạo /home/agent/.android. Kiểm tra lại khả năng thực tế khi triển khai; dùng Android Studio/runner có thiết bị nếu vẫn bị chặn. Không gọi close/reopen là reboot và không ghi test chưa chạy thành pass.

## 10. Checklist hoàn thành

- [ ] Nhánh nền ARC-01 và baseline build đã kiểm tra.
- [ ] Đủ 5 Entity, schema/version được review; stableId TEXT không thay bằng rowid.
- [ ] userdata.db tách riêng; tham chiếu catalog bằng ID logic.
- [ ] UserPreference đúng mặc định, idempotent, không ghi đè dữ liệu hiện có.
- [ ] API nội bộ đủ để repository sử dụng; UI không nhận DAO/Entity.
- [ ] Không main-thread query và không destructive fallback/xóa DB để phục hồi.
- [ ] Transaction boundary rõ; phần đã triển khai có test rollback.
- [ ] Round-trip và file reopen giữ dữ liệu; schema export được commit.
- [ ] Migration test có bằng chứng hoặc ghi chính xác phần còn thiếu, chưa đóng DoD nếu yêu cầu bắt buộc chưa chứng minh.
- [ ] Force-stop/reboot trên thiết bị có evidence hoặc ghi rõ chưa chạy; không công bố AC-13 đầy đủ.
- [ ] Build/test có số executed/pass/fail/skip và report thực tế.
- [ ] Thi review; bàn giao DAO/schema cho DATA-03/06/07/08.

## 11. Phân bổ D2–D3 và kết quả bàn giao

| Giai đoạn | Nội dung | Giờ |
|---|---|---:|
| D2 | Kiểm tra nền và quyết định schema | 0,5 |
| D2 | Entity, ràng buộc và schema export | 1,5 |
| D2 | Database/factory/initializer preference | 1 |
| D3 | API nội bộ và transaction boundary | 1 |
| D3 | Migration policy và setup kiểm thử migration | 1 |
| D3 | Test, báo cáo và chuẩn bị review | 2 |
| Tổng | | **7** |

Nếu lỗi nền, migration hoặc thiết bị làm vượt 7 giờ, báo Thi kèm lỗi và phần chưa chứng minh; không cắt kiểm thử rồi đánh dấu hoàn thành.

Bàn giao gồm code, schema JSON, fixture/test, hướng dẫn chạy, báo cáo evidence và PR ghi rõ: đã đạt gì, migration nào thực sự được thử, reopen hay reboot, phần nào chờ task sau. DATA-02 chuẩn bị nền lưu trữ, **không tự chứng minh hoàn thành toàn bộ hành vi FR-VM-024/038/041..043 hoặc AC-13 của sản phẩm**.
