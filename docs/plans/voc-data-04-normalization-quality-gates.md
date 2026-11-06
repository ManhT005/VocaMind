# Kế hoạch triển khai VOC-DATA-04

## 1. Thông tin task và căn cứ

- Task: VOC-DATA-04 — Normalization + quality gates.
- Phụ trách: Vương Quang Thành. Reviewer: Trường.
- Ưu tiên: Critical. Ước lượng: 8 giờ. Vị trí kế hoạch: tuần 1, D3–D5.
- Phụ thuộc trực tiếp: VOC-DATA-03. Cần tái sử dụng contract VOC-DATA-02 và ledger VOC-DATA-01.
- Bảng phân công: https://docs.google.com/spreadsheets/d/1Zx4QWaSalYk2-KWoaRRxwL6Hd-vv-sa2Lv9dBDxzQFg/edit
- Căn cứ chính: VocaMind_SRS_Baseline_v1.1(3).docx, §7.2–7.3, DATA-02/03, BR-01/12, AC-04/10/12, DEC-14.
- Tài liệu đối chiếu: SRS_Ung_dung_hoc_tu_vung_tieng_Anh_Android(3).docx.

Bảng phân công sử dụng mô hình catalog đóng sẵn và mã yêu cầu của Baseline v1.1, vì vậy dùng baseline này cho task. SRS 1.0 mô tả ứng dụng tự quản lý từ vựng và cho phép thiếu ví dụ; không áp dụng quy tắc đó để nới lỏng bộ QA MVP của baseline.

Kế hoạch dựa trên tài liệu và bảng phân công, chưa kiểm tra mã nguồn repository. Tên thư mục, module, CLI và trường provenance bổ sung phải được xác nhận từ code trước khi triển khai; các tên đầu ra dưới đây là đề xuất.

## 2. Mục tiêu và phạm vi

Xây bước QC build-time sau ingestion, trước load/build catalog:

`VOC-DATA-03 output → normalization → record gates → dataset/pack gates → validated output + reports`

Trong phạm vi:

- Chuẩn hóa Unicode, khoảng trắng, lemma và POS.
- Kiểm tra một nét nghĩa, meaningVi và cặp exampleEn/exampleVi.
- Kiểm tra stableId, duplicate và provenance/license.
- Kiểm tra mỗi pack có ít nhất 4 nghĩa phân biệt để tạo Quiz.
- Xuất báo cáo, fixtures, tests và hướng dẫn chạy.

Ranh giới:

- Không thêm UI Android hoặc network API runtime.
- Không viết lại ingestion hoặc thuật toán stableId đã chốt.
- CEFR/topic enrichment thuộc VOC-DATA-05.
- Build catalog.db, Room/FTS validation và nghiệm thu catalog ≥150 stableId cùng membership 40/40/40/30 thuộc VOC-DATA-06 và các task DB tương ứng.
- Có thể báo sớm số lượng từ/membership, nhưng không tự biến các ngưỡng release cuối cùng thành điều kiện bắt buộc cho mọi fixture nhỏ của task này.

## 3. Xác nhận đầu vào trước khi code

- [ ] Xác định nhánh tích hợp và commit thực tế chứa VOC-DATA-01/02/03.
- [ ] Đọc AGENTS.md và quy ước repository.
- [ ] Chạy lại pipeline VOC-DATA-03 theo hướng dẫn hiện có.
- [ ] Xác định định dạng canonical, pack membership, source ledger, snapshot và checksum.
- [ ] Đọc hàm stableId hiện có và fixture chuẩn của VOC-DATA-02.
- [ ] Xác nhận API/output mà VOC-DATA-06 sẽ tiêu thụ.
- [ ] Ghi rõ thiếu sót hoặc lệch contract; xử lý trước khi nối QC.

Bảng hiện ghi VOC-DATA-03 Hoàn thành và gate VOC-DATA-04 Ready; đây không thay thế việc xác minh code và output thực tế.

## 4. Quy tắc normalization và validation

