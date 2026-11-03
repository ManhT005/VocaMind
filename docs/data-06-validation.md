# DATA-06 — Atomic review transaction

## 1. Phạm vi, nguồn và nhánh

| Hạng mục | Giá trị |
| --- | --- |
| Phân công | D7–D8, 6 giờ, Nhân thực hiện / Thi review, Critical |
| Căn cứ | VocaMind_SRS_Baseline_v1.1.docx và VocaMind — Phân công.xlsx đính kèm phiên này |
| Yêu cầu | FR-VM-024, BR-10, NFR-VM-07; phần transaction của AC-08 |
| Phụ thuộc chính thức | DATA-02 và LEARN-01 |
| Nhánh local | feature/data-06-atomic-review-transaction |
| Nền | origin/develop tại c2ea69e, có sẵn UserDatabase và LeitnerScheduler |
| Kế hoạch | [data-06-atomic-review-transaction.md](plans/data-06-atomic-review-transaction.md) |

Không merge/push/reset nhánh khác. Phần nguồn trong tài liệu được dùng làm yêu
cầu tham chiếu, không coi là lệnh chạy hoặc quyền công bố thay reviewer.

## 2. Cấu trúc và hướng phụ thuộc

```text
app/src/main/java/com/vocamind/app/
├── domain/learning/
│   ├── ReviewProgress.java          # Snapshot immutable, không Room/Android
│   ├── ReviewRepository.java        # Contract đọc và commit
│   ├── ReviewWriteException.java    # CONFLICT / STORAGE, giữ cause
│   └── RecordReviewUseCase.java     # Clock + scheduler → một commit
├── data/user/
│   ├── UserDao.java                 # Thêm @Transaction recordReviewIfUnchanged
│   ├── ReviewMapper.java            # Snapshot ↔ Entity nội bộ
│   ├── ReviewStateChangedException.java
│   └── RoomReviewRepository.java
└── core/ReviewProvider.java         # Ghép dependency, dùng UserDatabase có sẵn
```

Không thay Entity, schema/version, catalog asset, UI hoặc thuật toán scheduler.
Các API DATA-02 cũ được giữ để tương thích. Consumer mới của DATA-06 dùng
RecordReviewUseCase, không gọi DAO hoặc tách thao tác insert/update từ UI.

## 3. Luồng ghi

1. Caller cung cấp stableId canonical 16 lowercase hex của từ hợp lệ trong catalog.
2. Use case đọc ReviewProgress qua domain repository, nhận Optional.empty nếu từ mới.
3. Lấy Clock.millis đúng một lần; gọi scheduler thật với state, rating, time và ZoneId.
4. Tạo snapshot mới: giữ learnedAt/isSaved hiện có, tăng reviewCount chính xác,
   cập nhật lastReviewedAt/box/dueAt/mistakes/leech. Từ chưa học có learnedAt bằng
   thời điểm đánh giá đầu tiên; timestamp null của saved-only row được xử lý tương tự.
5. Gọi đúng một repository commit. DAO kiểm tra snapshot còn khớp và ghi event +
   progress trong cùng @Transaction. Nếu state đổi, kể cả isSaved, chặn stale write.
6. Chỉ trả ScheduleResult sau khi repository commit thành công. Lỗi không trở thành
   kết quả thành công hoặc empty; không tự retry, xóa DB hay thay đổi session queue.

Hai lần execute liên tiếp vẫn có thể là hai đánh giá hợp lệ. Conflict check chỉ
chặn ghi cùng snapshot, không phải guard per-card/600 ms. FLASH-03 chịu trách nhiệm
khóa thẻ/debounce; FLASH-02 chịu next/relearn queue. Chưa công bố toàn AC-08 đạt.

Factory không sở hữu vòng đời UserDatabase: application/data owner mở một instance
trên worker, giữ và đóng nó theo scope. Không tạo mới DB cho mỗi click.

Ví dụ ghép dependency, thực hiện trên worker:

```java
RecordReviewUseCase recordReview = ReviewProvider.create(
        applicationOwnedUserDatabase, Clock.systemUTC(), ZoneId.systemDefault());
ScheduleResult committed = recordReview.execute(stableId, Rating.REMEMBERED);
// Chỉ cập nhật thành công/đi tiếp sau khi execute trả về.
```

Caller xử lý ReviewWriteException.Reason.CONFLICT bằng reload/giữ trạng thái UI
để quyết định lại; STORAGE dùng safe error và retry có chủ đích. Không âm thầm
retry rating cũ trên dữ liệu mới. Việc xác nhận từ thuộc catalog/gói active và
session authorization thuộc tầng use case/session tích hợp phía trên.

