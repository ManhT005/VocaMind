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
và preview không activate pack hoặc ghi userdata/catalog. Full detail/save cần
VOCAB-02 tiếp tục; SEARCH-02 tiếp tục hardening empty/error và device UI QA.

Validation local trên code SHA `67c06d4aca3b237d7eb8565dfbb410b5acafd674`:

- 406 Android/JVM tests pass, gồm 26 lượt SEARCH-01 trên API26/35.
- assembleDebug, assembleDebugAndroidTest, lintDebug pass; lint 0 errors/7 warnings.
- 22 Python/data steps pass, gồm 159 unittest tests và expected refusal gates.
- Host native FTS/UI→detail→Back/recreate/read-only snapshot pass. Môi trường Windows
  cần TEMP ngắn C:\tvm cho SQLite tests có tên dài; giữ log attempts thất bại.
- Có phase trace và raw DATA-05 host benchmark; **AC-05/NFR-VM-03 HOLD**.
  Không có connected device; Airplane Mode thực/reference-device performance NOT RUN.

Xem [validation và handoff](search-01-validation.md) để biết SHA, môi trường,
commands, scope timing, test mapping và các phần NOT RUN/BLOCKED. Không sửa
SRS/phân công, không tự đánh dấu task Hoàn thành. CI trên pushed head và ít nhất
một reviewer khác tác giả vẫn cần trước merge; chưa có yêu cầu tạo PR/merge.
