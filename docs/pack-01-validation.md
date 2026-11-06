# PACK-01 — Danh sách gói học

Nhánh `feature/pack-01-pack-list`, chồng lên `feature/data-04-repositories` (PR #21) vì phụ thuộc DATA-04.
PR này nên target nhánh DATA-04 cho tới khi #21 merge vào `develop`.

## Phạm vi theo bảng phân công
- Danh sách gói: title, description, số từ, tiến độ `đã học/tổng` lấy từ user data (`PackRepository.getPackStates`).
- Chi tiết gói: từ theo `PackWord.orderNo`, mỗi từ gắn progress theo stableId (`PackRepository.getPackWords`); từ chưa học hiện "Chưa học", từ đã học hiện hộp Leitner.
- Trạng thái loading / empty / error + nút thử lại; lỗi đọc không bị hiển thị thành "trống".
- UI không biết DAO/Room: `PacksFragment` → `PacksViewModel` → `GetPacks` → `PackRepository`.

## Thiết kế
- `AsyncListViewModel<T>` dùng chung cho danh sách gói và danh sách từ (worker/main executor, bỏ qua retry chồng, đóng DB sau read cuối).
- `PacksViewModelFactory` mở catalog + userdata **lười trên worker**, không mở DB trên main thread.
- Hai màn dùng chung nav graph (`packDetailFragment`, truyền packId/title qua Bundle).

## Kiểm thử (JDK 17)
`./gradlew :app:testDebugUnitTest :app:assembleDebug` — 192 test, 0 lỗi, build debug thành công.
Mới: `PacksViewModelTest` (4), `RepositoryIntegrationTest.packWordsKeepOrderNoAndMergeProgressByStableId` (DB thật, SDK 26/35).

## Chưa làm
- Chưa chạy trên emulator/thiết bị, chưa chụp màn hình; UI mới chỉ qua build và test ViewModel.
- Kích hoạt/tạm dừng gói thuộc PACK-02, chưa có nút trong màn này. Cờ "Đang học" chỉ hiển thị.
- Mỗi màn (danh sách, chi tiết) tự mở một cặp DB qua factory; nếu thấy tốn thì gom thành container cấp ứng dụng.
- Fixture asset thật có đủ 4 gói hay không chưa kiểm tra bằng UI.
