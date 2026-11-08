# PROGRESS-02 — Kế hoạch màn Tiến độ và lịch sử Quiz

**Dự án:** VocaMind — Android Java, Room, MVVM.
**Ngày lập:** 08/11/2026 (Asia/Saigon). **Trạng thái:** Chỉ lập kế hoạch; chưa triển khai hoặc xác nhận PASS cho PROGRESS-02.
**Owner:** Vương Quang Thành. **Reviewer theo phân công:** Lê Mạnh Trường. **Phối hợp DATA-07:** Bùi Vĩ Nhân.
**Phân công gốc:** Tuần 2, D8–D9; High; 7 giờ; phụ thuộc `PROGRESS-01, DATA-07`. D8–D9 là lịch gốc, không phải xác nhận tiến độ thực tế ngày 08/11.
**Nhánh dự kiến triển khai:** `feature/progress-02-ui-history`. **Base đã kiểm tra:** `develop` tại `fd76f21`.

## 1. Căn cứ và giới hạn đối chiếu

| Nguồn | Nội dung áp dụng |
|---|---|
| `VocaMind_SRS_Baseline_v1.1.docx` người dùng đính kèm | Baseline hiện hành: FR-VM-041..044, BR-09, UC-07, AC-11, DEC-15; FR-VM-038/039 liên quan nguồn lịch sử; NFR-VM-01/04/09/10/11 liên quan UI/offline. |
| `SRS_Ung_dung_hoc_tu_vung_tieng_Anh_Android.docx` | Đối chiếu tài liệu gốc; không mở rộng baseline v1.1 bằng tính năng/chỉ số từ bản cũ. |
| `VocaMind — Phân công .xlsx`, sheet Phân công | PROGRESS-02: màn Progress từ DB, tổng từ, đủ 6 box, ba nhóm, lịch sử Quiz, empty state, GetProgress qua ProgressRepository, đối chiếu fixture DB. |
| Cùng bảng: PROGRESS-01, DATA-07, QUIZ-03 | Contract dùng chung; aggregation từ userdata; Quiz chỉ ghi kết quả hoàn tất. |
| `docs/progress-01-handoff.md`, `docs/plans/data-07-progress-aggregation-queries.md`, source hiện tại | Dùng API đã triển khai; ghi nhận khác biệt giữa ghi chú bàn giao cũ và code hiện hành. |

Google Sheets: https://docs.google.com/spreadsheets/d/1Zx4QWaSalYk2-KWoaRRxwL6Hd-vv-sa2Lv9dBDxzQFg/edit

Link trực tuyến trả HTTP 403 trong môi trường làm việc ngày lập kế hoạch. Phân công được đối chiếu từ XLSX người dùng đã gửi; chưa xác minh thay đổi mới hơn trên Sheets. Trạng thái “Chưa bắt đầu/Waiting” trong XLSX là dữ liệu lịch sử, không dùng để phủ nhận code đã có trên develop.

## 2. Kết quả cần đạt

Người học vào Tiến độ thấy số liệu thật từ `userdata.db`, lịch sử Quiz đã hoàn tất và hiểu đúng ý nghĩa Mastered. Màn hình dùng được offline, phản ánh dữ liệu mới khi quay lại sau học/Quiz/lưu từ; lỗi đọc dữ liệu được hiển thị thành lỗi có thể thử lại.

Sản phẩm bàn giao:

1. Progress UI có bốn chỉ số, đủ sáu box, chú thích Mastered và danh sách lịch sử Quiz.
2. ViewModel tải dữ liệu qua `GetProgressUseCase`, quản lý loading/empty/content/error và vòng đời.
3. Tái sử dụng DATA-07 và composition hiện có; UI không truy vấn DAO hoặc tạo số liệu tạm.
4. Test ViewModel/UI và đối chiếu Room fixture → repository → use case → màn hình; kiểm thử tích hợp Quiz.
5. Tài liệu `docs/progress-02-validation.md` ghi commit, môi trường, lệnh, kết quả thật, minh chứng và giới hạn còn lại.

## 3. Hiện trạng và phụ thuộc thực tế

