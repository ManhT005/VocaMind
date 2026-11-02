# QUIZ-01 — Kế hoạch sửa test generator contract

- Repository: ManhT005/VocaMind.
- Nhánh fix: `fix/quiz-01-generator-test`.
- Base develop: `fff380127b0808b203165e649e22eaf714027afb`.
- PR gốc đã merge: #6; merge commit `37f14ca3cdb9086e9960fef988290c0839d5aa3e`.
- Ngày lập: 02/11/2026.
- Owner theo phân công: Thành; reviewer: Trường.
- Trạng thái: chỉ lập kế hoạch, chưa sửa Java hoặc chạy test cho bản fix.

## 1. Căn cứ và phạm vi

Đã đối chiếu ba tài liệu người dùng cung cấp:
1. `VocaMind_SRS_Baseline_v1.1.docx`: FR-VM-034..040, BR-08, UC-06, AC-10, DEC-07.
2. `VocaMind — Phân công.xlsx`, dòng QUIZ-01: model Question/Option/Session/Result và generator contract thuần domain; unit test invariants và pool<4. Owner Thành, reviewer Trường; task không có dependency.
3. `SRS_Ung_dung_hoc_tu_vung_tieng_Anh_Android(1).docx`: FR-10/11, AC-10/11 liên quan điều kiện bắt đầu, bốn lựa chọn và kết quả. Không trộn AC-12 lịch sử Quiz của tài liệu này với AC-12 bản quyền của baseline.

Dùng hệ yêu cầu baseline được phân công QUIZ-01 dẫn chiếu. Tài liệu tham chiếu repository:
- `docs/plans/quiz-01-domain-model.md`
- `docs/quiz-01-validation.md`

Task fix chỉ sửa test double và regression test. Production generator, full Unicode casefold và kiểm chứng mapping nghĩa thuộc QUIZ-02. UI, debounce 600 ms, confirm exit, lifecycle và ghi QuizAttempt thuộc phần tích hợp/QUIZ-03.

## 2. Lỗi cần sửa

File: `app/src/test/java/com/vocamind/app/domain/quiz/QuizDomainTest.java`.
Hàm: `generatorContractWithSeededFake()`.

Fake generator chỉ loại nhiễu có meaningKey bằng đáp án đúng. Nó không dedup meaningKey giữa các nhiễu. Ví dụ pool năm stableId có nghĩa A,A,B,C,D là READY (bốn nghĩa khác nhau). Khi hỏi D, vòng lặp có thể chọn A,A,B; constructor QuizQuestion ném IllegalArgumentException vì nghĩa nhiễu trùng.

Đây là lỗi test double và thiếu ca regression; chưa phải lỗi generator production. Review hiện là phân tích tĩnh, chưa tái chạy Java/Gradle trong môi trường review vì không có javac trong PATH. Không ghi nhận ca đã FAIL/PASS trước khi thực chạy.

## 3. Các bước thực hiện

### Bước 1 — Kiểm tra checkout

- Đọc AGENTS.md nếu có tại checkout thực tế; base đã kiểm tra không có AGENTS.md trong tree repository.
- Checkout nhánh fix, bảo toàn mọi thay đổi chưa commit.
- Đọc file test và tài liệu domain trước khi sửa; nếu develop có thay đổi mới thì đánh giá lại phạm vi trước khi đồng bộ nhánh.
- Không reset hoặc force-push nhánh của thành viên.

### Bước 2 — Tạo regression trước khi sửa

Tách fake generator thành helper private trong test class nếu cần để các ca tái sử dụng cùng implementation. Không đưa fake vào production source.

Thêm ca có năm stableId khác nhau với nghĩa A,A,B,C,D theo thứ tự cố định. Xác nhận:
- QuizPool READY.
- TargetQuestionCount=5.
- Gọi fake không được ném lỗi và trả READY.
- Output có đúng năm câu, stableId không lặp và đều thuộc pool.
- Mỗi câu có bốn option với bốn meaningKey khác nhau.
- Option đúng có meaningKey khớp vocabulary tương ứng trong pool.
- Không giảm còn bốn câu chỉ vì pool có bốn nghĩa.

Chạy regression trên logic cũ và lưu kết quả thật. Ca này dự kiến fail do duplicate meaningKey. Nếu không fail, kiểm tra fixture thực sự đưa hai nghĩa A vào nhánh chọn nhiễu; không sửa assertion chỉ để test xanh.

### Bước 3 — Sửa fake chọn nhiễu

