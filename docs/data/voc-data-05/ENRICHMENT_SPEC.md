# VOC-DATA-05 — CEFR và topic metadata v1

Sidecar build-time khóa `editorial-accepted-v1` bằng commit, SHA-256 canonical
và dataset digest dùng chung với Quiz Policy. Mỗi stable ID có đúng một dòng
theo thứ tự ID tăng dần. Phiên bản `0.1.0` có 150 dòng và giữ cả CEFR lẫn topic
ở `unknown`. Chưa có nguồn nhãn được duyệt hoặc sign-off của Thành.

| Trạng thái | CEFR | Topic | Điều kiện |
| --- | --- | --- | --- |
| `unknown` | `null` | `[]` | Chưa có căn cứ cho trường đó |
| `suggested` | A1–C2 | ID taxonomy | Có evidence phù hợp |
| `verified` | A1–C2 | ID taxonomy | Evidence đủ cho mỗi giá trị và review approved |

CEFR gắn với sense và POS. Rank NGSL/TSL không phải căn cứ CEFR. Topic dùng
[10 định nghĩa có phạm vi chọn và loại](topic-taxonomy-v1.json). Chẳng hạn
`study` có thể là công trình nghiên cứu hoặc việc học bài. Context pack và
AI chỉ hỗ trợ đề xuất, còn xác minh cần evidence theo nghĩa.

- `label-sources.json` chứa `sources` và `evidence`. Nguồn có `id`, `kind`,
  `status`. Nguồn `approved` cần `snapshot`, `permission`, `approvedBy`,
  `approvedAt` và `approval` có `artifact` và `artifactSha256`.
- Evidence có `id`, `sourceId`, `stableId`, `field`, `values`, `rationale`,
  `artifact` và `artifactSha256`. AI thêm `model`, `provider`, `promptVersion`
  và `createdAt`. Nguồn held hoặc artifact ngoài thư mục ledger bị từ chối.
- Review có `stableId`, `decision`, `reviewedBy`, `reviewedAt`, `claimSha256`,
  `artifact` và `artifactSha256`. Claim hash ràng buộc snapshot, record,
  định nghĩa topic, nguồn và evidence. Reviewer chịu trách nhiệm kiểm tra
  thẩm quyền nguồn và nội dung bằng chứng.
- Bump phiên bản khi thay snapshot, nhãn, nguồn, taxonomy hoặc review. Sidecar
  không được đóng gói vào runtime catalog.

| Field | Evidence đủ điều kiện xác minh | Chỉ hỗ trợ đề xuất |
| --- | --- | --- |
| CEFR | `external-sense-cefr`, `editorial-assessment` | `ai-suggestion` |
| Topic | `external-topic`, `editorial-sense-topic` | `editorial-pack-context`, `ai-suggestion` |
