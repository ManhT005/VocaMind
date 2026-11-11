> Latest technical retests: [qa-02-resume-ba02a88.md](qa-02-resume-ba02a88.md). CI ba02a88 PR SUCCESS / push API35 precondition FAIL; local controller fix passed host+API30, API35/API26 CI still pending. Planner/Quiz/IME/font/Attribution coverage updated. TalkBack deferred by user; privacy/rights/sign-off remain open. Earlier sections are historical.

> Latest stop snapshot: [blocker retests](qa-02-blocker-retest-20261111.md). F01 verified/fixed; approved API30 journey/reopen/reboot and mandatory benchmark retest PASS; controlled5/5 PASS. Privacy/rights/sign-off and remaining P0 still open. Earlier entries are historical.

> Current-source continuation (2026-11-11): [verified results and limitations](qa-02-continuation-20261111.md). Both current-head CI workflows succeeded; ZIP reports remain HTTP401, so F01 is not closed. Historical pending/failing CI statements below describe earlier runs. New API30 journey blocked before Activity; B02 remains FAIL; backup policy requires review.

# QA-02 — Nghiệm thu P0

## Chặn môi trường API35

Đã tải và xác minh command-line tools chính thức; Android CLI tải đầy đủ
image API35 Google APIs x86_64 r09. Cài đặt lỗi khi giải nén96%:
`There is not enough space on the disk`. API35 chưa cài thành công và chưa chạy.
Hồ sơ chờ người có thẩm quyền duyệt nằm tại `qa-02-approval-request.md`;
quyền nội dung và reviewer vẫn chưa ký.

## Evidence bổ sung đã thu hồi

`build/qa02/ldplayer-components-recovery/` có output hoàn tất:8/8 UI cases,
20/20 catalog upgrade contracts PASS;0 skip. Đã kiểm tra2 status records/test
và40 status records cho20 upgrade tests. Scope:Attribution lifecycle/long text,
Search EN–VI/Save/route/read failure+retry,Quiz lifecycle/abandon,DATA08 upgrade.
Summary public:`evidence/qa-02/ldplayer-components-20261110/summary.json`.
Đây là fixture APK đã cài trước thay đổi comparator mới; không chứng nhận toàn
bộ P0 hoặc APK production mới.

## Trạng thái mới nhất sau tối ưu FTS

**Lượt xác minh tiếp theo không đạt performance gate.** Raw đã thu tại
`build/qa02/ldplayer-startup-final-check/`: broad FTS mean42,500ms/max130,341ms;
startup hợp lệ2206..7856ms, một số lần quay về launcher. Vì vậy lần đo FTS
49,020ms trước đó chỉ là một lượt PASS, chưa chứng minh ổn định. B02 vẫn FAIL.
Đã khởi chạy bộ8 UI components và20 catalog-upgrade contracts bằng
`tools/validation/run_qa02_ldplayer_components.sh`; kết quả đang chờ thu,
chưa được tính PASS. Tối ưu DAO mới cần rebuild/rerun toàn bộ journey trước
khi đóng QA-02; dữ liệu các lượt cũ không tự chứng nhận APK mới.

`CatalogDao.searchVocabulary` đọc đầy đủ kết quả rồi sort trong Java để tránh
temporary B-tree của SQLite. Test tập trung: FtsSearchTest 20/20 và
FtsSearchBenchmarkTest 2/2 PASS. Raw retest LDPlayer nằm tại
`build/qa02/ldplayer-benchmark-retest-20261110/fts.txt`: cả 5 workloads đạt ngưỡng
bắt buộc mọi mẫu <50ms; workload 5.000 rows mean34,998ms/max49,020ms. Target
mean<15ms chưa đạt. Đây là kết quả đo, chưa thay thế đầy đủ gate qualification.
Startup vẫn chưa đạt: lần retest có MainActivity1874..2786ms và các mẫu trả launcher.
Đang chạy lại với cooldown giữa force-stop/start; chưa kết luận PASS.

Matrix đã thay scope PHYSICAL bằng APPROVED_LDPLAYER và bỏ blocker B01 đã giải
quyết. Các ca chưa chạy chuyển NOT RUN; không tự chuyển PASS. Vì DAO production
đã đổi, các evidence journey trước thay đổi vẫn là lịch sử và cần đánh giá/rerun
trên APK mới trước nghiệm thu cuối.