| Thành phần trên develop fd76f21 | Kết luận để triển khai |
|---|---|
| Domain `ProgressSummary`, `QuizHistoryItem`, `ProgressSnapshot`, `ProgressReadResult` | Đã có; giữ invariants và mapping, không tạo model nghiệp vụ song song. |
| `GetProgressUseCase` | Là functional interface đồng bộ `execute()`; không phải việc tạo thêm interface trùng tên. |
| `RepositoryProvider.progressUseCase()` | Đã trả `progress::readProgress`; tái sử dụng production wiring này. |
| `ProgressProvider`, `RoomProgressRepository`, `UserDao.progressSnapshot()`, `ProgressMapper` | DATA-07 đã có code aggregation/mapping/snapshot; kiểm tra kết quả qua test tích hợp thay vì viết lại SQL trong UI. |
| `ProgressViewState` | Đã có LOADING/EMPTY/CONTENT/ERROR, empty từng section và nhãn; tái sử dụng, đưa bản hiển thị vào Android resources. |
| `ProgressFragment`, `fragment_progress.xml` | Mới là placeholder; đây là phần chính phải thay. |
| QUIZ-03 | Đã nằm trong develop qua PR #33; có thể kiểm thử history bằng luồng hoàn tất Quiz thật. |
| VOCAB-02 | Đã nằm trong develop; giữ policy saved-only row khi kiểm thử thống kê. |

Không coi sự hiện diện của code hoặc test cũ là kết quả PASS của màn hình mới. Các mục PENDING trong hồ sơ PROGRESS-01 cũ cần đọc theo mốc lịch sử; source và test hiện hành mới là đầu vào kỹ thuật của kế hoạch này.

Phụ thuộc chính thức vẫn là PROGRESS-01 + DATA-07. QUIZ-03 phục vụ ca tích hợp completed-only; không thêm thành điều kiện chặn việc viết UI. Nếu luồng Flashcard production chưa sẵn sàng, dùng fixture/production writer kiểm tra cập nhật progress và ghi rõ giới hạn; chưa tuyên bố full Flashcard E2E.

## 4. Quy tắc số liệu không được thay đổi

| Mục | Quy tắc |
|---|---|
| Tổng từ đã học | `COUNT(DISTINCT UserWordProgress.vocabularyId)` trên toàn userdata. |
| Sáu box | Box 0: **Học lại**; box 1..5: **Cấp ôn 1..5**. Hiển thị cả box có count=0. |
| Nhóm | Learning = box0+box1; Reviewing = box2+box3; Mastered = box4+box5. Đọc getters domain đã có. |
| Invariant | Tổng sáu box = tổng từ; tổng ba nhóm = tổng từ. |
| Phạm vi | Không nhân bản theo PackWord; không lọc active pack, isSaved hoặc learnedAt. Pause pack không làm mất thống kê. |
| Saved-only | Row lưu lần đầu với learnedAt=null vẫn được tính vào tổng và box0 theo policy hiện hành. Bỏ lưu giữ row, nên không tự giảm tổng. Không đổi nhãn/định nghĩa để né hành vi này. |
| Lịch sử | Chỉ đọc QuizAttempt đã persist; mới nhất trước theo completedAt DESC, attemptId DESC. Không đưa phiên đang làm/bỏ dở vào history. |
| Điểm | Hiển thị đúng dữ liệu đã validate; wrongCount từ getter domain. Không tính lại điểm/sort/filter trong Fragment. |
| Dữ liệu lỗi | READ_FAILED hoặc INVALID_DATA → ERROR, không biến thành bộ số 0 hoặc bỏ riêng record hỏng. |

Chú thích luôn đọc được trên phần nội dung/empty hợp lệ:

> Mastered là trạng thái ôn tập trong ứng dụng, không phải chứng nhận năng lực ngoại ngữ.

## 5. Thiết kế màn hình và trạng thái

Thứ tự nội dung đề xuất:

1. Tiêu đề “Tiến độ”, thao tác tải lại rõ ràng.
2. Bốn chỉ số: Tổng từ đã học, Learning, Reviewing, Mastered; ghi phạm vi box của từng nhóm.
3. Phân bố sáu box bằng sáu hàng/cột có nhãn và số cụ thể; có thể có thanh tỷ lệ phụ trợ, không phụ thuộc màu. Không cần thư viện chart mới.
4. Chú thích Mastered.
5. Lịch sử Quiz: thời điểm hoàn tất, số câu, đúng/sai và điểm phần trăm cho từng lượt.

