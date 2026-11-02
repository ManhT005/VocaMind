# ARC-03 — CI Foundation cho VocaMind

**Project:** VocaMind\
**Task ID:** ARC-03\
**Nhóm:** Architecture / DevEx / Quality Gate\
**Tên task:** CI Foundation\
**Requirement:** NFR-VM-18\
**Branch đề xuất:** `feature/arc-03-ci-foundation`\
**Base branch:** `develop` sau khi ARC-02 được rebase/merge\
**Ưu tiên:** P0\
**Ước lượng:** 0.5–1 ngày\
**Phạm vi:** GitHub Actions CI cho build + local unit test

---

## 1. Kết quả review ARC-02 trước khi sang ARC-03

Đã kiểm tra trực tiếp nhánh:

```text
feature/arc-02-navigation-shell
```

### 1.1. Những phần ARC-02 đã đạt

Implementation hiện có đầy đủ application shell:

```text
MainActivity
→ NavHostFragment
→ Home
   ├── Packs
   ├── Vocabulary
   ├── Today
   ├── Quiz
   └── Progress
```

Đã xác nhận trên code:

- `MainActivity` dùng `AppCompatActivity`;
- ViewBinding được dùng qua `ActivityMainBinding`;
- có `FragmentContainerView`;
- `NavHostFragment` là default host;
- `nav_graph.xml` có đủ 6 destination;
- `HomeFragment` có navigation tới 5 destination còn lại;
- các destination feature là Fragment placeholder;
- Manifest vẫn chỉ có một launcher Activity;
- không có `INTERNET` permission;
- Material Components được thêm để hỗ trợ shell UI;
- `Home` là start destination.

Validation document của ARC-02 ghi nhận:

```text
clean assembleDebug                   PASS
compileDebugJavaWithJavac            PASS
testDebugUnitTest                    NO-SOURCE
lintDebug                            PASS (0 errors)
Cold launch → Home                   PASS
Home → các route → Back → Home       PASS
Rotate destination                   PASS
Airplane Mode                        PASS
No crash                             PASS
```

Người phát triển cũng đã xác nhận project kết nối/chạy được trong Android Studio.

### 1.2. Việc còn lại trước khi đóng ARC-02

ARC-02 hiện chưa hoàn toàn sạch về Git history:

```text
feature/arc-02-navigation-shell
ahead develop:   2 commits
behind develop:  1 commit
status:          diverged
```

Nguyên nhân: ARC-02 được tạo từ commit ARC-01 trước khi PR #2 được merge; sau đó ARC-01 đã merge vào `develop`.

PR #2 hiện đã:

```text
CLOSED
MERGED
merge commit: 91f6721...
```

Không tìm thấy PR hiện tại cho branch ARC-02.

### Hành động bắt buộc trước ARC-03

```bash
git checkout feature/arc-02-navigation-shell
git fetch origin
git rebase origin/develop
```

Nếu có conflict:

```bash
# resolve
git add <files>
git rebase --continue
```

Sau rebase:

```bash
./gradlew clean assembleDebug
./gradlew :app:compileDebugJavaWithJavac
git status
```

Sau đó push:

```bash
git push --force-with-lease origin feature/arc-02-navigation-shell
```

Mở PR:

```text
feature/arc-02-navigation-shell
→ develop
```

Merge ARC-02 trước khi tạo ARC-03.

> Kết luận: **ARC-02 đạt yêu cầu kỹ thuật và runtime; chỉ còn rebase + PR/merge để hoàn tất integration gate.**

---

## 2. Mục tiêu ARC-03

Thiết lập CI chuẩn cho repository để mọi Pull Request quan trọng đều được kiểm tra tự động.

Sau ARC-03:

```text
Developer push / PR
        ↓
GitHub Actions
        ↓
Checkout
        ↓
JDK 17
        ↓
Gradle cache
        ↓
Wrapper validation
        ↓
JUnit
        ↓
assembleDebug
        ↓
PASS → Green
FAIL → Red
```

