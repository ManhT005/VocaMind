> Latest technical retests: [qa-02-resume-ba02a88.md](qa-02-resume-ba02a88.md). CI ba02a88 PR SUCCESS / push API35 precondition FAIL; local controller fix passed host+API30, API35/API26 CI still pending. Planner/Quiz/IME/font/Attribution coverage updated. TalkBack deferred by user; privacy/rights/sign-off remain open. Earlier sections are historical.

# QA-02 rà soát khoảng trống theo SRS v1.1

## Trạng thái hiện hành sau retest 11/11/2026

Các bảng bên dưới mục “Rà soát ban đầu” là lịch sử trước retest, không phải danh sách việc còn thiếu hiện tại. Kết quả áp dụng theo source/APK và từng lớp kiểm thử tại [báo cáo mới nhất](qa-02-resume-ba02a88.md).

- [x] F01: đối chiếu hai CI run của0442777, checkout/artifact digest/JDK17/counts và expected probe skips; đóng đúng phạm vi row-visibility harness.
- [x] Small Phone API30 đã được người dùng chấp thuận thay LDPlayer cho nghiệm thu và benchmark; không suy rộng sang API khác.
- [x] APK/corpus hiện tại đã audit; defaults5/10/OFF/20 kiểm trước setup quota10; hành trình offline10 thẻ/Quiz10/Progress/Attribution/reopen/reboot có boot-ID đổi và logical snapshots nhất quán.
- [x] Planner due-first/quota/paused/no-active/no-due/shared-pack dedup; empty Progress; review failure/recovery; leech4/HARD/REMEMBERED; Quiz pool0/3 và double-tap; external-link offline/return có evidence ở các lớp khai báo riêng.
- [x] IME/Back, detail dài nhất corpus hiện tại, font130% và action targets đã kiểm trên API30; không thay thế TalkBack audit.
- [x] B02 mandatory startup<1500ms và FTS5000<50ms đạt trên API30 được duyệt. Broad mean26.816ms chưa đạt mục tiêu15ms. Giữ fail LDPlayer, không gọi là tối ưu trước–sau cùng thiết bị.
- [ ] F05: helper offline mới đã qua host/API30, chưa có current-source API35/API26 CI. PR CI của source cũ không chứng nhận diff mới.
- [ ] Full QA150-word journey trên API26/API35 chưa chạy; thiết bị sẵn cấu hình chỉ có API30. Evidence Discovery scope hẹp hơn được giữ riêng.
- [ ] TalkBack/focus order chưa chạy, được người dùng cho phép để lại; vẫn NOT RUN.
- [ ] NFR15: giữ Android backup theo lựa chọn người dùng, chờ PO chấp nhận ngoại lệ đúng snapshot; không có privacy PASS.
- [ ] B03: PO/Content Lead đối chiếu identity snapshot và quyền/kênh phát hành. B05/WEEK-2-GATE: Nhân review evidence và sign-off; chưa có xác nhận hợp lệ.

## Rà soát ban đầu — lịch sử trước retest

Ngày rà soát: 11/11/2026. Đây là rà soát yêu cầu và bằng chứng, không phải lượt chạy test mới hoặc chữ ký nghiệm thu. Giữ nguyên các thay đổi local trước lượt rà soát.

Nguồn chuẩn: `C:/Users/LMT/Documents/University/Nam4/N4_HK1/PTUDTBDT/VocaMind_SRS_Baseline_v1.1.docx`, VM-SRS-001 v1.1, SHA256 `bc2d687bb1a70f28fe0e4566f77d07dc877d08269a6265c361a06fb816c0c7ec`. Đã đọc FR/BR/UC/DATA/NFR, §10.3–10.5 và DEC-01..15. Bản trích phục vụ rà soát nằm local tại `build/qa02/srs-review/baseline-v1.1.txt`. Không sửa SRS.

Đối chiếu với kế hoạch QA-02, matrix, defects, handoff, validation, approval request, evidence index và continuation ngày11/11. PASS contract/host hoặc historical APK không tự trở thành PASS E2E của APK hiện tại.

## Checklist nghiệm thu còn mở

