# ARC-01 — Android Foundation / Gradle & Core Dependencies

> Đây là kế hoạch gốc trước khi triển khai ARC-01. Các phần mô tả hiện trạng
> phản ánh thời điểm lập kế hoạch. Xem [kết quả kiểm tra](../arc-01-validation.md)
> và [baseline hiện tại](../android-foundation.md) để đối chiếu với phần đã triển khai.

- **Project:** VocaMind
- **Task ID:** ARC-01
- **Branch đề xuất:** `feature/arc-01-android-foundation`
- **Base branch:** `develop`
- **Parent foundation:** `feature/foundation-architecture` đã được dùng để bootstrap project
- **Owner:** Chọn trong Thi / Trường / Nhân / Đạo / Thành
- **Reviewer:** Tech Lead hoặc thành viên phụ trách Data/Architecture
- **Mức ưu tiên:** P0 / Blocker
- **Ước lượng:** 0.5–1 ngày nếu môi trường Android Studio đã sẵn sàng
- **Phạm vi:** Build configuration / dependency foundation, chưa triển khai nghiệp vụ

---

## 1. Mục tiêu

Chuẩn hóa VocaMind thành một Android project có baseline kỹ thuật ổn định để toàn team có thể phát triển song song mà không phải tự cấu hình Gradle hoặc dependency theo từng feature.

Sau ARC-01, project phải:

1. mở được bằng Android Studio;
2. Gradle Sync thành công;
3. dùng **JDK 17**;
4. giữ **minSdk 26**;
5. giữ `compileSdk 35`, `targetSdk 35` ở baseline hiện tại;
6. bật **ViewBinding**;
7. có dependency nền cho:
   - AndroidX Lifecycle / ViewModel / LiveData;
   - Navigation Component;
   - Room Runtime + Room Compiler;
8. dùng đúng annotation processor cho Java;
9. không thêm business code;
10. chạy thành công:

```bash
./gradlew clean assembleDebug
```

Trên Windows:

```powershell
.\gradlew.bat clean assembleDebug
```

---

# 2. Căn cứ kiến trúc

Baseline SRS v1.1 xác định hướng triển khai học thuật gồm:

```text
Java
XML / ViewBinding
Clean MVVM
LiveData
Navigation Component
Room
Gradle
GitHub Actions
```

Android tối thiểu:

```text
Android 8.0 / API 26+
```

ARC-01 chỉ thiết lập nền kỹ thuật cho các thành phần trên.

Các phần sau **không thuộc ARC-01**:

```text
Room Entity / DAO / Database
Navigation Graph
Fragment nghiệp vụ
Repository
UseCase
Leitner
Quiz
Pack
Search
CI workflow
```

Chúng thuộc các task tiếp theo.

---

# 3. Hiện trạng repo trước ARC-01

Tại nhánh foundation hiện tại, project đang có:

```text
AGP             = 8.7.3
Gradle          = 8.9
JDK source      = Java 17
compileSdk      = 35
targetSdk       = 35
minSdk          = 26
namespace       = com.vocamind.app
applicationId   = com.vocamind.app
```

`app/build.gradle` hiện mới có:

```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.vocamind.app'
    compileSdk 35

    defaultConfig {
        applicationId "com.vocamind.app"
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName "0.1.0"
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
```

Chưa có:

```text
buildFeatures.viewBinding
dependencies {}
Lifecycle
Navigation
Room
JUnit dependency
```

---

# 4. Compatibility baseline phải giữ

Không nâng AGP/Gradle trong ARC-01 nếu không có blocker.

Baseline hiện tại là hợp lệ:

```text
Android Gradle Plugin 8.7.3
Gradle 8.9
JDK 17
compileSdk 35
```

AGP 8.7 yêu cầu Gradle 8.9 và JDK 17, đồng thời hỗ trợ API tối đa 35.

Do đó ARC-01 **không phải task upgrade toolchain**.

Quy tắc:

