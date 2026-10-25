# Kế hoạch DATA-03 — DAO foundation

## 1. Thông tin task

| Hạng mục | Nội dung |
| --- | --- |
| Dự án | VocaMind — Android, Java 17, Room, Clean MVVM |
| Task | DATA-03 — DAO foundation |
| Lịch / Ước lượng | Tuần 1, D2–D3 / 7 giờ |
| Người thực hiện / Reviewer | Thi, Nhân / Đạo |
| Ưu tiên / Trạng thái theo phân công | Critical / Chưa bắt đầu, Waiting |
| Phụ thuộc chính thức | DATA-01 CatalogDatabase; DATA-02 UserDatabase |
| Phân lớp | L2 Storage / Persistence / Data Integrity; hỗ trợ L1 Platform / Integration |
| Mục tiêu | Primitive truy vấn/ghi đủ cho P0; repository/use case sử dụng, UI không phụ thuộc Room |

Đây là **plan**, chưa phải bằng chứng DATA-03 đã triển khai hoặc kiểm thử.
Các tên phương thức mới bên dưới là đề xuất, cần chốt khi triển khai.

## 2. Căn cứ và nguyên tắc áp dụng

- `VocaMind_SRS_Baseline_v1.1.docx`: §7.2–7.4; FR-VM-005..046;
  tập trung 005..010, 014..020, 024, 028, 034, 038..043, 045..046;
  NFR-VM-07; BR-01/02/07/10; ADR-01; DEC-02/05/15.
- `Đặc Tả SRS Cho VocaMind.docx`: Clean MVVM, repository che giấu DAO,
  database catalog/user tách vật lý, transaction review all-or-nothing.
  Khi khác baseline v1.1, áp dụng baseline; không coi ví dụ code SRS là code đã chạy.
- `VocaMind — Phân công.xlsx`, dòng DATA-03 và dòng người dùng cung cấp:
  query packs/vocab/due/new/progress/review/quiz history, fixture happy/empty/error,
  không duplicate stableId ngoài mong đợi; Thi, Nhân thực hiện, Đạo review.
- Đã đọc hai SRS và Excel tại thư mục cha `D:/ADR NHOM 2`; không sửa tài liệu gốc.
  Các mô tả hiện trạng dưới đây dựa vào checkout, không suy ra từ trạng thái Excel.

Phân biệt **DATA-03 DAO foundation** với **VOC-DATA-03 ingestion** và yêu cầu
**SRS §7.3 DATA-03 QA học liệu**. Ba mã này không thay thế nhau.
FR-VM-005..046 là phạm vi truy vết liên quan, không phải toàn bộ chức năng UI,
scheduler, Quiz và attribution phải được triển khai trong 7 giờ của task này.

## 3. Hiện trạng đã kiểm tra

| Thành phần trong checkout | Đã có | DATA-03 tiếp tục |
| --- | --- | --- |
| `data/catalog/CatalogDao.java` | Packs, pack, words theo orderNo, vocabulary theo stableId, sources, count membership, FTS query chứng minh fixture | Batch lookup, membership/candidate contract cho active packs, kiểm tra uniqueness và thứ tự |
| `domain/catalog/CatalogRepository.java` | Contract chỉ đọc, domain models, Optional/empty, worker-thread convention | Giữ ranh giới; mở rộng repository tổng thể thuộc DATA-04 |
| `data/user/UserDao.java` | Preference, pack/progress upsert, setSaved, review/quiz insert và list, recordReview transaction | Active packs, batch progress, due, dữ liệu kiểm tra new/quota, query history có phạm vi/thứ tự |
| `UserDatabase` và 5 entity | UserPack, UserWordProgress, ReviewEvent, QuizAttempt, UserPreference; schema version 1 | Không tái tạo DB; chỉ thay schema khi có nhu cầu thật và migration phù hợp |
| Fixture/catalog tests | 4 stableId dùng chung giữa 4 pack, 16 memberships; Robolectric API 26/35 | Giữ fixture cũ, bổ sung fixture kiểm thử đủ edge cases |

