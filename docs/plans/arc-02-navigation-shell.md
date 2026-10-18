# ARC-02 — Navigation Shell cho VocaMind (phục vụ FEAT-01)

> Ghi chú triển khai: ARC-01 chưa merge vào `develop`, nên nhánh
> `feature/arc-02-navigation-shell` được tạo từ `feature/arc-01-android-foundation`
> tại commit `1d72e06`. Cập nhật base lên `develop` sau khi ARC-01 được merge.
> Theo yêu cầu bổ sung, shell dùng Material Components cho Java/XML và
> `AppCompatActivity` (kế thừa `FragmentActivity`). Xem
> [baseline hiện tại](../android-foundation.md), [sketch](../sketchs.png) và
> [kết quả kiểm tra](../arc-02-validation.md).

- **Project:** VocaMind
- **Task ID:** ARC-02
- **Nhóm:** Architecture
- **Tên task:** Navigation shell
- **Phạm vi:** MainActivity + NavHost + các route rỗng
- **Phục vụ:** FEAT-01 — Khởi động ngoại tuyến & CSDL đóng sẵn
- **Branch đề xuất:** `feature/arc-02-navigation-shell`
- **Base branch dự kiến:** `develop` sau khi PR #2 (ARC-01) được merge
- **Ưu tiên:** P0 / Blocker cho các feature UI tiếp theo
- **Ước lượng:** 0.5–1 ngày
- **Ngôn ngữ/UI:** Java + XML + ViewBinding + AndroidX Navigation + Material Components

---

## 1. Mục tiêu

ARC-02 tạo **application shell** đầu tiên cho VocaMind.

Sau task này, app phải:

1. khởi động vào `MainActivity`;
2. `MainActivity` chỉ đóng vai trò host;
3. có một `NavHostFragment`;
4. có `nav_graph.xml`;
5. có 6 destination rỗng:
   - Home
   - Packs
   - Vocabulary
   - Today
   - Quiz
   - Progress
6. `Home` là start destination;
7. điều hướng thử giữa các destination không crash;
8. nút Back hoạt động đúng;
9. xoay màn hình không làm app crash hoặc reset navigation sai;
10. không thêm business logic, Room, Repository hoặc dữ liệu thật.

ARC-02 là lớp nền để các task UI sau chỉ cần thay nội dung từng Fragment mà không phải thay cơ chế điều hướng toàn app.

---

## 2. Quan hệ với FEAT-01

FEAT-01 yêu cầu ứng dụng:

```text
Khởi động được
→ hoạt động offline
→ có CSDL đóng sẵn
→ đi vào hành trình sử dụng chính
```

ARC-02 chỉ phụ trách phần:

```text
App launch
→ MainActivity
→ NavHost
→ Home
```

ARC-02 **không triển khai database**.

Phần database của FEAT-01 sẽ được thực hiện bởi các DATA task:

```text
CatalogDatabase
UserDatabase
prepackaged catalog
startup data initialization
```

Do đó:

```text
ARC-01
  ↓
ARC-02 ──────┐
             ├──→ FEAT-01 hoàn chỉnh
DATA tasks ──┘
```

---

## 3. Điều kiện bắt đầu

Không bắt đầu ARC-02 trên một nền chưa ổn định.

Trước khi tạo branch:

- PR #2 ARC-01 đã merge vào `develop`;
- `develop` build thành công;
- ViewBinding đã bật;
- Navigation dependencies đã resolve;
- JDK 17;
- minSdk 26;
- Gradle Sync thành công trên máy developer.

Commands:

```bash
git checkout develop
git pull origin develop

./gradlew clean assembleDebug
```

Windows:

```powershell
.\gradlew.bat clean assembleDebug
```

Nếu build fail trước khi sửa code ARC-02, dừng task và xử lý foundation trước.

---

## 4. Không nằm trong phạm vi ARC-02

Không triển khai:

```text
Room Entity / DAO / Database
Pack list thật
Vocabulary list thật
Today Planner
Flashcard
Quiz logic
Progress calculation
Repository
UseCase
ViewModel nghiệp vụ
Bottom Navigation hoàn chỉnh
Drawer
Toolbar phức tạp
Safe Args
Deep Link
Animation navigation
TTS
Network
Backend
```