| Nhóm | Quy tắc | Kết quả |
| --- | --- | --- |
| Unicode | NFC; xử lý khoảng trắng thừa và ký tự điều khiển không hợp lệ; giữ dấu tiếng Việt | Output ổn định, không mất nội dung |
| Lemma/POS | Theo contract VOC-DATA-02; POS lạ không tự suy đoán | Báo lỗi nếu không map được |
| StableId | Dùng thuật toán contract; kiểm tra định dạng và tính nhất quán với identity tuple | Sai ID phải báo lỗi, không tự sửa để pass |
| Trường bắt buộc | meaningVi, exampleEn, exampleVi sau chuẩn hóa đều khác rỗng | Thiếu trường làm gate fail |
| Single sense | Một record biểu diễn một nét nghĩa | Tự động phát hiện dấu hiệu nghi vấn; duyệt nội dung xác nhận |
| Ví dụ | Câu EN dùng đúng nét nghĩa, câu VI dịch tương ứng | Cần bằng chứng duyệt nội dung |
| IPA | Nullable; chuỗi rỗng xử lý theo contract | Không reject chỉ vì thiếu IPA |
| Provenance | Truy về nguồn/snapshot; giữ nguồn từng thành phần khi ghép học liệu | Không truy vết được làm gate fail |
| License | Đối chiếu ledger, evidence và phê duyệt; không chỉ kiểm tra licenseCode tồn tại | Nguồn thiếu/chưa duyệt chặn output được duyệt |
| Duplicate | StableId duy nhất trong dataset | Fail; không âm thầm ghi đè hoặc chọn record đầu |
| Pack | Tham chiếu hợp lệ; mỗi pack ≥4 nghĩa Việt phân biệt | Không đủ làm gate fail |

### 4.1. Bảo toàn stableId

- Không đưa wording nghĩa hoặc câu ví dụ vào identity.
- Không tạo thuật toán ID mới song song với VOC-DATA-02.
- Nếu normalization làm lemma/POS lệch với ID hiện có, báo lỗi và giải quyết theo contract; không âm thầm đổi ID đã phát hành.
- Sửa meaningVi/exampleEn/exampleVi không được đổi stableId.
- Không dùng FTS rowid làm khóa nghiệp vụ.

### 4.2. Khóa so sánh nghĩa cho Quiz

Dùng NFC + trim + casefold và quy tắc khoảng trắng nhất quán để đếm nghĩa phân biệt. Tái sử dụng hoặc chia sẻ quy ước với QUIZ-02; không tự bỏ dấu tiếng Việt hay xóa dấu câu tùy tiện. Giữ riêng nội dung hiển thị.

Đếm chuỗi đã chuẩn hóa chỉ chứng minh khác biệt theo quy tắc so sánh; không chứng minh hai cách diễn đạt đồng nghĩa là hai nghĩa khác nhau. Nội dung mơ hồ cần review.

### 4.3. Cảnh báo và lỗi bắt buộc

- Blocking error: thiếu trường bắt buộc, ID sai/trùng, provenance/license không đủ, pack dưới 4 nghĩa phân biệt, tham chiếu pack/record lỗi.
- Warning: ví dụ ngoài khoảng 5–20 từ hoặc dấu hiệu nhiều nét nghĩa cần rà soát.
- SRS chỉ đề xuất 5–20 từ/câu; không tự đặt thành hard gate.
- Warning về nội dung không thay thế quyết định duyệt nghĩa/ví dụ cho bộ MVP.

## 5. Trình tự thực hiện và thời lượng

| Bước | Giờ | Công việc | Điều kiện hoàn tất |
| --- | ---: | --- | --- |
| 1 | 0,75 | Xác nhận code và contract đầu vào | Có sample input và lệnh pipeline chạy được |
| 2 | 1,50 | Implement normalization | Deterministic, idempotent, bảo toàn nội dung và identity |
| 3 | 1,50 | Implement record gates | Lỗi có mã, record reference, field và lý do |
| 4 | 1,00 | Implement dataset/pack gates | Duplicate và thiếu nghĩa phân biệt đều bị phát hiện |
| 5 | 0,75 | Nối CLI/pipeline và reports | Gate fail trả mã lỗi; không xuất nhầm artifact đã duyệt |
| 6 | 1,50 | Viết và chạy tests | Fixtures hợp lệ/lỗi và integration pipeline pass |
| 7 | 1,00 | Review nội dung, tài liệu và PR | Có bằng chứng và reviewer tái chạy được |
| Tổng | 8,00 | | |

Ước lượng giả định đầu vào đã dùng được. Nếu cần biên tập nhiều nghĩa/ví dụ hoặc sửa upstream contract, ghi nhận phần việc phát sinh riêng; không rút ngắn QA để giữ đúng 8 giờ.

## 6. Output và báo cáo

Tái sử dụng cấu trúc repo; tên gợi ý:

- `validated_vocab.jsonl`: chỉ record được chấp nhận, định dạng theo contract hiện hữu.
- `rejected_records.jsonl`: record reference, stableId nếu có, errorCode, field, reason; đủ truy vết về đầu vào.
- `qc_report.json`: tổng input/accepted/rejected; số lỗi theo mã; duplicate ID; lỗi provenance/license; thống kê từng pack; trạng thái overall.
- `qc_report.md`: bản dễ đọc cho reviewer, ghi input snapshot/checksum, config/version, lệnh chạy và kết quả.
- Fixtures và tests trong module pipeline hiện có.
- Hướng dẫn normalization, gate và cách xử lý reject.
- Bằng chứng review nghĩa/ví dụ theo record hoặc danh sách truy vết tương đương.