UserDao hiện `reviews()`/`quizzes()` sắp theo ID, chưa phải contract lịch sử theo
thời gian. `setSaved` chỉ cập nhật progress đã tồn tại; hành vi saved-only đang
để chờ chốt, xem [DATA-02 validation](../data-02-validation.md).
Kết quả build/test cũ trong báo cáo DATA-01/02 không thay kiểm thử DATA-03.

## 4. Phạm vi và ranh giới

### Đầu ra bắt buộc

1. Query contract: input, output, ordering, dedup, empty/not-found/error.
2. Catalog DAO chỉ đọc, User DAO đọc/ghi; thao tác theo stableId/packId logic.
3. Primitive cho active packs, due/new, progress, review và quiz history.
4. Transaction entry point ghi review rõ ràng, có happy path và rollback test.
5. DAO integration tests trên Room/SQLite thật, fixture có từ thuộc nhiều pack.
6. Tài liệu bàn giao DATA-04/06/07, kết quả build/test thật khi triển khai.

| Task khác | Ranh giới giữ lại |
| --- | --- |
| DATA-01/02 | Foundation DB, asset và schema đã có; không viết lại |
| DATA-04 | Repository contracts/implementations, Entity↔Domain mapper, Result/error translation, fake repositories, phối hợp hai DB |
| DATA-05 | FTS search đầy đủ, chuẩn hóa expression, UX/performance benchmark |
| DATA-06 | Luồng RecordReview hoàn chỉnh, concurrency/idempotency và tích hợp scheduler/repository transaction |
| DATA-07 | ProgressSummary, sáu box/group 0–1/2–3/4–5 và QuizHistory aggregation cấp sản phẩm |
| Domain / Feature | Planner, quota policy, lịch Leitner, retry FORGOT trong phiên, debounce 600 ms, Quiz completion, UI/LiveData |
| VOC-DATA / DATA-08 | Học liệu sản xuất/license, catalog release, staged upgrade/rollback và migration tích hợp |

Không thêm dependency ngược domain→Room hoặc đưa DAO/Entity vào public API
Fragment/ViewModel. DAO có thể trả entity/projection nội bộ; DATA-04 map sang
domain. DATA-03 không tạo repository song song để thay implementation đã có.

## 5. Query contract đề xuất

### 5.1. Quy ước chung

- Primitive đồng bộ/blocking, chạy trên worker executor. Không dùng
  `allowMainThreadQueries()` trong sản phẩm.
- Đọc collection không có dữ liệu → list rỗng; đọc một ID không tồn tại →
  nullable row được khai báo rõ. Repository hiện có chuyển thành Optional;
  không bắt lỗi DB rồi trả empty như thể truy vấn thành công.
- ID null/rỗng, limit không hợp lệ, range thời gian đảo ngược → từ chối bằng
  validation tại entry point nội bộ; bind SQL parameters, không nối chuỗi input.
- Mốc thời gian epoch milliseconds do caller truyền vào. Dùng range
  `[startInclusive, endExclusive)` cho ngày/lịch sử; DAO không tự lấy clock,
  timezone hoặc tự cộng số milliseconds để tính ngày lịch.
- Mọi query có LIMIT cần ordering xác định và tie-breaker. Không dùng rowid
  làm định danh domain; FTS chỉ join integer rowid nội bộ rồi trả stableId TEXT.
- Danh sách ID đầu vào được khử trùng. Input rỗng trả empty/0, không trở thành
  truy vấn toàn kho. Với tập ID lớn, chia batch theo giới hạn bind của SQLite
  thấp nhất được hỗ trợ và merge/sort/dedup trước global limit; kiểm thử biên batch.

### 5.2. Catalog — chỉ đọc