Không thêm quyền:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

FEAT-01 phải giữ offline-first.

---

## 5. Cấu trúc file mục tiêu

Sau ARC-02, phần UI shell nên có dạng:

```text
app/src/main/
├── java/com/vocamind/app/
│   ├── MainActivity.java
│   └── feature/
│       ├── home/
│       │   └── HomeFragment.java
│       ├── packs/
│       │   └── PacksFragment.java
│       ├── vocabulary/
│       │   └── VocabularyFragment.java
│       ├── today/
│       │   └── TodayFragment.java
│       ├── quiz/
│       │   └── QuizFragment.java
│       └── progress/
│           └── ProgressFragment.java
│
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── fragment_home.xml
    │   ├── fragment_packs.xml
    │   ├── fragment_vocabulary.xml
    │   ├── fragment_today.xml
    │   ├── fragment_quiz.xml
    │   └── fragment_progress.xml
    │
    ├── navigation/
    │   └── nav_graph.xml
    │
    └── values/
        └── strings.xml
```

Không cần tạo package `presentation/` sâu hơn ở task này. Với MVP 3 tuần, structure trên đủ rõ và ít ceremony.

---

## 6. Step 1 — Tạo branch

Sau khi ARC-01 merge:

```bash
git checkout develop
git pull origin develop

git checkout -b feature/arc-02-navigation-shell
```

Kiểm tra:

```bash
git branch --show-current
git status
```

Expected:

```text
feature/arc-02-navigation-shell
working tree clean
```

---

## 7. Step 2 — Chuyển MainActivity thành Fragment host

Hiện foundation dùng `android.app.Activity`.

ARC-02 nên chuyển sang:

```java
androidx.fragment.app.FragmentActivity
```

Lý do:

- có `FragmentManager`;
- tương thích trực tiếp với `NavHostFragment`;
- chưa cần thêm AppCompat chỉ để host navigation.

Target:

```java
package com.vocamind.app;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.vocamind.app.databinding.ActivityMainBinding;

public class MainActivity extends FragmentActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }
}
```

### Không làm trong MainActivity

Không đặt:

```text
DAO
Repository
Room
business rules
feature state
```

MainActivity là shell host.

---

## 8. Step 3 — Tạo `activity_main.xml`

Tạo:

```text
app/src/main/res/layout/activity_main.xml
```

Khuyến nghị dùng `FragmentContainerView`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.fragment.app.FragmentContainerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_host_fragment"
    android:name="androidx.navigation.fragment.NavHostFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    app:defaultNavHost="true"
    app:navGraph="@navigation/nav_graph" />
```

### Ý nghĩa

`defaultNavHost=true` giúp `NavHostFragment` nhận system Back.

`navGraph` gắn application shell với graph trung tâm.

---

## 9. Step 4 — Tạo Navigation Graph

Tạo:

```text
app/src/main/res/navigation/nav_graph.xml
```

Start destination:

```text
HomeFragment
```

Graph tối thiểu:

```xml
<navigation
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/homeFragment">

    <fragment
        android:id="@+id/homeFragment"
        android:name="com.vocamind.app.feature.home.HomeFragment"
        android:label="@string/home" />

    <fragment
        android:id="@+id/packsFragment"
        android:name="com.vocamind.app.feature.packs.PacksFragment"
        android:label="@string/packs" />

    <fragment
        android:id="@+id/vocabularyFragment"
        android:name="com.vocamind.app.feature.vocabulary.VocabularyFragment"
        android:label="@string/vocabulary" />

    <fragment
        android:id="@+id/todayFragment"
        android:name="com.vocamind.app.feature.today.TodayFragment"
        android:label="@string/today" />

    <fragment
        android:id="@+id/quizFragment"
        android:name="com.vocamind.app.feature.quiz.QuizFragment"
        android:label="@string/quiz" />

    <fragment
        android:id="@+id/progressFragment"
        android:name="com.vocamind.app.feature.progress.ProgressFragment"
        android:label="@string/progress" />