Mục tiêu cụ thể:

1. GitHub Actions chạy khi có PR vào `develop` hoặc `main`;
2. chạy lại khi có push vào `develop` hoặc `main`;
3. dùng JDK 17;
4. dùng Gradle Wrapper của repo;
5. cache Gradle dependency/build state hợp lý;
6. chạy `testDebugUnitTest`;
7. chạy `assembleDebug`;
8. bất kỳ test/build failure nào làm workflow đỏ;
9. không dùng `continue-on-error`;
10. không cần secrets;
11. chuẩn bị status check ổn định để dùng làm branch protection gate.

---

## 3. Liên hệ NFR-VM-18

NFR-VM-18 yêu cầu thay đổi phải được:

```text
build
+
chạy JUnit
+
được kiểm chứng trong CI
```

ARC-03 hiện thực hóa requirement này ở repository level.

ARC-03 **không định nghĩa coverage threshold**.

Coverage nếu cần sẽ thuộc task QA/quality riêng.

---

## 4. Không nằm trong phạm vi ARC-03

Không triển khai:

```text
Espresso / instrumentation emulator CI
API 26 emulator matrix
release signing
Play Store publish
Firebase Test Lab
SonarQube
CodeQL custom policy
coverage gate
APK release pipeline
CD / deployment
semantic release
dependency update bot
```

Không over-engineer CI ở sprint đầu.

ARC-03 chỉ làm:

```text
build + JUnit gate
```

---

## 5. Precondition

ARC-03 chỉ bắt đầu sau khi:

```text
ARC-01 merged
ARC-02 merged
develop build local PASS
```

Commands:

```bash
git checkout develop
git pull origin develop

./gradlew clean assembleDebug
./gradlew testDebugUnitTest
```

Nếu local build fail:

```text
không tạo CI để che lỗi local
```

Sửa baseline trước.

---

## 6. Tạo branch

```bash
git checkout develop
git pull origin develop

git checkout -b feature/arc-03-ci-foundation
```

Kiểm tra:

```bash
git branch --show-current
git status
```

Expected:

```text
feature/arc-03-ci-foundation
working tree clean
```

---

## 7. File chính cần tạo

Tạo:

```text
.github/workflows/android-ci.yml
```

Expected repository:

```text
VocaMind/
├── .github/
│   └── workflows/
│       └── android-ci.yml
├── app/
├── gradle/
├── gradlew
├── gradlew.bat
└── ...
```

ARC-03 không cần sửa feature code.

---

## 8. Thiết kế trigger

Workflow phải chạy trên:

```text
Pull Request → develop
Pull Request → main
Push → develop
Push → main
```

Target:

```yaml
on:
  pull_request:
    branches:
      - develop
      - main

  push:
    branches:
      - develop
      - main
```

PR event để ngăn lỗi trước merge; push event để xác minh trạng thái thật sau merge.

---

## 9. Quyền tối thiểu

CI chỉ cần đọc source.

Dùng:

```yaml
permissions:
  contents: read
```

Không cấp quyền ghi nếu không cần.

---

## 10. Concurrency

Khuyến nghị:

```yaml
concurrency:
  group: android-ci-${{ github.ref }}
  cancel-in-progress: true
```

Push mới vào cùng PR sẽ hủy run cũ, giảm thời gian CI.

---

## 11. Runner

Dùng:

```yaml
runs-on: ubuntu-latest
```

Không cần Windows runner cho baseline.

---

## 12. Checkout

Dùng:

```yaml
- name: Checkout
  uses: actions/checkout@v4
```

---

## 13. Setup JDK 17

Dùng Temurin:

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '17'
```

CI phải giữ đúng baseline ARC-01.

---

## 14. Validate Gradle Wrapper

Khuyến nghị:

```yaml
- name: Validate Gradle Wrapper
  uses: gradle/actions/wrapper-validation@v4