| Phạm vi | Bằng chứng đã có | Cần hoàn tất để chốt |
|---|---|---|
| AC-01; FR001–004; NFR01/02 | Build QA mới; harness kiểm quota5; các journey cũ | [ ] First launch offline APK hiện tại; xác minh cả dailyNewWords5, dailyGoalMinutes10, reminderEnabled0, reminderHour20 trước setup; khai báo quota10; startup launch→render <1500ms trên cấu hình được duyệt, ≥10 lần theo protocol |
| AC-02; DATA03–05; DEC10/14 | Audit corpus150/4 packs lịch sử | [ ] Audit trực tiếp corpus trong APK hiện tại: ≥150 unique stableId, membership tối thiểu40/40/40/30, mỗi pack≥4 nghĩa phân biệt, meaning/ví dụ EN–VI đủ, schema/checksum/version/FTS đúng. Không lấy hash app trùng làm thay audit asset |
| AC-03/04; FR005–010 | Journey và component lịch sử | [ ] Current-build UI tên/mô tả/count/progress packs, thứ tự PackWord, activate/pause, saved/unsaved; detail lemma/POS/EN–VI/IPA placeholder; catalog bất biến và trạng thái giữ qua reopen/reboot |
| AC-05; FR011–014; NFR03 | 11/11 FTS SQLite contracts sau bổ sung Unicode/NOCASE; technical benchmark FAIL | [ ] Xác minh CI/JDK17 reports; current-build UI Anh–Việt/current query/no-match/no stale result/không chặn UI; audit rowid join→stableId; FTS5000 mọi mẫu<50ms, mục tiêu<15ms báo riêng |
| AC-06; FR021/022/026; NFR09/10 | UI lifecycle lịch sử | [ ] Current-build mặt trước ẩn rating, sau lật mới chấm; đo touch targets≥48dp, màn nhỏ/IME/font/rotation; phản hồi Quiz không chỉ dùng màu. TalkBack/focus theo task là evidence bổ sung; SRS không ghi một gate TalkBack độc lập |
| AC-07; FR023/027–033; DEC01/11/12 | Scheduler evidence lịch sử; host tests hỗ trợ | [ ] Đối chiếu đủ10 TC-SCHED và tests bổ sung HARD-clear; UI leech ngưỡng4 qua setup nhiều phiên hợp lệ, nhãn “Từ hay quên”/ngữ cảnh, không khóa lật; HARD và REMEMBERED reset mistakes và gỡ cờ; ngày lịch local/DST/23:55/00:05/timezone-switch không rewrite dueAt cũ |
| AC-08; FR024/025; NFR07/08 | 39/39 SQLite regression mới; triple-tap lịch sử | [ ] Current-build triple-tap3 lần cách150ms, cửa sổ600ms và thẻ kế tiếp; review-write failure UI giữ thẻ/queue, rollback và retry không ghi trùng. SQLite rollback không chứng minh error/recovery UI |
| AC-09; FR015–020; BR07 | 9/9 host PlanToday; current UI FAIL | [ ] Điều tra F04: Today báo không đọc được danh sách gói; dueAt≤now ASC, due trước new, new theo PackWord.orderNo/quota còn lại, active-only/paused-only/no-active/no-due/overlap/dedup; không tự activate; reset quota00:00 local; FORGOT relearn tối đa1 lần sau≥2 thẻ khác nếu có, lần quên tiếp không loop |
| AC-10; FR034–040; BR08/11; DEC07 | 8 host Quiz tests mới; SQLite completion và lifecycle lịch sử | [ ] Coverage pool0/3/4/7/10/12→0/0/4/7/10/10 câu; unique active stableIds; phân biệt meaningVi bằng NFC+trim+casefold; 1 đúng+3 nhiễu khác nhau, vị trí xáo trộn; lock đáp án đầu/double-tap600ms; abandon không lưu; complete đúng1 attempt, điểm đúng. Kế thừa host tests đủ coverage sau xác minh, không viết trùng |
| AC-11; FR041–044; DEC15 | Aggregation contracts mới; UI lịch sử | [ ] Current-build empty Progress/history:0/rỗng/hướng dẫn; bốn chỉ số, đủ6 box0..5 và nhóm0–1/2–3/4–5, sum=COUNT DISTINCT progress, lịch sử Quiz và Mastered disclaimer; saved-only row theo contract không bị diễn giải thành từ đã review |
| AC-12; FR045/046; DATA06; NFR12 | Attribution/ledger lịch sử; B03 unsigned | [ ] Attribution current-build offline trước học, source/license đúng snapshot; external-link failure/no-browser/return không crash hoặc chặn xem nội dung; PO/Content Lead ký quyền snapshot và kênh. Không phát hành mục chưa rõ quyền |
| AC-13; FR003/010; NFR06/07; DEC04/13 | DATA08 upgrade20 contracts lịch sử; SQLite39 mới | [ ] Xác minh applicability và rerun phần đổi: force-stop/reboot boot-ID đổi, logical userdata/catalog snapshots nhất quán WAL; catalog stage→validate→swap/rollback, userdata v1→v2; runtime corruption/schema/low-storage→safe error/retry, không xóa userdata/orphan stableIds |
| AC-14; NFR05/11/15 | QA APK mới cách ly, manifest không INTERNET; backup chưa review | [ ] Release artifact≤25MB/cấu hình ABI, corpus và rights đúng; dependencies/calls/storage/backup paths. QA debug size và offline journey không thay chứng minh release/local-only |
| NFR14; TC-UI-10; §10.5 | Journey mới FAIL trước10 thẻ/Quiz10; video/log giữ riêng | [ ] Hành trình liên tục current APK offline từ đầu→10 thẻ đủ3 ratings→Quiz10→Progress6box→Attribution→reboot, không crash/ANR; defaults trước seed, không reset/reseed giữa pha; leech controlled-state bổ sung khai báo riêng |

