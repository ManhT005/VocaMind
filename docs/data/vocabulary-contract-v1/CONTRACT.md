# VOC-DATA-02 — Vocabulary canonical schema & data contract

**Phiên bản:** 1.0.0

**Trạng thái:** Implementation candidate — đã kiểm thử contract; chờ review của Nhân và xác nhận áp dụng của Trường. Không tự coi là baseline đã được nhóm phê duyệt.

**Owner:** Vương Quang Thành. **Reviewer theo phân công:** Bùi Vĩ Nhân. **Consumer ETL:** Lê Mạnh Trường. **Tích hợp catalog:** Nguyễn Mạnh Thi.

**Mục tiêu:** Các bên dùng chung một cấu trúc dữ liệu và quy tắc ID trước khi phát triển database/pipeline. Chưa có contract đã phát hành trong repository nên bắt đầu ở v1, không giả định đã tồn tại v2.

## 1. Căn cứ và phạm vi

Nguồn yêu cầu:

- `VocaMind_SRS_Baseline_v1.1.docx`: BR-01/02/08/12; FR-VM-008/009/014/045/046; mục 7.1–7.4; AC-02/04/10/12/13; DEC-04/05/09/10/14.
- `VocaMind — Phân công .xlsx`, sheet Phân công, VOC-DATA-02: canonical schema, single-sense, SHA-256 stableId 16 hex, sourceId/contentVersion, mapping Room, fixture hợp lệ/không hợp lệ, giữ ID khi sửa wording. Reviewer: Nhân. Đầu vào liên quan: VOC-DATA-01, DATA-01.
- SRS Android v1.0 là tài liệu lịch sử; không nhập CRUD từ/chủ đề của bản này vào catalog MVP v1.1.

**Yêu cầu từ tài liệu:** một mục từ một sense; có lemma/POS/nghĩa/ví dụ EN–VI; IPA nullable; ID bền vững; catalog/userdata tách biệt; FTS join rowid INTEGER; nguồn/snapshot được truy vết và phê duyệt; ≥150 từ duy nhất/4 gói theo mức 40/40/40/30.

**Quyết định kỹ thuật đề xuất của contract này:** tên file/envelope JSON, kiểu version dạng `X.Y.Z`, enum POS, whitespace/case normalization chính xác, senseIndex bắt đầu từ 1, registry build-time, metadata provenance theo thành phần, mã pack và orderNo bắt đầu từ 0. Các quyết định này cụ thể hóa chỗ SRS chưa quy định; cần review trước khi đánh dấu task Done.

Contract bao phủ dữ liệu đầu vào chuẩn của ETL và mapping catalog; không triển khai Android UI, Room entities/migrations, FTS asset, tải nguồn, thuật toán học hoặc Quiz generator. Python validator chạy lúc phát triển, không phải dependency runtime của app.

## 2. Định dạng trao đổi duy nhất

File chuẩn: `canonical-catalog.json`, UTF-8 không BOM, JSON nghiêm ngặt, không comment, không property trùng. JSON Schema Draft 2020-12 trong `catalog.schema.json` là chuẩn cấu trúc có thể kiểm tra bằng máy. Các field integer phải được ghi bằng token nguyên (`1`, không `1.0` hoặc `1e0`); đây là kiểm tra canonical bổ sung của validator vì JSON Schema coi số có phần thập phân bằng 0 là integer về mặt toán học. Không tự thêm field; field mới đi qua version/review.

Envelope có đúng các trường:

| Trường | Ý nghĩa |
|---|---|
| schemaVersion | `1.0.0`, phiên bản contract/shape. |
| contentVersion | Phiên bản snapshot nội dung `X.Y.Z`, ví dụ `1.0.0`. Không phải Room DB version. |
| fixtureOnly | `true` với dữ liệu thử; bản phát hành phải là `false` sau review nội dung thực. |
| sources | Danh sách metadata nguồn/snapshot, đủ để đóng gói attribution offline. |
| entries | Các VocabularyEntry canonical, một bản ghi mỗi sense. |
| identityRegistry | Sổ định danh bền vững dùng build-time, giữ qua các lần nhập. |
| packs | Metadata gói học. |
| packWords | Liên kết gói–từ và thứ tự hiển thị. |

