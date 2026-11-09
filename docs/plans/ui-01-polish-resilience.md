# UI-01 — Kế hoạch polish giao diện và tăng độ ổn định

**Ngày lập:** 09/11/2026 (Asia/Saigon). **Owner:** Vương Quang Thành. **Reviewer:** Lê Mạnh Trường.  
**Phân công gốc:** Tuần 3/D11–D13, High, 7 giờ, phụ thuộc `WEEK-2-GATE`. Lịch gốc không phải xác nhận tiến độ thực tế.  
**Nhánh:** `feature/ui-01-polish-resilience`. **Base khảo sát:** `develop` tại `c5d0ad6`, đã merge FLASH-03 qua PR #51.  
**Trạng thái:** Chỉ lập kế hoạch; chưa sửa code hoặc xác nhận PASS cho UI-01.

## 1. Căn cứ VocaMind

- `VocaMind — Phân công .xlsx`, sheet Phân công, UI-01: polish/resilience toàn UI P0; rotate/state restore, long text, empty/error, offline, accessibility, màn nhỏ; không reset dữ liệu khi lifecycle/reopen.
- `VocaMind_SRS_Baseline_v1.1.docx` đã gửi trong hội thoại là baseline hiện hành.
- `SRS_Ung_dung_hoc_tu_vung_tieng_Anh_Android.docx` vừa gửi đã được đọc để đối chiếu tài liệu gốc; không lấy nội dung cũ mâu thuẫn để mở rộng v1.1.
- Source trên develop và contract/evidence các feature: tái sử dụng, không viết lại nghiệp vụ đã chốt.
- UI-02 sở hữu automation discovery; QA-02 sở hữu full E2E nghiệm thu/video/log. UI-01 sửa resilience và bổ sung regression cho lỗi đã sửa, không làm lại toàn bộ hai task này.

Google Sheets: https://docs.google.com/spreadsheets/d/1Zx4QWaSalYk2-KWoaRRxwL6Hd-vv-sa2Lv9dBDxzQFg/edit

Sheets trả HTTP 403 khi khảo sát; dùng XLSX người dùng đã cung cấp, chưa xác minh thay đổi trực tuyến mới hơn. Trạng thái lịch sử trong XLSX không thay thế trạng thái source hiện tại.

| Yêu cầu | Tiêu chí áp dụng |
|---|---|
| FR-VM-026 | Giữ đúng thẻ hiện tại, mặt thẻ, vị trí phiên/câu khi đổi cấu hình; không tự ghi review/Quiz do tái tạo UI. |
| NFR-VM-01 | Luồng P0 dùng local data khi Airplane Mode từ lần mở đầu. |
| NFR-VM-09 | Vùng chạm tác vụ chính ≥48dp; ưu tiên tác vụ trọng tâm trong 40% đáy theo thiết kế nguồn, vẫn dùng được trên màn nhỏ/font lớn. |
| NFR-VM-10 | Empty/error rõ, feedback Quiz có text/icon, không chỉ dựa màu. |
| NFR-VM-14 | Không crash trong 10 thẻ + Quiz 10 câu + reboot offline trên cấu hình tham chiếu; có logcat/video khi nghiệm thu. |
| AC-06 | Flashcard lật đúng, chỉ chấm sau lật, vùng chạm ≥48dp. |
| AC-14 | Không phụ thuộc mạng/server, không bắt TTS P1 để học. APK release ≤25MB cần phối hợp release/QA, không suy từ debug APK. |

## 2. Mục tiêu và phạm vi

Hoàn thiện UI P0 hiện có để nội dung đọc được, thao tác rõ và state ổn định trên thiết bị mục tiêu. Giữ Android Java/Room/MVVM, không redesign toàn app.

Trong phạm vi: layout/resources, scroll/wrapping, insets/keyboard, vùng chạm, nhãn accessibility, feedback, lifecycle/binding/state restore, error/retry/reset và điều hướng. Chỉ sửa khi có tình huống hoặc thiếu sót cụ thể, không chỉnh hàng loạt theo sở thích.

