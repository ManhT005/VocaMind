# QA-02 — Kế hoạch hoàn thành nghiệm thu end-to-end P0

**Ngày lập:** 09/11/2026, Asia/Saigon. **Người thực hiện theo phân công:** Thi, Trường, Thành. **Reviewer:** Nhân.  
**Phân công gốc:** Tuần 3/D11–D14, Critical, 10 giờ, phụ thuộc WEEK-2-GATE.  
**Nhánh kế hoạch:** `feature/qa-02-e2e-acceptance`. **Base khảo sát:** develop `c5d0ad6`.  
**Trạng thái:** Chỉ lập kế hoạch; chưa thực hiện QA-02 hoặc xác nhận PASS.

## 1. Căn cứ và mục tiêu

- XLSX `VocaMind — Phân công .xlsx`, sheet Phân công: QA-02 nghiệm thu P0 trên thiết bị thật/Airplane Mode, first launch → pack → search → Today → 10 thẻ → Quiz → Progress → Attribution → reboot; có video/log/evidence, đối chiếu Requirement/Test Matrix.
- Baseline hiện hành `VocaMind_SRS_Baseline_v1.1.docx` đã gửi: AC-01..14 (P0), NFR-VM-01/14/15, TC-UI-10 và các FR/BR tương ứng.
- DOCX `SRS_Ung_dung_hoc_tu_vung_tieng_Anh_Android.docx` vừa gửi đã đọc để đối chiếu tài liệu gốc; không mở rộng bằng yêu cầu mâu thuẫn baseline v1.1.
- Source/harness hiện có: DATA-08 upgrade/rollback, DATA-09 performance/artifact, FLASH-03 chống duplicate và các tests feature đã merge. Dùng đúng source hiện hành, không mặc định mọi task đã nghiệm thu vì đã merge.

Đối chiếu thêm SRS §10.5: nghiệm thu cần bằng chứng offline từ lần mở đầu, leech, upgrade/rollback, học liệu/quyền nguồn và benchmark. Các gate chuyên trách được phối hợp hoặc kế thừa có kiểm chứng, không yêu cầu nhóm QA-02 viết lại toàn bộ harness DATA-08/09.

Google Sheets: https://docs.google.com/spreadsheets/d/1Zx4QWaSalYk2-KWoaRRxwL6Hd-vv-sa2Lv9dBDxzQFg/edit

Sheets trả HTTP 403; dùng bản Excel đã cung cấp, chưa xác minh thay đổi trực tuyến mới hơn.

**Nguyên tắc người dùng đã chốt: QA-02 phải hoàn thành toàn bộ phạm vi nghiệm thu mới Done.** Ca chưa chạy là NOT RUN/BLOCKED; ca fail phải sửa và retest. Không chuyển phần thiếu của QA-02 sang QA-03 chỉ để đóng task. “Done code” của feature không phải kết quả nghiệm thu QA-02.

## 2. Điều kiện bắt đầu

1. Thành đối chiếu WEEK-2-GATE, lập danh sách feature P0/known issues, ghi trạng thái thật; chưa có xác nhận gate thì không tự ghi PASS. Có thể chuẩn bị test trước khi đủ điều kiện.
2. Thi đóng băng commit/artifact để chạy; ghi commit, build profile, applicationId, version, schema, hash app APK/test APK/catalog. CI trên đúng head phải đạt; kiểm tra manifest không phụ thuộc backend/tài khoản.
3. Có thiết bị Android thật phù hợp, ưu tiên cấu hình tham chiếu; bổ sung API26/màn nhỏ theo matrix nhóm. Emulator hỗ trợ tái hiện, không tự thay thế điều kiện thiết bị thật của QA-02. Performance phải có inventory theo protocol DATA-09/SRS.
4. Có bản cài QA riêng an toàn, corpus ≥150 stableId/≥4 packs, đủ dữ liệu cho 10 thẻ và Quiz10 câu; xác nhận pack membership 40/40/40/30. Không đưa fixture test vào production assets hoặc clear dữ liệu app người dùng.
5. Đối chiếu ledger/quyền học liệu và release gate. Có thể chạy corpus nội bộ được phép để tìm lỗi, nhưng không coi kiểm thử nội bộ là quyền phân phối; AC-12 còn thiếu phê duyệt thì giữ BLOCKED.
6. Chuẩn bị quay video, logcat, snapshot dữ liệu logic, bảng test và bug tracker. Mọi thao tác đổi cấu hình thiết bị cần snapshot/restore trạng thái ban đầu.