</navigation>
```

ARC-02 chưa cần action XML cố định nếu navigation thử được thực hiện qua `NavController.navigate(destinationId)`.

Không bật Safe Args.

---

## 10. Step 5 — Tạo 6 Fragment placeholder

Mỗi Fragment chỉ cần:

- inflate layout;
- không business logic;
- không database;
- không ViewModel bắt buộc.

Ví dụ:

```java
package com.vocamind.app.feature.home;

import androidx.fragment.app.Fragment;

import com.vocamind.app.R;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
    }
}
```

Các Fragment còn lại tương tự:

```text
PacksFragment
VocabularyFragment
TodayFragment
QuizFragment
ProgressFragment
```

Giữ class nhỏ.

---

## 11. Step 6 — Placeholder layouts

Mỗi layout chỉ cần đủ để nhận biết destination đang hoạt động.

Ví dụ:

```xml
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="@string/home"
        android:textSize="24sp" />

</FrameLayout>
```

Không thiết kế UI final ở ARC-02.

Placeholder nên có:

```text
Home
Packs
Vocabulary
Today
Quiz
Progress
```

để smoke test trực quan.

---

## 12. Step 7 — Cách điều hướng thử

ARC-02 cần chứng minh 6 route thực sự hoạt động.

### Phương án khuyến nghị — temporary debug navigation trong Home

Trong `HomeFragment`, có thể đặt 5 button nhỏ:

```text
Open Packs
Open Vocabulary
Open Today
Open Quiz
Open Progress
```

Buttons chỉ phục vụ shell validation.

Ví dụ:

```java
NavHostFragment.findNavController(this)
        .navigate(R.id.packsFragment);