Ngoài phạm vi: đổi Leitner/Quiz/Today, schema/migration, contract progress/saved-only, nguồn học liệu, benchmark FTS, backend/AI/cloud hoặc phát hành TTS P1. Nếu phát hiện lỗi nghiệp vụ, ghi finding riêng và phối hợp owner; không che lỗi bằng số 0/empty hoặc thay expectation.

## 3. Hiện trạng và dependency

- Home, Packs/pack detail, danh sách từ/detail, Search, Today, Flashcard, Quiz, Progress và Attribution đã có layout/navigation.
- Flashcard đã có ViewModel giữ state khi rotation, occurrence ID, khóa rating khi ghi và debounce 600ms. Giữ các guard FLASH-03.
- Quiz/Search đã dùng SavedStateHandle; Progress đọc lại DB qua ViewModel. Không tạo bản sao state độc lập với contract hiện hành.
- Chưa audit trực quan toàn bộ matrix UI-01; không coi clipping/crash giả định là lỗi đã tái hiện. PASS của feature cũ không phải PASS của UI-01 mới.
- WEEK-2-GATE chưa được xác nhận chỉ từ việc các PR đã merge. Bước đầu phải đối chiếu checklist gate/CI/blocker của nhóm. Có thể chuẩn bị audit trước, không tự ghi gate PASS hoặc sẵn sàng phát hành.

## 4. Bảng audit theo màn

| Màn | Kiểm tra và sửa khi có lỗi |
|---|---|
| Home/navigation | Nút không tràn, back stack đúng, không mở lặp do render; system bars không che nội dung. |
| Packs/pack detail/list từ | Tiêu đề dài, list scroll, loading/empty/error; activate/pause không lặp khi rotation, giữ route/stableId. |
| Search | IME không che kết quả, input giữ qua Back/rotation; không hiện hoặc chọn kết quả generation cũ; retry/reset rõ. |
| Vocabulary detail | Nghĩa/ví dụ EN–VI dài đọc đủ, IPA thiếu đúng placeholder; bind/reopen không tự Save/Unsave. |
| Today | Count/empty/error rõ, refresh sau quay về; lifecycle không tự tạo phiên lặp. |
| Flashcard | Tap nội dung lật được, scroll không gây flip ngoài ý muốn; rotate giữ thẻ/mặt/vị trí; ba rating ≥48dp, khóa khi ghi, lỗi giữ thẻ. |
| Quiz | Câu/4 lựa chọn dài wrap; feedback, next/result/retry/confirm exit dùng được; rotate không đổi đáp án hoặc finalize thêm. |
| Progress | Đủ sáu box, history scroll, Mastered disclaimer; empty từng section, retry rõ, không trộn snapshot cũ/mới. |
| Attribution | Nguồn/license dài scroll/wrap, đọc offline; mở link thất bại không làm app dừng. |

Mỗi finding ghi màn/cấu hình, bước tái hiện, expected/actual, yêu cầu liên quan, mức độ, ảnh/log nếu có, file sửa và regression. Không coi tất cả màn phải được viết lại.

## 5. Quy tắc triển khai

### Layout và accessibility

- Dùng wrap/scroll phù hợp; tránh chiều cao cố định cắt học liệu hoặc nút. Không thêm ellipsize để che nghĩa/ví dụ bắt buộc.
- Đo vùng chạm thực tế tác vụ chính ≥48dp, không chỉ icon; không tạo touch delegate chồng nút khác.
- Có nhãn text/icon và contentDescription có nghĩa cho nút icon, focus order hợp lý; tránh node đọc lặp. Kiểm thao tác TalkBack trọng yếu khi có thiết bị, không tự tuyên bố chứng nhận accessibility.
- Kiểm dark mode, font lớn và portrait/landscape; chuỗi mới vào resources, không hard-code màu làm mất khả năng đọc hoặc hiện stack trace cho người học.
- Giữ nhãn chuẩn: box0 “Học lại”, IPA fallback và Mastered disclaimer.

