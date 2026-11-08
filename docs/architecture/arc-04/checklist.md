# ARC-04 — Checklist và trace tích hợp

| ID | Kết quả | Phạm vi bằng chứng |
| --- | --- | --- |
| A04-T01 | PASS · source | Fragment/ViewModel gọi factory/domain, scan không thấy import Room/DAO trực tiếp trong feature. |
| A04-T02 | PASS · source | I/O qua worker, UI state về main, Attribution mở URL qua Intent ngoài app. |
| A04-T03 | PASS · source/host | Domain thuần Java và không import Android/Room/data/UI. |
| A04-T04 | PASS · source/host | Catalog v2 và userdata v2 độc lập, liên hệ bằng stable ID. Catalog DAO chỉ đọc nghiệp vụ. |
| A04-T05 | PASS · migration userdata | v1→v2 thêm completion key/index, thiếu migration từ chối mở. Upgrade catalog thuộc DATA-08. |
| A04-T06 | PASS · correctness | FTS4 join rowid và trả stable ID TEXT. Hiệu năng thiết bị chờ DATA-09. |
| A04-T07 | PASS · transaction, HOLD · UI | Compare/write trong một Room transaction. Test rollback/race bảo vệ event và progress. Guard Flash 600 ms chờ luồng UI. |
| A04-T08 | PASS · host/API 28 CI | Quiz policy ràng buộc projection. Generator 4–10 câu và bốn lựa chọn. Quiz device kiểm restore và một history row. |
| A04-T09 | PASS · Quiz và NavHost host, HOLD · QA tổng | Quiz API 28 có recreate/back/reopen. Audit chưa chạy lại toàn bộ route trên thiết bị. |
| A04-T10 | PASS · asset/host, HOLD · release | Attribution dùng local credits. Smoke API 26 ở lần QA VOC-DATA-06, quyền phân phối vẫn mở. |
| A04-T11 | PASS · offline boundary | Manifest và dependency debug được kiểm ở baseline. Quiz chạy khi thiết bị không có kết nối. |
| A04-T12 | PASS · baseline | CI `42385571766` xanh tại `cd83f1c`. Đây là mốc trước thay đổi allowlist. |
| A04-T13 | PASS · merge mô phỏng, HOLD · PR #32 | #33 đã merge. #32 mô phỏng merge sạch, cần kiểm tra tree hợp nhất cuối. |
| A04-T14 | DEFERRED | DATA-08/09, QA-02, ARC-05 và tính năng P1 theo gate riêng. |
| A04-T15 | HOLD · sign-off | Review hồi cứu 08/11. D6–D10 chưa có daily logs. Nhân chưa sign-off. |

| Luồng | Trace |
| --- | --- |
| Packs, Detail, Save | NavHost → PackDetailFragment → VocabularyDetailViewModel → GetVocabularyDetail/ToggleSaved → RoomVocabularyRepository → catalog read/userdata save. Test first-save, loading/error, double tap pending, rollback và conflict. |
| Today, review, Progress | TodayFragment hiện active-pack boundary. RecordReviewUseCase → LeitnerScheduler → RoomReviewRepository → UserDao. GetProgressUseCase → RoomProgressRepository → progressSnapshot. ProgressFragment còn shell. |
| Quiz | QuizFragment → QuizViewModel/SavedStateHandle → GenerateQuiz → RoomQuizRepository → policy asset, active packs và insertQuizOnce. |
| Attribution | AttributionFragment → AttributionViewModel → GetContentSources → RoomCatalogRepository → ContentCredits/local assets. |

[Static scan và merge-tree](../../evidence/arc-04/20261108/static-scan.txt) ·
[CI Quiz offline](https://github.com/ManhT005/VocaMind/actions/runs/42385571766/job/128442110677)