| State | Hành vi dự kiến |
|---|---|
| LOADING | Báo đang tải, không hiển thị số 0 giả; khóa thao tác retry/refresh lặp trong cùng request. |
| EMPTY toàn màn | Read thành công, total=0 và history rỗng: bốn chỉ số 0, sáu box 0/đồ thị rỗng, hướng dẫn bắt đầu học và history empty. |
| CONTENT có progress, history rỗng | Giữ số progress thật, riêng lịch sử hiển thị “Chưa có bài Quiz hoàn tất.” |
| CONTENT chỉ có history | Bốn chỉ số/sáu box đều 0 và hướng dẫn học ở section progress; history vẫn hiển thị. Quiz không tự tạo word progress. |
| CONTENT đủ dữ liệu | Hiển thị toàn bộ snapshot hợp lệ và danh sách theo thứ tự repository/domain. |
| ERROR | Thông báo không thể tải tiến độ và nút “Thử lại”; không trình bày dữ liệu lần trước như dữ liệu vừa đọc thành công. |

Quyết định kỹ thuật cho v1: refresh chuyển sang LOADING và tạm ẩn số liệu cũ; lỗi chuyển ERROR. Không thêm mô hình dữ liệu cũ cần cảnh báo trong đợt này.

Ngày giờ: giữ epoch milliseconds ở domain; format ở presentation bằng locale và múi giờ thiết bị, gồm ngày và giờ, không hard-code UTC hoặc Asia/Saigon. Test inject timezone/locale. Lịch sử không cắt ngầm 10 dòng; dùng RecyclerView/ListAdapter với stable identity theo attemptId, tránh dựng toàn bộ history bằng nhiều TextView trong ScrollView. Chưa bổ sung filter/export/paging contract.

## 6. Luồng triển khai và vòng đời

```text
ProgressFragment → ProgressViewModel → GetProgressUseCase.execute()
                                      ↓ (background executor)
                              ProgressRepository.readProgress()
                                      ↓
                       RoomProgressRepository / DATA-07 snapshot
                                      ↓
                         ProgressViewState → UI trên main thread
```

- Tạo `ProgressViewModel` và factory dùng `RepositoryProvider.progressUseCase()`; factory nhận dependency, không mở DB mới cho mỗi refresh.
- Dùng executor cho lời gọi đồng bộ; không dùng allowMainThreadQueries trong production. Kết quả state dispatch về main thread.
- Observe bằng viewLifecycleOwner, không giữ View/Fragment/Activity trong ViewModel, không cập nhật binding sau onDestroyView.
- Tải khi vào màn, đọc lại khi màn trở lại RESUMED sau học/Quiz và có nút tải lại thủ công. Chỉ chọn một entry point lifecycle để tránh onViewCreated + onResume tạo hai request giống nhau.
- Coalesce yêu cầu đang chạy; nếu có yêu cầu làm mới trong lúc đọc, ghi nhận một lần đọc tiếp sau khi hoàn tất, tránh mất cập nhật khi quay lại màn.
- Chặn response cũ ghi đè state mới bằng request generation/token khi có thay thế request; bỏ kết quả sau onCleared và giải phóng executor do ViewModel sở hữu. Không đóng database do application sở hữu.
- Rotation tái tạo view an toàn và dùng ViewModel được giữ lại; process recreation đọc lại DB, không lưu snapshot lớn/history vào Bundle.
- Mọi chuỗi UI mới vào resources; giữ API contract PROGRESS-01 tương thích, tránh sửa test cũ chỉ để khớp thay đổi không cần thiết.
- Kiểm tra auto-merge Gradle/CI trước push: giữ dependencies chuẩn bị catalog test assets cho cả build job và device job, không bỏ bước cài jsonschema.

## 7. File dự kiến thay đổi

| File/khu vực | Công việc |
|---|---|
| `feature/progress/ProgressFragment.java`, `ProgressViewModel.java` (mới) | Bind state, lifecycle, background load/retry/refresh. |
| `core/ProgressViewModelFactory.java` (mới) | Wiring use case hiện có, tương tự pattern factory của repo. |
| `feature/progress/QuizHistoryAdapter.java` (mới, nếu cần) | Bind item bất biến; stable ID; ngày giờ ở presentation. |
| `res/layout/fragment_progress.xml`, item history, `res/values/strings.xml` | Bốn chỉ số, sáu box, history, loading/empty/error và chú thích. |
| Test `feature/progress` và instrumented progress flow | State/lifecycle/formatting và đối chiếu số liệu Room → UI. |
| `docs/progress-02-validation.md`, `docs/README.md` | Kết quả kiểm chứng và index. |

