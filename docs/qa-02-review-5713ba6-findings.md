> CURRENT STATUS: [qa-02-runner-and-decisions-20261111.md](qa-02-runner-and-decisions-20261111.md). R02 FIXED_HOST_RETESTED (48 host tests); no new Android run/CI for local diff. B03 rights and F03 backup policy PENDING; B05 unsigned. WEEK-2 dependency confirmation located in UI01 validation at aba854d8; it is not QA02 acceptance. R01/R03, full TalkBack, API35 journey and reference-device search stay at the user-selected stopping state; no PASS inferred. Earlier status below is historical.

# Findings — local review PR55/source5713ba6

## R01 — P0 acceptance blocker, Realme first-review assertion tái hiện

- Status: RETEST_PASS_AT_ANIMATION0; original animation1x failure remains reproduced/root cause unverified. Không có source fix; không tự gọi là production defect đã kết luận hoặc đã được sửa.
- Source: Qa02JourneyInstrumentedTest.java:123–144; Qa02Harness.rotateAndRestore():182–187.
- Repro: Realme RMX3783/API33, animations1x, TalkBack off, airplane1/WiFi0/data0/defaultnone; fresh owned QA150, defaults5 trước quota10; chạy offlineJourney. Test activate/pause/search/save/Today, lật thẻ đầu, xoay landscape→portrait và recreate, tap rate_forgot.
- Expected: đúng1 ReviewEvent FORGOT cho stableId hiện tại và tương ứng consecutiveMistakes/relearn.
- Actual: đúng1 ReviewEvent HARD, assertion expectedFORGOT/actualHARD; run03 và04 đều tái hiện tại line144. Snapshot03 giữ nguyên (1 review,0 Quiz). Ảnh lỗi nút Khó có trạng thái pressed; đây chưa đủ để khẳng định handler gán sai.
- Counter-evidence/scope: cùng source/APK API26 journey05 PASS; Realme10 controlled cases không có rotation trước review đều PASS, bao gồm UI FORGOT/HARD/REMEMBERED và write recovery. Không dùng những PASS này để xóa lỗi journey.
- Retest:Realme journey05 animation0x PASS,50.034s;force-stop PASS;physical reboot probe PASS với cùng APK/source. BootIDeb1c2977...→4054a292...,14reviews/1Quiz retained. Đây là điều kiện cấu hình được quan sát, chưa chứng minh animation là nguyên nhân duy nhất.
- Next:Thành/Thi xác định synchronization sau rotation/recreate và ghi precondition animation rõ trong runner; cần source-change authorization cho diagnostics/fix rồi retest1x/0x. Không đổi expected thànhHARD hoặc bỏ rotation/assertion để làm xanh. Tài liệu local chỉ đề xuất, không sửa source theo giới hạn lượt này.

## R02 — P1 runner portability/API26 và physical classification

- Source: tools/validation/run_qa02_device.py:80–81 chặn nếu cmd connectivity help không cóairplane-mode, dù shared OfflineNetwork có legacy26/27 fallback. Environment metadata hardcode EMULATOR/supplemental dù nhận serial máy thật.
- Actual: API26 local báo No shell command implementation. svc wifi còn thiếuCHANGE_WIFI_STATE (logcat SecurityException shelluid2000), shell exit137; đây là environment permission, không app crash. Realme pmclear bịCLEAR_APP_USER_DATA deny và settings writes bịWRITE_SECURE_SETTINGS deny.
- Local workaround: bản sao runner ignored, manual network được người dùng đặt, strict flags+effectiveWiFi+defaultnetwork xác minh trước phases; API26 dùngpmclear trước fresh run, Realme freshfirstinstall và run-as reset chỉ QA sandbox giữa independent fixtures. Không root, không bypass quyền Android hoặc clear original app.
- Proposal (chưa sửa source): explicit manual/read-only mode; guard owned package/fresh-data provenance; derive physical/emulator classification từinventory, không diễn giải model property đơn lẻ là bằng chứng physical. Legacy/missing permission không được bỏ offline assertion.

## R03 — P1 evidence completeness gap trên Realme

- Actual: binary screenrecord inaccessible/notfound. Original collector không kiểm pull returncode và chỉ ghi videoCoverage template; Realme03/04 không cóscreen.mp4. Test result và video evidence phải tách riêng.
- Local sampled collector04 cũng INVALID vì dumpsys window windows không trả current focus trên OEM;0frames, giữ nguyên index thay vì gọi videoPASS. Collector manual sau dùng dumpsys window đúng và ghi ảnh chỉ khi QA foreground, cótimestamp; không audio, không continuousvideo.
- Proposal: kiểm recording start/finish/pull result, file nonzero/format/duration, time coverage; fail evidence gate nếu thiếu artifact, không sửa assertion sản phẩm. Người dùng có thể dùng recorder OEM nhưng cần xác nhận/thực hiện trực tiếp và tránh nội dung cá nhân.

## R04 — P1/P2 hồ sơ aggregate và gate mâu thuẫn

- Approval request còn dòng B02FAIL/API35ZIP401 trong phần lịch sử, trong khi reports sau đã xác minh PASS mandatory benchmark API30 và CI; matrix có nhiều lớpEMU/DEVICE/AC-gate dễ đọc thành cùngsnapshot.
- Proposal local: một bảng latest technical state theo source/APK/device/run; historical fail/retest append-only; approvals là bảng riêng gồm B03rights snapshot, NFR15backup exception, WEEK2 quyết định cóbinding và B05Nhân review. Không tự đưa FAIL lịch sử thànhPASS hoặc PASS kỹ thuật thànhphê duyệt.

## Không phải kết luận vi phạm SRS

allowBackup=true không tự chứng minh vi phạm NFR15 nguyên văn. Realme observed BackupManager enabled, Googletransport selected, manifest không custombackup/dataextractionrules; dữ liệu Room trongapp-private, không INTERNET. Chưa trigger/quan sát actualbackup, không khẳng định không bao giờ ra ngoàithiết bị; PO policy exception vẫn pending theo lựa chọn giữbackup của người dùng.

## R05 — P2 phạm vi resolver khác với lỗi tải trang trong browser

- Source:Qa02ControlledInstrumentedTest.attributionExternalLinkReturnsOfflineWithoutUserdataChanges kiểm external package khácQA. Android resolver cũng đáp ứng điều kiện đó.
- Actual:API26 controlled01 screenshot external-offline.png là hộp “Open with” Chrome/WebView Browser Tester, không phải browser network error. TestPASS đúng launch/chooser cancel/return và unchanged userdata; chưa chứng minh browser load failure. Realme screenshot cùng test là Chrome ERR_INTERNET_DISCONNECTED thực.
- Retest: API26 browser-error-02 đã chọn WebView Browser Tester / Just Once, xác minh ERR_INTERNET_DISCONNECTED, Back về Attribution và userdata không đổi. Core UI PASS; host exit1 ở cleanup, khôi phục riêng đã xác minh thành công tại api26-browser-cleanup/restoration.json. Không đổi default browser/account. Đây là gap diễn giải coverage đã bổ sung local, không phải bằng chứng defect production. Tracked helper nên ghi rõ resolver hay browser; không sửa source trong lượt này.

## Local probe limitations — không gán thành bug production

Full activity-top input-focus parser dùng cho thử TalkBack local chứa nhiều task và khác accessibility focus. Các “target inaccessible” của helper này không chứng minh nút app không tiếp cận được. Chỉ giữ active service + screenshot + user-confirmed spoken content làm partial evidence; full gesture/feedback audit còn thiếu. Windows computer-use helper unavailable sau đủretries/reset; không có mirror evidence.