```

Khi ARC-03 hoặc UI Home thật được triển khai, các nút debug này được bỏ.

Lý do chọn phương án này:

- dễ demo;
- không cần Material BottomNavigation;
- không thêm dependency;
- kiểm tra back stack trực tiếp;
- diff nhỏ.

---

## 13. Quy tắc Back Stack

Smoke flows bắt buộc:

```text
Home
→ Packs
→ Back
→ Home
```

```text
Home
→ Vocabulary
→ Back
→ Home
```

```text
Home
→ Today
→ Back
→ Home
```

```text
Home
→ Quiz
→ Back
→ Home
```

```text
Home
→ Progress
→ Back
→ Home
```

Tại Home:

```text
Back
→ Activity finish
```

Không được:

```text
Back
→ blank screen
Back
→ duplicate Fragment
Back
→ crash
```

---

## 14. Không tạo nested graph ở ARC-02

Chỉ cần một graph:

```text
nav_graph.xml
```

Không tách:

```text
home_graph.xml
study_graph.xml
quiz_graph.xml
```

ở giai đoạn này.

Nested graph chỉ đáng dùng khi flow thực tế đủ phức tạp.

MVP 3 tuần cần ưu tiên đơn giản.

---

## 15. Strings

Không hardcode label trong Java.

`strings.xml` tối thiểu:

```xml
<string name="app_name">VocaMind</string>
<string name="home">Trang chủ</string>
<string name="packs">Gói học</string>
<string name="vocabulary">Từ vựng</string>
<string name="today">Ôn tập hôm nay</string>
<string name="quiz">Kiểm tra</string>
<string name="progress">Tiến độ</string>
```

ARC-02 chỉ cần route names ổn định.

---

## 16. Orientation / recreation check

Navigation shell phải chịu được lifecycle cơ bản.

Test:

```text
Home
→ Today
→ rotate device
```

Expected:

```text
vẫn ở Today
không quay lại Home
không tạo destination duplicate
không crash
```

Lặp lại ít nhất với:

```text
Packs
Quiz
```

Không tự gọi:

```java
navigate(...)
```

trong `onCreate()` mỗi lần Activity recreate ngoài cơ chế graph.

Navigation Component tự restore destination nếu cấu hình đúng.

---

## 17. FEAT-01 startup rule

ARC-02 phải tạo startup path ổn định:

```text
Launcher
→ MainActivity
→ nav_graph
→ Home
```

Không chèn:

```text
Login
Network check
Remote config
API call
Permission dialog
```

FEAT-01 là offline-first.

Khi DATA task hoàn thành, startup data initialization sẽ được gắn vào flow riêng mà không phá navigation shell.

---

## 18. Manifest check

Giữ MainActivity launcher:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

ARC-02 không cần thêm Activity khác.

Kiến trúc target:

```text
Single Activity
+ Multiple Fragments
```

---

## 19. Build validation

Chạy:

```bash
./gradlew clean assembleDebug
```

Sau đó:

```bash
./gradlew :app:compileDebugJavaWithJavac
```

Expected:

```text
BUILD SUCCESSFUL
```

Windows:

```powershell
.\gradlew.bat clean assembleDebug
.\gradlew.bat :app:compileDebugJavaWithJavac
```

---

## 20. Runtime smoke test

Run trên emulator/device API 26+.

Checklist:

- app mở được;
- Home hiển thị;
- không crash startup;
- đi tới Packs;
- Back về Home;
- đi Vocabulary;
- Back về Home;
- đi Today;
- Back về Home;
- đi Quiz;
- Back về Home;
- đi Progress;
- Back về Home;
- rotate không crash;
- đóng/mở app lại vẫn vào Home.

---

## 21. Kiểm tra offline

Bật Airplane Mode trước khi mở app.

Run:

```text
Airplane Mode ON
→ launch VocaMind
→ Home
→ navigate các placeholder
```

Expected:

```text
không có network error
không có spinner chờ network
không crash
```

ARC-02 không được tạo dependency runtime vào Internet.

---

## 22. Automated test mức tối thiểu

Nếu team chưa setup Espresso/Nav instrumentation trong sprint này, ARC-02 có thể chấp nhận smoke test có evidence.

Các test cases cần ghi lại:

```text
TC-ARC02-01 launch → Home
TC-ARC02-02 Home → Packs → Back → Home
TC-ARC02-03 Home → Vocabulary → Back → Home
TC-ARC02-04 Home → Today → Back → Home
TC-ARC02-05 Home → Quiz → Back → Home
TC-ARC02-06 Home → Progress → Back → Home
TC-ARC02-07 rotate destination → destination preserved
TC-ARC02-08 Airplane Mode → launch/navigation succeeds
```

---

## 23. Definition of Done

### Architecture

- [ ] Single `MainActivity`.
- [ ] `MainActivity` là navigation host, không chứa business logic.
- [ ] Có `NavHostFragment`.
- [ ] Có `nav_graph.xml`.
- [ ] Home là start destination.
- [ ] Có 6 Fragment route.

### Routes

- [ ] Home.
- [ ] Packs.
- [ ] Vocabulary.
- [ ] Today.
- [ ] Quiz.
- [ ] Progress.

### Navigation

- [ ] Mỗi route mở được.
- [ ] Back trả về đúng destination trước.
- [ ] Back tại Home đóng Activity bình thường.
- [ ] Không duplicate fragment do recreate.
- [ ] Rotate không crash.

### FEAT-01 support

- [ ] App launch đi trực tiếp MainActivity → Home.
- [ ] Không cần Internet.
- [ ] Không thêm Internet permission.
- [ ] Không có DB/business implementation trong ARC-02.

### Build

- [ ] `compileDebugJavaWithJavac` pass.
- [ ] `assembleDebug` pass.
- [ ] APK cài và launch được.

### Repository

- [ ] Không commit build artifacts.
- [ ] Không commit `.idea`.
- [ ] Không commit `local.properties`.
- [ ] PR target `develop`.
- [ ] Reviewer đã kiểm tra back stack.

---

## 24. Acceptance Criteria

### AC-ARC02-01 — Startup

**Given** app được cài mới.

**When** người dùng launch VocaMind.

**Then**

```text
MainActivity
→ NavHost
→ Home
```

được hiển thị mà không crash.

### AC-ARC02-02 — Route availability

6 destinations phải tồn tại và resolve được:

```text
Home
Packs
Vocabulary
Today
Quiz
Progress
```

### AC-ARC02-03 — Back Stack

Từ mỗi top-level placeholder:

```text
Home → Destination → Back
```

phải quay về Home.

### AC-ARC02-04 — Lifecycle

Sau rotate, `current destination` được giữ đúng.

### AC-ARC02-05 — Offline

Bật Airplane Mode không ảnh hưởng startup/navigation shell.

### AC-ARC02-06 — Build

```bash
./gradlew clean assembleDebug
```

phải trả:

```text
BUILD SUCCESSFUL
```

---

## 25. PR đề xuất

### Title

```text
ARC-02: Add application navigation shell
```

### Body

```markdown
## Task
ARC-02 — Navigation Shell

