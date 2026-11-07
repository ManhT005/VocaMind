# VOCAB-02 — Tích hợp develop 993e70a và audit nguồn test probe

Ngày 07/11/2026. Theo yêu cầu xử lý recheck PR #32 của Thành. Không approve/merge PR hoặc đánh dấu Done trong bước này.

## 1. Tích hợp

- PR head đầu vào: `ecbe356f1f0cf91cef2fe7f4d96369face07bd42`.
- Develop được tích hợp: `993e70a07a05c50c5de8099edcdf50b92c91c5e9`.
- Conflict tại QuizPolicyCompositionTest được resolve giữ kiểm tra first-save, total/box0 qua DATA-07, counters/learnedAt, canonical RecordReviewUseCase của DATA-06 và Quiz policy fail-closed. Không thay bằng đường review legacy để bỏ các kiểm tra mới.
- Rà auto-merge phát hiện RepositoryProvider trùng import, field và accessor vocabulary do cả hai phía đã thêm cùng dependency. Đã bỏ phần trùng, giữ đúng một vocabulary()/VocabularyRepository, progress()/progressUseCase(), recordReview(Clock,ZoneId), Quiz policy loader và các dependency hiện có.
- Không thay contract first-save, schema UserDatabase, Query new/due/stats hoặc logic detail/Save để che xung đột.
- Không reset/stash hoặc ghi đè thay đổi người dùng; dùng checkout hiện có, không tạo worktree.

## 2. Hash probe: điều đã xác định và điều chưa thể kết luận

File: docs/validation/vocab-02/device-9f2dfd9-20261107/VocabularyDeviceQa.java.

- Digest ghi trong metadata lịch sử: `089ab4b2171040bff1dd9d62f4b050cba3d77487b9e763c2d7aa77730c8393e1`.
- SHA-256 của bytes bản lưu trong Git: `4f1461beda68ac415abfb16cf45c877eaad29b202b9b8b8de34af5bf2c558737`.
- Các biến đổi LF/CRLF, BOM và EOF/encoding thường gặp được kiểm tra chưa tái tạo digest lịch sử.
- Không có original tested source hoặc test APK probe tương ứng trong workspace để chứng minh nguyên nhân. Không kết luận source lịch sử giống source archive chỉ vì test log ghi PASS.
- Metadata/log lịch sử giữ nguyên, không thay expected hash để tạo PASS giả. Thêm probe-binding-review.json ghi riêng digest archive đã xác minh và trạng thái UNVERIFIED_HISTORICAL_BINDING; đặt cảnh báo ở đầu tài liệu QA.

Đây là một gap liên kết evidence, không chứng minh các lần test lịch sử đã fail hoặc production có lỗi. Nhưng cần đóng gap trước khi dựa vào chúng để nghiệm thu Done.

## 3. Kiểm chứng trong lần sửa này

- Đã kiểm tra không còn unmerged paths/conflict markers và diff mới không có whitespace lỗi.
- Đã kiểm tra dependency vocabulary không còn khai báo trùng; test giữ các assertions của DATA-06/07 và lưu/bỏ lưu.
- Máy cloud hiện chưa có javac/JDK compiler, ANDROID_HOME/ANDROID_SDK_ROOT hoặc adb; không có /dev/kvm. Chưa chạy lại Gradle hoặc emulator tại đây. Không ghi các check đó là PASS.
- Sau khi push, dùng GitHub CI ở SHA mới để xác nhận compile/unit/build. Kết quả CI được báo riêng theo run URL, không tự coi CI head cũ là kết quả bản tích hợp.
- Chưa thay đổi các checkbox nghiệm thu hoặc trạng thái task.

## 4. Đóng gap probe bằng replay trên QA install riêng

Có thể cung cấp original tested source khớp digest lịch sử để giải thích binding của lượt cũ. Với bản tích hợp mới, hướng ưu tiên là chạy lại bản probe archive và ghi một run mới:

1. Dùng test emulator/API phù hợp, giữ package QA `com.vocamind.app.vocab02qa`; không clear/wipe app hoặc AVD thật của người dùng.
2. Tạo thư mục run mới, không ghi đè device-9f2dfd9-20261107. Copy đúng probe archive vào sourceSet ngoài app theo qa.init.gradle; ghi source commit, hash app sources/config, bytes probe, init script và catalog assets trước build.
3. Build app/test APK từ source tích hợp, ghi SHA256 hai APK ngay sau build. Không dùng APK của ecbe356/a99e742 cho source mới.
4. Cài QA APK, bật/kiểm tra Airplane Mode trước first launch. Chạy đúng thứ tự các probe method a/b/c/d/e/f/h; g chỉ export evidence.
5. Sau a: force-stop QA package rồi chạy b. Ghi boot ID, reboot thật/đợi boot/offline rồi chạy c. Force-stop rồi d, reboot/offline lần nữa rồi e; chạy f và h để kiểm tra error/retry/IPA fixture/loading rotation.
6. Nếu probe hoặc source cần sửa để chạy trên catalog mới, giữ bản trước, mô tả thay đổi, tính hash lại và chạy lại các phase phụ thuộc. Không sửa assertions để giữ PASS nếu behavior đã sai.
7. Thu log có exit/result, PNG, catalog/progress/history snapshots, boot IDs và app/test/probe hashes vào metadata của run mới. Chỉ đổi bindingStatus sang VERIFIED khi bytes/run được đối chiếu; reviewer kiểm tra trước khi chốt.

Ví dụ method selector khi đã chuẩn bị đúng QA package/device:

```text
adb -s <qa-serial> shell am instrument -w -r -e class com.vocamind.app.feature.vocabulary.VocabularyDeviceQa#aReadOfflineAndFirstSave com.vocamind.app.vocab02qa.test/androidx.test.runner.AndroidJUnitRunner
```

Đây là hướng dẫn tái thực hiện, chưa được chạy trong cloud. Không chạy toàn class bằng một lệnh rồi bỏ qua các bước force-stop/reboot và state prerequisites giữa phase.

## 5. Cổng còn lại

- [ ] CI mới trên source tích hợp thành công.
- [ ] Probe source/run được liên kết có bằng chứng hoặc replay trên bản tích hợp mới.
- [ ] V02-A..D được reviewer chấp nhận trên đúng revision.
- [ ] Thành quyết định nghiệm thu/merge; task chưa được tự đánh dấu Done.