Chọn JSON envelope để nhóm có một fixture bàn giao dễ kiểm tra. Nếu ingestion cần streaming/JSONL thì đó là định dạng nội bộ của VOC-DATA-03; output bàn giao vẫn theo envelope này. Không đưa envelope nguyên xi làm Room asset; catalog.db cần bước build/validate riêng.

## 3. Hợp đồng VocabularyEntry

Mọi property trong bảng đều phải xuất hiện. Chỉ `ipa` được null; không thay null bằng `"null"`, chuỗi trắng hoặc tự sinh IPA. Trường String bắt buộc phải có nội dung sau chuẩn hóa.

| Trường | Kiểu | Quy tắc |
|---|---|---|
| id | String | 16 ký tự `[0-9a-f]`, stableId của sense, map sang VocabularyEntry.id TEXT PK. Không thêm field alias stableId. |
| lemma | String | Cách viết hiển thị; NFC/whitespace canonical; giữ hoa/thường có ý nghĩa. |
| normalizedLemma | String | Kết quả N1(lemma) tại mục 4; không bỏ dấu hoặc dấu câu. |
| pos | String enum | Một mã POS tại mục 4; không dùng `n.`, `v.` hoặc nhãn tiếng Việt trong output. |
| senseIndex | Integer | 1..2147483647, nhóm cấp ổn định theo (normalizedLemma, pos); không lấy số dòng hoặc xếp hạng nguồn. Build-time. |
| meaningVi | String | Một nét nghĩa; không nối nhiều sense để tăng coverage. Các cách diễn đạt đồng nghĩa của cùng sense không tự tạo sense mới. |
| exampleEn | String | Ví dụ tiếng Anh thể hiện sense đó; bắt buộc ở bộ QA MVP. |
| exampleVi | String | Dịch tương ứng với exampleEn; bắt buộc ở bộ QA MVP. |
| ipa | String hoặc null | Có thì canonical và có provenance; thiếu thì UI hiển thị “Chưa có phiên âm”. |
| sourceId | String | ID nguồn lexical chính; phải tồn tại trong sources. Không đủ để thay thế provenance của nghĩa/ví dụ lấy từ nguồn khác. |
| contentVersion | String | Bằng contentVersion của envelope; biểu thị snapshot chứa entry. |
| provenance | Array | Tham chiếu nguồn theo thành phần; không rỗng, xem mục 6. Build-time metadata. |

Không đặt `isSaved`, `isFavorite`, `box`, `dueAt`, `isLeech`, `reviewCount` hoặc lịch sử vào entry. FR-VM-009 dùng UserWordProgress.isSaved; catalog không bị UI sửa.

**Nội dung cần người kiểm tra:** đúng nghĩa, đúng từ loại, ví dụ thể hiện sense, bản dịch tương ứng, nguồn thực và quyền sử dụng. Validator không hiểu ngữ nghĩa để thay reviewer. Đề xuất độ dài câu 5–20 từ trong SRS là guideline QC, không tự biến thành ràng buộc cứng loại mọi câu ngắn/dài.

## 4. Chuẩn hóa và POS

### 4.1 Chuẩn hóa text hiển thị T1

1. Unicode NFC, không NFKC.
2. Chuyển mỗi ký tự White_Space trong tập cố định sau thành ASCII space U+0020: U+0009..U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000..U+200A, U+2028, U+2029, U+202F, U+205F, U+3000.
3. Gộp chuỗi ASCII space liên tiếp thành một; bỏ space đầu/cuối.
4. Từ chối Cc/Cs còn lại và U+200B/U+FEFF/U+2060 trong canonical output; không âm thầm xóa ký tự để hợp thức hóa.

Áp dụng T1 cho lemma/meaningVi/exampleEn/exampleVi/ipa, tên/mô tả gói. Canonical output phải đã chuẩn hóa; validator báo lỗi thay vì tự sửa. Ingestion giữ bản gốc nếu cần audit và ghi log phép chuyển đổi; đây là công việc VOC-DATA-03/04.

### 4.2 N1 cho định danh và normalizedLemma

