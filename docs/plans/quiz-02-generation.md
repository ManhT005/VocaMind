# QUIZ-02 — Kế hoạch triển khai Quiz generation

**Dự án:** VocaMind

**Owner:** Vương Quang Thành. **Reviewer theo phân công:** Lê Mạnh Trường. **Phối hợp repository/data:** Bùi Vĩ Nhân, Nguyễn Minh Đạo.

**Ưu tiên:** Critical, P0. **Ước lượng gốc:** 8 giờ, D6–D8 của Tuần 2 theo bảng phân công; không phải xác nhận lịch thực tế.

**Phụ thuộc trong phân công:** QUIZ-01, DATA-04.

**Nhánh:** `feature/quiz-02-generation`. **Base develop đã đọc:** `4959cf6d29314d7105f4285edd76e3169d912958`.

**Trạng thái:** Kế hoạch; chưa triển khai generator/normalizer hoặc chạy test QUIZ-02 trong bước này.

## 1. Căn cứ và mức độ xác minh

- SRS mới được người dùng gửi: `VocaMind_SRS_Baseline_v1.1.docx`, FR-VM-034/035/037/040, BR-08, UC-06, AC-10, DEC-07. FR-VM-038/039 dùng để xác định ranh giới với việc lưu kết quả.
- Bản Excel `VocaMind — Phân công .xlsx` đã gửi: dòng QUIZ-02, 8 giờ, Thành phụ trách, Trường review; nhiệm vụ normalize/dedup/sample/shuffle, đọc pool active qua repository, không ghi QuizAttempt.
- Google Sheets được dẫn lại nhưng chưa đọc được live trong môi trường hiện tại. Không khẳng định bản Excel chứa mọi chỉnh sửa mới trên Sheet.
- `SRS_Ung_dung_hoc_tu_vung_tieng_Anh_Android.docx` là bản lịch sử; dùng baseline v1.1 cho phạm vi triển khai, không tự gộp CRUD/chủ đề của bản cũ.
- Đã đọc API QUIZ-01 thực tế trên develop: QuizGenerator, MeaningNormalizer, QuizPool, QuizQuestion, QuizOption, QuizVocabulary, QuizGenerationOutcome; kiểm tra QuizRepository/RoomQuizRepository làm đầu vào tích hợp.

## 2. Mục tiêu và ranh giới

Tạo bài Quiz hợp lệ từ snapshot vocabulary của các gói active: 4..10 câu, không lặp stableId; mỗi câu đúng một nghĩa mục tiêu và ba nghĩa nhiễu khác nhau; thứ tự câu và vị trí đáp án được xáo trộn, kiểm thử tái lập bằng seed.

Đầu ra:

1. Production MeaningNormalizer thực hiện NFC + trim + full Unicode casefold.
2. Implementation QuizGenerator theo interface hiện có.
3. Boundary/use case đọc pool qua QuizRepository, map thành QuizVocabulary/QuizPool và trả outcome đúng loại; tên API cụ thể giữ theo code hiện có.
4. Unit/property-style tests, repository integration fixture cho active/paused/shared và tài liệu validation/handoff.

Không xây UI Quiz, animation, debounce 600 ms, dialog thoát, lịch sử/thống kê hoặc ghi QuizAttempt. Các phần đó thuộc QUIZ-03/data task. Không đưa AI/API/network vào lúc sinh bài, không sửa lịch Leitner hoặc schema catalog chỉ để thuận tiện cho generator.

## 3. Hợp đồng hiện có cần giữ

| API | Cách dùng trong QUIZ-02 |
|---|---|
| QuizGenerator.generate(QuizPool, Random) | Inject randomness; không dùng Random mới hoặc đồng hồ ngầm bên trong. |
| QuizPool | Dedup stableId, reject cùng ID khác payload, đánh giá availability, cung cấp meaningKey và target count. |
| QuizPool.createQuestion | Bắt buộc dùng để validate option key/text và nghĩa đáp án đúng qua cùng normalizer của pool. |
| QuizGenerationOutcome | READY / EMPTY với lý do / INVALID_INPUT; lỗi storage không bị đổi thành EMPTY. |
| QuizVocabulary | Giữ stableId, lemma, POS, nghĩa và context để bàn giao prompt không mơ hồ. |

Không tự dựng lại bộ model hoặc dùng factory bypass validation của QUIZ-01. QuizOption.id là định danh lựa chọn; stableId của entry cung cấp option có thể làm ID duy nhất trong câu khi đã chọn bốn entry khác nhau. Caller submit ID, không suy vị trí đúng cố định.

