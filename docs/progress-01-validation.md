# PROGRESS-01 — Kết quả kiểm chứng

## Publish và CI — 02/11/2026, Asia/Saigon (UTC+07)

Người dùng đã yêu cầu thực hiện toàn bộ phần còn lại. Đã push nhánh feature,
cập nhật title/body [PR #13](https://github.com/ManhT005/VocaMind/pull/13).
GitHub xác nhận head `a7bcf2576e33382ef6ae5a737c381592cfba7cad`, base
`fff380127b0808b203165e649e22eaf714027afb`, OPEN, mergeable=true, chưa merged.

| Event | Run / job | Kết quả |
| --- | --- | --- |
| pull_request | [41452870632](https://github.com/ManhT005/VocaMind/actions/runs/41452870632), job 125302115036 | Build & Test SUCCESS |
| push | [41452860074](https://github.com/ManhT005/VocaMind/actions/runs/41452860074), job 125302080385 | Build & Test SUCCESS |

Hai run gắn PR head a7bcf25. Log Checkout của PR run xác nhận thực thi merge ref
`refs/pull/13/merge` tại `dd08e5d8546be0a3c6701232260b7548eb211867`, không nhầm
với head feature. PR run dùng Ubuntu26.04, Temurin17.0.21; Python 46 tests OK,
registry gate, wrapper validation, toolchain verification, unit tests và assembleDebug
đều SUCCESS. Đã đọc jobs/steps và check-runs của head: hai Build & Test checks;
check-runs query trực tiếp merge SHA rỗng dù checkout log chứng minh merge ref.
Không suy ra tổng Android tests từ job status: số 90 là XML local ở phần dưới.

Đã đọc [ruleset 25062147](https://github.com/ManhT005/VocaMind/rules/25062147):
active, áp dụng main/develop/default, yêu cầu qua PR và chặn non-fast-forward,
cho phép merge/squash/rebase; không có required_status_checks hoặc linear_history
trong ruleset đọc được. Endpoint legacy branch protection develop trả 403
Resource not accessible by integration; không tuyên bố đã xác minh phần đó.

Đã gửi [yêu cầu xác nhận DATA-07](https://github.com/ManhT005/VocaMind/pull/13#issuecomment-6578401482)
cho @nhandanghoc (author PR DATA-02 #10), với bốn mục và schema/commit áp dụng.
Chưa có phản hồi xác nhận; tất cả vẫn PENDING. Đã đọc lại bảng phân công native
Drive: PROGRESS-01 Review (70%), DATA-07 và PROGRESS-02 Chưa bắt đầu; không tự
sửa trạng thái người phụ trách hoặc triển khai task ngoài phạm vi để đóng task này.

Bổ sung evidence sau a7bcf25 chỉ sửa docs; source/config/test vẫn là e21cb05.
Sau push commit docs, cần xác minh CI tại head cuối và cập nhật URL/SHA trên PR,
không dùng hai run trên thay cho checks của head mới. Request review Trường
sẽ thực hiện sau khi checks head cuối đạt; kết quả yêu cầu được ghi trên PR.
Không tự Approve/merge hoặc đánh dấu Done thay reviewer/Nhân.

Các mục “chưa push/chưa gửi” trong hồ sơ local dưới đây là trạng thái trước
lượt publish này, giữ nguyên để bảo toàn lịch sử, không phải trạng thái hiện tại.

## Sau tích hợp develop — 02/11/2026, Asia/Saigon (UTC+07)

| Mốc | SHA / kết quả |
| --- | --- |
| Feature trước sửa / PR head đã đọc | d23e6a07ed5e6c4522947d4d1d9f84f85f379897 |
| Develop đã fetch và chọn tích hợp | fff380127b0808b203165e649e22eaf714027afb |
| Merge commit local / source đã kiểm thử | e21cb0577fbe980c6c285c0c31ee7b68f141e1bf |
| Conflict Git báo | Chỉ docs/README.md; giữ các hàng Progress và mọi hàng develop, gồm VOC-DATA-01, source registry/approval, DATA-02, LEARN-01/02 |
| Source Progress thay đổi trong lần fix | Không; không tìm thấy lỗi contract cần sửa |

Merge commit chứa đầy đủ thay đổi develop (kể cả cấu hình build/CI đã có ở
develop); không tự đổi dependency/Gradle/toolchain hoặc source task khác.
Không còn unmerged paths; `git merge-base --is-ancestor` xác nhận develop SHA
đã chọn là tổ tiên của merge commit (exit 0). Trước merge chỉ có thư mục local
untracked; đã giữ nguyên, không stash/reset/clean. Không tìm thấy AGENTS.md
áp dụng trong các thư mục cha đã kiểm tra hoặc cây source.

### Môi trường và lệnh thực chạy

Windows 10 amd64 / PowerShell; Eclipse Temurin **17.0.20.1+1** từ JDK local;
Gradle Wrapper **8.9**; Android SDK theo local.properties. Các lệnh Gradle dùng
JAVA_HOME trỏ JDK 17, GRADLE_USER_HOME trỏ cache người dùng hiện có, được cấp
quyền ghi cache sau lần sandbox lỗi `.zip.lck (Access is denied)`.
Không sửa cấu hình dự án để vượt lỗi môi trường.

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.progress.*' --tests 'com.vocamind.app.feature.progress.*' --rerun-tasks
.\gradlew.bat testDebugUnitTest assembleDebug
git diff --check
git diff --cached --check
```

| Kiểm tra | Exit / kết quả thực tế |
| --- | --- |
| Java / Gradle --version | 0; JDK 17 / Wrapper 8.9 |
| Focused Progress | 0; BUILD SUCCESSFUL 1m35s; 18 tests, 0 failures/errors/skipped |
| Full unit suite + assembleDebug | 0; BUILD SUCCESSFUL 58s; 90 tests, 0 failures/errors/skipped; APK tạo thành công |
| Vocabulary registry gate mới trong CI | 0 qua launcher Python bên dưới; sourceCount=7, eligibleSourceCount=0 (không phải release approval) |
| Vocabulary Python unittest | 0; 46 tests, OK; Python 3.13.9 |
| Whitespace checks | 0 cho cả working tree và index |

Focused task dùng `--rerun-tasks`: 27/27 tasks thực thi; XML timestamp
`2026-11-02T13:17:40` UTC (20:17:40 +07), được copy vào
`build/progress-01-recheck/TEST-*Progress*.xml` trước full suite.
Full suite task `:app:testDebugUnitTest` thực thi lại do bỏ test filters;
compile inputs giữ nguyên nên 37 tasks UP-TO-DATE là hợp lệ. 5 tasks thực thi,
gồm test, dex/merge/package APK; không dùng NO-SOURCE làm bằng chứng.
Toàn bộ source/config/test đã đúng merge tree trước khi chạy; merge commit
chỉ được tạo sau focused run, không đổi inputs của cả hai lượt test.
Các cập nhật evidence tiếp theo chỉ sửa docs; không gán report lịch sử sang SHA mới.

XML full suite tại `app/build/test-results/testDebugUnitTest/TEST-*.xml`:

| Lớp | Tests | Failures / errors / skipped | Timestamp UTC |
| --- | --- | --- | --- |
| CatalogAssetTest | 22 | 0 / 0 / 0 | 13:18:14 |
| UserDatabaseTest | 16 | 0 / 0 / 0 | 13:18:43 |
| LeitnerSchedulerBoundaryTest | 8 | 0 / 0 / 0 | 13:18:47 |
| LeitnerSchedulerSrsTest | 10 | 0 / 0 / 0 | 13:18:47 |
| LeitnerSchedulerTest | 6 | 0 / 0 / 0 | 13:18:47 |
| ProgressDomainTest | 11 | 0 / 0 / 0 | 13:18:47 |
| QuizDomainTest | 10 | 0 / 0 / 0 | 13:18:47 |
| ProgressViewStateTest | 7 | 0 / 0 / 0 | 13:18:47 |

HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.
APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`303d47d87ab4bfe2ae315a57550315d0193c4ddaf2096e170234aa39b76a237f`.

Python embedded local không tự thêm thư mục script vào sys.path, nên gọi
trực tiếp gate ban đầu lỗi `ModuleNotFoundError: source_gate` (exit 1).
Đã xác định lỗi launcher local, không sửa script/CI; chạy lại tương đương:

```powershell
.\.tools\python\python.exe -c "import runpy,sys; sys.path.insert(0,'tools/vocabulary'); sys.argv=['tools/vocabulary/gate_sources.py','--snapshot','docs/data/snapshot.json','--json']; runpy.run_path(sys.argv[0],run_name='__main__')"
.\.tools\python\python.exe -m unittest discover -s tools/vocabulary -p 'test_*.py' -v
```

### CI, review và acceptance còn chờ

Đã đọc lại workflow sau merge: JDK17, Python3.13, registry gate/Python tests,
wrapper validation, toolchain verification, full unit tests, assembleDebug.
Local Gradle/Python đạt không thay thế GitHub wrapper-validation hay CI.
Branch protection/ruleset chưa được xác minh; không kết luận không có quy định.

[PR #13](https://github.com/ManhT005/VocaMind/pull/13) vẫn OPEN, chưa merged,
review Trường Request changes tại d23e6a0. Khi đọc API trước sửa, head=d23e6a0,
base=fff3801, mergeable=false, description trống. Workflow-runs query cho head
trả rỗng; combined-status query cũng trả statuses rỗng. Chưa có kết quả
check-runs riêng để kết luận CI xanh hoặc toàn bộ Actions lỗi.
Chưa push local merge, nên chưa có CI URL/head/merge-ref mới để xác nhận.
PR description đã chuẩn bị ở [bản nháp](progress-01-pr-description.md).
Không gửi request review hoặc tin nhắn cho người khác, không tự merge PR.

Trạng thái: **Technical fixes complete (local)**; **Awaiting data confirmation**,
**Awaiting CI**, **Awaiting review**. Bốn xác nhận của Nhân trong handoff còn
PENDING, không tự đổi saved-only policy. Chưa nghiệm thu DATA-07/PROGRESS-02
hoặc AC-11 end-to-end. Nguồn SRS gốc chưa đọc lại được lần này; xem audit mới.

## Hồ sơ kiểm chứng lịch sử — 25/10/2026

Số 28 tests và JDK21 dưới đây là lịch sử riêng; không dùng làm evidence cho
d23e6a0 hoặc merge e21cb05. Hồ sơ cũ không ghi đủ source SHA của lượt chạy;
không tự suy diễn SHA đó từ timestamp hoặc commit audit.

Ngày: 25/10/2026. Nhánh: `feature/progress-01-contract`.
Base develop: `37f14ca3cdb9086e9960fef988290c0839d5aa3e`.

Đã triển khai model/contract/view state và local tests theo
[kế hoạch](plans/progress-01-contract.md). Chi tiết API, nguồn tài liệu và mapping
nằm trong [handoff](progress-01-handoff.md). Sau triển khai đã đối chiếu trực tiếp
hai file SRS người dùng cung cấp với code/test, XLSX phân công và plan; xem
[audit SRS](progress-01-srs-audit.md). Không phát hiện điểm lệch cần sửa source.

## Môi trường thực thi

- Windows / PowerShell, Gradle Wrapper 8.9, cấu hình Android giữ nguyên.
- JVM thực chạy: Eclipse Temurin **21.0.7**, từ cache JDK có sẵn.
  Source/target của project vẫn là Java 17. Chưa chạy JDK 17 CI cho task này.
- `java` chưa nằm trong PATH mặc định; chỉ đặt JAVA_HOME cho tiến trình kiểm tra.
- Lần thử Gradle trong sandbox bị chặn ghi lock ở `C:\.gradle`, rồi ở cache
  `C:\Users\thanh\.gradle`. Lần chạy được cấp quyền dùng cache có sẵn đã thành công;
  không đổi dependency/version để vượt lỗi môi trường.

Các lệnh Gradle thành công dùng:

```powershell
$env:JAVA_HOME='C:\Users\thanh\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:GRADLE_USER_HOME='C:\Users\thanh\.gradle'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.vocamind.app.domain.progress.*' --tests 'com.vocamind.app.feature.progress.*'
.\gradlew.bat testDebugUnitTest assembleDebug
```

## Kết quả thực chạy

| Kiểm tra | Kết quả |
| --- | --- |
| Focused Android Gradle Progress tests | BUILD SUCCESSFUL, 1m 21s; 18 tests |
| Toàn bộ `testDebugUnitTest assembleDebug` | BUILD SUCCESSFUL, 50s; 28 tests; APK debug được tạo |
| `ProgressDomainTest` | tests=11, failures=0, errors=0, skipped=0 |
| `ProgressViewStateTest` | tests=7, failures=0, errors=0, skipped=0 |
| Hồi quy `QuizDomainTest` | tests=10, failures=0, errors=0, skipped=0 |
| `git diff --check` | PASS; kiểm tra whitespace riêng cho source/test mới cũng không có lỗi |
| Domain dependency scan | Chỉ dùng Java standard library; không import Android/AndroidX/data/feature |

Đã đọc XML của lần chạy cuối tại
`app/build/test-results/testDebugUnitTest/TEST-*.xml`, timestamp
`2026-10-25T10:23:06` (UTC); không dùng report cũ hoặc NO-SOURCE làm bằng chứng.
Report HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

Coverage hành vi đã kiểm chứng:

- Summary 0; fixture [2,3,4,5,6,7] → total 27, nhóm 5/9/13; từng biên box.
- Null, số box sai, count/total âm, tổng lệch, count vượt int và tràn long;
  defensive copy hai chiều của array.
- History ID/time/count/score sai; mọi tổ hợp correct 0..N với N=4..10,
  gồm 0%, 100%, 17%, 29%; wrongCount dẫn xuất.
- Sort theo thời gian rồi ID, giá trị Long.MAX_VALUE, history >10 record,
  không sửa thứ tự input; reject null/duplicate ID; list read-only.
- Success/failure payload riêng; fake repository/use case đọc lại khi retry;
  invalid persisted score → INVALID_DATA, không bị chuyển thành empty.
- LOADING không có số liệu giả; EMPTY thành công; CONTENT với một hoặc cả hai
  section có dữ liệu; ERROR giữ typed failure, cho retry.
- Box/group labels, empty/error copy và Mastered disclaimer trong view contract.

## Checklist bàn giao

- [x] Model summary/history/snapshot bất biến, có invariant và validation.
- [x] Repository/use case interface và test double, không phụ thuộc DB thật.
- [x] View states/section empty/retry/labels được đặc tả và test.
- [x] Focused tests, full regression và debug build thành công bằng Android Gradle.
- [x] Không sửa schema/DAO/Quiz/Leitner/UI/Gradle và không thêm filter learnedAt/pack.
- [x] Handoff rõ trách nhiệm DATA-07 / PROGRESS-02 và saved-only semantics.
- [x] Đối chiếu trực tiếp SRS baseline v1.1 và SRS Android v1.0; ma trận audit phân biệt contract với DB/UI acceptance.
- [ ] Nhân xác nhận mapping/aggregation và policy saved-only row; Trường review.
- [ ] PR vào develop và xử lý phản hồi review.

Phần implementation local đã hoàn thành; chưa coi toàn bộ quy trình Done của nhóm
đã hoàn tất. Tại thời điểm kiểm chứng trước commit/push, chưa tạo PR/merge
hoặc ghi trạng thái Done vào bảng phân công.
Không có DB integration, device/UI/E2E/performance test hoặc CI run trong task này;
AC-11 end-to-end cần DATA-07 và PROGRESS-02.
