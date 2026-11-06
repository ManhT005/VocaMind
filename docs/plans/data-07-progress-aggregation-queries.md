# DATA-07 — Progress aggregation queries

## 1. Phân công và căn cứ

| Hạng mục | Nội dung |
| --- | --- |
| Tuần / Ngày | 2 / D8 |
| Task / Nhóm | DATA-07 / Data |
| Người thực hiện / Reviewer | Nhân / Thi |
| Ước lượng / Ưu tiên | 5 giờ / High |
| Phụ thuộc | DATA-02, DATA-03 |
| Yêu cầu | FR-VM-041..043; UC-07; AC-11 |
| Nguồn | VocaMind_SRS_Baseline_v1.1.docx và VocaMind — Phân công.xlsx đính kèm phiên này |
| Nhánh | feature/data-07-progress-aggregation-queries |

Dùng đúng contract PROGRESS-01 đã có: ProgressRepository.readProgress trả
ProgressReadResult với ProgressSnapshot chứa ProgressSummary và List<QuizHistoryItem>.
Không tạo DTO thay thế hoặc tự tính lại thống kê trong UI.

## 2. Nền tích hợp

Nhánh được tạo từ origin/develop tại c2ea69e (có DATA-02 và Progress contract).
DATA-03 tại e668bcb được merge **chỉ vào nhánh DATA-07**, tạo integration commit
84ad09d. Xung đột docs/README.md được giải quyết bằng giữ các liên kết của cả hai
nhánh. Không merge hoặc push vào develop/main.

Khi chạy nền đã ghép, 32 lượt test DATA-03 lỗi do fixture chèn 6 cột ContentSource
trong schema ATTR-01 hiện có 7 cột. Sửa INSERT của fixture test bằng tên cột
rõ ràng và attributionText synthetic. Schema v2 còn yêu cầu orderNo duy nhất: seed
pack/word có thứ tự riêng, giữ các assertion ordering/batching cũ và thêm assertion
từ chối thứ tự trùng. Không thay schema hoặc logic sản phẩm để làm test qua.

## 3. Phạm vi và quyết định

- SQL COUNT(DISTINCT vocabularyId) cho toàn UserWordProgress.
- SQL GROUP BY box, COUNT DISTINCT; mapper điền 0 cho box không có bản ghi để
  luôn trả đủ sáu phần tử box0..5.
- Learning=box0+1, Reviewing=box2+3, Mastered=box4+5 dùng getter domain hiện có.
- Không lọc active pack, isSaved hoặc learnedAt. Theo SRS/contract, tổng bằng số
  distinct progress, kể cả bản ghi saved-only nếu nó đã tồn tại trong bảng.
- Đọc **toàn bộ** QuizAttempt theo completedAt DESC, id DESC khi timestamp trùng;
  không đặt limit/date window ngầm. Chỉ lượt hoàn tất được phép vào bảng theo
  contract ghi của Quiz; DATA-07 không tạo trạng thái phiên đang làm/dở dang.
- Một @Transaction cho total + grouped counts + history để tránh snapshot pha trộn.
- Empty DB → success với tổng 0, sáu box 0, history rỗng.
- Chỉ history hoặc chỉ progress → vẫn có content, không trả toàn snapshot rỗng.
- Dữ liệu sai box/score/invariant → INVALID_DATA; storage lỗi → READ_FAILED.
  Không biến lỗi thành empty success, không sửa score hoặc xóa dữ liệu.
- Không sửa UI, Entity, schema/version, scheduler hoặc luồng ghi DATA-06.

## 4. Cấu trúc

```text
app/src/main/java/com/vocamind/app/
├── data/user/
│   ├── UserDao.java                  # Thêm aggregate/history queries và @Transaction
│   ├── BoxProgressCount.java         # Projection GROUP BY nội bộ
│   ├── ProgressRows.java             # Các kết quả thuộc một transaction
│   ├── ProgressMapper.java           # Validate và map sang contract hiện có
│   └── RoomProgressRepository.java   # Triển khai ProgressRepository
└── core/ProgressProvider.java        # Ghép dependency, không tự mở/đóng UserDatabase
```

