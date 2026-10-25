# PROGRESS-01 — Đối chiếu trực tiếp SRS, phân công và kế hoạch

Ngày đối chiếu: 25/10/2026. Implementation được kiểm tra: commit `4b882ad`
trên `feature/progress-01-contract`.

## Tài liệu đã đọc

1. `G:\My Drive\Học tập\android\KQ Tuần 1\VocaMind_SRS_Baseline_v1.1.docx`:
   VM-SRS-001, v1.1, ngày 14/10/2026; baseline nghiệp vụ dùng triển khai.
   Đọc trực tiếp nội dung DOCX, đối chiếu §4.7–4.8, BR-08/09, UC-07,
   §7.1–7.4, AC-11, DEC-13/15 và TC-DEC-15.
2. `G:\My Drive\Học tập\android\KQ Tuần 1\SRS_Ung_dung_hoc_tu_vung_tieng_Anh_Android.docx`:
   SRS-ENGVOC-ANDROID-01, v1.0, ngày 09/10/2026. Tài liệu lịch sử:
   FR-10..12 có quy tắc Quiz và lịch sử tương thích, nhưng không thay thế
   phạm vi VocaMind baseline v1.1. Không đưa CRUD từ/chủ đề của bản này vào task.
3. `C:\Users\thanh\Downloads\VocaMind — Phân công .xlsx`:
   đọc trực tiếp các dòng PROGRESS-01, DATA-07, PROGRESS-02 tại sheet Phân công;
   khớp nội dung bảng Drive đã dùng khi triển khai.
4. [Kế hoạch PROGRESS-01](plans/progress-01-contract.md): đối chiếu mục 2–8,
   10 và 12–13 với source/test và tài liệu bàn giao hiện tại.

Văn bản trích để kiểm tra nằm tại `build/progress-01-audit/`, được Git bỏ qua;
không thêm bản sao toàn bộ SRS vào repository.

## Kết luận

Không phát hiện mâu thuẫn giữa implementation hiện tại và các yêu cầu trực tiếp
thuộc **phạm vi contract PROGRESS-01** trong SRS baseline, bảng phân công và plan.
Không cần sửa source/test sau lần đối chiếu này. Đây là kiểm tra contract và
evidence unit test, chưa phải xác nhận giao diện hoặc database đã đáp ứng AC-11.

## Ma trận đối chiếu