Nếu cần helper formatter, giữ trong presentation, không tạo framework chung. Không đổi schema/DAO/Quiz writer/Leitner hoặc bổ sung chart dependency trừ khi có lỗi tích hợp được chứng minh; tách và giải thích mọi thay đổi vượt phạm vi.

## 8. Kế hoạch thực hiện 7 giờ

| Thứ tự | Thời lượng | Công việc và đầu ra |
|---|---:|---|
| 1 | 0,5 giờ | Cập nhật develop, kiểm tra dependency/API, chốt layout và fixture kỳ vọng; ghi base commit. |
| 2 | 1,5 giờ | ViewModel/factory, background load, state, retry, refresh và chống kết quả cũ. |
| 3 | 1,5 giờ | UI bốn chỉ số/sáu box, chú thích, các empty/error và nhãn resources. |
| 4 | 1 giờ | History adapter, thời gian locale/timezone, lifecycle quay lại sau Quiz/học. |
| 5 | 1,5 giờ | Unit/UI/Room integration tests; fixture độc lập và các regression trọng yếu. |
| 6 | 1 giờ | Build/lint, chạy thiết bị offline/reopen, ghi minh chứng, tự review rồi gửi Trường. |
| **Tổng** | **7 giờ** | Ước lượng công việc theo phân công, không cam kết thời gian chờ CI/reviewer. |

Chạy theo thứ tự trên; nếu thiếu thiết bị/toolchain, tiếp tục phần không phụ thuộc và ghi bước chưa thực hiện, không đánh dấu PASS thay bằng source inspection. Nếu phát hiện lỗi data contract cần sửa, báo tác động và ước lượng bổ sung.

## 9. Fixture và ma trận kiểm thử

Fixture chuẩn: 12 vocabularyId duy nhất; box counts `[2,1,3,2,1,3]`; Learning=3, Reviewing=5, Mastered=4. Có vocabularyId thuộc nhiều pack, một pack paused và một saved-only row learnedAt=null trong box0; tất cả vẫn nằm trong tổng 12.

History fixture: 3 attempts, trong đó 2 cùng completedAt để kiểm tie-break id DESC; ví dụ `7/10 → 70%`, `3/4 → 75%`, `2/6 → 33%`. Dữ liệu kỳ vọng được định nghĩa độc lập với formatter/UI; không lấy chính giá trị UI làm expected.

| ID | Tình huống | Kết quả cần chứng minh |
|---|---|---|
| P02-01 | DB hoàn toàn rỗng | Read success, 4 chỉ số=0, đủ 6 nhãn/count=0, thông điệp progress/history và disclaimer. |
| P02-02 | Fixture chuẩn qua Room repository/use case tới UI | Total=12, sáu count đúng, ba nhóm 3/5/4; tổng=12. |
| P02-03 | Cùng từ ở nhiều pack, pack paused, saved-only | Không đếm trùng/ẩn; bỏ lưu không xóa row hoặc tự giảm tổng. |
| P02-04 | Chỉ progress hoặc chỉ Quiz history | Empty từng section độc lập; không che section có dữ liệu. |
| P02-05 | History có cùng timestamp, hơn 10 records | Thứ tự time DESC/id DESC; đầy đủ danh sách có thể cuộn, không cắt ẩn. |
| P02-06 | Locale/timezone khác nhau, timestamp gần ranh giới ngày | Ngày giờ theo thiết bị, count/score không đổi. |
| P02-07 | READ_FAILED, INVALID_DATA, retry sau lỗi | ERROR có retry; không giả empty hoặc tự sửa điểm; retry thành công render snapshot thật. |
| P02-08 | Refresh nhanh, request chậm, yêu cầu mới khi đang đọc | Không duplicate IO vô hạn, không mất refresh, response cũ không ghi đè. |
| P02-09 | Rotation, back/forward, destroy view trong khi đọc, process recreation | Không crash/leak binding; đọc/render đúng sau khôi phục, không persist snapshot giả. |
| P02-10 | Hoàn tất Quiz thật rồi vào Progress | Xuất hiện đúng 1 attempt, đúng counts/score/time; total/box không tự tăng vì Quiz. |
| P02-11 | Bỏ Quiz giữa chừng / thao tác hoàn tất lặp theo guard hiện có | Abandon không tạo history; không nhân đôi attempt. Tái dùng test QUIZ-03 và bổ sung kiểm chứng history UI. |
| P02-12 | Ghi review bằng production writer khi quay lại màn | Refresh phản ánh box/tổng mới; history Quiz độc lập; nếu chưa có Flashcard UI phải ghi rõ chưa test full flow. |
| P02-13 | Airplane Mode trước first launch của test install; force-stop/reopen và reboot | Progress/history đọc từ local DB, số liệu giữ đúng; không phụ thuộc mạng. |
| P02-14 | Màn nhỏ, dark mode, font lớn, accessibility review | Không mất nhãn/count, không dùng màu làm thông tin duy nhất; nút chính ≥48dp, list cuộn được. |