## Quyết định thiết bị thay thế 10/11/2026

Chủ dự án xác nhận: **“Tôi chấp nhận LDPlayer là thiết bị nghiệm thu và benchmark
thay thế cho QA-02.”** Vì vậy B01 (thiếu thiết bị thật) được gỡ và LDPlayer G82U8
được dùng cho các dòng DEVICE/reference kể từ quyết định này. Quyết định chỉ thay
gate loại thiết bị; từng ca vẫn phải chạy và chỉ được PASS khi có evidence. B02
chuyển từ thiếu thiết bị tham chiếu sang chờ thực thi benchmark trên LDPlayer.
Quyết định này không tự thay thế phê duyệt quyền nội dung/phát hành hay reviewer.

### Kết quả trên thiết bị thay thế đã duyệt

- `ldplayer-approved-20261110`: hành trình chính chạy offline PASS, 1/1 test,
  55,536 giây; raw local `build/qa02/ldplayer-20261110/offlineJourney-approved.txt`.
- Force-stop/reopen offline PASS, 1/1 test, 4,262 giây; snapshot userdata và
  catalog oracle giữ nguyên; raw local
  `build/qa02/ldplayer-20261110/force-stop-reopen-approved.txt`.
- Reboot/reopen offline PASS, 1/1 test, 5,325 giây; raw local
  `build/qa02/ldplayer-20261110/reboot-reopen-approved.txt`. E10 DEVICE đã PASS.
- Bổ sung trên LDPlayer: 39/39 SQLite contracts PASS. Triple-tap lần đầu FAIL
  do synchronous injection tạo gap 282ms; harness chuyển sang async injection,
  giữ ngưỡng 150..200ms và retest PASS 1/1 cùng assertions transactional/next card.
- Benchmark DATA-09 trên thiết bị thay thế đã duyệt: 11 startup attempts và FTS
  fixture 5.000 từ đã giữ raw tại `build/qa02/ldplayer-benchmark-20261110/`.
  Kết quả **FAIL**: workload `benchword` materialize 5.000 rows có mean 45,897ms,
  max 157,923ms (gate yêu cầu mọi mẫu <50ms; target mean <15ms). Nhiều startup
  attempt không mở đúng MainActivity và các mẫu hợp lệ có giá trị vượt 1.500ms.
  Không nới ngưỡng hoặc ghi PASS. B02 chuyển từ chờ thiết bị sang lỗi benchmark.
- API35 chưa chạy: SDK hiện chỉ có system image API37; máy không có
  `sdkmanager`/API35 image để tạo đúng AVD. API37 không được dùng để giả thành API35.
- APK SHA-256:
  `3bab0397cc1687fc20a6ff8ca5c8096b4a7c2b1f294e0a0056f71e68072d70d7`.
- Các dòng E01, E02, E03, E05, E06, E07, E08 DEVICE được chuyển PASS theo
  phạm vi assertion của hành trình. E04 due-first boundary, E09 external-link và
  phần reboot của E10 vẫn chưa được suy diễn thành PASS.

## Cập nhật LDPlayer 10/11/2026

Đã kết nối LDPlayer `127.0.0.1:5555` (model G82U8), build thành công
`assembleCatalogQa` và `assembleCatalogQaAndroidTest` từ working tree hiện tại,
rồi cài hai APK QA cách ly. Raw output ở
`build/qa02/ldplayer-20261110/offlineJourney.txt` (ignored, local).
Lần gọi đúng `qa02Phase=journey` dừng trước Activity: kiểm tra offline báo
`Airplane mode before launch expected:<1> but was:<0>`; **FAIL do precondition**, không phải
PASS hành trình hay lỗi production. Hai lần gọi ban đầu thiếu reset/phase đã được
khắc phục, không tính là test app. Trước đó LDPlayer mất ADB khi tắt Wi-Fi;
chưa có cách giữ ADB và chứng minh offline đồng thời. Không cập nhật các ca
PHYSICAL thành PASS hoặc đánh dấu QA-02 Done. Các gate thiết bị thật/benchmark
tham chiếu, quyền phát hành, API35 CI, P0 còn thiếu và reviewer vẫn mở.