| Căn cứ trực tiếp | Yêu cầu | Code và bằng chứng hiện có | Kết luận / giới hạn |
| --- | --- | --- | --- |
| FR-VM-041; UC-07; PROGRESS-01; plan §5.1 | Tổng từ, Learning 0–1, Reviewing 2–3, Mastered 4–5 | `ProgressSummary` có total và ba getter nhóm; `allSixBoxesContributeToTheCorrectGroups` kiểm tra fixture 27 → 5/9/13 và từng box | Khớp contract; render thuộc PROGRESS-02 |
| FR-VM-042; DEC-15; TC-DEC-15; PROGRESS-01; plan §5.1 | Đủ box 0..5; tổng bằng COUNT DISTINCT vocabularyId | Summary bắt buộc 6 count không âm, sum=total, reject overflow; `ProgressRepository` quy định distinct và cùng snapshot; test summary/long/copy | Khớp contract; SQL DISTINCT và consistency phải có DATA-07 integration test |
| FR-VM-042; DEC-15; PROGRESS-01; plan §5.2 | Box 0 là Học lại, box 1..5 là Cấp ôn 1..5 | `ProgressViewState.getBoxLabel`; `labelsExplainEveryBoxGroupMasteredAndRecovery` | Khớp nhãn; chưa bind resources/UI |
| FR-VM-043; UC-07; PROGRESS-01; plan §6–7 | Lịch sử Quiz và empty state | `QuizHistoryItem`, `ProgressSnapshot`, EMPTY/CONTENT; tests cả hai section rỗng, chỉ history, chỉ progress và cả hai có dữ liệu | Khớp payload/state; dữ liệu thật và UI thuộc DATA-07/PROGRESS-02 |
| FR-VM-044; BR-09; UC-07; PROGRESS-01; plan §5.2 | Mastered chỉ là tiến độ nội bộ, không phải chứng nhận | `MASTERED_EXPLANATION` và test nội dung; Mastered chỉ cộng box4/5 | Khớp contract; cần UI review ở PROGRESS-02 |
| FR-VM-037..039; BR-08; §7.2; plan §6.1 | 4..10 câu; chỉ attempt hoàn tất; field thời điểm/count/score | `QuizHistoryItem` nhận ID/time từ caller, kiểm tra count/correct/score; repository chỉ nhận completed attempts | Khớp contract; model không tự chứng minh record đã persist/hoàn tất, adapter và QUIZ-03 phải bảo đảm |
| UC-07; DEC-13; plan §7.2 | Chưa học có số 0 và hướng dẫn; lỗi DB có retry, không tự xóa dữ liệu | Success empty khác READ_FAILED/INVALID_DATA; view LOADING/EMPTY/CONTENT/ERROR, messages/retry; fake failure→retry success và invalid score tests | Khớp contract; không có IO/reset trong state |
| §7.1–7.2; PROGRESS-01; plan §4/6 | Nguồn từ userdata: UserWordProgress và QuizAttempt | Repository Javadoc và handoff quy định mapping, logical snapshot và nguồn số liệu | Khớp boundary; mapping/query thực tế thuộc DATA-07 |
| PROGRESS-01 so với DATA-07/PROGRESS-02; plan §3/8 | Model + use case contract + view state; chưa xây DB/UI thật | Interface repository/use case, fake trong test; domain chỉ Java, không sửa Room/Fragment/Gradle | Đúng phạm vi phân công |
| AC-11 | UI thống kê khớp DB, đủ 6 trạng thái và lịch sử | Contract và unit tests hỗ trợ tích hợp | Chưa nghiệm thu end-to-end; cần DATA-07/PROGRESS-02 |

## Phân biệt yêu cầu SRS và quyết định kỹ thuật của plan

- SRS v1.1 xác định nhóm box, nguồn distinct, sáu trạng thái, nhãn, lịch sử và
  disclaimer. SRS không bổ sung filter active pack/isSaved/learnedAt cho Progress;
  giữ định nghĩa `COUNT(DISTINCT UserWordProgress.vocabularyId)` như plan.
  Saved-only row policy vẫn cần Nhân/Thành xác nhận, không tự thêm filter.
- `long` cho counts, defensive copy, reject overflow, ID > 0, epoch >= 0,
  tie-break ID DESC và không pagination ẩn là quyết định kỹ thuật trong plan,
  không gán chúng thành câu chữ bắt buộc của SRS.
- Thứ tự mới nhất trước và công thức làm tròn có trong SRS Android v1.0
  FR-11/12; plan §6 đã chốt cùng quy ước và `QuizResult` hiện tại dùng cùng công thức.
  Baseline v1.1 quy định field `completedAt/questionCount/correctCount/scorePercent`,
  completed-only và 4..10 câu; không cần kéo thêm phạm vi của SRS cũ vào MVP.
- Đồng bộ background execution, refresh/lifecycle, giữ dữ liệu cũ khi refresh,
  query transaction và định dạng timezone/locale thuộc các bước tích hợp sau.

## Evidence và cập nhật sau audit

Đã đọc lại toàn bộ source Progress và hai lớp test. Evidence thực chạy trước audit:
18 Progress tests + 10 Quiz tests = 28 tests đạt, debug build thành công;
xem [validation](progress-01-validation.md). Lần audit chỉ cập nhật tài liệu,
không thay source/test/Gradle nên không chạy lại build cho thay đổi tài liệu.

Đã gỡ giới hạn “chưa đối chiếu trực tiếp baseline” trong handoff/checklist hiện tại.
Plan gốc giữ nguyên để bảo toàn lịch sử. Review của Trường, xác nhận của Nhân,
DB/UI tests và PR/merge vẫn là các bước riêng; không đánh dấu chúng đã hoàn tất.
