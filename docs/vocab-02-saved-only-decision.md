# VOCAB-02 — contract saved-only due-now

Ngày chốt triển khai: 06/11/2026. Nguồn xác nhận: chỉ dẫn trực tiếp của người dùng trong phiên
VOCAB-02 sau review 2482a4e. Không ghi thay tên/chữ ký Thành hoặc Nhân.
Đây là quyết định kỹ thuật cho phần SRS chưa quy định giá trị khởi tạo saved-only,
**không phải trích dẫn quy định khởi tạo có sẵn trong SRS**. Không sửa SRS hoặc phân công.
Code triển khai: `016373c37fc0f39d35f9ae3f9ea010be46dbda59`.

## Representation được thực hiện

| Field | First-save khi chưa có progress |
| --- | --- |
| vocabularyId | stableId TEXT hợp lệ, đúng membership pack của route |
| isSaved | true |
| box | 0 |
| dueAt | thời điểm Save, epoch milliseconds từ clock trên worker |
| learnedAt / lastReviewedAt | null / null |
| reviewCount / consecutiveMistakes | 0 / 0 |
| isLeech | false |

Không dùng Long.MAX_VALUE hoặc timestamps học giả. Không tạo ReviewEvent khi Save.

## Hệ quả baseline được chấp nhận

- FR-VM-042: thêm một row progress làm tổng COUNT DISTINCT và box0 tăng đúng 1.
  Không thêm filter learnedAt vào thống kê. Box0 của tổng thống kê có thể gồm từ chỉ lưu,
  chưa có review; đây là hệ quả representation đã được chấp nhận, không chứng minh từ đã thuộc.
- FR-VM-017: từ đã có progress bị loại khỏi new pool, kể cả sau Unsave.
- FR-VM-016/020: dueAt=Save time nên từ có thể được lấy qua due pool khi đến hạn và thuộc
  ít nhất một pack active. Save không tự activate pack. Paused/inactive không làm phát sinh due eligibility.
- Pack learnedCount và learned-today hiện dựa trên learnedAt nên chỉ Save vẫn bằng 0.
  Không đồng nhất tổng progress FR-VM-042 với số từ đã có lượt học.
- Unsave chỉ đổi isSaved=false; giữ row, dueAt, box và toàn bộ trường/lịch sử khác.
- First review thực sự mới gán learnedAt/lastReviewedAt, tăng counters và ghi ReviewEvent,
  cập nhật lịch theo scheduler; isSaved được giữ từ row hiện hành trong transaction review.

Không hứa Save hoàn toàn không ảnh hưởng planner/thống kê. Các phương án PENDING cũ dùng
sentinel hoặc đổi filter đã được thay bằng contract này; không áp dụng chúng.

## Transaction và idempotency

RoomVocabularyRepository kiểm tra catalog/membership trước ghi. Toàn bộ thao tác userdata trong
một transaction: targeted UPDATE isSaved; nếu 0 row và target=true thì INSERT OR IGNORE row trên.
Nếu insert bị bỏ qua, phải targeted UPDATE xác nhận được đúng một row; nếu không thì lỗi và rollback,
không coi UPDATE 0 là success. Không upsert bản sao progress đã đọc trước đó.

Target=false và row chưa tồn tại: đọc xác nhận absence trong transaction, trả confirmed false,
không tạo row. Target=false với row có sẵn chỉ UPDATE flag, không xóa.
Save lặp không reset dueAt/timestamps/counters và không tạo trùng. Clock chỉ đọc khi cần tạo row.
First-review và first-save trên các kết nối DB được SQLite transaction tuần tự hóa;
review đọc row hiện hành và insert-if-absent không ghi đè kết quả review.
Retry UI chỉ reconcile persisted state, không tự replay/đảo trạng thái.

## Kiểm chứng và nghiệm thu

[Validation first-save](vocab-02-first-save-validation.md) ghi SHA, lệnh, kết quả và evidence mới.
P1 first-save đã có triển khai và acceptance host API26/35; cần reviewer kiểm tra head mới.
Device QA riêng vẫn NOT RUN nếu chưa có thiết bị; không tự đánh dấu VOCAB-02 Hoàn thành.