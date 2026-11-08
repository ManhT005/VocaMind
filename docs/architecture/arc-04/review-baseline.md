# ARC-04 — Baseline tích hợp tuần 2

Audit hồi cứu ngày 08/11/2026 trên `origin/develop` tại
`cd83f1ce874a899029a85879c3c40c97343683d6`. Owner Thi, reviewer Nhân.
Nhánh `feature/arc-04-week2-integration-review`. [CI baseline](https://github.com/ManhT005/VocaMind/actions/runs/42385571766)

| Thay đổi | Trạng thái tại baseline |
| --- | --- |
| VOC-DATA-06 | Merge tại `993e70a`, catalog 150 từ cho QA nội bộ. [PR #35](https://github.com/ManhT005/VocaMind/pull/35) |
| QUIZ-03 | Merge tại `cd83f1c`, có session restore, ghi kết quả một lần và userdata migration v1→v2. [PR #33](https://github.com/ManhT005/VocaMind/pull/33) |
| VOCAB-02 | PR mở, head `9808d62`. Detail/Save cơ bản đã vào baseline qua PR #35. Merge mô phỏng sạch, tree cuối chờ kiểm tra. [PR #32](https://github.com/ManhT005/VocaMind/pull/32) |
| VOC-DATA-05 | Sidecar nằm trên nhánh riêng. Cập nhật baseline sau khi merge. |
| DATA-08 | Có head `c252ff4` ngoài baseline, merge mô phỏng sạch và còn gate kiểm thử. |

Audit bám SRS §9.1–9.3, ADR-01 (hai DB), ADR-02 (Leitner), ADR-03 (FTS4),
ADR-04 (TTS P1) và NFR-VM-11/12/18. Các AC trọng tâm gồm AC-02/03/04/08/10/11/12/13/14.
[SRS Baseline v1.1](https://docs.google.com/document/d/1rNqKaQGttr3DtK2OhIHOPYzUsJN2gVrv/edit)

D6–D10 giữ `NOT VERIFIED` cho nhật ký review hằng ngày. Audit hồi cứu, CI
kỹ thuật, WEEK-1-GATE và sign-off của Nhân là các bằng chứng riêng.