## 4. Quy tắc nghiệp vụ cần bảo toàn

- Pool chỉ lấy vocabulary thuộc pack active; gói paused/inactive hoặc orphan không đóng góp. Không tự kích hoạt gói để đủ điều kiện.
- Cùng stableId ở nhiều pack chỉ tính một. Hai payload xung đột cùng ID là invalid input, không âm thầm giữ first/last.
- Cần ≥4 stableId hợp lệ và ≥4 meaningKey khác nhau. Thiếu từ hoặc thiếu nghĩa trả EMPTY có lý do, không tạo session rỗng.
- Khi đủ điều kiện, N=min(10, số stableId hợp lệ). Không đổi N thành số meaningKey: nhiều entry cùng nghĩa vẫn có thể được hỏi ở các câu khác nhau.
- Câu hỏi không lặp stableId. Bốn lựa chọn trong mỗi câu có meaningKey khác nhau từng đôi; nghĩa đúng khớp từ được hỏi.
- Dòng checklist “no repeated stableId/meaning” được hiểu theo BR-08: không lặp ID câu và không lặp nghĩa trong bốn options của cùng câu. Không tự cấm hai câu có nghĩa đúng giống nhau, vì sẽ làm sai target count khi pool có nghĩa trùng.
- Không lấy nhiễu từ ngoài snapshot pool hoặc bịa nghĩa để đủ bốn lựa chọn.
- Sinh bài không được thay đổi input, catalog, activation, progress, ReviewEvent hoặc QuizAttempt.

## 5. Thiết kế production normalizer

Quy trình: Unicode NFC → trim tập Unicode White_Space thống nhất với contract → full default Unicode casefold; giữ dấu, không NFKC, không bỏ dấu, không dùng casing theo locale thiết bị. Không tự collapse khoảng trắng nội bộ ngoài quy định nguồn/contract.

`String.toLowerCase(Locale.ROOT)` không đủ cho ß/SS hoặc sigma; normalizer đơn giản trong test QUIZ-01 không được dùng làm production adapter.

Hướng triển khai ưu tiên: một utility Java thuần với bảng full casefold sinh từ Unicode CaseFolding.txt phiên bản pin rõ. Dùng mapping C/F, ưu tiên full F; không dùng Turkic T cho default folding. Giữ URL/version/checksum và license notice của dữ liệu sinh bảng; xác minh artifact chính thức trước khi dùng, không tự ghi checksum chưa tải. Không kéo cả thư viện lớn vào APK khi chưa có phân tích dung lượng/compatibility.

Kiểm tra code point/supplementary mappings, không duyệt char rồi làm mất surrogate pair. Không đổi ID stableId khi chuẩn hóa nghĩa. NFC trên Android API 26/35 và JDK phải qua vector; nếu có khác biệt Unicode version thì giải quyết trước khi tuyên bố conformant. Không bỏ qua case fail hoặc đổi expected để hợp thức hóa implementation.

Vector bắt buộc: NFC/NFD tiếng Việt; khoảng trắng đầu/cuối/NBSP; khác hoa/thường; Straße/STRASSE; sigma Σ/σ/ς; dotted I theo default folding; dấu tiếng Việt ban/bàn phải khác; empty/blank/null phải được xử lý theo input contract.

## 6. Thuật toán generator đề xuất

1. Kiểm tra pool/random. Null là INVALID_INPUT, không phải thiếu dữ liệu.
2. Nếu pool chưa READY, trả EMPTY giữ đúng availability reason.
3. Copy và sắp vocabulary theo stableId để canonicalize input order; không thay đổi list nguồn. Đây là quy ước kỹ thuật giúp cùng snapshot/seed cho cùng output kể cả repository trả thứ tự khác.
4. Lập bucket meaningKey → entries; sắp thứ tự bucket và entry ổn định trước random sampling.
5. Shuffle danh sách candidate bằng Random được truyền vào; lấy N entry đầu, không lấy hoàn lại.
6. Với từng từ mục tiêu, loại bucket cùng meaningKey; chọn ba bucket khác nhau không hoàn lại, rồi chọn một entry đại diện mỗi bucket.
7. Tạo một option đúng từ nghĩa của target và ba nhiễu; key lấy từ cùng pool/normalizer. Shuffle bốn options; correctOptionId đi theo option, không dựa vào index cũ.
8. Tạo câu qua pool.createQuestion; xác nhận membership, đúng N, unique question IDs và các invariants trước trả READY.