Sau đó đã khôi phục LDPlayer (`airplane_mode_on=0`, boot complete) và dừng thay đổi
cấu hình mạng. `tools/validation/test_qa02_runner.py` chạy lại 4/4 PASS. Lượt
`testDebugUnitTest` trên máy hiện tại không tạo được SQLite/Robolectric database
(`SQLITE_CANTOPEN`/`AccessDeniedException` trong thư mục tạm), nên bị dừng và
không được ghi là app FAIL hay PASS. Trạng thái ma trận vẫn là 12 PASS, 34 BLOCKED,
10 NOT RUN, 1 FAIL.

**IN PROGRESS — chưa đủ điều kiện Done.** Ngày chạy:09/11/2026. Nhánh
`feature/qa-02-e2e-acceptance`, base `440f77643063310abe88067ba9d49198571efe67`.
[Kế hoạch](plans/qa-02-e2e-acceptance.md) có nội dung Git diff giống bản Downloads
người dùng gửi; SHA file khác vì line endings. WEEK-2-GATE đã được Thành xác nhận.
Người thao tác tự động: Codex hỗ trợ Thành. Nhân chưa review/sign-off; không ghi
Thi/Trường/Nhân là người đã chạy hoặc duyệt. Chưa commit/push QA-02 trong lượt này.

## Kết quả đã có

| Run | Scope và kết quả |
|---|---|
| [sdk28-04](evidence/qa-02/sdk28-04/summary.json) | PASS 3 invocation: hành trình liên tục,force-stop/reopen,reboot/reopen trên Android SDK emulator API28/Android9. |
| [sdk28-supplement-02](evidence/qa-02/sdk28-supplement-02/summary.json) | PASS 39 SQLite contracts:21 atomic review,17 aggregation,1 Quiz migration/idempotency; PASS triple-tap thực tế152/152ms. |
| [sdk28-components-01](evidence/qa-02/sdk28-components-01/summary.json) | PASS 8 component cases: Attribution offline/lifecycle/long text; Search EN–VI/reset/Save/opaque route/real Room read failure+retry; Quiz lifecycle/abandon. Injection được khai báo, không thay hành trình production. |
| [sdk28-upgrade-01](evidence/qa-02/sdk28-upgrade-01/summary.json) | PASS 20 DATA-08 contracts trên DB UUID riêng:runtime corruption,migration v1→v2,update/rollback,fault boundaries,stableId protection,không xóa userdata lỗi. |
| [CI baseline](evidence/qa-02/ci-baseline/summary.json) | 596 unit tests,0 fail/error/skip; lint0 Error/Fatal. Discovery API26 và Quiz/DATA08 reboot API28 xanh. Discovery API35 đỏ; toàn workflow chưa xanh. |
| Host runners | 25 UI02 host tests +4 QA02 runner tests PASS; không tính thành test app trên điện thoại. |