```text
Nếu project build được:
    giữ AGP 8.7.3 + Gradle 8.9
Else:
    xác định lỗi môi trường trước
    không tự nâng version để "thử fix"
```

---

# 5. Dependency baseline đề xuất

Tại thời điểm lập kế hoạch, có thể dùng các AndroidX stable sau:

```text
Lifecycle   2.11.0
Navigation  2.10.2
Room        2.8.5
JUnit       4.13.2
```

> Khi implement, nếu Android Studio/Gradle báo dependency constraint hoặc compatibility issue thì owner phải ghi rõ trong PR trước khi đổi version.

VocaMind là Java project nên Room compiler dùng:

```gradle
annotationProcessor
```

Không dùng:

```text
kapt
ksp
```

trong ARC-01.

---

# 6. File dự kiến thay đổi

ARC-01 nên giữ diff nhỏ.

```text
VocaMind/
├── build.gradle                       # kiểm tra, ưu tiên không đổi
├── settings.gradle                    # kiểm tra repository
├── gradle.properties                  # kiểm tra AndroidX/JVM config
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties  # kiểm tra Gradle 8.9
└── app/
    └── build.gradle                   # file thay đổi chính
```

Không tạo Entity/DAO trong task này.

---

# 7. Implementation Plan

## Step 1 — Tạo branch

Không code trực tiếp trên `develop`.

```bash
git checkout develop
git pull origin develop

git checkout -b feature/arc-01-android-foundation
```

Nếu foundation chưa được merge vào `develop`, Tech Lead phải merge/rebase foundation trước hoặc tạo ARC-01 từ đúng commit foundation theo quyết định của team.

Kiểm tra:

```bash
git status
git branch --show-current
```

Expected:

```text
feature/arc-01-android-foundation
```

---

# 8. Step 2 — Kiểm tra Java runtime

Chạy:

```bash
java -version
```

Expected:

```text
17.x
```

Sau đó:

```bash
./gradlew --version
```

Expected phải chứa:

```text
Gradle 8.9
JVM 17
```

Windows:

```powershell
.\gradlew.bat --version
```

## Nếu JVM không phải 17

Trong Android Studio:

```text
File
→ Settings
→ Build, Execution, Deployment
→ Build Tools
→ Gradle
→ Gradle JDK
→ chọn JDK 17
```

Không hardcode đường dẫn JDK cá nhân vào repo.

Không commit:

```text
local.properties
.idea/
```

---

# 9. Step 3 — Kiểm tra Gradle Wrapper

File:

```text
gradle/wrapper/gradle-wrapper.properties
```

Baseline cần giữ:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

Không đổi sang:

```text
Gradle 9.x
```

trong ARC-01.

Lý do:

```text
ARC-01 = stabilize foundation
không phải toolchain migration
```

---

# 10. Step 4 — Kiểm tra Android Gradle Plugin

Root:

```text
build.gradle
```

Baseline:

```gradle
plugins {
    id 'com.android.application' version '8.7.3' apply false
}
```

Không thêm plugin không cần thiết.

Không thêm:

```text
Kotlin
KSP
Hilt
Google Services
Firebase
Compose
```

---

# 11. Step 5 — Kiểm tra repositories

`settings.gradle` phải có:

```gradle
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}
```

### Ý nghĩa

`google()`:

```text
Android Gradle Plugin
AndroidX
Room
Lifecycle
Navigation
```

`mavenCentral()`:

```text
dependency Java/JUnit phổ biến
```

`FAIL_ON_PROJECT_REPOS` giúp dependency source được quản lý tập trung.

---

# 12. Step 6 — Kiểm tra Android SDK baseline

Trong:

```text
app/build.gradle
```

giữ:

```gradle
android {
    namespace 'com.vocamind.app'
    compileSdk 35

    defaultConfig {
        applicationId "com.vocamind.app"
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName "0.1.0"
    }
}
```

Không đổi:

```text
applicationId
namespace
minSdk
```