## Điều chỉnh cách hiểu và chất lượng hồ sơ

- [x] Đã tìm được và đọc SRS gốc; blocker thiếu vị trí SRS trong continuation nay đã được giải quyết. Không thay đổi kết luận test từ việc đọc tài liệu.
- [ ] F01: hai workflow current head SUCCESS, nhưng reports ZIP401; còn cần exact checkout SHA, actual counts/discovery/skips/assertion scope và JDK17 FTS. Không còn lý do ghi current API35 CI đang fail chỉ vì run lịch sử.
- [ ] Matrix phải tách historical PASS khỏi current-source certification; cập nhật source/APK/evidence cho các dòng còn ghi440f776. API-latest nên tách job SUCCESS đã xác minh khỏi acceptance/report verification còn pending, tránh gộp thành FAIL của current CI.
- [ ] Kiểm WEEK-2-GATE: validation ghi Thành đã xác nhận; cần liên kết quyết định/bằng chứng và phạm vi snapshot. Đây là gate kế hoạch task, không phải tên yêu cầu độc lập trong SRS.
- [ ] B02 vẫn FAIL. Không so API30 với LDPlayer như before/after; cần raw lịch sử và lý do loại launcher samples, giữ mẫu chậm hợp lệ. Chưa xác minh nguyên nhân spike thì không tự nhận tối ưu hoàn tất.
- [ ] NFR15: nguyên văn hành vi là không gửi/lưu kết quả sang dịch vụ của dự án, dữ liệu trong vùng nhớ cục bộ app. `allowBackup=true` là gap cần kiểm đường backup theo prompt task, chưa đủ kết luận tự động vi phạm SRS hoặc bắt buộc đổi false. F03 là finding chờ xác minh; privacy chưa được chứng nhận.
- [ ] Xác minh device approval LDPlayer theo quyết định đã ghi; không mở rộng sang Small Phone API30. Thiết bị kỹ thuật khác vẫn thu evidence nhưng không thay acceptance/benchmark gate được duyệt.
- [ ] Mỗi run có source/diff, app/test/corpus hashes, fixture, inventory/clock/timezone, command/exit, instrumentation counts/status, raw video/log/snapshots; shell exit0 không thay kết quả instrumentation. Các run fail/setup-invalid/retest giữ riêng.
- [ ] B03 owner PO/Content Lead ký đúng rights snapshot/kênh; B05 Nhân review matrix/evidence. Người thực thi chỉ chuẩn bị hồ sơ, không tự ký.

## Phạm vi không tự thêm

TTS/notifications/onboarding là P1, chỉ thành acceptance khi có trong release scope và acceptance được PO xác nhận. Defaults FR004 vẫn P0. AC15 recall70% là pilot hậu MVP; AI/OCR/cloud ngoài phạm vi. Không thêm yêu cầu mã hóa at-rest/root protection hoặc coverage % không có quyết định; SRS §9.3 và NFR18 không chốt những ngưỡng đó.

Kết luận: checklist trước đúng các blocker chính nhưng chưa đủ chi tiết DEC/FR. Ưu tiên tiếp theo là giải quyết Today F04 và hoàn tất current-build journey, lấy CI reports đóng F01, xác minh B02 trên LDPlayer rồi hoàn tất các controlled cases nêu trên. Chỉ sau evidence P0 đầy đủ và sign-off mới xét Done theo §10.5. Chưa push hoặc ghi GitHub.