```

Repo đã commit `gradle-wrapper.jar`, `gradle-wrapper.properties` và checksum ở ARC-01.

---

## 15. Setup Gradle + Cache

Dùng:

```yaml
- name: Set up Gradle
  uses: gradle/actions/setup-gradle@v4
```

Không thêm `actions/cache` song song cho cùng Gradle directories nếu chưa có nhu cầu riêng.

---

## 16. Environment verification

```yaml
- name: Verify toolchain
  run: |
    java -version
    ./gradlew --version
```

Expected:

```text
Java 17
Gradle 8.9
```

---

## 17. Test step

```yaml
- name: Run unit tests
  run: ./gradlew testDebugUnitTest
```

Không dùng:

```yaml
continue-on-error: true
```

Nếu JUnit fail:

```text
step fail
→ job fail
→ workflow red
→ PR red
```

Hiện repo chưa có local unit test thật nên có thể thấy `NO-SOURCE`. Điều này chấp nhận được ở ARC-03; khi các task sau thêm JUnit, cùng command này sẽ chạy thật.

---

## 18. Build step

```yaml
- name: Assemble debug APK
  run: ./gradlew assembleDebug
```

Compile/resource/manifest/dependency lỗi phải làm workflow đỏ.

Nên tách test và build thành hai step để dễ đọc log.

---

## 19. Workflow target hoàn chỉnh

`.github/workflows/android-ci.yml` nên tương đương:

```yaml
name: Android CI

on:
  pull_request:
    branches:
      - develop
      - main
  push:
    branches:
      - develop
      - main

permissions:
  contents: read

concurrency:
  group: android-ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-test:
    name: Build & Test
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Validate Gradle Wrapper
        uses: gradle/actions/wrapper-validation@v4

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Verify toolchain
        run: |
          java -version
          ./gradlew --version

      - name: Run unit tests
        run: ./gradlew testDebugUnitTest

      - name: Assemble debug APK
        run: ./gradlew assembleDebug
```

Tên job nên giữ ổn định: `Build & Test`.

---

## 20. Có cần `clean` trong CI không?

Không bắt buộc.

Khuyến nghị chạy:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

thay vì `clean` mỗi lần để tận dụng Gradle cache/incremental build.

---

## 21. Có cần chmod wrapper?

Không thêm `chmod +x gradlew` mặc định nếu executable bit trong repo đã đúng.

Nếu runner thực tế báo `Permission denied`, kiểm tra executable bit trong Git thay vì workaround vội.

---

## 22. Test workflow trên Pull Request

```bash
git add .github/workflows/android-ci.yml
git commit -m "ci: add Android build and unit test workflow"
git push -u origin feature/arc-03-ci-foundation
```

Mở PR:

```text
feature/arc-03-ci-foundation
→ develop
```

Expected:

```text
Checks
└── Android CI / Build & Test
    ├── Checkout
    ├── Set up JDK 17
    ├── Validate Gradle Wrapper
    ├── Set up Gradle
    ├── Verify toolchain
    ├── Run unit tests
    └── Assemble debug APK