Trước khi bắt đầu, ghi giờ thiết bị/host, timezone và version catalog để tính expected dueAt theo lịch địa phương; không so timestamp bằng một giá trị cố định không gắn với lần chạy. Video/collector chạy trên host được phép, nhưng bản app phải được xác nhận không có active network và không dựa vào kết nối Internet của host.

Thiết bị hoặc artifact thiếu điều kiện phải được ghi thành blocker có người phụ trách và bước giải quyết, không bỏ ca hoặc hạ chuẩn để hoàn tất.

## 3. Phân việc đề xuất trong nhóm

| Người | Trách nhiệm QA-02 |
|---|---|
| Thành | Điều phối, chốt matrix/kịch bản, kiểm Flashcard/Quiz/Progress và tính nhất quán nghiệp vụ; tổng hợp kết quả, gửi Nhân review. |
| Trường | Chạy discovery: packs/vocab/search/detail/save/Attribution, empty/error, layout/accessibility; ghi video và kiểm offline. |
| Thi | Chuẩn bị build/thiết bị/cài mới, logcat/hash, snapshot/reboot, tích hợp harness DATA-08/09 và tổng hợp artifact/test reports. |
| Nhân | Reviewer theo phân công; đối chiếu DB/transaction/migration/performance và tính đầy đủ minh chứng; phối hợp xử lý lỗi data. |

Phân việc chi tiết là đề xuất triển khai, không thay owner/reviewer của Excel. Người đang thao tác thiết bị và người ghi nhận có thể đổi vai, nhưng mỗi ca phải có tên người chạy và reviewer.

## 4. Hành trình chính liên tục — TC-UI-10

Chạy trên bản cài QA mới, bật Airplane Mode và tắt Wi-Fi/data **trước lần mở Activity đầu tiên**; lưu readback xác nhận không có active network. Không chỉ dùng Gradle --offline làm bằng chứng app offline. Giữ một video liên tục hoặc các đoạn có mốc nối rõ; không seed/reset DB giữa hành trình để giả trạng thái đạt.

| Bước | Thao tác | Expected và minh chứng |
|---|---|---|
| 1 | Mở lần đầu offline, không đăng nhập | Home hiện, catalog local mở đúng; log startup và thông tin thiết bị. |
| 2 | Khám phá pack, activate/pause/reactivate | State chính xác và persist; chỉ gói active đóng góp planner. |
| 3 | Search tiếng Anh/Việt, nhập nhanh/clear, mở detail | Query hiện tại đúng; stableId đúng; nghĩa/ví dụ EN–VI/IPA fallback, Save/Unsave đúng và catalog không đổi. |
| 4 | Vào Today, bắt đầu học | Due trước new, không trùng stableId do nhiều pack, tuân quota/active packs. Ghi tập expected độc lập từ DB/planner contract. |
| 5 | Học 10 thẻ có FORGOT/HARD/REMEMBERED | Chỉ chấm sau lật; box/due/counters theo scheduler; FORGOT được relearn tối đa 1 lần/từ, không loop. Ghi riêng 10 thẻ đầu và các occurrence relearn phát sinh. |
| 6 | Rotate/Back an toàn ở các điểm đã định | Giữ state theo contract, không phát sinh review lặp; retry lỗi không làm mất thẻ/dữ liệu đã ghi. |
| 7 | Quiz10 câu, trả lời hết | 4 lựa chọn/câu, câu/đáp án hợp lệ; lock/feedback/next đúng; chỉ một attempt khi hoàn tất và điểm đúng. |
| 8 | Progress | Bốn chỉ số, đủ box0..5, history và Mastered disclaimer; UI khớp snapshot userdata, Quiz không tự tăng word progress. |
| 9 | Attribution offline | Đọc nguồn/license từ catalog; mở link khi offline không crash hoặc khóa app. |
| 10 | Snapshot → force-stop/reopen → reboot offline/reopen | Active packs, saved flags, progress, ReviewEvent và completed Quiz history giữ nguyên; không thêm event/attempt vì reopen; logcat không crash/ANR của app. |

Không yêu cầu mọi phiên chưa hoàn tất phục hồi đúng mặt sau force-stop/reboot ngoài contract hiện hành; yêu cầu cứng là dữ liệu đã commit không mất/nhân đôi, rotation giữ state được đặc tả.

## 5. Ca bổ sung bắt buộc trong QA-02

Các ca có seed/fault injection dùng QA DB riêng hoặc lượt độc lập, không phá hành trình chính. Ghi rõ dữ liệu thiết lập và expected; không sửa production semantics để ca dễ pass.