| Use case / Primitive | Input | Output và thứ tự | Empty / uniqueness |
| --- | --- | --- | --- |
| Danh sách packs: `packs`, `pack` | Không / packId | Pack rows; `orderNo ASC, id ASC` | List rỗng / null nếu không có |
| Từ trong một pack: `words` | packId | Vocabulary; `PackWord.orderNo ASC, vocabularyId ASC` | Một lần/stableId trong pack; dựa khóa ghép membership |
| Chi tiết/batch vocabulary: `vocabulary`, `vocabulariesByIds` | stableId / tập stableId | Entity đầy đủ, IPA nullable; batch sắp stableId nếu caller không yêu cầu order riêng | Không tự trả row giả cho ID thiếu; batch không duplicate |
| Membership active candidates: `membershipsForPacks` | Tập activePackIds | Projection packId, vocabularyId, packOrderNo, wordOrderNo | Có thể nhiều membership cho cùng stableId; đây là kết quả membership có chủ đích |
| Unique candidates cho planner/Quiz | Membership projection | Chọn occurrence đầu theo `(packOrderNo, packId, wordOrderNo, vocabularyId)` | Một stableId duy nhất; chốt quy tắc tie-break với DATA-04/planner |
| Attribution: `sources`, `sourceForVocabulary` | Không / stableId | ContentSource gồm URL, licenseCode/licenseUrl, snapshotDate | Empty/null nếu thiếu, không tải mạng để bù |
| Count membership: `wordCount` | packId | Số từ thuộc pack | Không dùng tổng membership nhiều pack làm số unique toàn kho |

Thứ tự **giữa nhiều pack** là quyết định triển khai đề xuất; SRS chỉ chốt
PackWord.orderNo cho từ mới. Giữ cả packOrderNo/wordOrderNo khi bàn giao, Đạo
review trước khi khóa contract. Không `SELECT DISTINCT` toàn projection có
packId/orderNo rồi cho rằng đã dedup vocabularyId.

### 5.3. Userdata — đọc/ghi

| Use case / Primitive | Input | Output / semantics |
| --- | --- | --- |
| Active packs: `activePacks` | Không | Chỉ isActive=true, packId ổn định; không tự activate pack khi rỗng |
| Activate/pause | packId, trạng thái, thời điểm | Update trạng thái có chủ đích; không REPLACE làm mất activatedAt/cột khác; giữ semantics thời điểm kích hoạt đã chốt |
| Progress: `progress`, `progressByIds` | stableId / tập ID | Mỗi vocabularyId tối đa một row, box 0..5; learnedAt/lastReviewedAt nullable |
| Due: `dueProgress` | Eligible active vocabulary IDs, now | Chỉ `dueAt <= now`, `dueAt ASC, vocabularyId ASC`; gồm box 0, không coi box 0 là chưa từng học |
| Existing IDs cho new | Eligible IDs | Những stableId đã có UserWordProgress; caller lấy hiệu với candidate IDs |
| Daily new-count primitive | dayStart, nextDayStart | Đếm vocabularyId có learnedAt trong `[start,end)`; không đếm ReviewEvent vì một từ có nhiều lần ôn |
| Đọc theo box | box 0..5, tùy tập vocabulary IDs | Progress rows theo `dueAt, vocabularyId`; invalid box bị từ chối |
| Saved | stableId, boolean | Chỉ đổi isSaved, trả affected-row count; thiếu row không tự tạo progress giả |
| Review history | stableId, limit; range nếu use case cần | `reviewedAt DESC, id DESC`; event lặp vocabularyId là lịch sử hợp lệ, không DISTINCT để xóa lượt |
| Quiz history | limit; range khi cần | `completedAt DESC, id DESC`; giữ các lượt riêng biệt dù cùng score/thời điểm |
| Preference | singleton id=1 | Đọc dailyNewWords và settings; không reset cấu hình đã lưu |

SRS FR-VM-017 định nghĩa **new = chưa có progress**, không phải `box=0`.
Saved-only chưa có contract đã duyệt: không mở rộng setSaved thành tạo row làm
mất quyền chọn new hoặc làm tăng tổng từ đã học. Phối hợp Đạo/DATA-04/07 chốt
trước khi triển khai luồng lưu từ chưa học; ghi blocker nếu chưa thống nhất.