### State và lifecycle

- ViewModel là nguồn state; observe qua viewLifecycleOwner, bỏ binding/listener/adapter khi view hủy; callback cũ không bind lại view hoặc state/generation mới.
- Rotation: Flashcard giữ stableId/occurrence, mặt, vị trí, saving; Quiz giữ câu/đáp án/feedback/result/save; Search giữ input và state hiện hành. Không khóa rotation hoặc restart phiên để né lỗi.
- Không đưa View/Activity/DB vào SavedStateHandle; dữ liệu lớn đọc lại repository. Không enqueue ghi dữ liệu trong render.
- Process recreation tôn trọng khả năng restore contract hiện có; nếu không khôi phục được thì trở về state an toàn, không tự tạo review/attempt. UI-01 không thêm exactly-once qua process death hoặc cam kết phục hồi mọi phiên Flashcard chưa lưu.
- Force-stop/reopen/reboot phải giữ dữ liệu đã commit: active packs, progress, reviews, saved flags, completed Quiz history. Không đồng nhất với bắt buộc mở lại đúng mặt của phiên chưa lưu.
- Error không biến thành empty. Retry không tự lặp thao tác ghi khi outcome chưa rõ; tái sử dụng contract feature thay vì thêm tầng retry riêng.

## 6. Trình tự thực hiện — 7 giờ gốc

| Bước | Thời lượng | Đầu ra |
|---|---:|---|
| 1 | 1 giờ | Cập nhật develop, kiểm tra WEEK-2-GATE, audit matrix và tạo finding list ưu tiên. |
| 2 | 1,5 giờ | Sửa clipping/scroll/IME/insets/touch targets theo finding. |
| 3 | 1,5 giờ | Sửa lifecycle/rotate/back/state restore; giữ guard ghi dữ liệu. |
| 4 | 1 giờ | Empty/error/retry, feedback text/icon và accessibility. |
| 5 | 1,5 giờ | Regression lỗi đã sửa, build/lint, smoke 10 thẻ + Quiz/offline; ghi kết quả thật hoặc pending. |
| 6 | 0,5 giờ | Tự review diff, validation/handoff, gửi Trường review. |
| **Tổng** | **7 giờ** | Không bao gồm chờ CI/thiết bị/reviewer hoặc lỗi ngoài scope. |

Nếu vượt 7 giờ, ưu tiên crash/data loss/state corruption, rồi thao tác không dùng được/clipping. Ghi estimate bổ sung để Thành chốt, không cắt ngầm P0.

## 7. Matrix kiểm tra

| ID | Cấu hình/tình huống | Kỳ vọng |
|---|---|---|
| U01-01 | API26, khoảng 320dp rộng, portrait | Thao tác được, nội dung bắt buộc không cắt, list/detail scroll đủ. |
| U01-02 | API mới trong matrix QA, landscape, system bars, IME | Không che nội dung/nút hoặc crash khi đổi cấu hình. |
| U01-03 | Dark mode, font150% và stress200%, text dài | Nghĩa/ví dụ/4 đáp án/disclaimer đọc đủ, không chỉ màu. |
| U01-04 | Rotate Flashcard trước/sau flip, đang lưu | Đúng thẻ/mặt/vị trí/lock, chỉ một review cho thao tác hợp lệ. |
| U01-05 | Rotate Quiz ở answer/feedback/result/save pending | Không mất đáp án/nhân đôi attempt; Back giữa bài theo confirm hiện có. |
| U01-06 | Search nhanh/clear/Back/recreate, pending async diff | Query đúng, không hiện/chọn list cũ hoặc bind view hủy. |
| U01-07 | Empty các feature và lỗi đọc/ghi có kiểm soát | Thông điệp đúng từng trường hợp, retry/reset dùng được, không fake dữ liệu. |
| U01-08 | Airplane Mode trước first launch QA install | Gói→search/detail→Today→học→Quiz→Progress→Attribution chạy local. |
| U01-09 | 10 thẻ với các rating → Quiz10 câu → reboot offline | Không crash/ANR, dữ liệu commit còn nguyên, lifecycle không tăng event/attempt. |
| U01-10 | Touch targets và thao tác accessibility trọng yếu | ≥48dp, tên hành động rõ, không chỉ màu, vẫn scroll/chọn được. |