Lập chỉ mục bucket một lần, tránh vòng lặp random-until-unique có thể kẹt khi chỉ có bốn nghĩa. Chọn giải pháp vừa đủ cho catalog; benchmark lớn chỉ khi có bằng chứng cần tối ưu, không thêm dependency cho sampling.

### Nghĩa khác chuỗi chưa đủ chứng minh đáp án không mơ hồ

Một lemma có thể có nhiều POS/sense. Mang POS/context của mục tiêu trong câu và bàn giao QUIZ-03 hiển thị prompt đủ phân biệt. Loại các nhiễu biết rõ tạo thêm đáp án hợp lý cho cùng prompt, hoặc yêu cầu content QA chỉnh dữ liệu trước khi phát hành. Không coi inequality meaningKey là kiểm tra ngữ nghĩa đầy đủ.

Nếu quy tắc chống mơ hồ làm không thể tạo bài đúng N, cần trả lỗi rõ và ghi vấn đề dữ liệu/thiết kế để review. Không tự giảm N, lặp từ, bịa nhiễu hoặc âm thầm trả bài sai SRS. Chốt policy này bằng fixture đa nghĩa trước khi nghiệm thu generator thật.

## 7. Tích hợp repository và lỗi

- Dùng QuizRepository/active-only data boundary hiện có; domain generator không nhận DAO, Context hay Room entity.
- Map stableId/lemma/POS/meaning/context theo contract; không dùng FTS rowid hoặc parse ID thành số.
- Phân biệt READ_FAILED/NOT_FOUND/invalid data với pool hợp lệ nhưng chưa đủ từ. UI cần lý do để hướng dẫn người dùng; không tạo dữ liệu trống để che lỗi DB.
- Query là blocking nên caller thực thi ngoài main thread. Không tự tạo thread/framework trong model để né quy ước hiện có.
- Mỗi lần bắt đầu Quiz đọc snapshot mới. Thay đổi activation phải phản ánh ở lần bắt đầu tiếp theo; không dùng pool cache xuyên thay đổi. Chính sách thay đổi pack trong một phiên đã chạy thuộc QUIZ-03, không tự thêm hủy phiên giữa chừng.
- Giữ outcome/domain boundary hiện có; nếu cần use case orchestration, dùng RepoResult hiện có để giữ lỗi storage, không tạo framework Result song song.

## 8. Kế hoạch kiểm thử

| Nhóm | Ca cần kiểm tra | Kết quả mong đợi |
|---|---|---|
| Số lượng | 0/3/4/7/9/10/12 unique IDs | EMPTY/EMPTY/4/7/9/10/10 câu khi đủ nghĩa. |
| Thiếu nghĩa | ≥4 hoặc ≥10 ID nhưng 1/2/3 meaningKey | EMPTY đúng lý do, không loop hoặc bù options. |
| ID | Cùng ID ở nhiều pack; cùng lemma khác ID; cùng ID khác payload | Dedup đúng, giữ sense riêng; conflict không bị nuốt. |
| Meaning normalization | Các vector mục 5, đổi locale thiết bị | Key conformant, giữ dấu; không dùng fake normalizer làm evidence. |
| Options | Pool đúng 4 nghĩa; pool lớn; nhiều từ chung nghĩa | 4 options, một key đúng, ba nhiễu khác nhau và thuộc pool. |
| Output | Target count, membership, unique question ID, correct mapping | Đúng N; không tạo ID/từ ngoài pool; createQuestion validation chạy thật. |
| Randomness | Cùng snapshot/seed mới, đảo input order; nhiều seed | Output tái lập theo policy; vị trí đúng có thay đổi; không assert ngẫu nhiên phải khác trong mọi cặp seed. |
| Property-style | Nhiều pool/seed xác định, gồm phân bố nghĩa lệch | Invariants luôn giữ, không test xác suất flaky hoặc lấy zero-test làm đạt. |
| Bất biến | Snapshot input trước/sau generate, collections readonly | Không sửa pool/options/caller-owned list. |
| Active boundary | A/B/shared, pause A, all paused, orphan row | Chỉ active source; shared một lần; không auto-activate. |
| Không side effects | Snapshot catalog/userdata trước/sau tạo bài | Không tạo QuizAttempt/progress/review hoặc sửa UserPack. |
| Lỗi | Null random/pool, repository failure, dữ liệu xung đột | INVALID_INPUT/storage error phân biệt EMPTY. |
| Đa nghĩa | Cùng lemma khác POS/sense, context thiếu | Policy prompt/nhiễu rõ; trường hợp không bảo đảm báo lỗi thay vì tạo bài mơ hồ. |