N1 = T1 rồi chỉ chuyển ASCII `A..Z` → `a..z`. Không áp dụng Unicode lower/casefold theo locale cho ID; giữ nguyên ký tự ngoài ASCII, dấu tiếng Việt/loanword, apostrophe và hyphen. Như vậy `Book` và `book` cùng key; `café` và `cafe`, `check-in` và `check in` là key khác. Trường hợp chính tả tương đương khác dấu câu phải được người phụ trách hợp nhất về canonical lemma trước khi cấp ID; không tự merge bằng heuristic.

Quy tắc ASCII fold là quyết định kỹ thuật để Python/Java không tạo khác ID do locale hoặc Unicode casing. UTF-8 và NFC phải nhất quán. Có vector NFC/NFD, NBSP, apostrophe và hyphen trong fixtures. Chạy vector trước khi dùng bất kỳ implementation Java nào; mã hiện tại chỉ xác minh implementation Python.

### 4.3 POS canonical

`noun`, `verb`, `adjective`, `adverb`, `pronoun`, `determiner`, `preposition`, `conjunction`, `interjection`, `numeral`, `particle`, `other`.

Ví dụ mapping adapter: `n`, `n.`, `NOUN` → noun; `v`, `v.`, `VERB` → verb; `adj`, `adj.` → adjective; `adv`, `adv.` → adverb. Adapter nguồn phải có mapping rõ và test; không tự đẩy nhãn chưa hiểu vào `other`. `other` chỉ dùng khi được content reviewer phân loại; “phrasal verb” thường có POS verb và lemma là cả cụm. Cần review tương thích nguồn thực trước khi chốt enum.

### 4.4 Khóa phân biệt nghĩa cho Quiz — BR-08

`meaningKey = NFC(meaningVi).trim(Unicode White_Space).casefold()` — full Unicode casefold, không bỏ dấu. Casefold ở đây khác ASCII fold dùng tạo ID. Không thay bằng SQLite NOCASE hoặc Java lowercase rồi coi là tương đương. Vector có `Straße/STRASSE` và sigma để phát hiện implementation chưa đủ. Với Python đã chạy, Unicode data là 15.0.0; implementation khác phải qua các vector và thống nhất version xử lý nếu mở rộng tập ký tự.

Không bắt toàn bộ entries có nghĩa duy nhất: nhiều từ có thể cùng nghĩa Việt. Nhưng mỗi pack cần ít nhất bốn meaningKey khác nhau; Quiz chọn nhiễu theo key, không lấy bốn entry rồi mặc định nghĩa khác nhau. Generator cụ thể thuộc QUIZ-02.

## 5. StableId và identityRegistry

Theo BR-01:

```text
key = N1(lemma) + "_" + pos + "_" + decimal(senseIndex)
fullSha256 = lowercaseHex(SHA-256(UTF-8(key)))
id = fullSha256[0:16]
```

Không salt, không BOM, không newline, không padding số, không hash nghĩa/ví dụ/sourceId/contentVersion. Thuật toán thay đổi phải version/review; không sửa công thức rồi cập nhật hàng loạt ID đã dùng.

### 5.1 Cấp và lưu senseIndex

1. Xác định canonical lemma/POS và sense bằng review nội dung.
2. Tra registry của nhóm. Nếu là sense đã có, dùng ID/index cũ dù nguồn đánh số sense khác.
3. Nếu là sense mới, cấp index lớn hơn mọi index đã dùng (kể cả retired) trong cùng lemma/POS. Cấp theo sổ chung, không theo thứ tự sort file.
4. Lưu registry cùng dữ liệu, review/merge tập trung để tránh hai người cấp cùng index. SenseDefinition là mô tả để người biên soạn nhận diện sense, không tham gia hash.
5. Tính full hash; nếu 16-hex ID đụng ID của key khác thì dừng build và review collision strategy. Không đổi index tùy tiện để che va chạm; không overwrite.

Mỗi registry record: `id`, `identityLemma` = N1(lemma), `pos`, `senseIndex`, `fullSha256`, `senseDefinition`, `status` = active/retired. Các field identity/hash bất biến sau phát hành. Giữ retired record vĩnh viễn và không tái sử dụng.

### 5.2 Thay đổi qua các phiên bản

