## Mục tiêu và phạm vi

Triển khai SEARCH-01 — Search UI + ViewModel. Phụ trách: Trường. Reviewer: Thành. Phụ thuộc: DATA-05. Tìm kiếm Anh–Việt offline và mở full detail VOCAB-02 bằng stableId TEXT; route từ pack giữ kiểm tra membership. Không tự đánh dấu task Hoàn thành hoặc sửa SRS/phân công.

## Đã triển khai

- Luồng SearchView → ViewModel → SearchVocabularyUseCase → VocabularySearchRepository → FTS4. Tái sử dụng parser, use case và SearchProvider của DATA-05; UI không truy vấn DAO/Room trực tiếp.
- Debounce 600 ms theo handoff; generation tăng mỗi lần input/clear/reset/retry để chặn kết quả, lỗi và click cũ, gồm chuỗi A → B → A. Không giả định cancel Future sẽ dừng SQLite.
- Các state NoInput, Loading, Content, NoMatch, Error; retry/reset và phục hồi raw query qua Back/recreation. DB/I/O chạy trên worker, callback hiển thị trên main thread; không đóng shared catalog theo lifecycle của view.
- Route tìm kiếm toàn catalog không cần packId, không bịa pack mặc định hoặc loại bỏ pack inactive. Dùng full detail/Save của VOCAB-02 qua API explicit. Search/navigation không ghi business state; Save chỉ ghi khi người dùng bấm theo policy VOCAB-02.
- Tối ưu đọc entity qua Cursor nội bộ DAO, giữ nguyên MATCH, ánh xạ rowid → stableId, thứ tự, đủ 10 trường và toàn bộ kết quả. Đóng Cursor đúng ownership; kiểm tra kết nối qua Room để giữ READ_FAILED sau khi owner đóng DB.
- Sửa test pack còn chờ preview `route_message` cũ: kiểm tra full detail, lemma/nghĩa, Save enabled, stableId và packId; giữ kiểm tra Back, rotation và pause pack.
- Tích hợp develop mới nhất có PROGRESS-02; resolve conflict trong `docs/README.md` bằng cách giữ cả hai nhóm tài liệu. Chưa merge feature lên develop/main.

## Kiểm chứng

Sau tích hợp develop `0afcdd6609cd505ed488f0be454f35f2906b6ac2`, source đã kiểm thử tại commit `feed39ade6607293be2d3e97c6734e2dabd0c6e6`:

- **533 tests / 54 suites PASS**, không failure/error/skipped, Robolectric API26/35.
- `assembleDebug` PASS; `lintDebug`: **0 errors / 14 warnings**.
- Môi trường Windows, Temurin17, Gradle8.9 cache, SDK35. Lệnh: `testDebugUnitTest assembleDebug lintDebug --offline`. [Evidence tích hợp develop](docs/evidence/search-01-pr47-merge/).

Evidence trước lần tích hợp develop cuối, giữ riêng SHA trong báo cáo:

- 519 host tests / 51 suites PASS; build QA APK/test APK PASS; lintCatalogQa 0 errors / 13 warnings.
- QA Small Phone API30 đã chạy EN/VI, uppercase, Unicode NFC/NFD, typing nhanh/clear, full detail/Save/Back/recreation, lỗi Room thật → Error → Retry và hiển thị/cuộn đủ 5.000 kết quả.
- Regression cuối trước publish: **46/46** instrumentation pack/quiz/atomic review/progress/migration PASS; ca first-launch trên package mới chạy riêng **1/1 PASS**; QA150 search/detail/retry/5.000 kết quả chạy lại **3/3 PASS**. Không tuyên bố có một lượt full suite47 xanh: ca first-launch cần dữ liệu ban đầu riêng.
- Giữ log các lượt lỗi do fixture QA150 khác fixture4, điều kiện first-launch và assertion preview cũ. Không đổi yêu cầu sản phẩm để làm test PASS.
- Host coverage gồm Today, Flashcard, Quiz, packs, detail, progress/repository. Không gọi host tests là QA thủ công. Chưa chạy lại emulator QA sau lần merge PROGRESS-02 cuối; chưa manual toàn bộ Flashcard/Quiz production trên bản search này. CI và reviewer approval được theo dõi riêng, không coi local PASS là CI PASS.