320dp/font150–200% là cấu hình kiểm tra đề xuất, không phải con số nguyên văn SRS. NFR-VM-14 cần cấu hình tham chiếu/đã thống nhất; emulator smoke không tự thay thế nghiệm thu thiết bị.

Regression mới tập trung lỗi đã sửa và invariants rủi ro cao; tái dùng tests feature hiện có. UI-02/QA-02 tiếp tục sở hữu automation/acceptance đầy đủ tương ứng.

## 8. File, kiểm chứng và minh chứng

Chỉ đổi layout/resources/styles/dimens và Fragment/ViewModel liên quan finding, test cùng feature. Không đổi schema/catalog assets/domain để chữa layout.

Lệnh baseline khi toolchain khả dụng:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
```

Instrumentation dùng đúng test class/profile/thiết bị đã chọn; ghi lệnh thực tế trong validation. CI Quiz-device PASS không tự chứng minh toàn UI matrix PASS. Dùng appId QA riêng và fixture đủ Quiz10 câu; không clear/reset app người dùng hoặc đưa fixture vào production assets. Giữ dependency/prepare catalog test assets của CI hiện hành.

Tạo `docs/ui-01-validation.md`: base/final commit, finding→fix→test, API/size/font/locale, lệnh/kết quả/pending. Minh chứng tại `docs/evidence/ui-01/`: ảnh trước/sau, logcat theo thời gian smoke, reports và video hành trình NFR-VM-14 khi thực hiện. Ràng buộc test source/APK/log bằng commit/hash; giữ minh chứng lịch sử nguyên trạng.

## 9. Điều kiện hoàn thành

Theo quyết định của Thành trong phiên làm việc, phân biệt **Done triển khai kỹ thuật** với **nghiệm thu kiểm thử**. Không bắt chờ mọi ca thiết bị để xác nhận code xong, nhưng không ghi ca chưa chạy là PASS.

**Done phần code:**

- [ ] Findings trong scope đã sửa; không còn lỗi biết rõ gây mất dữ liệu/sai state/crash/chặn tác vụ P0.
- [ ] Layout/touch/feedback/empty/error và lifecycle đáp ứng phạm vi; giữ contract nghiệp vụ.
- [ ] Có regression phù hợp cho lỗi state/ghi trùng đã sửa; build/lint/checks khả dụng đạt trên head cuối, pending ghi rõ.
- [ ] Validation liệt kê test đã/chưa chạy; Trường nhận review code và góp ý đã xử lý.

**Nghiệm thu đầy đủ trước bàn giao MVP:**

- [ ] Matrix API26/màn nhỏ/rotate/back/offline và smoke 10 thẻ + Quiz/reboot có kết quả thực tế.
- [ ] Logcat/video và dữ liệu trước/sau chứng minh không crash, không mất/nhân đôi dữ liệu; phối hợp QA-02.
- [ ] Đối chiếu NFR/AC; AC-14 dung lượng release do phối hợp QA/release xác nhận, không suy từ sửa UI.

Nếu Done code trước, danh sách test còn lại phải có người nhận (Thành phối hợp QA-02/UI-02, Trường review), tình huống/môi trường và lịch trước bàn giao; không chỉ ghi “test sau”. File này chưa xác nhận WEEK-2-GATE, approval hoặc triển khai hoàn tất.