| Thay đổi | Xử lý |
|---|---|
| Sửa wording nghĩa/ví dụ, thêm IPA, cập nhật nguồn | Giữ ID; sửa provenance nếu cần, tăng contentVersion. Reviewer xác nhận vẫn cùng sense. |
| Đổi hoa/thường/whitespace nhưng N1 không đổi | Giữ ID. |
| Đảo thứ tự file/gói | Giữ ID/index; chỉ cập nhật thứ tự gói nếu chủ đích. |
| Bổ sung sense mới | Cấp index mới, không đánh số lại sense cũ. |
| Chỉnh lemma/POS khiến identity key đổi | Chặn phát hành theo v1; cần ADR compatibility/migration. Không tự ghi lại progress với ID mới. |
| Bỏ entry/pack đã phát hành | V1 chưa có alias format nên chặn removal; giữ bản ghi tương thích theo DEC-04 hoặc thiết kế extension được review. Không xóa userdata. |

Dùng `--previous` với canonical export đã phát hành để phát hiện drift/removal. Giữ baseline export thật; so với fixture không chứng minh tương thích bản đã phát hành. Registry và baseline phải được lưu trong Git hoặc artifact versioned, không tạo lại từ catalog hiện tại rồi coi là lịch sử.

## 6. Nguồn, snapshot và provenance nhiều nguồn

**Nguồn phổ biến không tự chứng minh quyền đóng gói.** BR-12/DEC-09 yêu cầu đối chiếu điều kiện của từng nguồn/snapshot và PO/Content Lead duyệt. Contract không kết luận NGSL/TSL, Kaikki ViWiktionary hoặc Tatoeba chắc chắn được phép dùng theo mọi cách.

Mỗi sources record có: `id`, `title`, `url`, `licenseCode`, `licenseUrl`, `snapshotDate` (YYYY-MM-DD), `snapshotSha256` (64 hex), `attributionText`, `approvalStatus`, `approvedBy`, `evidenceRef`.

- ID nguồn là slug ASCII, xác định một snapshot/bộ điều khoản. Snapshot/hash/URL/license đổi thì tạo sourceId mới, không sửa ngầm snapshot đã có. Validator kiểm tra điều này khi so baseline.
- `approvalStatus`: pending / approved / fixture_only. approved phải có người duyệt và evidenceRef; nhập chuỗi “approved” không thay thế review thật.
- SHA-256 của chính artifact snapshot được nhập, không phải HTML landing page. Với nhiều file, tạo manifest checksum được lưu lại và hash manifest theo adapter; mô tả artifact cụ thể trong hồ sơ nguồn. Validator hiện chỉ kiểm tra định dạng hash, không tải hoặc xác minh artifact nguồn.
- Attribution phải có text đủ để đọc offline, gồm tên/tác giả hoặc contributor attribution theo điều kiện thực tế, thông tin license và sửa đổi nếu cần. Không chỉ lưu link để người dùng tự lên mạng đọc.
- Nguồn thành phần lexical, nghĩa và ví dụ có thể khác nhau. Không gắn toàn bộ mục từ vào wordlist rồi bỏ qua nguồn nghĩa/ví dụ.

Mỗi provenance record: `component` = lexical / meaningVi / examplePair / ipa; `sourceId`; `locator` để tìm lại mục trong snapshot. Bắt buộc có lexical, meaningVi, examplePair; ipa bắt buộc có ref khi không null. Cho phép nhiều nguồn trong cùng component. Nếu hai vế ví dụ đến từ hai nơi, ghi cả hai locator và mô tả rõ phía EN/VI. Locator của bản dịch do nhóm làm phải trỏ vào hồ sơ biên soạn/kiểm tra, không giả định thừa hưởng giấy phép chỉ vì đã dịch.

`VocabularyEntry.sourceId` là nguồn lexical chính và phải xuất hiện trong lexical provenance. Tất cả sources tham chiếu đều được đóng gói thành ContentSource và hiển thị Attribution offline; provenance chi tiết ở build artifacts, không tự thêm các bảng Android ngoài SRS. Nếu cần hiển thị provenance từng field trong UI tương lai, phải review schema extension.

Source registry + provenance chỉ cung cấp truy vết. Hồ sơ snapshot/license evidence và quyết định phê duyệt thuộc VOC-DATA-01/04/06, không được giả lập để đóng task này. Không dùng FVDP hoặc Tatoeba audio trong phạm vi MVP hiện tại.

## 7. LearningPack và PackWord