Với mỗi câu:
1. Thêm option đúng từ vocabulary hiện tại.
2. Khởi tạo Set<String> usedMeaningKeys chứa key của đáp án đúng.
3. Duyệt pool; chỉ thêm nhiễu nếu key chưa nằm trong set.
4. Dừng khi đủ bốn option.
5. Chỉ thêm key khi thực sự chọn option đó.

Dùng cùng meaningKey từ QuizPool; không tự định nghĩa normalization mới. Giữ Random được truyền vào, tính xác định với seed, guard null và outcome EMPTY/INVALID_INPUT hiện có.

Không biến fake thành thuật toán sampling/shuffle production. Không thêm dependency, Android/Room hoặc sửa schema để xử lý lỗi test này.

### Bước 4 — Kiểm tra ma trận

| Ca | Pool | Kết quả |
| --- | --- | --- |
| REG-01 | 5 ID, A,A,B,C,D | READY, 5 câu, mỗi câu bốn key khác nhau |
| REG-02 | 7 ID, >=4 nghĩa, có nghĩa lặp | READY, 7 câu unique ID |
| REG-03 | 12 ID, >=4 nghĩa, có nghĩa lặp | READY, 10 câu unique ID |
| REG-04 | >=4 ID nhưng chỉ 3 nghĩa | EMPTY, không sinh session |
| REG-05 | 3 ID | EMPTY |
| REG-06 | Cùng pool và seed | Thứ tự câu đầu ra giống nhau |
| REG-07 | null pool hoặc null Random | INVALID_INPUT |

Giữ các test model/session/result hiện có. Kiểm tra output với invariant độc lập, không chép lại thuật toán chọn nhiễu vào assertion. Không bắt buộc các câu khác nhau có correct meaning khác nhau: chỉ stableId câu phải unique, bốn nghĩa trong từng câu phải khác nhau.

### Bước 5 — Chạy kiểm tra

Dùng toolchain baseline JDK 17 và Android SDK của project; không đổi Gradle/SDK version để né lỗi môi trường.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.vocamind.app.domain.quiz.*"
.\gradlew.bat testDebugUnitTest assembleDebug
git diff --check
```

Linux dùng `./gradlew` tương đương.
- Đọc JUnit XML/report lần chạy mới: có test Quiz, không failure/error.
- Nếu thiếu toolchain thì ghi NOT RUN và lý do; không coi log cũ là kết quả bản fix.
- Không bắt buộc emulator cho thay đổi test thuần domain này.
- Sau push, kiểm tra CI của commit fix; không lấy CI của commit cũ làm bằng chứng.

### Bước 6 — Cập nhật tài liệu và bàn giao

- Cập nhật `docs/quiz-01-validation.md` bằng mục bổ sung có ngày, commit code, ca regression, lệnh/kết quả và giới hạn. Giữ bằng chứng cũ gắn đúng commit.
- Thêm link plan này vào `docs/README.md` khi thực hiện fix.
- Commit test và tài liệu đúng phạm vi, tạo PR vào develop khi người dùng yêu cầu hoặc workflow thực thi được giao yêu cầu.
- Reviewer Trường kiểm tra lại behavior, CI và handoff.
- Không tự merge, đổi trạng thái bảng phân công hoặc tuyên bố toàn bộ AC-10 đã đạt.

## 4. Điểm bàn giao QUIZ-02

QuizQuestion hiện kiểm tra số option, uniqueness và correctOptionId tồn tại; chưa tự kiểm tra đáp án đúng tương ứng nghĩa vocabulary. Tài liệu validation đã giao trách nhiệm mapping cho QUIZ-02, nên không tự mở rộng fix này thành thay đổi API model.

Khi làm QUIZ-02, cần test/validator cho:
- correct option khớp meaningKey của từ;
- meaningKey khớp text theo normalizer production;
- câu thuộc pool active và số câu bằng target;
- lựa chọn được shuffle;
- Unicode NFC + trim + full casefold đúng;
- distractor không mơ hồ về sense/POS.

Test fake không chứng minh generator production đáp ứng những điều này.

## 5. Definition of Done bản fix

- [ ] Regression REG-01 bắt được lỗi logic cũ.
- [ ] Fake generator dedup nhiễu theo meaningKey.
- [ ] REG-01..07 được kiểm chứng, test hiện có vẫn pass.
- [ ] Focused/full unit tests và assembleDebug có evidence đúng commit.
- [ ] Validation/docs index được cập nhật.
- [ ] CI head fix xanh và Trường review.
- [ ] Chỉ kết luận phạm vi QUIZ-01 fix; không đóng AC-10 toàn Quiz khi chưa tích hợp.