trong ARC-01 trừ khi Tech Lead có ADR riêng.

---

# 13. Step 7 — Java 17 compile options

Phải có:

```gradle
compileOptions {
    sourceCompatibility JavaVersion.VERSION_17
    targetCompatibility JavaVersion.VERSION_17
}
```

Không thêm Kotlin compile options.

Verification:

```bash
./gradlew :app:compileDebugJavaWithJavac
```

---

# 14. Step 8 — Bật ViewBinding

Trong block:

```gradle
android {
```

thêm:

```gradle
buildFeatures {
    viewBinding true
}
```

Expected structure:

```gradle
android {
    namespace 'com.vocamind.app'
    compileSdk 35

    defaultConfig {
        ...
    }

    buildFeatures {
        viewBinding true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
```

### ARC-01 không bắt buộc

- tạo UI thật;
- convert `MainActivity` sang binding;
- tạo Fragment;
- tạo navigation graph.

Các việc đó dành cho ARC-02.

Mục tiêu task này chỉ là bảo đảm ViewBinding generation đã được enable.

---

# 15. Step 9 — Thêm Lifecycle dependencies

Thêm vào:

```gradle
dependencies {
```

Baseline:

```gradle
def lifecycle_version = "2.11.0"

implementation "androidx.lifecycle:lifecycle-viewmodel:$lifecycle_version"
implementation "androidx.lifecycle:lifecycle-livedata:$lifecycle_version"
implementation "androidx.lifecycle:lifecycle-runtime:$lifecycle_version"
```

## Vì sao dùng các artifact này

### `lifecycle-viewmodel`

Cho:

```text
Fragment / Activity
→ ViewModel
→ state
```

### `lifecycle-livedata`

Cho UI observe state theo Clean MVVM baseline.

### `lifecycle-runtime`

Cho lifecycle-aware runtime.

---

# 16. Step 10 — Thêm Navigation dependencies

```gradle
def nav_version = "2.10.2"

implementation "androidx.navigation:navigation-fragment:$nav_version"
implementation "androidx.navigation:navigation-ui:$nav_version"
```

ARC-01 chỉ thêm dependencies.

ARC-02 mới implement:

```text
NavHostFragment
nav_graph.xml
destinations
back-stack
```

## Safe Args

**Không bật Safe Args trong ARC-01 trừ khi ARC-02 đã chốt dùng.**

Lý do:

- hiện chưa có nav graph;
- tránh thêm plugin khi chưa có consumer;
- giảm build configuration sớm.

Có thể bổ sung Safe Args trong ARC-02.

---

# 17. Step 11 — Thêm Room dependencies

```gradle
def room_version = "2.8.5"

implementation "androidx.room:room-runtime:$room_version"
annotationProcessor "androidx.room:room-compiler:$room_version"
```

VocaMind dùng Java nên compiler bắt buộc qua:

```gradle
annotationProcessor
```

Không dùng:

```gradle
ksp
kapt
```

---

# 18. Room schema export — quyết định cho ARC-01

Khuyến nghị:

**ARC-01 chỉ chuẩn bị dependency.**

Schema configuration được triển khai trong DATA-01 khi `CatalogDatabase` và `UserDatabase` bắt đầu tồn tại.

Không tạo fake Room entity chỉ để compiler chạy.

Mục tiêu là:

```text
foundation không chứa domain/data implementation giả
```

---

# 19. Step 12 — Thêm Unit Test baseline

Để tất cả thành viên có thể thêm unit test ngay từ task đầu:

```gradle
testImplementation 'junit:junit:4.13.2'
```

Không cần Espresso trong ARC-01 nếu chưa có UI test task.

Android test dependencies có thể bổ sung khi ARC-02/QA-01 triển khai.

---

# 20. app/build.gradle target state

Sau ARC-01, file nên tương đương:

