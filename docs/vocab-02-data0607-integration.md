# VOCAB-02 — tích hợp develop DATA-06/DATA-07

Ngày 06/11/2026. Phụ trách Trường; reviewer Thành. PR #32 vào develop; không tự merge/Done.
Develop đã fetch: **8209ef7**, sau DATA-06/DATA-07 (và DATA-05).
**SHA code/tests tích hợp: a99e742c67edda2ecbd0f3629f47ff551af1a972.**
Validation 287 tests tại 016373c/develop45db604 là lịch sử, không thay cho lượt tích hợp này.

## Bảo toàn workspace và resolve

Workspace chính đang ở feature/search-01-search-ui với thay đổi chưa commit; không switch/reset/stash
hoặc commit các thay đổi SEARCH-01. Tạo worktree riêng .worktrees/vocab-02-integration cho
feature/vocab-02-vocabulary-detail từ e5a3ddd. Cấu hình exclude chỉ local, không sửa .gitignore dự án.

Merge origin/develop và resolve từng phần RepositoryProvider, giữ đồng thời:

- VocabularyRepository, RoomVocabularyRepository và vocabulary().
- progress, ProgressProvider.create, progress() và progressUseCase().
- recordReview(Clock, ZoneId) qua ReviewProvider và canonical ReviewRepository.
- QuizPolicyAsset loader từ PR #31, không fallback unrestricted trong production.

Rà diff UserDao với origin/develop: chỉ bổ sung first-save insert-if-absent/comment hiện hành;
giữ nguyên distinctProgressCount/progressCountsByBox/completedQuizHistory/progressSnapshot và
recordReviewIfUnchanged/sameProgress. Không đổi query hoặc schema của DATA-06/07.

## Kiểm chứng tích hợp

QuizPolicyCompositionTest nay kiểm tra first-save qua vocabulary(), progress()/progressUseCase()
đọc total=1/box0=1 trong cùng provider; thực thi review qua recordReview(fixedClock, UTC),
giữ saved flag và Quiz fail-closed theo policy fixture. Không dùng adapter review cũ cho ca composition này.

RepositoryIntegrationTest kiểm tra first-save→reopen→Unsave, total/box0 qua RoomProgressRepository thật,
learned-today=0 khi chỉ Save và các pool inactive/active/paused/shared như contract.

DATA-06 so sánh toàn snapshot, gồm isSaved. Khi Save thắng giữa snapshot và commit review,
CONFLICT là kết quả bảo vệ dữ liệu hợp lệ; không ép review success bằng cách bỏ compare hoặc tự retry.
Tests concurrent chỉ chấp nhận success hoặc CONFLICT; nếu conflict, chứng minh không phát sinh event,
saved flag còn nguyên, rồi mô phỏng **quyết định người dùng mới** bằng RecordReviewUseCase đọc lại.
Test deterministic snapshot→Save→commit stale xác nhận toàn userdata snapshot không đổi khi conflict,
lượt đánh giá mới tạo đúng một event, không mất flag.

## Lệnh / evidence

Windows/JDK17, Gradle8.9, GRADLE_USER_HOME dùng cache hiện có; Robolectric API26/35.
Mọi lệnh Gradle chạy trong worktree riêng, không dùng output/build của SEARCH-01.
Native host dùng TEMP/TMP và java.io.tmpdir=`C:/Temp/vm02` qua JAVA_TOOL_OPTIONS.
Lượt focused đầu dùng TEMP dài mặc định: 144 tests, 2 lỗi SQLITE_CANTOPEN ở method DATA-07
có tên dài khi mở file-backed DB. Giữ log lỗi riêng, chạy lại với TEMP ngắn;
không sửa production/test assertions để che lỗi và không gán kết quả ban đầu thành PASS.

```powershell
.\gradlew.bat testDebugUnitTest --tests '*RepositoryIntegrationTest' --tests '*QuizPolicyCompositionTest' --tests '*AtomicReviewTest' --tests '*ProgressAggregationTest' --offline --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
git diff --check
```

`git diff --check origin/develop -- app docs` PASS cho diff của task. Kiểm tra toàn bộ staged merge
ban đầu thấy dòng trống cuối file evidence adb-devices.txt đã có trên develop; giữ nguyên evidence upstream,
không sửa file đó chỉ để làm sạch lịch sử. Code/tests không đổi trong commit docs/evidence sau validation.

SHA code/tests và kết quả cuối: [evidence](validation/vocab-02/integration-8209ef7/local-evidence.json).
Full run: **408 tests / 39 suites, 0 failures, 0 errors, 0 skipped**; BUILD SUCCESSFUL.
assembleDebug/assembleDebugAndroidTest PASS, lintDebug **0 errors/11 warnings**.
Focused 144 tests PASS với TEMP ngắn trước full run. Code/tests không đổi giữa commit merge và kết quả cuối;
đã kiểm tra git diff --exit-code HEAD -- app. Device QA **NOT RUN**, ADB kiểm tra lại không có thiết bị.
[Build log](validation/vocab-02/integration-8209ef7/build.log),
[lint](validation/vocab-02/integration-8209ef7/lint-results.txt), XML các suite trọng tâm cùng thư mục.
Device QA NOT RUN nếu ADB vẫn không có thiết bị; host Room reopen/recreation không phải reboot/process death.
Không tuyên bố AC-12/13/content QA từ lượt tích hợp này. Contract due-now không đổi.