## AC-05 / NFR-VM-03: HOLD

SRS baseline v1.1 yêu cầu query FTS4 **<50 ms**, mục tiêu **<15 ms**, trên **Helio G85 / RAM 3 GB / Android 10**, với catalog 5.000 mục từ.

Benchmark DATA-05 giữ nguyên fixture SHA256 `5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`, 5 workloads, 20 warmups và 100 mẫu/workload. Không truncate hoặc loại outlier. Hai cặp APK cũ/mới trên Small Phone emulator, mỗi bản 200 mẫu cho workload trả đủ 5.000 kết quả:

| Chỉ số | Trước | Sau |
|---|---:|---:|
| Mean | 44,103 ms | 37,794 ms |
| p95 | 49,552 ms | 45,595 ms |
| Max | 109,465 ms | 52,977 ms |
| Mẫu ≥50 ms | 8/200 | 3/200 |

Mean giảm quan sát khoảng 14,3%. Bản mới vẫn có 3 mẫu vượt ngưỡng, mean broad vẫn trên mục tiêu15 ms. Không chọn riêng lượt new-b max43,145 ms để che lượt new-a max52,977 ms.

Phạm vi đo là DAO MATCH + order + entity materialization, gồm scalar guard ở bản mới; loại asset opening, parse, executor queue, domain mapping, debounce và render. Trace UI giữ riêng; không cộng lặp submit-to-main với query hoặc gọi bind-to-post là GPU frame.

**Lý do tiếp tục HOLD:**

1. Thiết bị đã đo là emulator Small Phone x86/ranchu, Android11/API30, khoảng2 GB RAM; khác SoC/RAM/OS tham chiếu trong SRS.
2. Toàn bộ mẫu emulator mới vẫn chưa đạt ngưỡng50 ms và mục tiêu15 ms cho workload rộng.
3. Chưa có inventory và raw samples từ thiết bị tham chiếu hoặc cấu hình thay thế được chấp thuận. Collector PASS không đồng nghĩa performance PASS; host benchmark không thay evidence thiết bị.

## Phương án thiết bị thay thế

Small Phone đã được dùng để QA chức năng và đo hiệu năng tham khảo. Nếu dùng thiết bị vật lý khác để nghiệm thu, cần owner/reviewer chấp thuận cấu hình thay thế trước; ghi model/SoC/RAM/API/build, điều kiện nguồn/nhiệt/background load và lý do phù hợp. Không tự coi emulator hoặc LDPlayer là tương đương Helio G85. Evidence LDPlayer API28 của PROGRESS-02 không chứng nhận độ trễ tìm kiếm SEARCH-01.

Khi có thiết bị phù hợp, chạy cùng fixture và harness với `data05EnforceLatency=true`, lưu mọi raw samples và SHA source/APK/fixture. Bất kỳ mẫu max≥50 ms là FAIL phép đo đó; báo mục tiêu15 ms riêng. Flag chỉ bật assertion, không chứng minh phần cứng. Chưa có quyết định chấp thuận thay thế nên **AC-05/NFR-VM-03 vẫn HOLD**; không sửa SRS hoặc tự đánh dấu task Hoàn thành.

## Evidence và handoff

- [Validation và contract SEARCH-02/VOCAB-02](docs/search-01-validation.md)
- [Tích hợp full VOCAB-02](docs/search-01-vocab02-integration.md)
- [Điều tra hiệu năng và raw samples](docs/search-01-performance-validation.md)
- [Regression các task liên quan](docs/search-01-cross-task-regression.md)
- [Logs, JUnit, hashes, inventory và screenshots](docs/evidence/search-01-performance-local/)

Thử QA: Home → Từ vựng, tìm `customer`/`khách hàng`, clear/gõ nhanh/NoMatch; chọn kết quả → full detail → Back giữ query; thử Save/Unsave/recreation. Fixture5.000 và Error/Retry nằm trong instrumentation, không thay catalog QA150. SEARCH-02 giữ parser/generation/debounce/state contract; VOCAB-02 giữ hai route catalog/pack và Save policy.
