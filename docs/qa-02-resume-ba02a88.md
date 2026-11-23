> Latest review of tested source5713ba6: [device results and limitations](qa-02-review-5713ba6-20261111.md), [requirement results](qa-02-review-5713ba6-results.csv), [findings](qa-02-review-5713ba6-findings.md). API26 full journey/reopen/reboot PASS; Realme API33 animation0x journey/reopen/physical reboot PASS, animation1x mismatch remains unresolved. TalkBack partial only; API35 full journey NOT RUN. Rights, privacy/backup, WEEK-2 binding and reviewer sign-off remain open. Earlier status paragraphs below are historical. This is documentation publication, not a fresh test run.

# QA-02 tiếp tục từ ba02a88

Ngày11/11/2026. Nhánh `feature/qa-02-e2e-acceptance`, head remote/local `ba02a887f7cfd511b52f00a766a440a01776db93` cùng các diff local ghi theo từng run. Không có thay đổi production Java, resources, manifest hoặc build profile trong lượt này. Chưa commit/push phần mới; không tự phê duyệt QA-02.

## CI retest sau push5713ba6 — trạng thái mới nhất

Người dùng đã cho phép commit/push; commit5713ba6e880e5b97542cd95b553f26ab8d3b8cda được push đúng nhánh feature/qa-02-e2e-acceptance. Không merge/comment/review hoặc sửa mô tả PR. Các đoạn phía dưới nói chưa push/F05 candidate là timeline trước quyết định này.

Push42990358456 và PR42990368223 đều SUCCESS đủ4 jobs. Push artifact checkout5713ba6; PR artifact checkout mergeef927e8a1117189f4d6a4141bfee42e3cf9604fc. Discovery source digest6441606c6470c10ce1d7982442a63ef7629582bc04d643994998ccbb21a7cd15 giống nhau. Toàn bộ10 artifact ZIP của hai workflow khớp GitHub SHA256 digest.

Mỗi workflow:599 unit tests,0 fail/error/skip; FTS correctness22 và benchmark2; JDK17.0.21 xác minh logs. Mỗi Discovery API26/API35:10 independent PASS; hai suites mỗi suite11 discovered,10 PASS+1 expected guarded probe skip. Probe riêng1 intentional FAIL, artifactsVerified=true; mọi invocation accepted, network restoration accepted=true. Quiz API28 job SUCCESS; counts/checkout/exit evidence lưu raw riêng, không suy diễn final JUnit XML không có trong artifact. Đây là synthetic Discovery và selected Quiz scope, không phải full QA150 acceptance trênAPI26/API35.

F05 chuyển FIXED_RETESTED đúng helper scope, giữ failed runba02a88 trước đó. Summary public thêm ci5713ba6; raw reports/logs/verification tại build/qa02/ci-5713ba6. Cập nhật hồ sơ sau CI đang local, chưa push thêm nhằm không kích hoạt lại CI chỉ vì tài liệu. Không thay source kể từ5713ba6.

## CI trên head đã push — lịch sử ba02a88

| Run | Kết quả đúng phạm vi |
|---|---|
| PR42961748436 | Workflow SUCCESS, artifact checkout merge77b879642cef0082294288c65868dfb296ef2c93. Build & Test598 tests,0 fail/error/skip; FTS correctness22, benchmark2. Discovery API35 artifact digest khớp. |
| Push42961741320 | Build & Test598 tests,0 fail/error/skip; FTS correctness22, benchmark2; Quiz API28 và Discovery API26 SUCCESS. Discovery API35 FAIL trước instrumentation vì default network100 vẫn active. Không tính là app test failure hoặc test PASS. |

Reports ZIP SHA256 đã đối chiếu GitHub digest. Cả hai có RepositoryIntegrationTest64/64 và LeitnerSchedulerCalendarTimeTest8/8,0 skip; kế thừa SQLite/Room shared-pack dedup/paused/due/new và local-calendar/DST tests theo source không đổi. Không tạo test trùng hoặc gọi chúng là UI E2E.

