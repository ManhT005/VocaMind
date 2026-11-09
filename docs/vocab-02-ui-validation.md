# VOCAB-02 — UI refresh

Ngày: 06/11/2026. Phụ trách Trường; reviewer Thành.
Code/tests SHA: **894db81e8b3e339dd130b5e992c6a6a04a4256d2**.
Tiếp tục nhánh feature/vocab-02-vocabulary-detail; working tree trước thay đổi sạch.
Không thay policy saved-only hoặc đánh dấu VOCAB-02/AC-04 hoàn thành.

## Bố cục mới

- Thanh tiêu đề và Back 48dp ở phía trên, giữ route/back stack VOCAB-01.
- Lemma nổi bật, POS dạng nhãn, IPA nằm riêng bên dưới.
- Thẻ nghĩa và thẻ ví dụ Anh–Việt có nhãn, typography/khoảng cách rõ ràng.
- Nội dung cuộn độc lập; nút Lưu/Bỏ lưu full-width cố định ở cuối, không che nội dung.
- Palette xanh theo app, có màu riêng cho nền thẻ/viền ở light và dark mode.
- Loading có progress indicator; pending thể hiện trên nút. Không lặp tiêu đề trong status.
- Thông báo lỗi đặt sát hành động. Retry chỉ hiện khi lỗi read/write có thể reconcile;
  saved-only và not-found không hiện nút Retry gây hiểu nhầm.

Không sửa nội dung catalog hoặc repository/save semantics. Save từ chưa có progress vẫn
**BLOCKED** theo [quyết định đang chờ](vocab-02-saved-only-decision.md).

## Kiểm chứng

JDK17, Gradle8.9, Windows. Robolectric API26/35; layout capture dùng native graphics.

```powershell
.\gradlew.bat testDebugUnitTest --tests '*VocabularyDetailTest' --tests '*VocabularyListTest' assembleDebug lintDebug --offline --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
git diff --check
```

Focused tests/build/lint PASS trước commit code; full run trên SHA trên.
Full regression **245 tests, 0 failures, 0 errors, 0 skipped**; assembleDebug và
assembleDebugAndroidTest PASS. lintDebug PASS: **0 errors / 11 warnings**.
Trong đó 6 warnings nền cũ, 4 cảnh báo accessibilityHeading chỉ áp dụng từ API28
(API26 vẫn có text/contentDescription), 1 cảnh báo root background overdraw.
git diff --check PASS.
Kết quả và hashes: [evidence](validation/vocab-02/ui-894db81/local-evidence.json),
[build log](validation/vocab-02/ui-894db81/build.log),
[lint](validation/vocab-02/ui-894db81/lint-results.txt).

VocabularyDetailTest kiểm tra state rendering, không Retry cho saved-only/not-found,
Retry cho read failure, button state, multiline/native measurement ở 320×480dp/fontScale1.5,
content cuộn và footer không đè nội dung. VocabularyListTest kiểm tra graph navigation,
Save pending/recreation, Back/reopen/Unsave. Full run có các regression repository/review khác.

Ảnh PNG là **host-rendered synthetic fixture**, không phải screenshot thiết bị hay evidence QA catalog.
Đã xem light/ready, light/blocked và dark/ready ở API35; ảnh các trạng thái API26/35 được giữ để review.

- [Light](validation/vocab-02/ui-894db81/detail-35-light-ready.png)
- [Saved-only blocker](validation/vocab-02/ui-894db81/detail-35-light-blocked.png)
- [Dark](validation/vocab-02/ui-894db81/detail-35-dark-ready.png)

Device visual QA, TalkBack, Airplane Mode và app process restart **NOT RUN**;
không có thiết bị trong lượt kiểm chứng trước. Không coi host render là device QA.

## Reviewer

Cài APK mới: app/build/outputs/apk/debug/app-debug.apk. Mở Gói học → một từ.
Kiểm tra light/dark, font lớn, cuộn nghĩa/ví dụ dài; Back phía trên và nút Lưu cuối màn hình.
Từ chưa có progress: click Lưu thấy thông báo gần nút, không có Retry.
Từ có progress: Lưu/Bỏ lưu, Back/reopen và rotation giữ đúng trạng thái như bản trước.
