# PROGRESS-01: đồng bộ develop và xác minh lại contract

PR #13 bị conflict ở `docs/README.md` với develop. Đã tích hợp develop
`fff380127b0808b203165e649e22eaf714027afb` vào feature bằng merge commit local
`e21cb0577fbe980c6c285c0c31ee7b68f141e1bf`, giữ đầy đủ các liên kết của hai phía.

Phạm vi Progress vẫn là domain/repository/use case/view-state contract.
Đọc lại 7 file production và 2 lớp test cùng entity DATA-02 không phát hiện
lỗi logic cần sửa source. Giữ DISTINCT toàn userdata, sáu box và nhóm 0–1/2–3/4–5,
history completed-only, score round, error khác empty và Mastered disclaimer.

Validation ngày 02/11/2026 (UTC+07), source tree e21cb05, Temurin17/Gradle8.9:

- Focused Progress: 18 tests đạt; full Android unit suite: 90 tests đạt.
- assembleDebug đạt; vocabulary registry gate và 46 Python tests đạt.
- Whitespace và conflict checks đạt; develop SHA đã có trong lịch sử local.
- Chi tiết provenance/report/APK và giới hạn nguồn SRS tại
  [validation](progress-01-validation.md) và [audit](progress-01-srs-audit.md).

CI: PENDING — chưa push, chưa có run URL/head/merge SHA mới. Không dùng CI cũ
hoặc kết quả local thay cho checks tại PR head cuối. Ruleset chưa được xác minh.
Khi publish, cần bổ sung head SHA, workflow event, run URL và mọi required check.

Handoff: Nhân chưa xác nhận aggregation/snapshot, Quiz mapping, error mapping
và saved-only policy; bốn dòng đều PENDING trong
[handoff](progress-01-handoff.md). DATA-07 adapter/DB fixtures và PROGRESS-02 UI
acceptance/AC-11 vẫn riêng. Chờ CI và review lại của Trường; chưa Done/chưa merge.

Đây là bản nháp local để cập nhật PR sau khi được phép publish; chưa gửi
request review hoặc ghi đè mô tả PR trên GitHub.