F01 row visibility được giữ FIXED_RETESTED theo scope đã có. F05 là gap mới của offline setup: raw `connectivity-before-launch.txt` cho thấy CELLULAR/HSPA trên eth0, network100, dù flags đã tắt. Shared helper trước đó chỉ ghi airplane setting; phần thông báo ConnectivityService chỉ có trong QA runner.

Đã chuyển controller action vào helper chung và bỏ override trùng. Vẫn yêu cầu flags1/0/0, effective Wi-Fi disabled, default network exactly none và restore; không tăng deadline hoặc bỏ assertion. Có fallback giới hạn API26/27 khi OS báo rõ command chưa hỗ trợ; mọi active network vẫn bị từ chối. [AOSP ghi nhận command này được bổ sung tháng3/2018](https://android.googlesource.com/platform/frameworks/base/+/386886ff02a6e431c4d06edf49b2a496dc9d8a2e), nên không gọi vô điều kiện trên API26.

Host regression:27 network/runner+8 CI-device+4 QA runner PASS. Bao gồm cellular100 chỉ tắt khi controller được gọi, restoration và legacy26 vẫn từ chối network active. API30 Discovery hiện hành:10 independent PASS; suite1/2 mỗi lượt11 discovered,10 PASS+1 expected probe skip; probe riêng cố ý FAIL1 và artifactsVerified=true. Network restore accepted=true. F05 là FIX CANDIDATE, còn cần chạy source mới trên API35 CI; không biến API30 thành API35 hoặc rerun CI của source cũ.

## Các ca P0 được bổ sung

| Run local dưới build/qa02 | Kết quả và giới hạn |
|---|---|
| api30-controlled-03 | 7/7 PASS:empty Progress/history;no active pack và Quiz pool0;paused-only;active pack không due/new;leech4/HARD;leech REMEMBERED;review-write failure/recovery. Fixture khai báo, không phải continuous E2E. |
| api30-planner-ui-01 | 1/1 PASS qua UI:2 overdue theo dueAt ASC trước new;quota2 dùng1 nên new1;older due của paused pack bị loại;exact3 IDs/3 ReviewEvents. Shared-pack overlap kế thừa Room/host contracts, không tuyên bố UI fixture này có overlap. |
| api30-planner-overlap-04 | 1/1 PASS UI trên profile synthetic4 riêng:hai active packs có8 memberships/4 stableIds;quota8→đúng4 cards/4 ReviewEvents/4 progress rows. Snapshot transaction trước/sau. Các lượt02/03 quota2 chưa buộc đi qua toàn bộ membership trùng, giữ phạm vi hẹp; run01 compile fail rồi gọi stale APK là INVALID SETUP, không phải product failure. |
| api30-controlled-04 | 1/1 PASS:actual touchscreen double-tap150..200ms, chỉ1 answer,4 options disabled, không QuizAttempt hoặc đổi userdata. |
| api30-quiz-pool3-01 | 1/1 PASS:controlled renderer/lifecycle sử dụng real generator với3 nghĩa phân biệt→empty, không options/next, không write sau recreate. Factory/navigation setup được khai báo; không gọi là production E2E. |
| api30-targets-01 | 2/2 PASS:double-tap và review recovery regression;actual Quiz options312×48dp;ratings98.5/98.5/99×56dp, có text labels. Không thay full TalkBack/focus-order audit. |
| api30-attribution-external-01 | FAIL do test không scroll tới nút nguồn ngoài viewport; giữ riêng, chưa phải product defect. |
| api30-attribution-external-02 | 1/1 PASS sau scroll đúng row:actual external window mở offline, Back về Attribution, userdata không đổi. Screenshot lúc external mới mở là màn trắng; không chứng nhận lỗi tải web của browser hoặc successful fetch. |
| api30-attribution-external-03 | 1/1 PASS launch/return sau khi đợi browser render. Kiểm ảnh thấy Chrome “No internet”; Back về Attribution, userdata không đổi. Detector tự động trảfalse, giữ nguyên raw; xác minh ảnh được ghi riêng, không đổi thành automatic assertion PASS. |
| api30-ime-01 | 1/1 PASS actual typing và WindowInsets xác nhận IME hiện;results đúng;Back đóng IME, detail/back giữ query;userdata không đổi. show_ime_with_hard_keyboard được snapshot/restore. |
| api30-long-detail-font130-01 | 1/1 PASS:chọn detail có tổng lemma/meaning/examples dài nhất trong150 từ, assert effective Configuration.fontScale=1.3;scroll đọc đủ meaning/EN/VI/IPA placeholder, userdata không đổi. Không giả đây là stress với text dài vô hạn. |
| ExternalLinksTest trên host | 6/6 PASS,0 fail/error/skip:valid/invalid URL và ActivityNotFoundException/SecurityException được kiểm bằng controlled Context. Không gán thành injected UI failure E2E. |

Attribution có bằng chứng actual offline network-error/return bằng ảnh run03 và helper exception contracts riêng. Đây là hai lớp evidence khác nhau; ảnh được Codex kiểm kỹ thuật, không phải chữ ký review của Nhân. Không dựa vào thành công của browser để đọc ghi công.

## Hành trình và artifact hiện tại

`api30-font130-journey-01`: font_scale được đặt1.3 trước launch; journey1/1 PASS47.532s; force-stop/reopen1/1 PASS4.021s; reboot/reopen1/1 PASS16.072s. Boot-ID đổi, logical snapshots bằng nhau, offline trước từng phase; không reset/reseed giữa phase. Defaults5/10/OFF/20 trước quota10 setup. Video/logs vẫn cần Nhân review. Font original setting null; sau delete/readback hệ điều hành trả default1.0, ghi đúng effective default thay vì tuyên bố raw null giữ nguyên. Network restore accepted=true; animations phục hồi.

App QA SHA256 vẫn `970216a5fcdd53b9e95dce97205c5e745001f06fd8e91de9d6593f8ea33fbfe0`, giống app đã qua journey trước đó. Test APK/source khác được ghi theo run. Audit lại package_qa trên APK này exit0:150 unique stableIds;membership150;packs40/40/40/30;distinct meanings40/40/40/30;150 example pairs;IPA nullable150;FTS mapped150/checks300;integrity ok;0 FK violations. Asset hashes và releaseAuthorized=false được giữ. APK QA7,810,526 bytes không thay release artifact acceptance.

Không sửa DAO/benchmark workload hoặc nới ngưỡng. Kết quả mandatory B02 trên DATA09 release-based/API30 đã được duyệt ở báo cáo trước giữ đúng artifact/source applicability; broad mean26.816ms vẫn chưa đạt mục tiêu15ms. Không gọi đây là before/after optimization so với LDPlayer.

Approval checklist đang yêu cầu canonical target `c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8`. APK được kiểm có metadata snapshot `editorial-qa-v2`, dataset `12aac7bfbe037c9136c3973cc724eee68205b88673ad25f40f7881de50d5398e`, canonical `1a4b3da8396834868b82955a1e03384e6263876302a82774e6677f786e27b60d`. Đây là các identity/đường chuẩn hóa cần owner đối chiếu; không tự coi bằng nhau hoặc chuyển quyền phê duyệt giữa chúng. Technical QA trên internal bundle không chứng nhận release candidate. B03 vẫn HOLD đến khi có mapping và quyết định đúng snapshot/kênh.

## Gate còn mở và hành động tiếp theo

### Lượt hoàn thiện hồ sơ và regression cuối

`api30-final-controlled-01`:12/12 PASS,0 skip, từng invocation exit0 và instrumentation result được kiểm độc lập. Chạy lại toàn bộ12 ca của Qa02ControlledInstrumentedTest trên app APK970216a5... đã audit, font effective1.3; có actual IME, fixture riêng cho mỗi ca và chỉ clear package QA sở hữu. Không gọi lượt này là continuous E2E. Shared-pack/quota8 và generator pool3 kế thừa các run riêng đã ghi ở trên, không gộp counts vào12.

Mỗi ca có command, instrumentation, logcat, video, snapshot tar; source manifest/diff và APK hashes ghi tại root run. Public summary thêm `finalConsolidatedRegression` cùng hashes của manifest/patch/artifact index. Network restoration accepted=true. Readback cuối:font1.0;show_ime_with_hard_keyboard1;animation1.0/1.0/null;network flags1/0/0, đúng cấu hình original ghi trong run. Không bật mạng để cài APK ngoài nguồn.

Matrix đã thống nhất các dòng DEVICE với evidence API30 được duyệt:planner,Discovery controlled search,Attribution và benchmark; các dòng EMU cũ đổi scope HISTORICAL thay vì xóa hoặc gán kết quả mới cho source cũ. AC aggregate vẫn BLOCKED khi chờ review/quyền/privacy. Defect B01/E01/F04 đã bổ sung quyết định và retest hiện hành, giữ nguyên fail và nguyên nhân chưa xác minh. SRS gap review có checklist hiện hành riêng trước phần rà soát ban đầu.

Kiểm kê SDK/AVD hiện tại:chỉ `Small_Phone` API30 được cấu hình và đang kết nối; không có AVD API26/API35. Có system images37.0/37.2 nhưng chưa có AVD, không được coi là thiết bị hiện hữu đã test hoặc tự thay coverage API26/35. API30 thay LDPlayer đúng quyết định người dùng cho acceptance/benchmark; không thay thế nhãn kết quả API26/API35 hoặc TalkBack.

`git diff --check` PASS; production Java/resources/manifest/build profile không đổi. Các thay đổi source là instrumentation/controlled fixtures và shared validation network helper; risk còn lại là tương thích controller/fallback trên API35/API26 chưa retest source mới. Cần commit/push được người dùng cho phép để CI kiểm đúng diff này; rerun workflow source cũ không giải quyết gap. Dừng trước push/cập nhật PR/comment theo giới hạn đã giao.

- [x] F05:5713ba6 đã retest API35/API26 trên cả push/PR CI; FIXED_RETESTED cho shared helper. Xem mục CI mới nhất; lịch sử fail giữ nguyên.
- [ ] QA02-A11Y:API30 chưa cài TalkBack. Người dùng sau đó cho biết không có đường dẫn APK và cho phép để phần này lại; giữ NOT RUN, không gỡ gate hoặc tự tải APK không xác minh. Actual touch targets/labels đã có evidence riêng.
- [ ] F03/NFR15:giữ Android backup theo quyết định người dùng; có Google Backup transport, Backup Manager quan sát disabled. Không trigger backup, không đổi manifest. Chờ PO xác nhận ngoại lệ đúng snapshot; không có privacy PASS.
- [ ] B03:PO/Content Lead phê duyệt quyền snapshot/kênh; release artifact/content vẫn HOLD. Không tự ký.
- [ ] B05:Nhân review video/log/snapshots, applicability, matrix và các giới hạn trên rồi sign-off. Không đánh dấu QA-02 Done.

Final source/APK freeze tại raw `api30-long-detail-font130-01/`:320 input files khớp hashes được lưu ZIP private, app/test APK private và freeze-manifest. Không đưa các tệp này lên GitHub. Scan logcat của journey/font,planner vàtarget retests không thấy app-specific Process crash/ANR markers; vẫn cần review raw đầy đủ.

Các run có manifest/source diff/APK hashes, command/exit, counts, video/logcat và snapshots trong đường dẫn raw local tương ứng; summary public tại `evidence/qa-02/resume-ba02a88/summary.json`. Raw APK/corpus/video/logs không được đưa lên GitHub.