```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.vocamind.app'
    compileSdk 35

    defaultConfig {
        applicationId "com.vocamind.app"
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName "0.1.0"
    }

    buildFeatures {
        viewBinding true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    def lifecycle_version = "2.11.0"
    def nav_version = "2.10.2"
    def room_version = "2.8.5"

    implementation "androidx.lifecycle:lifecycle-viewmodel:$lifecycle_version"
    implementation "androidx.lifecycle:lifecycle-livedata:$lifecycle_version"
    implementation "androidx.lifecycle:lifecycle-runtime:$lifecycle_version"

    implementation "androidx.navigation:navigation-fragment:$nav_version"
    implementation "androidx.navigation:navigation-ui:$nav_version"

    implementation "androidx.room:room-runtime:$room_version"
    annotationProcessor "androidx.room:room-compiler:$room_version"

    testImplementation 'junit:junit:4.13.2'
}
```

Đây là target conceptual của task.

Nếu dependency resolver yêu cầu thay đổi version, ghi lại lý do trong PR.

---

# 21. Step 13 — Gradle Sync

Trong Android Studio:

```text
Sync Project with Gradle Files
```

Không chấp nhận trạng thái:

```text
Gradle sync failed
Unresolved dependency
Unsupported class version
SDK missing
```

Nếu Android SDK 35 chưa có:

```text
Tools
→ SDK Manager
→ Android SDK
→ SDK Platforms
→ Android 15 / API 35
```

Cài xong sync lại.

---

# 22. Step 14 — Compile check

Chạy:

```bash
./gradlew :app:compileDebugJavaWithJavac
```

Expected:

```text
BUILD SUCCESSFUL
```

Nếu fail, phân loại lỗi trước khi sửa.

### Type A — JDK

Ví dụ:

```text
Unsupported class file
invalid source release
```

Kiểm tra:

```bash
java -version
./gradlew --version
```

### Type B — SDK

Ví dụ:

```text
failed to find target with hash string android-35
```

Cài API 35.

### Type C — Dependency

Ví dụ:

```text
Could not resolve androidx...
```

Kiểm tra:

```text
google()
mavenCentral()
Internet/proxy
```

Không đổi dependency version ngẫu nhiên.

---

# 23. Step 15 — Unit test baseline

Chạy:

```bash
./gradlew testDebugUnitTest
```

Hiện project có thể chưa có test Java thực.

Mục tiêu:

```text
task chạy được
không dependency/config error
```

Nếu Gradle trả:

```text
NO-SOURCE
```

thì không phải lỗi.

---

# 24. Step 16 — assembleDebug gate

Đây là acceptance command chính:

```bash
./gradlew clean assembleDebug
```

Windows:

```powershell
.\gradlew.bat clean assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

APK output dự kiến:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Không commit APK.

---

# 25. Step 17 — Android Studio smoke run

Sau build command:

1. mở Android Studio;
2. Gradle Sync;
3. chọn emulator/device API 26+;
4. Run `app`;
5. app mở được;
6. màn hình blank hiện tại không crash.

ARC-01 không đánh giá UI.

Chỉ xác nhận:

```text
process starts
MainActivity launches
no ClassNotFoundException
no resource crash
```

---

# 26. Step 18 — Dependency inspection

Chạy:

```bash
./gradlew :app:dependencies
```

Kiểm tra không có dependency resolution failure.

Có thể lọc:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

Owner cần đặc biệt nhìn:

```text
androidx.lifecycle
androidx.navigation
androidx.room
```

Không cần xử lý mọi transitive dependency nếu Gradle resolve bình thường.

---

# 27. Step 19 — Kiểm tra repository cleanliness

Chạy:

```bash
git status
```

Không được có:

```text
.idea/
.gradle/
local.properties
app/build/
build/
*.apk
```

Nếu xuất hiện, kiểm tra `.gitignore`.

---

# 28. Step 20 — Commit strategy

Khuyến nghị 1–2 commit, không cần chia vụn.

Ví dụ:

```bash
git add app/build.gradle