Test contract dùng chung ở app/src/catalogTest/java theo cấu trúc hiện có;
wrapper Robolectric ở src/test và AndroidJUnit4 ở src/androidTest.
Tài liệu dùng lowercase kebab-case, kế hoạch trong docs/plans, báo cáo trong docs.

## 5. Kế hoạch 5 giờ

| Bước | Công việc | Giờ |
| --- | --- | ---: |
| 1 | Đọc SRS/phân công, kiểm tra DATA-02/03 và Progress contract | 0,5 |
| 2 | SQL aggregate/history và transaction read snapshot | 1 |
| 3 | Mapper, repository và dependency factory | 1 |
| 4 | File-backed integration: fixture, empty, history, invalid data, concurrency | 1,5 |
| 5 | Build/regression, evidence và bàn giao Thi | 1 |

## 6. Test matrix

| Ca | Tiêu chí |
| --- | --- |
| T01 | DB rỗng trả success với 6 số 0 và history rỗng |
| T02 | Fixture box0..5 = 2/3/4/5/6/7 → tổng 27; Learning/Reviewing/Mastered = 5/9/13 |
| T03 | Box vắng trả 0; không tự lọc active/isSaved/learnedAt |
| T04 | Upsert cùng vocabularyId đổi box, không tăng tổng distinct |
| T05 | 20 quiz attempts: không cắt ngầm; completedAt DESC, id DESC; giữ score đã lưu |
| T06 | Chỉ progress hoặc chỉ history không bị xem là toàn DB rỗng |
| T07 | Raw fixture box=6 trả INVALID_DATA; không sửa dữ liệu |
| T08 | Raw quiz score sai trả INVALID_DATA; không tự tính lại score |
| T09 | DB đóng trả READ_FAILED, không trả empty success |
| T10 | Reopen giữ count/history; snapshot domain immutable |
| T11 | Writer đổi box và thêm quiz giữa các câu đọc: repository không trả snapshot pha trộn |
| T12 | Mapper giữ long > Integer.MAX_VALUE; từ chối total/group mismatch và group trùng/sai |

Không dùng mock để thay kiểm tra SQL. DB test có UUID riêng; chỉ cleanup DB test.
Connected instrumentation phải báo riêng với host native SQLite và APK build.

## 7. Nghiệm thu và giới hạn

- [x] Queries/mapper/repository khớp contract và fixture.
- [x] Empty, full six-box, ordering, error và transaction consistency được kiểm chứng.
- [x] Không diff Entity/schema/asset; không expose DAO/Entity cho UI.
- [x] Build app/test APK và regression thực sự chạy, có số test/report.
- [x] Báo cáo ghi rõ kết quả host/device và phần chưa chạy.
- [ ] Thi review; PROGRESS-02 tiếp tục bind UI và nghiệm thu AC-11 phần hiển thị.

DATA-07 chỉ chứng minh phần dữ liệu của AC-11, không tự xác nhận biểu đồ/UI đã
đúng, chưa sửa trạng thái Excel, chưa ghi nhận review thay thành viên. DATA-06 vẫn
là nhánh riêng; khi tích hợp sau cần giữ cả transaction ghi review và read snapshot
trong UserDao, không thay toàn file bằng bản từ nhánh khác.

## 8. Trạng thái thực tế

Đã triển khai tại local: 27 lượt mới DATA-07 và toàn bộ 185 lượt test đều đạt;
app/test APK build thành công. Xem [báo cáo xác minh](../data-07-validation.md) và
[kết quả từng suite](../evidence/data-07/unit-results.json). Chưa chạy connected
instrumentation, chưa được Thi review. Có commit local tích hợp DATA-03 (84ad09d);
nhánh bàn giao là feature/data-07-progress-aggregation-queries, chưa merge vào develop/main.