## 4. Kiểm thử mới

- RecordReviewUseCaseTest: 6 JUnit test thuần Java, fake repository, fixed clock.
- AtomicReviewContract: 10 test dùng file-backed Room/SQLite thật; wrapper host chạy
  native SQLite API 26 và 35 (20 lượt), wrapper AndroidJUnit4 để chạy trên thiết bị.
- SQL triggers RAISE(ABORT) chỉ nằm trong DB test, không có fault hook ở code sản phẩm.
- Test dùng DB tên UUID data06-*.db, cleanup không tác động userdata.db/catalog.db.

| Trường hợp | Bằng chứng |
| --- | --- |
| Từ mới | Đúng một event/progress, box/time/rating/due khớp |
| Từ cũ với cả ba rating | Box/due/mistakes/leech khớp scheduler; giữ saved/learnedAt |
| Insert event thất bại | Snapshot tiến độ không đổi |
| Insert progress mới lỗi sau insert event | Event rollback, không có progress mới |
| Update progress cũ lỗi sau insert event | Event rollback, toàn snapshot cũ giữ nguyên |
| Bỏ trigger lỗi, retry và reopen | Đúng một review đã commit, dữ liệu giữ bền |
| Saved flag hoặc first review đổi trước commit | CONFLICT, không ghi đè hoặc thêm event |
| Hai DB instance đọc cùng snapshot | Một commit thành công, một conflict, reviewCount chỉ tăng 1 |
| DB đóng | STORAGE, có cause; không trả success |
| Invalid ID/rating và tràn reviewCount | Không gọi commit |

Lượt test đầu tiên phát hiện SQLITE_BUSY khi setup mở thêm UserDatabase sau khi
đã seed, lỗi ở callback onOpen của DATA-02 trước khi tới luồng review. Setup đã
khởi tạo cả hai instance trước seed. Ca cạnh tranh vẫn dùng hai instance độc lập,
barrier sau hai lượt đọc snapshot, và yêu cầu một success + một conflict; không
bỏ/skip assertion transaction. Đây chưa phải kiểm chứng mọi kiểu mở đa instance
của UserDatabase; code tích hợp được hướng dẫn dùng một application-owned instance.

## 5. Lệnh và kết quả thực tế

Baseline develop trước sửa: testDebugUnitTest + assembleDebug thành công.
Sau sửa, chạy tại root repository, JDK 17 và Android SDK của môi trường:

```bash
source /workspace/toolchains/activate.sh
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2 --console=plain
```

**BUILD SUCCESSFUL. 151 executed, 151 passed, 0 failures/errors/skipped.**
Trong đó 26 lượt mới DATA-06 = 6 domain + 10 integration × API 26/35;
125 lượt còn lại là regression kế thừa. App APK và instrumentation APK build thành công.
Kết quả từng suite và SHA-256 các file triển khai có trong
[evidence/data-06/unit-results.json](evidence/data-06/unit-results.json).
Report đầy đủ được runner sinh trong app/build/reports/tests/testDebugUnitTest;
XML hiện tại nằm ở app/build/test-results/testDebugUnitTest.

Connected instrumentation **chưa chạy**. Không có /dev/kvm ở môi trường này;
không xem build test APK hay host SQLite là evidence chạy trên thiết bị thật.
Để chạy trên Android Studio/emulator/runner có thiết bị:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.user.AtomicReviewInstrumentedTest
```

Trên Windows dùng .\gradlew.bat thay ./gradlew. API tối thiểu là 26.

## 6. Giới hạn và bàn giao

- Thi chưa review; không tự sửa trạng thái Excel hoặc xác nhận nghiệm thu thay nhóm.
- Nhánh bàn giao: feature/data-06-atomic-review-transaction; chưa merge vào develop/main.
- DATA-03/04 cần tiếp tục tích hợp contract/mappers; không thay UserDao bằng file
  từ nhánh khác, mà merge và giữ cả các API hiện có cùng transaction DATA-06.
- UI/session queue, 600 ms debounce, idempotency per-card, crash/reboot và migration
  release không được công bố đã nghiệm thu bằng các test này.
- LEARN-01 hiện tính dueAt bằng đầu ngày địa phương. FR-VM-029/030 của tài liệu
  đính kèm diễn đạt cùng giờ địa phương. DATA-06 không đổi luật scheduler ngoài
  scope; xác minh lưu đúng output hiện có, không xác nhận toàn AC-07 hoặc chốt lại
  khác biệt này. Cần reviewer/owner LEARN-01 xác nhận riêng trước nghiệm thu sản phẩm.
