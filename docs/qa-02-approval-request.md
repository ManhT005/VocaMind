> Latest evidence: qa-02-blocker-retest-20261111.md. API35 CI verified; mandatory benchmark retest passed on user-approved API30; broad15ms target missed. Backup retained by user decision, pending PO privacy confirmation. Rights and reviewer remain unsigned; QA02 not Done. Older status below is historical.

# QA-02 — Hồ sơ chờ phê duyệt

Trạng thái: **CHƯA KÝ**. Không cấp quyền phát hành khi các mục dưới còn trống.

## Quyền nội dung

Snapshot binding của technical retest: APK internal QA có snapshotId `editorial-qa-v2`, metadata.datasetSha256 `12aac7bfbe037c9136c3973cc724eee68205b88673ad25f40f7881de50d5398e`, metadata.canonicalSha256 `1a4b3da8396834868b82955a1e03384e6263876302a82774e6677f786e27b60d`, catalog DB SHA256 `41bcfacfcebdc42c8059c95f396c738fdabab62c4cbd8a255499e9c446502709`. Canonical target trong checklist bên dưới vẫn giữ nguyên. PO/Content Lead phải xác nhận mapping/promotion và phạm vi; không có quyền chuyển tiếp mặc nhiên từ internal QA sang release/các kênh phân phối.

- Dataset SHA-256: `c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8`.
- Người duyệt / vai trò / căn cứ được ủy quyền: **chờ cung cấp**.
- Bằng chứng quyền sử dụng từng thành phần và quyết định LIC-01..12: **chờ duyệt**.
- Kênh được phép (GitHub / ZIP / APK / store): **chưa kênh nào được duyệt**.
- Thông báo bản quyền, giấy phép và địa chỉ tiếp nhận khiếu nại: **chờ cung cấp**.
- Ngày và xác nhận quyết định: **chưa ký**.

Đối chiếu `licensing/voc-data-06/APPROVAL_CHECKLIST.md` và hồ sơ nguồn trước ký.
Chấp thuận LDPlayer chỉ thay gate thiết bị, không thay các mục quyền nội dung.

## Review nghiệm thu

- Reviewer / vai trò / căn cứ được ủy quyền: **chờ cung cấp**.
- Matrix và evidence được review: `qa-02-requirement-test-matrix.csv`,
  `qa-02-validation.md`, `qa-02-defects.csv`.
- Benchmark: **FAIL/chờ sửa và retest**.
- API35: **hai job current-head SUCCESS; tải ZIP reports HTTP401, chưa xác minh counts/assertions để đóng F01**.
- Quyết định: **chưa đủ điều kiện ký Done**.

Reviewer chỉ ký sau khi các ca P0 còn mở có kết quả và mọi lỗi nghiệm thu được xử lý.