```

---

## 23. Negative test bắt buộc

Chỉ thấy green chưa chứng minh gate hoạt động đúng.

Tạo một compile error tạm trên branch thử nghiệm, push và xác nhận:

```text
Assemble debug APK → FAIL
Workflow → RED
```

Sau đó revert lỗi ngay.

Không merge commit lỗi vào `develop`.

Nếu sau này đã có JUnit thật, có thể thêm một test fail tạm để xác nhận `Run unit tests` cũng làm workflow đỏ.

---

## 24. Branch protection / Ruleset

Workflow đỏ chưa chắc đã chặn merge nếu branch chưa require status check.

Để đạt yêu cầu "PR fail build/test phải đỏ và không được merge", sau khi workflow chạy ít nhất một lần, vào:

```text
Repository
→ Settings
→ Branches / Rules
```

Cho `develop`:

```text
Require a pull request before merging
Require status checks to pass before merging
```

Required check:

```text
Android CI / Build & Test
```

Khuyến nghị tương tự cho `main`.

Connector hiện không đọc được branch-protection của repo vì GitHub App trả 403, nên bước này cần repo admin xác nhận trực tiếp trên GitHub UI.

---

## 25. Quy tắc merge

Sau ARC-03:

```text
CI green → có thể review/merge
CI red   → không merge
```

Không dùng `continue-on-error` để tạo workflow xanh giả.

---

## 26. Cache verification

Ở run đầu có thể cache miss; run sau phải thấy Gradle cache được restore/setup bình thường.

Acceptance chỉ cần cache setup không lỗi, không lấy tốc độ làm gate.

---

## 27. CI logs cần đọc khi fail

### JDK fail
Kiểm tra `Set up JDK 17` và `Verify toolchain`.

### Dependency fail
Kiểm tra repositories, dependency version và sự cố mạng.

### Test fail
Mở log `Run unit tests`, tìm test class/method/assertion.

### Build fail
Mở `Assemble debug APK`, kiểm tra compile/XML/Manifest/annotation processing/dependency conflict.

---

## 28. Optional — Upload test report

Không bắt buộc cho ARC-03. Nếu cần evidence có thể thêm sau bằng `actions/upload-artifact@v4`.

---

## 29. Optional — Upload debug APK

Không bắt buộc. Có thể để task release/build artifact riêng.

---

## 30. Documentation cần cập nhật

Sau CI pass, cập nhật:

```text
README.md
docs/android-foundation.md
docs/arc-03-validation.md
```

`docs/arc-03-validation.md` nên ghi ngắn gọn:

```text
Workflow name
Trigger
JDK version
Gradle version
Run URL
Test result
Assemble result
Negative-test result
Branch protection state
```

---

## 31. Validation document mẫu

```markdown
# ARC-03 validation

## Environment
- GitHub Actions: ubuntu-latest
- JDK: 17
- Gradle: 8.9
- AGP: 8.7.3

## Workflow
`.github/workflows/android-ci.yml`

## Checks
| Check | Result |
|---|---|
| Checkout | Passed |
| JDK 17 | Passed |
| Gradle wrapper validation | Passed |
| Gradle cache setup | Passed |
| testDebugUnitTest | Passed / NO-SOURCE |
| assembleDebug | Passed |
| Intentional compile failure | Workflow failed as expected |
| Reverted fix | Workflow returned green |

## Required status check
`Android CI / Build & Test`
```

---

## 32. Acceptance Criteria

### AC-ARC03-01 — PR trigger
PR target `develop` hoặc `main` phải tự động chạy Android CI.

### AC-ARC03-02 — JDK
CI log xác nhận JDK 17.

### AC-ARC03-03 — Test
CI phải invoke:

```bash
./gradlew testDebugUnitTest
```

Test fail → workflow red.

### AC-ARC03-04 — Build
CI phải invoke:

```bash
./gradlew assembleDebug
```

Build fail → workflow red.

### AC-ARC03-05 — Cache
Gradle cache setup chạy thành công và không duplicate cache mechanism.

### AC-ARC03-06 — Green baseline
Code hợp lệ → `Android CI / Build & Test` green.

### AC-ARC03-07 — Failure propagation
Compile/test failure cố ý → failed step → failed job → red PR check.

### AC-ARC03-08 — Merge gate
Sau khi repo admin bật required status check, CI red phải block merge.

---

## 33. Definition of Done

### Workflow
- [ ] Có `.github/workflows/android-ci.yml`.
- [ ] Trigger PR → develop.
- [ ] Trigger PR → main.
- [ ] Trigger push → develop.
- [ ] Trigger push → main.
- [ ] `contents: read`.
- [ ] concurrency configured.
- [ ] runner `ubuntu-latest`.

### Toolchain
- [ ] checkout configured.
- [ ] JDK 17 configured.
- [ ] Gradle Wrapper validation configured.
- [ ] `setup-gradle` configured.
- [ ] Gradle cache hoạt động.

### Quality gate
- [ ] `testDebugUnitTest` chạy.
- [ ] `assembleDebug` chạy.
- [ ] Không `continue-on-error`.
- [ ] test failure → red.
- [ ] build failure → red.
- [ ] valid branch → green.

### Governance
- [ ] Status check name ổn định.
- [ ] Required status check được bật cho `develop` hoặc documented pending admin.
- [ ] `main` protection được review.

### Documentation
- [ ] README/docs cập nhật.
- [ ] Có ARC-03 validation evidence.
- [ ] Link workflow run được lưu trong PR hoặc validation.

---

## 34. PR template cho ARC-03

### Title

```text
ARC-03: Add Android CI build and test gate
```

### Body

```markdown
## Task
ARC-03 — CI Foundation