Quota thuộc planner/use case: đề xuất đếm từ mới đã học toàn ngày để đổi/pause
pack không reset quota, rồi lấy `max(0, dailyNewWords - learnedToday)`.
Đây là policy cần chốt với planner; DAO chỉ cung cấp primitive range, không tự
áp quota, tự reset learnedAt hoặc đưa timezone vào SQL.

### 5.4. Phối hợp due/new qua hai DB

1. Đọc snapshot userdata: activePackIds, các progress cần thiết, preference và
   daily count trong một read transaction nếu cần tính nhất quán cùng UserDatabase.
2. Catalog nhận activePackIds để trả candidate memberships/unique IDs theo
   contract; intersect với catalog còn tồn tại, không lấy từ paused packs.
3. Due: lọc progress vào eligible IDs, `dueAt <= now`, sort và dedup stableId.
   **Lọc active trước LIMIT**, tránh paused/orphan rows chiếm hết cửa sổ kết quả.
4. New: lấy unique ordered candidates trừ **mọi** ID đã có progress;
   áp quota/limit sau filtering/dedup. Không limit raw memberships trước dedup.
5. Không có active pack → due/new/Quiz candidate pool rỗng; không tự kích hoạt.

SQL DAO chỉ chạy trong database của mình; không JOIN/ATTACH catalog với userdata,
không cross-DB FK hoặc tuyên bố transaction xuyên hai DB. DATA-03 bàn giao và
test primitive/composition fixture; implementation repository điều phối ở DATA-04,
thứ tự hàng đợi phiên học ở domain. Catalog bất biến về nghiệp vụ; nếu sau này
có swap phiên bản, DATA-04/08 phải xử lý snapshot/lifecycle, không giả định hai DB atomic.

## 6. Transaction entry points và error contract

- Tái sử dụng `UserDao.recordReview(event, progress)` của DATA-02: insert
  ReviewEvent và upsert/update UserWordProgress trong **một @Transaction**.
- Validate stableId hai row trùng nhau, boxAfter khớp progress.box,
  nextDueAt khớp progress.dueAt và các invariant đã có. Event ID trả về phải có
  semantics rõ nếu mở rộng return type; không đổi contract ngầm.
- Test thành công giữ đủ hai row; inject lỗi bước progress sau khi event đã
  insert thì rollback cả hai và giữ progress cũ. Dùng Room/SQLite thật, không
  thay rollback test bằng mock insert ném exception trước transaction.
- Không tính ScheduleResult, lịch ngày địa phương hoặc debounce trong DAO.
  Chỉ có @Transaction chưa chứng minh ngăn double-submit, stale progress hay
  lost update; DATA-06/use case phải chốt serialization/idempotency.
- Quiz insert chỉ nhận kết quả hoàn tất từ use case; primitive kiểm tra count,
  score, không tự suy ra phiên đã hoàn tất. Không ghi một QuizAttempt mỗi câu.
- Không nuốt constraint/disk/closed-DB exception, không tự xóa database hoặc
  destructive fallback. DATA-04 map exception sang domain Result/error.

## 7. Index, schema và giới hạn thực hiện

Giữ index membership hiện có `(packId, orderNo)` và vocabularyId. Xem query plan
thực tế trước khi thêm index userdata; cân nhắc dueAt/vocabularyId, learnedAt,
history timestamp/id theo query được chốt, không thêm mọi index suy đoán.

Nếu chỉ thêm query/DAO, không tăng DB version hay sửa schema asset. Nếu cần index
hoặc thay schema, đánh giá version đã phát hành, xuất Room schema, migration bảo
toàn dữ liệu; không sửa schema v1 đã dùng rồi né mismatch bằng destructive reset.
Nếu đổi catalog schema, phải regenerate/validate asset theo DATA-01, không chỉ sửa JSON.

