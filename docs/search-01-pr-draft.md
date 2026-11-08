# feat(search): add offline search UI with generation-safe results

Base dự kiến: develop. Head: feature/search-01-search-ui.
Tác giả phụ trách: Trường. Reviewer đề nghị: Thành. **Chỉ là bản nháp; chưa tạo PR.**

Search ở Home → Vocabulary tìm Anh–Việt qua use case/repository/FTS4 của DATA-05.
Debounce 600 ms theo handoff, invalidate generation ngay khi input đổi/clear/retry
và chặn cả response/lỗi/click cũ, gồm A → B → A. UI có NoInput, Loading, Content,
NoMatch, Error, retry/reset; raw query giữ qua Back/recreate/SavedStateHandle.

Process composition tái sử dụng SearchProvider và catalog trên worker, callback
main, không đóng shared catalog theo view lifecycle. Catalog-search detail route
nhận stableId TEXT không cần packId; pack route vẫn validate membership. Search
và navigation không activate pack hoặc ghi business state. Route catalog hiện
dùng full detail/Save của VOCAB-02 qua các API explicit không packId; Lưu/Bỏ lưu
chỉ ghi theo click và policy VOCAB-02. Route pack vẫn kiểm tra membership.
SEARCH-02 tiếp tục hardening empty/error và accessibility theo scope riêng.

Validation local mới nhất trên tree
`463e424998e23b0f89f6da21b1d15b77e5e33306`, đã ghi tại merge commit
`7cf9519fa476e10f604ad88c5fbef668228169f2` (HEAD371d9ec + develop78b9add):

- 517 Android/JVM tests / 51 suites pass trên API26/35; 199 Python unittest tests pass.
- debug và QA150 APK/test APK build pass; lint mỗi profile 0 errors/13 warnings.
- Small Phone API30 offline: 4 instrumentation tests pass, gồm full detail/Save,
  read failure thật → Error/Retry, FTS5000 → UI/cuộn và benchmark telemetry.
- Host native FTS/UI→detail→Back/recreate/read-only snapshot pass. Môi trường Windows
  cần TEMP ngắn C:\tvm cho SQLite tests có tên dài; giữ log attempts thất bại.
- Có phase trace và 500 raw samples emulator; broad-query max99.1540ms trong lượt mới.
  **AC-05/NFR-VM-03 HOLD**, benchmark cấu hình tham chiếu chưa chạy.

Xem [validation tích hợp](search-01-vocab02-integration.md) để biết tree/SHA, môi trường,
commands, scope timing, test mapping và các phần NOT RUN/BLOCKED. Không sửa
SRS/phân công, không tự đánh dấu task Hoàn thành. CI trên pushed head và ít nhất
một reviewer khác tác giả vẫn cần trước merge; chưa có yêu cầu tạo PR/merge.
