# DATA-04 — Repository contracts và implementations

Nhánh `feature/data-04-repositories`, nền `origin/develop` (`433abbf`, đã có DATA-03).
Task theo bảng phân công: contract ở domain, implementation ở data, mapper Entity↔Domain,
chuẩn hóa error/result, fake repository cho test.

## Thành phần

| Contract (domain) | Implementation (data/repository) | Vai trò |
| --- | --- | --- |
| `CatalogRepository` (đã có) | `RoomCatalogRepository` (đã có) | Vocabulary / pack / source từ catalog |
| `PackRepository` | `RoomPackRepository` | Gộp pack catalog với activation + số từ đã học theo packId |
| `StudyRepository` | `RoomStudyRepository` | Từ đến hạn và từ mới của pack đang active |
| `ReviewRepository` | `RoomReviewRepository` | Ghi ReviewEvent + progress trong một transaction |
| `QuizRepository` | `RoomQuizRepository` | Pool từ pack active, lưu QuizAttempt hoàn tất |
| `ProgressRepository` (đã có) | DATA-07 | Không làm lại ở task này |

- `RepoResult<T>` + `RepoError` (INVALID_ARGUMENT, NOT_FOUND, READ_FAILED, WRITE_FAILED, INVALID_DATA):
  lỗi không bao giờ bị thay bằng danh sách rỗng hay số 0. Exception Room/SQLite không ra khỏi data.
- `UserDataMapper`: Entity↔Domain; hàng lưu sai bất biến (box ngoài 0..5...) → INVALID_DATA.
- `ActiveCatalogScope`: điểm duy nhất nối hai DB — userdata cho packId active, catalog mở rộng thành
  stableId. Không FK/JOIN xuyên DB; pack paused/orphan không đóng góp từ.
- `RepositoryProvider` (core): composition root, chỉ trả về domain contract, không lộ DAO.
- Fake: `app/src/test/.../fake/Fake{Pack,Study,Review,Quiz}Repository` dùng cho ViewModel/domain test.

## Quyết định cần review

- `recordReview` kiểm tra từ tồn tại trong catalog (NOT_FOUND) trước, rồi một transaction userdata:
  `boxBefore` phải khớp box đang lưu (0 nếu chưa có progress), sai → INVALID_ARGUMENT (chặn ghi lại
  kết quả cũ). `learnedAt` đặt một lần, `isSaved` giữ nguyên, `reviewCount` +1.
- Catalog và userdata là hai file nên không có transaction xuyên hai DB; kiểm tra catalog đứng trước.
- Learned count của pack = số từ phân biệt có `learnedAt`; từ chung giữa hai pack tính ở cả hai.
- `activatePack` giữ thời điểm kích hoạt đầu tiên (theo DATA-03); pack không có trong catalog → NOT_FOUND.
- `QuizRepository.activeVocabulary` yêu cầu stableId 16 hex (theo `QuizVocabulary`); sai → INVALID_DATA.

## Kiểm thử (JDK 17, Gradle 8.9)

`./gradlew :app:testDebugUnitTest` — **186 test, 0 lỗi** (gồm toàn bộ test cũ).
Test mới: `RepositoryIntegrationTest` (12 ca × SDK 26/35, hai DB Room thật), `FakeRepositoriesTest`.
Phủ: gộp pack/active, thứ tự và khử trùng từ mới, due theo ngày, pause giữ progress, ghi review giữ
learnedAt/saved, từ không tồn tại, box cũ, rollback khi ghi lỗi (trigger), dữ liệu hỏng → INVALID_DATA,
quiz pool chỉ pack active, lưu attempt. `DataBoundaryTest` xác nhận domain/feature không import data/Room.

Chưa làm: instrumentation test trên thiết bị, wiring ViewModel (thuộc feature tương ứng), review của Thi.