## 8. Các bước triển khai — tổng 7 giờ

| Giai đoạn | Công việc | Giờ | Đầu ra kiểm chứng |
| --- | --- | ---: | --- |
| D2 | Kiểm tra DATA-01/02 trong nhánh nền, status và baseline | 0,5 | Base commit, tình trạng build, dependency thực tế |
| D2 | Chốt query contract, ordering/dedup, saved-only và ranh giới task với Đạo | 1 | Contract review, quyết định hoặc blocker cụ thể |
| D2 | Mở rộng catalog DAO / projection / batch primitives | 1 | Catalog read tests, không business write |
| D2–D3 | User DAO: active/due/progress/new-count/history và save primitives | 1,5 | Query và validate input theo contract |
| D3 | Rà soát transaction entry points, happy/rollback | 0,5 | Nguyên tử trong userdata được kiểm thử |
| D3 | Fixture edge cases và integration tests | 2 | Happy/empty/error, unique/order/quota boundaries |
| D3 | Build, validation report và handoff Đạo | 0,5 | Số pass/fail/skip thực tế, phần còn thiếu |
| Tổng | | **7** | |

Nhánh plan: `feature/data-03-dao-foundation`, tạo từ
`feature/data-02-user-database` tại commit `89225c2`, đã kiểm tra có CatalogDao và
UserDao. Đây là nhánh stack trên DATA-02, chưa khẳng định foundation đã merge vào
develop. Khi mở PR, dùng DATA-02 làm base trước khi dependency được tích hợp;
nếu chuyển base sang develop cần kiểm tra lại diff. Không mang commit VOC-DATA-01
vào DATA-03. Phần triển khai đã được bổ sung trong phiên tiếp theo; xem
[DATA-03 validation](../data-03-validation.md) cho API thực tế, kết quả và giới hạn.

## 9. Fixture và kế hoạch kiểm thử

Fixture riêng: pack A/B active, C paused, pack rỗng; từ dùng chung A/B; từ chỉ ở
C; từ không có progress; progress due trước/bằng/sau now, tie dueAt; box 0..5;
orphan progress ID không còn trong catalog; history có cùng timestamp; IPA null.
Không sửa asset sản phẩm chỉ để thêm ca test. Dùng UUID-named test DB; cleanup
không chạm catalog.db/userdata.db của người dùng.

Các mã dưới đây là test đề xuất, không phải mã sẵn có trong SRS:

| Test | Kết quả phải đạt | Truy vết |
| --- | --- | --- |
| DATA03-T01 | Packs/words đúng orderNo + tie-break; empty pack/unknown ID trả đúng empty/null | FR-VM-005/006/008 |
| DATA03-T02 | Batch vocabulary và pool A+B không duplicate stableId; membership projection vẫn giữ đủ liên kết | BR-01; FR-VM-020/034 |
| DATA03-T03 | Due chỉ từ active, `dueAt <= now` gồm equality, sort ổn định; box 0 vẫn được xét | FR-VM-016/020/028 |
| DATA03-T04 | Không active, paused-only hoặc orphan rows không rò vào due/new; không tự activate | DEC-02; FR-VM-019/020 |
| DATA03-T05 | New loại mọi ID có progress; sort/dedup/filter trước limit, shared membership không làm hụt quota | FR-VM-017; BR-07 |
| DATA03-T06 | Daily count đúng `[start,end)`, review lặp không tăng new-count; caller dùng mốc ngày đã tính | FR-VM-017; BR-07 |
| DATA03-T07 | Progress theo ID/box; invalid box/input/limit/range bị từ chối; batch rỗng không full scan trả toàn kho | §7.2–7.4 |
| DATA03-T08 | Saved flag không đổi dueAt/box/history; affected=0 khi thiếu row theo contract hiện tại | FR-VM-009/010 |
| DATA03-T09 | Review transaction thành công ghi đủ; lỗi bước 2 rollback event và giữ progress cũ | FR-VM-024; BR-10; NFR-VM-07 |
| DATA03-T10 | Review/Quiz history theo timestamp DESC/id DESC; empty và limit đúng; nhiều event cho cùng từ vẫn giữ đủ | FR-VM-038/043 |
| DATA03-T11 | ContentSource đọc offline, IPA null giữ null; không phát sinh nội dung mạng/AI | FR-VM-008/045/046 |
| DATA03-T12 | File DB reopen giữ pack/progress/history; closed-DB/constraint errors không bị chuyển thành empty hoặc reset | FR-VM-010; §7.4 |
| DATA03-T13 | Ranh giới batch ID, ID lặp và limit sau merge cho kết quả tương đương tập nhỏ | Contract query; FR-VM-020 |
| DATA03-T14 | Fragment/ViewModel/domain không import hay nhận DAO, Entity hoặc Room dependency qua constructor/public return type | Clean MVVM; yêu cầu Excel |