| Thực thể | Field |
|---|---|
| LearningPack | id (slug ổn định), title, description, category (slug), orderNo (int ≥0 duy nhất giữa packs), version = contentVersion |
| PackWord | packId, vocabularyId = stableId, orderNo (int ≥0 duy nhất trong pack) |

Thứ tự tăng dần theo orderNo, không đòi liên tục. Khóa ghép `(packId, vocabularyId)` duy nhất. Cùng từ có thể ở nhiều gói; không tạo lại entry/ID theo pack. Entry phải thuộc ít nhất một pack của export này.

Mã gói đề xuất cố định:

| id | Tên | Minimum membership release |
|---|---|---|
| toeic-basic | TOEIC Căn bản | 40 |
| toeic-office | TOEIC Công sở & Giao dịch | 40 |
| everyday-english | Tiếng Anh Đời sống Hàng ngày | 40 |
| essential-academic | Từ vựng Học thuật Thiết yếu | 30 |

Toàn catalog cần ≥150 ID duy nhất, không cộng membership để thay cho distinct. Bốn gói có tổng 150 membership nhưng có từ chồng nhau sẽ chưa đạt nếu distinct <150. Mỗi pack có ≥4 meaningKey khác nhau.

## 8. Mapping sang Room/domain và FTS

Nhân triển khai entity/DAO đúng tên nghiệp vụ SRS; không coi schema JSON là Room schema.

| Canonical | Đích | Kiểu/mapping |
|---|---|---|
| entry.id | VocabularyEntry.id | Java String / SQLite TEXT PK, NOT NULL |
| lemma/normalizedLemma/pos | Cột tương ứng | String/TEXT NOT NULL |
| meaningVi/exampleEn/exampleVi | Cột tương ứng | String/TEXT NOT NULL và nội dung không rỗng |
| ipa | VocabularyEntry.ipa | Nullable String/TEXT |
| sourceId | VocabularyEntry.sourceId | String/TEXT tham chiếu ContentSource.id trong catalog |
| contentVersion | VocabularyEntry.contentVersion | String/TEXT |
| source core metadata | ContentSource | id/title/url/licenseCode/licenseUrl/snapshotDate; bổ sung attributionText để thực hiện ghi công offline, review mapping thực tế với Nhân |
| source approvals/hash; entry.senseIndex/provenance; identityRegistry | ETL/versioned build artifacts | Không bắt buộc thêm cột vào Room để chạy UI. Phải giữ ngoài DB để nhập lại và audit. |
| packs/packWords | LearningPack/PackWord | Mapping trực tiếp; orderNo INTEGER; unique/index đúng ràng buộc |

Domain Vocabulary có thể dùng các field nghiệp vụ tương ứng id/lemma/pos/meaning/examples/ipa/sourceId. Không truyền DAO/Room entity thẳng ra UI; mapper bỏ metadata chỉ dành build-time. Đây là định nghĩa canonical DTO qua JSON Schema; DTO Java/Room implementation được dựng theo schema này ở DATA-01/04 khi tích hợp vào Android foundation. Chưa có bằng chứng build Android/Java trong task hiện tại.

FTS4 index lemma/normalizedLemma/meaningVi; `VocabularyFts.rowid = VocabularyEntry.rowid` INTEGER, rồi trả `VocabularyEntry.id` TEXT ra ngoài. Không ghi rowid vào userdata. Chú ý Room FTS external-content/schema identity theo implementation thực tế; dùng schema export thật và integration test asset ở DATA-01/05/VOC-DATA-06. Không hard-code Room identity_hash minh họa, không biến bảng VocabularyEntry thành WITHOUT ROWID.

`isSaved`, box, dueAt, reviews, QuizAttempt nằm trong userdata. Không tạo foreign key xuyên hai database; dùng logical stableId/packId như SRS.

## 9. Versioning và tính lặp lại

- schemaVersion thay đổi khi cấu trúc/ý nghĩa contract thay đổi. V1 validator chỉ nhận 1.0.0; không bỏ qua version không biết.
- contentVersion thay đổi khi export nội dung/metadata/thứ tự thay đổi. Giữ bản cũ, tăng version số, không rollback version âm thầm. Entries và packs mang cùng version snapshot.
- Room version là INTEGER độc lập của từng database, do Nhân quản lý; không lấy contentVersion làm Room version.
- Để output byte-deterministic: UTF-8, LF, JSON indent=2, property theo thứ tự schema; sources/entries/registry theo id tăng dần, packs theo (orderNo,id), packWords theo (packId,orderNo,vocabularyId). Validator chấp nhận thứ tự array khác nhưng không tự reorder; writer ETL chịu trách nhiệm serialization và checksum.
- Baseline và identity registry là input có version cùng snapshot nguồn/cấu hình mapper; không lấy thời gian hiện tại, random hoặc thứ tự nhập làm ID.