## Supports
FEAT-01

## Changes
- Converted MainActivity to FragmentActivity host
- Added ViewBinding-based activity layout
- Added NavHostFragment
- Added nav_graph.xml
- Added empty Home/Packs/Vocabulary/Today/Quiz/Progress routes
- Added temporary shell navigation for smoke testing

## Validation
- [ ] clean assembleDebug
- [ ] compileDebugJavaWithJavac
- [ ] app launches Home
- [ ] all six destinations resolve
- [ ] back stack verified
- [ ] orientation verified
- [ ] Airplane Mode verified

## Out of Scope
- Database
- Repository
- UseCase
- Feature UI
- Bottom navigation final design
- Safe Args
```

---

## 26. Reviewer checklist

Reviewer cần kiểm tra:

1. `MainActivity` không chứa business logic.
2. Không dùng nhiều Activity.
3. NavHost là default host.
4. Home là start destination.
5. 6 route đúng package.
6. Back stack không crash.
7. Không hardcode navigation state trong Activity.
8. Không gọi network.
9. Không thêm Room code vào ARC-02.
10. `assembleDebug` pass.

---

## 27. Những lỗi cần tránh

### Sai 1 — Activity tự replace Fragment

Không dùng Fragment transaction thủ công cho navigation chính.

Đã có Navigation Component.

### Sai 2 — Tạo Activity cho từng màn

Không tạo:

```text
PacksActivity
QuizActivity
ProgressActivity
```

VocaMind dùng Single Activity + Fragment shell.

### Sai 3 — Gọi navigate trong `MainActivity.onCreate()` liên tục

Sai vì orientation recreation có thể tạo stack duplicate.

Start destination phải để Navigation Graph quản lý.

### Sai 4 — Thêm UI final quá sớm

ARC-02 không phải task thiết kế Home/Packs/Quiz.

Placeholder đủ để kiểm chứng kiến trúc.

### Sai 5 — Nhét Room startup vào MainActivity

DATA initialization phải được thiết kế riêng.

MainActivity không phải data bootstrapper.

---

## 28. Handoff sau ARC-02

Sau khi ARC-02 merge, team có thể chạy song song:

```text
PACK-01
→ thay PacksFragment placeholder

VOCAB-01
→ thay VocabularyFragment placeholder

TODAY-01
→ thay TodayFragment placeholder

QUIZ
→ thay QuizFragment placeholder

PROGRESS
→ thay ProgressFragment placeholder
```

Các feature developer không nên sửa `MainActivity` và NavHost tùy ý.

Nếu cần route mới:

```text
feature PR
→ cập nhật nav_graph
→ architecture review
```

---

## 29. Final Gate

Owner chạy:

```bash
./gradlew clean assembleDebug
./gradlew :app:compileDebugJavaWithJavac
git status
```

Sau đó smoke:

```text
Launch → Home
Home → Packs → Back
Home → Vocabulary → Back
Home → Today → Back
Home → Quiz → Back
Home → Progress → Back
Rotate
Airplane Mode
```

Chỉ request review khi toàn bộ flow trên pass.

---

## 30. Kết quả mong đợi

Sau ARC-02, VocaMind có skeleton:

```text
Android launch
     ↓
MainActivity
     ↓
NavHostFragment
     ↓
Home
 ├── Packs
 ├── Vocabulary
 ├── Today
 ├── Quiz
 └── Progress
```

Đây là **UI/application shell**, chưa phải FEAT-01 hoàn chỉnh.

FEAT-01 chỉ hoàn chỉnh khi ARC-02 được kết hợp với phần startup/data foundation của các DATA task.