DAO tests dùng Room + native SQLite thật (Robolectric API 26/35 theo repo), không
chỉ mock. Contract composition có thể chạy qua test harness data layer để chứng
minh filtering/dedup hai DB, không cần xây toàn repository sản phẩm trong DATA-03.
Lỗi SQL cố định được Room compiler kiểm tra; error fixture dùng invalid input,
constraint, closed DB hoặc lỗi chèn bước 2, không sửa production SQL thành sai.

## 10. Lệnh xác minh dự kiến

Windows, từ root repo với JDK 17 và SDK trong local.properties:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Khi có tên test, chạy lọc suite DAO mới trong vòng phát triển rồi chạy toàn bộ
unit tests để phát hiện regression DATA-01/02/Quiz. Kiểm tra report tại
`app/build/reports/tests/testDebugUnitTest/index.html`, ghi executed/pass/fail/skip;
0 test không được tính pass. Không ghi số test dự kiến thành kết quả thật.

Nếu có thiết bị/emulator và instrumentation wrapper đã được thêm:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Lọc đúng class DATA-03 bằng runner argument sau khi biết tên thực tế. Nếu không
có thiết bị, ghi rõ chưa chạy connected tests; build test APK không chứng minh
đã chạy trên Android. DAO reopen test không thay reboot hay migration/rollback
cấp sản phẩm ở DATA-08. Các lệnh trên chưa được chạy trong phiên lập plan này.

## 11. Definition of Done và bàn giao

- [x] DATA-01/02 có trong base, baseline đã xác minh; chưa tự suy đã merge develop.
- [ ] Đạo review contract, đặc biệt multi-pack order, new/quota và saved-only;
  phần chưa chốt ghi rõ blocker, không âm thầm tạo semantics mới.
- [x] Catalog read/User read-write primitive đủ use case; query input/empty/error rõ.
- [x] Primitive và composition fixture kiểm chứng active-only/unique; caller DATA-04 tiếp tục wiring.
- [x] dueAt/orderNo/box có test biên và tie-breaker; không limit trước filter/dedup.
- [x] Review transaction có success/forced SQL failure, không tồn tại trạng thái nửa vời.
- [x] UI/domain không phụ thuộc DAO/Entity/Room; tests chạy worker thread, production DAO ghi contract worker.
- [x] Không cross-DB transaction/FK, không destructive fallback và không đổi userdata ngoài phạm vi.
- [x] Build/test có evidence thật; không đổi schema/index/asset.
- [x] Báo cáo `docs/data-03-validation.md` ghi implementation, report và giới hạn.
- [ ] Đạo review; bàn giao contracts/projections/transaction và test fixture cho DATA-04/06/07.

Đầu ra PR khi triển khai: problem/behavior, contract đã chốt, query mới, quyết định
index/schema, kết quả tests, boundary check và blocker còn lại. Không đánh dấu toàn
bộ FR-VM-005..046 hoặc nghiệm thu AC-08/09/10/11 sản phẩm chỉ nhờ DAO tests pass.