CI [42610694082](https://github.com/ManhT005/VocaMind/actions/runs/42610694082)
thuộc head440f776, thực thi checkout merge `2b7d40fe932cd16ad3c89ffd10ce0aec232c415f`.
Đã tải artifact, kiểm SHA256 GitHub cung cấp, đối chiếu **303 tracked input files**
với baseline:0 mismatch; trong đó181 file `app/src/main` giống hệt. Đây là hỗ trợ
production contracts không đổi, không phải CI cho harness QA02 mới chưa push.
Thay đổi hiện tại chỉ ở opt-in build profile và test/host harness; không sửa production
Java/resources/assets hay scheduler/transaction semantics.

## Artifact và điều kiện chạy

Main profile: `-PcatalogQa=true -Pqa02Acceptance=true`, package
`com.vocamind.app.qa02validation.qa`, version0.1.0/1,min26/target35.
Suffix `.qa` giữ policy internal QA. Profile từ chối flag QA không tương thích;
không dùng/clear `com.vocamind.app` hoặc dữ liệu cá nhân.

[APK verification](evidence/qa-02/preparation/apk-verification.json): đúng asset set/hash,
150 stableIds,4 packs40/40/40/30,integrity `ok`,0 foreign-key violations,300 FTS correctness
checks. Cả150 từ có IPA null; main kiểm fallback, baseline Discovery fixture có cả IPA/null.
APK QA7,734,558 bytes **không phải release APK**; không dùng nó chốt AC-14, không lấy150
từ thay benchmark5000. `releaseAuthorized=false`.

Main APK SHA256:
`3bab0397cc1687fc20a6ff8ca5c8096b4a7c2b1f294e0a0056f71e68072d70d7`.
Test APK/catalog hashes và full source manifest nằm trong report từng run.
Source của sdk28-04:base440f776 + sources digest
`9c0cc98a7070e2e1d62d74a9bbb5a2f7db96d8f040f9d2283543bef54cb497fa`;
đây là working-tree snapshot, không giả thành commit đã publish.

SDK emulator: `Android/sdk_phone_x86_64/generic_x86_64:9/PSR1.180720.012/4923214:userdebug/test-keys`,
720×1280/320dpi,MemTotal1,530,876kB,timezone `Asia/Bangkok` (UTC+7).
Không gọi đây là Helio G85/3GB/Android10. Airplane1/Wi-Fi0/data0,effective Wi-Fi disabled
và default network `none` được xác nhận trước Activity/từng invocation. Sau reboot có
ghi raw flags và kiểm lại offline trước reopen; không tuyên bố mạng tắt liên tục trong boot.

## Hành trình và đối chiếu dữ liệu

Trước Activity đầu tiên, kiểm default quota5 rồi cấu hình quota10 cho QA; không seed
pack/progress/review/Quiz. Sau đó main journey thay đổi dữ liệu qua UI, không clear/seed
giữa bước. Oracle catalog là copy riêng, không prewarm startup catalog của app. Expected
Today từ membership/active/progress; scheduler dùng literal rules/ngày lịch địa phương;
Quiz score từ packaged meanings; Progress từ userdata, không lấy kết quả use case làm
expected duy nhất.

Ba pack active; pack thứ tư activate rồi pause. Save/Unsave/Save một từ thuộc pack paused
tạo1 saved-only row đúng contract, loại khỏi Today. Due0/new10 và exact10 IDs/quota/
active-only/dedup PASS; **nonempty due-first/overlap/quota boundaries trên device chưa đủ**.

Flashcard:10 initial IDs +4 relearn =14 ReviewEvent. Đối chiếu mỗi rating/box/due/learnedAt/
lastReviewedAt/reviewCount/mistakes/leech; FORGOT chỉ thêm một relearn mỗi ID,queue hữu hạn.
Actual landscape/portrait và recreate giữ flipped card/answered Quiz. Quiz10 unique
eligible IDs,4 options khác nhau; điểm tính độc lập,1 completed attempt; Back/continue,
rotate/recreate và result/reopen không ghi lặp. Progress:4 chỉ số,6 boxes,history và
Mastered disclaimer khớp DB; Quiz không đổi word progress. **11 progress rows** gồm10
từ đã review +1 saved-only, không diễn giải thành11 từ đã review.

Snapshot đọc trong Room transaction:UserPreference/UserPack/UserWordProgress/ReviewEvent/
QuizAttempt/sqlite_sequence/schema. Force-stop và reboot (boot ID đổi) giữ nguyên tất cả;
catalog logical SHA sau reopen/reboot cũng khớp. Không copy SQLite main file bỏ qua WAL.
Attribution corpus150 đọc offline; component fixture kiểm source/license/navigation/long
text. **Main external-link failure/return chưa chạy**, không ghi PASS toàn bước9.

## Lỗi và gate còn mở

- `F01`: CI API35 click row chỉ hiện một phần. Helper đã scroll có offset và đợi ≥90%
  target hiện; API28 row actions PASS. **Chưa retest API35/chưa CI xanh cho code mới**,
  giữ OPEN/FAIL.
- `F02`: injector cũ dùng Espresso idle processing kéo gap thành3616ms. Đổi actual input
  injection sang instrumentation thread, giữ assertions150..200ms; retest152/152ms PASS.
  Không nới ngưỡng hoặc sửa logic chống duplicate của app.
- Hai lượt main đầu gặp race của harness:list loading/nút activation chưa enabled.
  Đã chờ state sẵn sàng; sdk28-04 PASS, giữ raw lượt lỗi.
- LDPlayer mất ADB khi `svc wifi disable`, trước Activity. Đã reboot/reconnect/restore;
  [readback](evidence/qa-02/preparation/ldplayer-recovery.json):Airplane0/Wi-Fi1(enabled)/data0.
  Không ghi LDPlayer application tests PASS.

[Matrix57 rows](qa-02-requirement-test-matrix.csv):12 PASS,34 BLOCKED,10 NOT RUN,1 FAIL.
PASS theo scope từng dòng, không cộng thành nghiệm thu tổng thể.
[Defect/blocker log](qa-02-defects.csv) có owner/evidence/retest.

1. **B01:thiết bị thật chưa có.** Thành xác nhận chỉ có LDPlayer; toàn hành trình/ca bắt
   buộc physical chưa chạy. Emulator không thay điều kiện này.
2. **B02:benchmark tham chiếu chưa có.** Chưa chạy startup first-launch +≥10 cold starts,
   FTS5000/workloads/ngưỡng50ms/target15ms trên reference hoặc approved substitute.
3. **B03:rights/release HOLD.** PO chưa ký,`releaseAuthorized=false`; chưa nhận hồ sơ mới.
   Không tự ký/đổi ledger hoặc coi internal QA là quyền phân phối.
4. **F01:CI API35 còn fail**, cần retest/current-head CI.
5. **B05:Nhân chưa review/sign-off** sau khi hoàn tất toàn P0.

P0 còn NOT RUN/partial:device due-first/no-pack/paused-only/no-due/quota/overlap;
independent empty Progress/history và full screen/font/IME/TalkBack matrix; injected
review-failure UI/retry; controlled multi-session leech4 và cả hai cách clear;
Quiz pool<4/actual double-tap; main offline external link. Host/SQLite/component/CI hỗ trợ
đã ghi rõ, không giả thành lượt device đã chạy. Tiếp tục trong QA-02 trên thiết bị phù
hợp; không dời QA-03 hoặc chốt “Done code” thay nghiệm thu.

## Chạy lại và evidence

Build tuần tự với JDK17/Gradle8.9/Python thực; một worker,heap768m trên máy này.
Chỉ định serial rõ ràng; không connected tests lên hai alias cùng LDPlayer. Ví dụ
giả định toolchain đã có trên PATH:

```powershell
.\gradlew.bat :app:assembleCatalogQa :app:assembleCatalogQaAndroidTest -PcatalogQa=true -Pqa02Acceptance=true --no-daemon --max-workers=1
python tools/validation/run_qa02_device.py --adb <adb.exe> --serial <SDK-serial> --aapt <aapt.exe> --output build/qa02/<new-run> --reboot
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -Pflash03DeviceIsolation=true --no-daemon --max-workers=1
python tools/validation/run_qa02_supplements.py --adb <adb.exe> --serial <SDK-serial> --aapt <aapt.exe> --output build/qa02/<new-supplement>
python tools/validation/run_qa02_supplements.py --adb <adb.exe> --serial <SDK-serial> --aapt <aapt.exe> --output build/qa02/<new-components> --ui-components-only
python tools/validation/run_qa02_supplements.py --adb <adb.exe> --serial <SDK-serial> --aapt <aapt.exe> --output build/qa02/<new-upgrade> --catalog-upgrade-only
```

Supplemental package `com.vocamind.app.flash03validation` dùng fixture4 từ khớp IDs cố
định; DB UUID riêng hoặc reset từng component độc lập. Không chạy contract fixture4 trên
corpus150 rồi coi setup error là app bug; fixture4 không thay AC-02. Main userdata khác
package, không bị các lượt này reset.

Ignored `build/qa02/<run>/` giữ command JSON,source/APK hashes,raw instrumentation/logcat,
network snapshot/restore,video và `qa02.tar` chứa PNG/timeline/logical snapshots/oracles.
Không upload raw log/network/corpus/APK vào repo. Public reports giữ metadata/hash và
trỏ raw cục bộ. Reviewer cần xem video/raw; file video tồn tại không phải sign-off.
Main video90.762s,force-stop10.53s,reboot probe14.31s,có finalized MP4 metadata,không cắt
bởi limit180s. Checkpoint screenshot chỉ khi active window thuộc package QA; failure
raw giữ riêng vì có full device log. Các cấu hình mạng đã restore và kiểm readback.