Yêu cầu hành vi:

- Cùng snapshot/config/tool version cho cùng nội dung đầu ra và kết quả validation.
- Timestamp chạy, nếu có, để riêng khỏi nội dung dùng so sánh deterministic.
- Dùng thứ tự output/report ổn định.
- Khi có blocking error, CLI trả mã lỗi; reject report không biến toàn bộ dataset thành pass.
- Chỉ công bố artifact đạt chuẩn sau khi toàn bộ gate liên quan pass; không để output cũ bị hiểu nhầm là kết quả hợp lệ của lần chạy lỗi.
- Sửa hoặc loại record lỗi có kiểm soát, chạy lại pack gates và ghi nhận ảnh hưởng số lượng.

## 7. Kiểm thử tối thiểu

| ID | Tình huống | Kết quả mong đợi |
| --- | --- | --- |
| QC-01 | Record đầy đủ, nguồn đã duyệt | Pass |
| QC-02 | meaningVi rỗng hoặc chỉ khoảng trắng | Fail, đúng field và mã lỗi |
| QC-03 | Thiếu exampleEn hoặc exampleVi | Fail |
| QC-04 | IPA null | Pass |
| QC-05 | Hai record cùng stableId | Fail, không ghi đè |
| QC-06 | Sửa wording nghĩa/ví dụ | StableId không đổi |
| QC-07 | Hai dạng Unicode tương đương | NFC output nhất quán |
| QC-08 | POS không map được | Fail |
| QC-09 | Provenance thiếu hoặc source không tồn tại | Fail |
| QC-10 | Nguồn có licenseCode nhưng chưa duyệt | Fail |
| QC-11 | Pack nhiều từ nhưng chỉ 3 nghĩa phân biệt | Fail |
| QC-12 | Pack đúng 4 nghĩa phân biệt | Pass gate Quiz |
| QC-13 | Nghĩa chỉ khác hoa/thường, NFC, khoảng trắng | Không đếm tăng giả |
| QC-14 | Cùng input/config chạy hai lần | Cùng nội dung output và kết quả QC |
| QC-15 | Normalize output đã normalize | Không đổi dữ liệu |
| QC-16 | Có một blocking error | CLI nonzero, không công bố output đạt |
| QC-17 | Ví dụ ngoài 5–20 từ | Warning theo policy, không tự reject |
| QC-18 | Pack tham chiếu vocabulary không có | Fail |
| QC-19 | Ghép nghĩa/ví dụ từ nhiều nguồn | Giữ được provenance các thành phần |

Kiểm thử tự động không thay thế kiểm tra nghĩa đúng, single sense và bản dịch tương ứng. Các nội dung này cần review thủ công cho bộ MVP.

## 8. Definition of Done

- [ ] Input/output tương thích VOC-DATA-02/03 và consumer VOC-DATA-06.
- [ ] Normalization deterministic, idempotent và giữ dấu tiếng Việt.
- [ ] Không tự đổi stableId khi sửa wording; không dùng FTS rowid làm ID.
- [ ] meaningVi/exampleEn/exampleVi được enforce; IPA nullable.
- [ ] Duplicate, provenance/license và pack gates hoạt động.
- [ ] Không coi license metadata tồn tại là đã được phê duyệt.
- [ ] Record lỗi được truy vết và xử lý có kiểm soát; rerun thành công.
- [ ] Các test quan trọng pass và report có bằng chứng.
- [ ] Có bằng chứng duyệt nghĩa/ví dụ cho dữ liệu MVP được chấp nhận.
- [ ] Reviewer chạy lại bằng hướng dẫn và thu được kết quả tương ứng.
- [ ] Trường review PR; CI/checks bắt buộc pass trước merge.

Task này cung cấp bằng chứng hỗ trợ AC-04/10/12; không tự xác nhận hoàn tất toàn bộ các AC, vì còn phụ thuộc UI, Quiz generator, attribution offline và phê duyệt nguồn.

## 9. Git và bàn giao

1. Xác nhận nhánh tích hợp thực tế và các dependency đã có trong nhánh đó.
2. Tạo nhánh task từ nhánh tích hợp đã cập nhật theo quy ước nhóm.
3. Tập trung thay đổi trong pipeline/tests/docs; tránh sửa Android schema hoặc contract chung khi không cần thiết.
4. Nếu bắt buộc sửa contract, đánh giá consumer và báo rõ trong PR; không âm thầm mở rộng phạm vi.
5. Chạy checks liên quan, cập nhật nhánh với integration base và giải quyết conflict trước review cuối.
6. Mở PR vào đúng nhánh tích hợp, giao Trường review; ghi base/head commit, lệnh chạy, kết quả QC và giới hạn còn lại.
7. Bàn giao artifact/contract cho VOC-DATA-05 và VOC-DATA-06.