| ID | Ca kiểm tra | Điều kiện đạt |
|---|---|---|
| QA02-S01 | No-pack, paused-only, no-due; quota và nhiều pack trùng từ | CTA/empty đúng, planner chỉ dùng gói active, không duplicate/loop. |
| QA02-S02 | Empty query/no-match/read error/retry/reset và async list | State rõ, không hiện/click stale result; retry đúng query hiện tại. |
| QA02-S03 | Empty Progress/history độc lập; vocabulary thiếu IPA, text dài | Không crash/fake data, nhãn đúng, màn nhỏ/IME/font lớn vẫn thao tác được. |
| QA02-S04 | FLASH-03: 3 tap cách150ms, sau đó thẻ tiếp theo | Thực sự chạy test thiết bị, ghi timing; đúng1 ReviewEvent/1 update cho lượt hợp lệ; thẻ sau chấm được. Không chấp nhận build test APK thay execution. |
| QA02-S05 | Inject lỗi transaction/review write | DATA-06 rollback toàn bộ; error/retry đúng, không advance/relearn hoặc ghi nửa chừng khi fail. |
| QA02-S06 | Leech và Leitner boundaries | Fixture nhiều phiên/đếm lỗi có kiểm soát: FORGOT liên tiếp4 bật leech, HARD/REMEMBERED gỡ; đối chiếu bộ10 scheduler tests. Không ép trong một phiên vượt rule relearn. |
| QA02-S07 | Quiz pool<4, abandon, double tap, rotate/result reopen | Empty đúng; abandon không tạo attempt; completed chỉ1 record, điểm/câu/stableId đúng. |
| QA02-S08 | Lỗi DB runtime, catalog staged update/rollback, userdata v1→v2 | Tái dùng DATA-08 trên source áp dụng: giữ dữ liệu/stableId, không destructive reset khi runtime failure; có snapshots trước/sau. |
| QA02-S09 | Startup/FTS/release artifact | Protocol DATA-09 đúng thiết bị/artifact/dataset, raw samples đầy đủ, ngưỡng SRS đạt; release APK ≤25MB và content đúng. |

## 6. Requirement/Test Matrix AC-01…14

Mỗi dòng phải trỏ test/run ID, commit/artifact, kết quả, evidence và người xác nhận. Có thể tái sử dụng evidence chuyên trách nếu chứng minh áp dụng đúng code/artifact và điều kiện; thay đổi đường chạy liên quan thì phải rerun. Không chỉ dẫn link báo cáo cũ rồi coi gate đã xanh.

| AC | Kiểm chứng phải có |
|---|---|
| 01 | First launch offline/không account và startup <1500ms theo protocol DATA-09; first launch riêng, ≥10 cold starts, giữ raw. |
| 02 | ≥150 stableId, ≥4 packs, membership40/40/40/30, schema/integrity hợp lệ của artifact được nghiệm thu. |
| 03 | Pack activate/pause và persistence qua reboot. |
| 04 | Detail EN–VI/IPA fallback, Save không sửa catalog. |
| 05 | Search Anh–Việt và FTS<50ms; mục tiêu<15ms báo riêng; fixture5000, Helio G85/3GB/Android10 hoặc thay thế được chấp thuận rõ ràng. Giữ workloads/raw và protocol DATA-09; không lấy CI host/emulator làm reference mặc nhiên. |
| 06 | Flashcard lật/chấm sau lật và vùng chạm≥48dp. |
| 07 | Bộ10 scheduler tests, leech theo rule; TTS P1 không chặn MVP. |
| 08 | Triple-tap thiết bị + transaction rollback, không ghi trùng/nửa chừng. |
| 09 | Planner active/due-first/quota/dedup và relearn tối đa1 lần/từ. |
| 10 | Quiz4..10 câu unique,4 lựa chọn, complete-only; pool<4 empty. |
| 11 | UI tổng/6box/nhóm/history khớp DB. |
| 12 | Attribution offline + ledger đúng snapshot + phê duyệt nguồn PO/Content Lead; internal QA acceptance không thay quyền phân phối. |
| 13 | Reboot/update/rollback/migration giữ userdata và tham chiếu stableId; runtime DB failure không tự xóa. |
| 14 | Release APK≤25MB, không cần mạng/server; TTS chỉ kiểm riêng nếu phát hành P1. |