Giữ toàn bộ regression QUIZ-01 và repository tests. Chạy normalizer/generator core bằng JUnit thuần; Android/Robolectric API 26/35 dùng cho adapter/integration khi cần. Không gọi mạng để sinh bài hoặc chạy test.

## 9. Các bước triển khai và ước lượng

| Bước | Công việc | Giờ đề xuất |
|---|---|---|
| 1 | Chốt API hiện có, interpretation nghĩa trùng và policy đa nghĩa | 0.75 |
| 2 | Production normalizer, nguồn Unicode/version/license, conformance vectors | 2.0 |
| 3 | Bucket/sample/shuffle và output validation | 2.0 |
| 4 | Orchestration repository, error/empty và kiểm tra no-write | 1.0 |
| 5 | Unit/property/integration regression, build và tài liệu evidence | 2.25 |
| Tổng | Bám ước lượng phân công | 8.0 |

Test được viết cùng mỗi bước; bước 5 là tổng hợp/chạy toàn bộ và sửa lỗi. Setup toolchain hoặc điều chỉnh policy nội dung phát sinh phải báo riêng, không bỏ test để giữ số giờ.

## 10. File dự kiến và lệnh kiểm chứng

```text
app/src/main/java/com/vocamind/app/domain/quiz/
  [implementation QuizGenerator]
  [implementation MeaningNormalizer và casefold table/helper]
  [use case orchestration nếu cần theo convention]
app/src/test/java/com/vocamind/app/domain/quiz/
  [normalizer/generator/property-style tests]
app/src/test/java/com/vocamind/app/data/repository/
  [active-pool/no-write integration tests]
tools/unicode/ hoặc thư mục provenance phù hợp
  [nguồn pin, license, generator bảng và conformance vectors]
docs/quiz-02-validation.md
```

Tên file cụ thể được chốt khi code; không nhân đôi QuizPool/Question/Result. Không sửa Fragment/layout hoặc schema trong task này.

Lệnh dự kiến từ checkout hiện có, không cần worktree:

```sh
java -version
./gradlew --version
./gradlew :app:testDebugUnitTest --tests 'com.vocamind.app.domain.quiz.*' --tests 'com.vocamind.app.data.repository.*'
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
git diff --check
git status --short
```

Dùng JDK 17/SDK theo foundation thực tế; kiểm tra toolchain trước build. Đọc JUnit XML/report của lần chạy hiện tại, ghi tests/failures/errors/skips. Chưa chạy các lệnh này cho QUIZ-02; không ghi sẵn PASS. Lint/build thành công không thay thế test semantics hoặc kiểm tra ngữ nghĩa học liệu.

## 11. Handoff, review và Done

- Thành bàn giao generator/normalizer, tests và fixtures; Trường review theo task.
- Nhân/Đạo xác nhận active-only repository projection và error contract; không để phần mapping chỉ tồn tại trong fake.
- QUIZ-03 nhận READY/EMPTY/error, questions/POS/context/correctOptionId và seeded examples; chịu trách nhiệm UI, debounce, confirmation exit và finalize persistence.
- Tạo docs/quiz-02-validation.md sau khi chạy, ghi commit/toolchain/lệnh/kết quả và giới hạn. PR target develop; nếu base thay đổi, chạy regression trên SHA tích hợp mới.

Checklist:

- [ ] Production normalizer conformant và có nguồn/version/license/checksum được xác minh.
- [ ] Generator thật trả đúng N, membership, ID và bốn nghĩa/options hợp lệ.
- [ ] EMPTY, INVALID_INPUT và storage failure phân biệt rõ.
- [ ] Active/paused/shared và no-write được integration test trên boundary thật.
- [ ] Seeded/property tests tái lập, regression QUIZ-01 không bị phá.
- [ ] Policy prompt/nhiễu đa nghĩa được review, không tự đổi SRS để giảm N.
- [ ] Unit/build/lint có evidence thực, CI trên commit triển khai được kiểm tra.
- [ ] Trường review; góp ý được xử lý trước khi đánh dấu Done.

QUIZ-02 Done không đồng nghĩa toàn bộ AC-10 đã nghiệm thu: UI, hoàn tất/lưu bài và bỏ dở còn thuộc QUIZ-03/integration.
