# DATA-06 — Atomic review transaction

## 1. Phân công và căn cứ

- Tuần 2, D7–D8; 6 giờ; owner Nhân, reviewer Thi; Critical.
- Phụ thuộc: DATA-02 và LEARN-01, theo `VocaMind — Phân công.xlsx` vừa cung cấp.
- SRS: `VocaMind_SRS_Baseline_v1.1.docx`, FR-VM-024, BR-10, AC-08, NFR-VM-07.
- Nhánh: `feature/data-06-atomic-review-transaction`, nền `origin/develop` tại `c2ea69e`.
- Quy ước dự án: Java 17, Room, Clean MVVM; tài liệu lowercase kebab-case trong docs/plans và docs.

## 2. Phạm vi và thiết kế

Luồng: caller worker → RecordReviewUseCase → đọc snapshot tiến độ → LeitnerScheduler
→ một repository commit → DAO @Transaction kiểm tra snapshot, insert ReviewEvent,
upsert UserWordProgress → chỉ trả thành công sau khi commit.

- Domain model/contract/use case không import Android hoặc Room.
- Event và progress cùng userdata.db; không transaction xuyên catalog.
- Lượt đầu: boxBefore=0; learnedAt=reviewedAt; reviewCount=1.
- Lượt tiếp: giữ learnedAt và isSaved; tăng reviewCount; cập nhật lastReviewedAt,
  box/dueAt/mistakes/leech đúng kết quả scheduler hiện có.
- Clock và ZoneId được inject; không đọc thời gian toàn cục rải rác, không UI/DAO
  gọi scheduler. Chỉ dùng một timestamp cho lịch, event và progress của một lượt.
- Snapshot được kiểm tra lại trong transaction. Nếu đã đổi (kể cả saved flag),
  trả lỗi conflict; không ghi một phần, không tự retry stale rating.
- Lỗi DB giữ nguyên cause, trả lỗi storage; caller giữ nguyên thẻ và cho retry
  có chủ đích. Không reset DB, không destructive fallback, không tăng schema version.
- Caller phải cung cấp stableId của từ hợp lệ trong catalog; DATA-06 không quyết định
  gói active, session queue hay quota và không sửa catalog.

Không mở rộng sang FLASH-02 UI/session next/relearn hoặc FLASH-03 debounce 600 ms.
AC-08 chỉ được chứng minh phần transaction/rollback trong task này, không tuyên bố
đã đạt toàn bộ anti-double-tap. Optimistic conflict không thay idempotency per-card.

## 3. Kế hoạch 6 giờ

| Bước | Công việc | Giờ |
| --- | --- | ---: |
| 1 | Đọc SRS/Excel; kiểm tra DATA-02, scheduler và baseline build | 0,5 |
| 2 | Immutable ReviewProgress, repository/error contract, RecordReviewUseCase | 1 |
| 3 | Mapper + Room repository + @Transaction snapshot check/write; factory | 1,5 |
| 4 | Test pure domain và file-backed Room: happy path, forced DB failures, stale writes | 2 |
| 5 | Build APK/test APK, regression, evidence và bàn giao Thi | 1 |

## 4. Kiểm thử bắt buộc

| Ca | Bằng chứng cần có |
| --- | --- |
| T01 | Lượt mới: đúng 1 event + 1 progress, rating/box/time/due khớp |
| T02 | Lượt cũ cho REMEMBERED/HARD/FORGOT: đúng scheduler, giữ saved/learnedAt |
| T03 | SQL trigger làm insert event lỗi: progress cũ không đổi |
| T04 | SQL trigger làm ghi progress lỗi sau insert event: event bị rollback |
| T05 | Cả insert progress mới và update progress cũ bị lỗi: không trạng thái nửa vời |
| T06 | Snapshot stale/new-word bị người khác ghi trước: conflict, không thêm event |
| T07 | Hai instance đọc cùng trạng thái: chỉ một commit thành công |
| T08 | Bỏ fault rồi retry có chủ đích: commit đúng một lượt; reopen giữ kết quả |
| T09 | Storage lỗi: use case không trả success, không đổi snapshot ban đầu |
| T10 | Clock cố định, DST/zone theo scheduler; input invalid/overflow không ghi |

Test DB có tên riêng và chỉ cleanup DB test. Unit test dùng fake repository;
Room integration dùng SQLite native trên API 26/35 và cùng contract có wrapper
instrumentation. Báo riêng host tests, APK build, và connected instrumentation.

## 5. Rủi ro/phụ thuộc cần ghi rõ

Scheduler LEARN-01 hiện tính dueAt vào đầu ngày địa phương, trong khi FR-VM-029/030
của SRS dùng diễn đạt “cùng giờ địa phương”. DATA-06 giữ scheduler đã tích hợp,
không âm thầm sửa quy tắc LEARN-01. Reviewer cần chốt khác biệt này riêng;
DATA-06 kiểm chứng lưu chính xác kết quả scheduler, không xác nhận AC-07 đầy đủ.

DATA-03/04 chưa được tích hợp toàn bộ vào nền chọn; chỉ bổ sung boundary cần cho
DATA-06, giữ các API cũ tương thích. Khi tích hợp các task đó cần chạy regression
và xử lý thay đổi chung UserDao bằng merge, không ghi đè file.

## 6. Definition of Done

- [x] Use case dùng scheduler thật và chỉ một repository commit cho mỗi execute.
- [x] Event/progress được ghi trong cùng Room @Transaction.
- [x] Happy path, lỗi bước 1/2, stale và reopen được kiểm chứng trên DB thực.
- [x] Không thay schema, catalog, scheduler hoặc UI ngoài scope.
- [x] Domain không phụ thuộc Room; DI và hướng dẫn worker thread rõ ràng.
- [x] Build/test/report có kết quả thực; connected test chưa chạy phải ghi rõ.
- [ ] Thi review; không tự đánh dấu Excel/SRS accepted hoặc hoàn thành toàn AC-08.

## 7. Trạng thái thực tế

Đã triển khai trên nhánh feature/data-06-atomic-review-transaction. Toàn bộ 151 test đạt, gồm 26 lượt mới
của DATA-06; APK ứng dụng và instrumentation APK build thành công. Xem
[báo cáo xác minh](../data-06-validation.md) và [kết quả máy đọc được](../evidence/data-06/unit-results.json).
Connected instrumentation, review của Thi và xác nhận khác biệt giờ hạn ôn
LEARN-01/SRS còn pending.