## Requirement
NFR-VM-18

## Changes
- Added Android GitHub Actions workflow
- Added JDK 17 setup
- Added Gradle wrapper validation
- Added Gradle cache setup
- Added unit test gate
- Added assembleDebug gate

## Validation
- [ ] PR triggers workflow
- [ ] Java 17 verified
- [ ] Gradle 8.9 verified
- [ ] testDebugUnitTest green / NO-SOURCE
- [ ] assembleDebug green
- [ ] intentional failure produced red workflow
- [ ] reverted branch returned green
- [ ] required status check configured or documented as pending admin

## Out of Scope
- Emulator tests
- Coverage threshold
- Release APK/CD
- Play Store
```

---

## 35. Reviewer checklist

Reviewer kiểm tra:

1. workflow chỉ dùng official actions;
2. JDK đúng 17;
3. build chạy qua `./gradlew`;
4. có wrapper validation;
5. cache không duplicate;
6. unit test là blocking step;
7. assemble là blocking step;
8. không `continue-on-error`;
9. không secrets;
10. trigger đúng `develop/main`;
11. job name ổn định;
12. negative failure test có evidence;
13. branch protection status check đã cấu hình hoặc pending rõ ràng.

---

## 36. Những lỗi cần tránh

### Chỉ chạy `assembleDebug`
Sai vì NFR-VM-18 cần JUnit.

### `continue-on-error: true`
Sai vì test fail nhưng workflow vẫn xanh.

### Dùng system Gradle
Phải dùng `./gradlew`.

### Cache thủ công + setup-gradle cùng lúc
Không cần duplicate cache.

### Workflow xanh nhưng branch chưa protected
CI đỏ vẫn có thể merge nếu chưa require status check.

### Matrix quá lớn
ARC-03 chỉ cần Ubuntu + JDK 17 + Debug.

---

## 37. Handoff sau ARC-03

Sau khi ARC-03 merge:

```text
ARC-01 Foundation
       ↓
ARC-02 Navigation
       ↓
ARC-03 CI gate
       ↓
DATA / LEARN / QUIZ / Feature tasks
```

Từ đây các JUnit mới sẽ tự động được CI chạy.

---

## 38. Final Gate

Local:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
git status
```

GitHub:

```text
Android CI / Build & Test
✓ Checkout
✓ JDK 17
✓ Wrapper validation
✓ Gradle setup/cache
✓ Unit tests
✓ assembleDebug
```

Negative proof:

```text
intentional failure → RED
revert/fix → GREEN
```

Governance:

```text
required status check
```

được bật hoặc có admin action rõ ràng.

---

## 39. Kết quả mong đợi

Sau ARC-03:

```text
Developer
   ↓
Pull Request
   ↓
Android CI
   ├── JDK 17
   ├── Gradle validation/cache
   ├── JUnit
   └── assembleDebug
        ↓
PASS → Review/Merge
FAIL → PR RED → Fix
```

Đây là quality gate nền tảng trước khi team bắt đầu merge nhiều DATA/Feature PR song song.
