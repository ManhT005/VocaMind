# VOCAB-02 — quyết định saved-only đang chờ

Ngày đối chiếu: 06/11/2026. Phụ trách Trường; cần Thành (reviewer) và Nhân (data owner) xác nhận.
Trạng thái: **PENDING / BLOCKED**, chưa có người/ngày/commit phê duyệt. Không phải quyết định nghiệp vụ mới.

Develop đã fetch: `46d6c18`. `UserDao.setSaved` chỉ UPDATE progress có sẵn.
[PROGRESS-01 handoff](progress-01-handoff.md) và [contract](plans/progress-01-contract.md)
vẫn ghi PENDING. Không có bằng chứng phê duyệt mới trong source/handoff hiện hành.

## Hai phương án cụ thể để Thành/Nhân review

Ưu tiên vẫn lưu isSaved trong UserWordProgress theo BR-02, box chỉ 0..5.
Nếu tạo row chỉ để lưu, representation đề xuất để thảo luận:

| Field | Giá trị / ý nghĩa đề xuất, CHƯA triển khai |
| --- | --- |
| vocabularyId | stableId TEXT đúng catalog |
| isSaved | true |
| box | 0; chưa có lượt review, nhưng sẽ được thống kê ở box0 theo literal FR-VM-042 |
| dueAt | Long.MAX_VALUE, sentinel không đến hạn trong ngày giờ thực; cần chốt contract rõ |
| learnedAt / lastReviewedAt | null / null; không giả lập thời điểm học |
| consecutiveMistakes / reviewCount | 0 / 0 |
| isLeech | false |

**A — giữ nguyên mọi query hiện hành.** Row trên bị loại khỏi newVocabularyIds ngay vì đã có progress
(FR-VM-017); không vào due ở thời gian thực; tổng COUNT DISTINCT và box0 đều tăng 1
(FR-VM-042), learned-today vẫn 0. Nhược điểm: từ chưa học đã Save không được planner chọn như
từ mới, kể cả sau Unsave nếu giữ progress. Không khuyến nghị chấp nhận ngầm.

**B — đề nghị làm rõ FR-VM-017 bằng quyết định/CR, rồi mới triển khai.** Dùng representation trên,
cho planner coi row learnedAt=null/reviewCount=0 là chưa bắt đầu học và vẫn vào new pool.
Due không chọn row chỉ lưu; learned-today vẫn 0. Tổng/box0 vẫn tăng 1 theo FR-VM-042,
phải được chấp nhận và diễn giải minh bạch; không tự lọc learnedAt khỏi thống kê.
Nếu muốn Save không tăng tổng/box0 thì phải có CR riêng cho FR-VM-042/representation,
không thể âm thầm đạt điều đó bằng filter. Không triển khai query/schema/filter nào của B trong nhánh này.

Với cả hai phương án: first review phải đọc row hiện tại trong transaction, giữ isSaved,
gán learnedAt khi thực sự review lần đầu, cập nhật dueAt theo scheduler, tăng counters và ghi lịch sử.
Conditional insert khi first save không được ghi đè row first review vừa tạo. Unsave không xóa progress;
missing-row unsave có thể no-op false chỉ sau khi contract được phê duyệt.
Phải bổ sung test first-save/first-review và new/due/stats theo quyết định cuối.

Khuyến nghị trình B để chốt semantics và ảnh hưởng FR-VM-017 trước; chưa có phê duyệt để thực hiện.
Không thêm bảng bookmark, box ngoài miền hoặc learnedAt giả.

## Hành vi bản triển khai độc lập

`RoomVocabularyRepository.setSaved` kiểm tra tồn tại/membership qua CatalogRepository,
sau đó transaction UPDATE duy nhất isSaved. Affected count=1 mới success.
Affected count=0 trả SAVED_POLICY_REQUIRED cho cả true/false, không insert và không báo no-op đã được duyệt.
UI vẫn có nút Lưu cho từ chưa học; click hiển thị blocker và Retry đọc lại state.
Đây là phần còn thiếu của tính năng, không phải giới hạn Save được chọn để đóng task.

Các row progress đã tồn tại (kể cả legacy learnedAt=null) chỉ được đổi flag.
Không sửa new/due/stats, catalog, schema hoặc review implementation.

## Thông tin cần bổ sung khi có quyết định

- Thành: người xác nhận, ngày, link/commit/contract và phạm vi phê duyệt.
- Nhân: representation/sentinel, new pool, due pool, FR-VM-042 và first review.
- Quyết định/CR cho FR-VM-017 và/hoặc FR-VM-042 nếu cần; test expectations cụ thể.

Cho đến khi có thông tin trên: Save từ chưa học, T05/T13 và AC-04 toàn phần **BLOCKED**.