git commit -m "chore: configure Android foundation dependencies"
```

Nếu phải sửa wrapper/config riêng:

```bash
git commit -m "build: align Gradle foundation with JDK 17"
```

Không dùng commit:

```text
update
fix
done
abc
```

---

# 29. Pull Request yêu cầu

## Title

```text
ARC-01: Configure Android foundation and core dependencies
```

## PR body

```markdown
## Task
ARC-01

## Scope
Configure Gradle/JDK 17/minSdk 26 foundation and add ViewBinding,
Lifecycle, Navigation and Room dependencies.

## Changes
- Verified AGP 8.7.3 / Gradle 8.9
- Verified Java 17
- Kept minSdk 26
- Enabled ViewBinding
- Added Lifecycle
- Added Navigation
- Added Room Runtime + compiler
- Added JUnit baseline

## Validation
- [ ] Android Studio Gradle Sync passes
- [ ] ./gradlew --version uses JVM 17
- [ ] ./gradlew :app:compileDebugJavaWithJavac passes
- [ ] ./gradlew testDebugUnitTest passes / NO-SOURCE
- [ ] ./gradlew clean assembleDebug passes
- [ ] App launches on emulator/device
- [ ] No build artifacts committed

## Out of Scope
- Navigation graph
- Room entities / DAO
- Feature UI
- Repository / UseCase
- CI workflow
```

---

# 30. Definition of Done — ARC-01

ARC-01 chỉ được chuyển sang **Done** nếu tất cả mục sau đạt.

### Toolchain

- [ ] AGP vẫn `8.7.3` hoặc thay đổi có lý do được review.
- [ ] Gradle `8.9`.
- [ ] Gradle JVM = JDK 17.
- [ ] `sourceCompatibility = JavaVersion.VERSION_17`.
- [ ] `targetCompatibility = JavaVersion.VERSION_17`.

### Android

- [ ] `compileSdk 35`.
- [ ] `targetSdk 35`.
- [ ] `minSdk 26`.
- [ ] namespace đúng `com.vocamind.app`.
- [ ] applicationId đúng `com.vocamind.app`.

### Foundation dependencies

- [ ] ViewBinding enabled.
- [ ] Lifecycle dependency resolve.
- [ ] Navigation dependency resolve.
- [ ] Room runtime resolve.
- [ ] Room compiler hoạt động bằng `annotationProcessor`.
- [ ] JUnit baseline resolve.

### Build

- [ ] Gradle Sync success.
- [ ] `compileDebugJavaWithJavac` success.
- [ ] `testDebugUnitTest` success hoặc `NO-SOURCE`.
- [ ] `clean assembleDebug` success.
- [ ] app debug khởi động không crash.

### Repository quality

- [ ] Không business logic trong ARC-01.
- [ ] Không Room Entity/DAO giả.
- [ ] Không Navigation graph giả.
- [ ] Không Kotlin/KSP/Hilt/Firebase/Compose.
- [ ] Không commit `local.properties`.
- [ ] Không commit build artifacts.
- [ ] PR có reviewer.
- [ ] PR target `develop`.

---

# 31. Acceptance Criteria

## AC-ARC01-01 — Build environment

**Given**

repo được clone sạch.

**When**

developer mở bằng Android Studio với JDK 17.

**Then**

Gradle Sync thành công.

---

## AC-ARC01-02 — Minimum Android

**Given**

`app/build.gradle`.

**Then**

phải có:

```text
minSdk 26
```

Không thấp hơn và không tự nâng trong task.

---

## AC-ARC01-03 — ViewBinding

**Given**

Android module `app`.

**Then**

phải bật:

```gradle
viewBinding true
```

---

## AC-ARC01-04 — Lifecycle

Gradle phải resolve được:

```text
lifecycle-viewmodel
lifecycle-livedata
lifecycle-runtime
```

---

## AC-ARC01-05 — Navigation

Gradle phải resolve được:

```text
navigation-fragment
navigation-ui
```

---

## AC-ARC01-06 — Room

Gradle phải resolve:

```text
room-runtime
room-compiler
```

với Java annotation processor.

---

## AC-ARC01-07 — Assemble

Command:

```bash
./gradlew clean assembleDebug
```

phải trả:

```text
BUILD SUCCESSFUL
```

---

# 32. Reviewer checklist

Reviewer không chỉ nhìn build xanh.

Phải kiểm tra:

### Architecture

- dependency chỉ được thêm tại app module;
- chưa có logic nghiệp vụ;
- không thêm framework ngoài scope;
- Java vẫn là ngôn ngữ baseline.

### Gradle

- không duplicate dependency;
- không dùng dynamic version:

```text
2.+
latest.release
+
```

- không dùng alpha/beta nếu không có ADR;
- version được pin rõ ràng.

### Room

Đảm bảo:

```text
annotationProcessor
```

không phải KSP/KAPT.

### Project hygiene

Không có:

```text
local.properties
.idea
build directory
APK
```

---

# 33. Các lỗi thường gặp

## 33.1 Android Studio đang dùng JDK khác terminal

Terminal:

```bash
java -version
```

có thể là 17 nhưng Gradle JDK trong Android Studio lại khác.

Phải kiểm tra cả:

```bash
./gradlew --version
```

---

## 33.2 Android SDK 35 chưa được cài

Biểu hiện:

```text
SDK location not found
failed to find android-35
```

Không sửa `compileSdk` xuống 34 để workaround.

Cài đúng SDK 35.

---

## 33.3 Dùng `implementation room-compiler`

Sai:

```gradle
implementation "androidx.room:room-compiler:..."
```

Đúng cho Java:

```gradle
annotationProcessor "androidx.room:room-compiler:..."
```

---

## 33.4 Thêm Safe Args quá sớm

ARC-01 chưa có NavGraph.

Không cần plugin Safe Args ở task này.

---

## 33.5 Nâng Gradle/AGP vô lý

Nếu dependency resolve fail:

```text
không mặc định nâng AGP
```

Phải xác định root cause trước.

---

# 34. Không được over-engineer ARC-01

ARC-01 là foundation task.

Không biến task này thành:

```text
architecture rewrite
dependency catalog migration
DI framework setup
Room implementation
Navigation implementation
UI redesign
```

Target diff nên nhỏ và dễ review.

---

# 35. Handoff sang các task sau

Sau ARC-01:

## ARC-02

Có thể dùng:

```text
ViewBinding
Navigation Component
Lifecycle/ViewModel
```

để tạo application shell.

## DATA-01 / DATA-02

Có thể dùng:

```text
Room runtime
Room compiler
```

để triển khai:

```text
CatalogDatabase
UserDatabase
```

## Feature tasks

Có thể dùng:

```text
ViewModel
LiveData
```

mà không phải thêm dependency riêng.

---

# 36. Expected result sau ARC-01

Cây project gần như vẫn giữ nguyên:

```text
VocaMind/
├── app/
│   ├── build.gradle        ← thay đổi chính
│   └── src/
│       └── main/
├── gradle/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

ARC-01 thành công khi:

```text
Foundation compile được
        ↓
Dependencies resolve được
        ↓
Team có cùng baseline
        ↓
ARC-02 / DATA-01 / feature work bắt đầu song song
```

Không cần bất kỳ functionality người dùng nào xuất hiện ở task này.

---

# 37. Final Gate

Owner trước khi request review phải chạy lần cuối:

```bash
./gradlew --version
./gradlew clean
./gradlew :app:compileDebugJavaWithJavac
./gradlew testDebugUnitTest
./gradlew assembleDebug
git status
```

Expected:

```text
JVM 17
BUILD SUCCESSFUL
BUILD SUCCESSFUL / NO-SOURCE
BUILD SUCCESSFUL
working tree clean
```

Nếu một trong các bước trên fail, ARC-01 chưa đủ điều kiện request merge.