AC-15/pilot recall, AI và tính năng P1 chưa phát hành không tự thêm vào QA-02 P0. NFR-VM-15 kiểm manifest/quyền/dependency và hành vi không gửi kết quả học sang dịch vụ của dự án; lưu trữ DB tại vùng cục bộ app.

## 7. Ước lượng và quy trình xử lý lỗi

| Bước | Giờ dự kiến |
|---|---:|
| Chốt gate, artifact, thiết bị và fixture | 1,5 |
| Chạy hành trình chính/video/snapshot/reboot | 3 |
| Các ca bổ sung và đối chiếu evidence chuyên trách | 2,5 |
| Triage, phối hợp fix, retest các ca ảnh hưởng | 2 |
| Tổng hợp matrix, Nhân review và chốt kết luận | 1 |
| **Tổng theo phân công gốc** | **10** |

10 giờ là estimate công việc, không phải quyền cắt ca. Chưa gồm chờ thiết bị/quyền nguồn hoặc sửa lỗi lớn. Nếu không đủ, cập nhật thời gian/người nhận cho đến khi đủ điều kiện, giữ QA-02 chưa Done.

Bảng trên phân bổ 10 giờ gốc theo công việc; không suy thành cam kết hoàn tất trong 10 giờ đồng hồ hoặc tổng 10 người-giờ khi ba người cùng tham gia. Thành phân lịch/người chạy theo thiết bị thực có và ghi lại effort thực tế.

Mỗi lỗi có ID, severity, commit/device, steps, expected/actual, log/ảnh/video, owner và trạng thái. Crash/data loss/duplicate/vi phạm gate P0 phải xử lý trước đóng task. Sau fix, ghi commit mới, retest lỗi và các chức năng bị tác động; nếu artifact đổi, cập nhật hash và xác định phần evidence nào phải chạy lại. Không ghi đè evidence cũ.

QA-03 là đợt regression/bug bash tiếp theo trên bản đã qua QA-02; không phải nơi chứa ca chưa chạy hoặc blocker P0 còn mở của QA-02.

## 8. Hồ sơ bàn giao và Done

Đầu ra dự kiến: `docs/qa-02-validation.md`, `docs/qa-02-requirement-test-matrix.csv`, `docs/qa-02-defects.csv`, evidence tại `docs/evidence/qa-02/<run-id>/`. Tệp matrix/defects được điền trong triển khai, không ghi trước PASS.

Matrix tối thiểu có: case ID, requirement/AC, preconditions, steps, expected, actual, status, người chạy, device, commit/artifact, evidence link, defect ID và retest run ID. Với chứng cứ kế thừa, ghi source commit, phạm vi thay đổi từ source đó tới artifact hiện tại và lý do còn áp dụng; không kế thừa chỉ vì tên test giống nhau.

Mỗi run giữ inventory/OS/API/RAM/locale/timezone, commit và APK/catalog hashes, profile/appId, fixture/expected, lệnh/test arguments, timestamps, video/logcat/reports, DB logical snapshots trước/sau và kết quả. Dùng export nhất quán để tránh snapshot bỏ sót WAL; không đưa dữ liệu cá nhân người dùng vào repo. Kết thúc restore cấu hình thiết bị và kiểm tra readback.

Trạng thái từng ca: PASS, FAIL, BLOCKED, NOT RUN; N/A chỉ khi thật sự ngoài P0 hiện hành và có lý do reviewer xác nhận, không dùng cho thiếu thiết bị/quyền nguồn.

- [ ] WEEK-2-GATE và điều kiện chạy đã đối chiếu, không có blocker làm bằng chứng mất giá trị.
- [ ] Hành trình liên tục và toàn bộ ca bổ sung trong scope hoàn tất; không còn FAIL/BLOCKED/NOT RUN của P0.
- [ ] Matrix AC-01..14(P0) đầy đủ, đúng thiết bị/artifact và evidence; các gate performance/content/release đạt bằng chứng tương ứng.
- [ ] Lỗi ảnh hưởng nghiệm thu đã fix/retest; dữ liệu sau reboot/update/rollback khớp expected, không duplicate.
- [ ] Video/log/reports/snapshots có binding commit/hash và kết luận trung thực.
- [ ] Thi/Trường/Thành tổng hợp, Nhân review xác nhận; mọi góp ý còn ảnh hưởng P0 đã giải quyết.

**Chỉ sau các bước trên mới chốt QA-02 Done.** File kế hoạch, code đã merge, CI xanh hoặc đồng ý “test sau” cho task feature không thay thế nghiệm thu này. Chưa có kết quả thực thi nào được xác nhận bởi việc tạo kế hoạch.