## 10. Các lớp kiểm tra và giới hạn

| Lớp | Đã cung cấp trong task này | Còn thuộc task sau |
|---|---|---|
| Structure | JSON Schema, format/date, field bắt buộc, enum, unknown field | Adapter nguồn thực |
| Cross-record | ID/registry/hash, provenance refs, pack links, duplicate/order, meaning diversity, baseline compatibility | Xác minh artifact nguồn thực và collision resolution nếu có |
| Release metadata | fixture flag, counts/quota, source approval metadata | Người duyệt phải kiểm tra evidence thật; validator không đọc/đánh giá tính hợp pháp evidence |
| Nội dung | Fixture/expected errors | QA nghĩa, ví dụ, bản dịch, single-sense thực tế (VOC-DATA-04) |
| Android | Mapping/FTS constraints | Room asset/schema hash/FTS integration, migration/rollback, performance và UI |

PASS_CONTRACT_CHECKS không có nghĩa app hoặc release đã đạt toàn bộ SRS. Không dùng dữ liệu synthetic để tuyên bố đủ 150 từ thật hoặc license được duyệt.

## 11. Handoff, review và Done

1. Thành bàn giao CONTRACT.md, catalog.schema.json, fixtures, validator/tests cùng version.
2. Nhân review entity/mapping, POS/types/nullable, rowid/stableId và constraints. Chưa có schema thì triển khai dựa trên contract sau review.
3. Trường xác nhận adapter nguồn thực có thể tạo đủ field và provenance; output thử phải qua validator. Nếu thiếu meaning/ví dụ thì báo gap nguồn, không đổi contract để dữ liệu thiếu vẫn pass.
4. Thi xác nhận source registry/attribution có đường sang catalog build, và đường lưu baseline/registry.
5. Ghi nhận góp ý và quyết định trong REVIEW.md; sửa contract/tests đồng bộ; mới chuyển trạng thái thành accepted và đánh dấu VOC-DATA-02 Done.

Chưa cần có 150 từ thật để review contract; nhưng fixture không đóng thay task thu thập/QC/đóng gói học liệu. Review là bước còn lại theo bảng phân công, không phải yêu cầu xin phép mới để tiếp tục tự soạn.

## 12. Ma trận truy vết rút gọn

| Yêu cầu | Quy định trong contract | Evidence được cung cấp |
|---|---|---|
| BR-01 / VOC-DATA-02 | Identity key, registry, stableId giữ khi sửa wording | Stable-id vectors, baseline tests |
| FR-VM-008 / DEC-14 | Nghĩa/ví dụ bắt buộc, IPA nullable | Schema + invalid fixtures + null IPA tests |
| FR-VM-014 / DEC-05 | TEXT stableId khác INTEGER rowid | Mapping tài liệu; test SQLite/Room còn thuộc integration |
| BR-08 / AC-10 | MeaningKey, ≥4 nghĩa/pack, không bỏ dấu | Casefold vectors và pack tests; Quiz generator ở QUIZ-02 |
| BR-12 / DEC-09 | Snapshot registry, provenance nhiều nguồn, approval metadata | Source/provenance tests; không thay legal/content sign-off |
| DEC-10 | Distinct count tách membership, minima 40/40/40/30 | Release metadata tests |
| DEC-04 / AC-13 | Registry bất biến, chặn removal v1, baseline comparison | Upgrade contract tests; DB swap/rollback còn thuộc DATA-08 |

## 13. Thay đổi sau khi chốt

Ghi yêu cầu thay đổi → đánh giá tác động ETL/Room/domain/fixtures → reviewer xác nhận → tăng schema/content version phù hợp → chạy tests → thông báo các bên. Không chỉnh ID/schema rồi yêu cầu bên còn lại tự đoán. Đổi schemaVersion không tự nâng cấp các Android database đã cài.
