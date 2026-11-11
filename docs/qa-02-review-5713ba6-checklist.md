> CURRENT STATUS: [qa-02-runner-and-decisions-20261111.md](qa-02-runner-and-decisions-20261111.md). R02 FIXED_HOST_RETESTED (48 host tests); no new Android run/CI for local diff. B03 rights and F03 backup policy PENDING; B05 unsigned. WEEK-2 dependency confirmation located in UI01 validation at aba854d8; it is not QA02 acceptance. R01/R03, full TalkBack, API35 journey and reference-device search stay at the user-selected stopping state; no PASS inferred. Earlier status below is historical.

# Checklist mục tiêu QA-02 — trạng thái cuối lượt kiểm thử local

- [x] Đối chiếu SRS, PR head/source, matrix, defect và hồ sơ bàn giao; bảo toàn thay đổi local có sẵn.
- [x] Xác minh CI hiện hành, JDK17, discovery/skips và DAO sorting contracts; không gọi Discovery xanh là toàn QA-02 PASS.
- [x] Build và freeze QA/DATA09 APK, source/fixture hashes, package cách ly và corpus.
- [x] API26: journey offline → pack/search/detail → Today10 → Quiz10 → Progress → Attribution; force-stop/reboot; digest giữ nguyên.
- [x] Realme API33: journey tương tự tại animation0x; force-stop/reboot thật; digest giữ nguyên. Defaults5 được kiểm trước setup quota10.
- [x] Mỗi thiết bị 10 controlled tests; API26 native SQLite ordering test; lỗi browser offline/return được bổ sung và đối chiếu userdata.
- [x] Realme startup/FTS5000 raw samples và mandatory thresholds đạt trên đúng cấu hình đo; giữ FAIL lịch sử, không đổi ngưỡng/workload.
- [x] TalkBack service thực sự hoạt động, một phần focus và giọng đọc được người dùng xác nhận; lưu đúng phạm vi partial.
- [x] Khôi phục thiết lập đã thay; bàn giao Realme; đóng AVD headless do lượt kiểm thử mở.
- [x] Báo cáo, matrix kết quả local, findings, đề xuất hợp nhất và gói evidence; không sửa tracked/source hoặc gửi GitHub.
- [ ] Xác minh nguyên nhân mismatch FORGOT/HARD trên Realme animation1x; retest cùng cấu hình sau xử lý được cho phép.
- [ ] Hoàn tất TalkBack touch focus order, toàn bộ actions và thông báo error/result. Nhân/người kiểm accessibility thực hiện tiếp.
- [ ] Full QA150 journey trên API35; CI synthetic chưa thay thế phần này.
- [ ] Reviewer xử lý R02 runner portability và R03 evidence recording; quay liên tục Realme nếu hồ sơ nghiệm thu yêu cầu.
- [ ] FTS broad-query mục tiêu <15ms: chưa đạt; mandatory <50ms đã đạt trên Realme và API30 được duyệt. Không sửa source trong lượt này.
- [ ] PO/Content Lead xác nhận quyền và binding đúng snapshot/phạm vi phát hành (B03).
- [ ] PO xác nhận chính sách privacy/backup theo lựa chọn giữ backup; không tự kết luận allowBackup=true vi phạm SRS.
- [ ] Liên kết xác nhận WEEK-2-GATE đúng phạm vi/snapshot; Nhân review/sign-off B05.
- [ ] QA-02 Done/nghiệm thu MVP: chưa đủ điều kiện. Merge hạ tầng: chưa đề nghị vô điều kiện, cần disposition findings.

Xem REPORT-FINAL.md và requirement-results-final.csv để phân biệt PASS kỹ thuật, FAIL, NOT RUN và BLOCKED. Không cần người dùng thao tác điện thoại thêm trong lượt này.
