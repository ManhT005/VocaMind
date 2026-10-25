# VOC-DATA-01 — Đối chiếu tài liệu gốc

Đã đọc các tài liệu tại `D:/ADR NHOM 2`; không sửa file Word/Excel gốc.
SHA-256 nhận diện đúng bản đã đối chiếu:

| Tài liệu | SHA-256 |
| --- | --- |
| Đặc Tả SRS Cho VocaMind.docx | `6c0b6b9cd7b8a420399a0a872f371a41c17f845c526a60b01f4a4e234bfc1c09` |
| VocaMind_SRS_Baseline_v1.1.docx | `bc2d687bb1a70f28fe0e4566f77d07dc877d08269a6265c361a06fb816c0c7ec` |
| VocaMind — Phân công.xlsx | `f5256e6767affe0cf3fb34403c409fa70df432b26a285e3fb66ac500a98ff9cd` |

Plan bên ngoài đã được đọc; plan trong repository là bản cập nhật cùng code.

| Căn cứ | Yêu cầu | Implementation / evidence |
| --- | --- | --- |
| Excel, dòng VOC-DATA-01 | D1; 5 giờ; Thi, Nhân; Trường review; nghiên cứu nguồn và ledger | Sáu source records, include/exclude, evidence và obligations |
| SRS cũ, mục kỹ nghệ học liệu/ETL | Kiểm toán quyền khi đóng gói nguồn ngoài | Điều khoản upstream lưu local; không coi kết luận pháp lý trong SRS cũ là giấy phép |
| Baseline §7.3 DATA-01 | NGSL/TSL, Kaikki ViWiktionary, Tatoeba text; không FVDP/audio | Registry và exclusion độc lập cờ eligibility |
| Baseline DATA-06; BR-12 | Ledger từng snapshot, evidence và PO/Content Lead duyệt | Snapshot NGSL/TSL, research evidence và approval template |
| AC-12; DEC-09 | Attribution offline, sign-off; mục chưa rõ quyền không đóng gói | Notice, legal code offline; gate kiểm tra role và snapshot ID, nguồn thật vẫn pending |
| NFR-VM-12 | Inventory/license và quyền phân phối trước release | Audit SHA-256/bytes cả pending snapshot và research evidence |

Excel đặt canonical schema ở VOC-DATA-02, ingestion reproducible ở VOC-DATA-03.
Task này bàn giao source governance; không tự triển khai normalization/sense,
mapping canonical, record QA hoặc catalog MVP. UI attribution offline và ngưỡng
150 từ cần nghiệm thu tích hợp/release, không thể đóng bằng test gate Python.

Baseline v1.1 là chuẩn tắc khi khác SRS cũ. Không suy giấy phép mã Java từ việc
đóng gói dữ liệu. Review kỹ thuật và sign-off quyền của PO/Content Lead là hai
trách nhiệm khác nhau. Chưa có sign-off thực; không đánh dấu AC-12 đã đạt.