Tầng kiểm thử: fake use case để kiểm concurrency/error deterministically; Robolectric/UI tests cho binding; Room fixture/instrumentation để kiểm source-of-truth và lifecycle thực. Không coi fake repository tests là bằng chứng AC-11 đầy đủ.

Lệnh dự kiến từ repo root, chạy khi toolchain sẵn sàng:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
./gradlew connectedDebugAndroidTest
```

Instrumented test dùng emulator/app ID QA riêng và fixture trong test sources. Không xóa userdata của app người dùng để chuẩn bị test; không đưa fixture vào production assets. Kiểm tra CI trên đúng commit đã ghép develop mới; không dùng PASS ở head cũ làm kết quả cho head mới.

## 10. Minh chứng và điều kiện Done

Ghi commit được kiểm thử, API/thiết bị, timezone/locale, applicationId, fixture/DB expectation, lệnh, kết quả và đường dẫn reports/screenshots. Với kiểm thử thiết bị, ràng buộc mã test, APK, log và ảnh bằng commit/hash thực tế; không ghi trước PASS hoặc thay hash lịch sử để hợp thức hóa bộ chứng cứ khác.

- [ ] Hiển thị đúng FR-VM-041: tổng và ba nhóm theo DB/domain.
- [ ] Hiển thị đủ sáu box và tên theo FR-VM-042/DEC-15, cả count=0.
- [ ] History/empty đúng FR-VM-043, UC-07; completed-only theo writer QUIZ-03.
- [ ] Chú thích Mastered đáp ứng FR-VM-044/BR-09, đọc được trên UI.
- [ ] P02-02 chứng minh Room → UI khớp fixture, DISTINCT và tổng box đạt AC-11; không chỉ test formatter.
- [ ] Loading/error/retry/refresh/lifecycle hoạt động; production DB IO ngoài main thread.
- [ ] Ca offline, reopen/reboot và tích hợp Quiz có minh chứng thật; mọi giới hạn còn lại ghi rõ trước review.
- [ ] Unit/build/lint và device checks cần thiết pass trên commit cuối; xử lý regression liên quan trước bàn giao.
- [ ] PR vào develop, Trường review; điểm sai aggregation/mapping phối hợp Nhân xử lý; chưa tự đánh dấu Done chỉ vì đã viết code hoặc CI xanh.

## 11. Truy vết yêu cầu → nghiệm thu

| Yêu cầu | Đầu ra | Ca kiểm thử |
|---|---|---|
| FR-VM-041 | Bốn chỉ số từ snapshot/domain | P02-02/03/12 |
| FR-VM-042, DEC-15 | Sáu box, nhãn, tổng DISTINCT | P02-01/02/03 |
| FR-VM-043, UC-07 | History và empty độc lập | P02-01/04/05/09/10/11 |
| FR-VM-044, BR-09 | Mastered là trạng thái nội bộ | P02-01/14 và UI review |
| AC-11 | Số liệu màn hình bằng user DB | P02-02/03/10/12 |
| NFR-VM-01/11 | Offline, không backend | P02-13 |
| NFR-VM-04/09/10 | API26+, thao tác dễ dùng, empty rõ | Build/smoke theo ma trận API; P02-01/07/14 |

Không thêm dashboard ngày/tuần, streak, chart điểm theo thời gian, chứng chỉ, đồng bộ cloud, xuất báo cáo hoặc chỉnh thuật toán Quiz/Leitner vào task này. Đây là các phạm vi chưa nằm trong PROGRESS-02 hiện hành.
