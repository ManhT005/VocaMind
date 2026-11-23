> CURRENT STATUS: [qa-02-runner-and-decisions-20261111.md](qa-02-runner-and-decisions-20261111.md). R02 FIXED_HOST_RETESTED (48 host tests); no new Android run/CI for local diff. B03 rights and F03 backup policy PENDING; B05 unsigned. WEEK-2 dependency confirmation located in UI01 validation at aba854d8; it is not QA02 acceptance. R01/R03, full TalkBack, API35 journey and reference-device search stay at the user-selected stopping state; no PASS inferred. Earlier status below is historical.

# QA-02 — Phiếu quyết định hiện tại, CHƯA KÝ

[Hồ sơ căn cứ đầy đủ](qa-02-runner-and-decisions-20261111.md) và [matrix](qa-02-requirement-test-matrix.csv) là trạng thái hiện tại. Nội dung này chuẩn bị để người dùng chuyển; chưa gửi ai.

App được kiểm thử tại5713ba6; base lượt sửa runner1f1defb +localdiff/hash. Không có app/APK/E2E run mới. QA app/test970216a5…/e0b80ea0…; DATA09d2337d28…/372860e1…; full hashes trong hồ sơ căn cứ. Runner48host tests PASS; CI/native smoke của diff mới NOT RUN.

| Quyết định | Người có thẩm quyền | Căn cứ sẵn sàng | Nội dung cần trả lời |
|---|---|---|---|
| B03 snapshot/component/channels | PO + Content Lead / authorized rights owner | QA dataset12aac7bf…,canonical1a4b3da8…,manifestb8009972…,catalog41bcfacf…; accepted datasetc066d4a3…; baseline/entry equality; LIC01..12,notices | Chọn snapshot promotion; duyệt4 mô tả pack, quyền từng component và xử lý NGSL,credits/notices/contact; quyết định riêng internal-QA/public-repository/ZIP/APK/store. Gắn quyền hạn, signed decision và reviewed notice hashes thật. |
| F03 privacy/backup | PO/người phụ trách privacy | NFR15 nguyên văn; userdata.db eligible; manifest/rules và quan sát API30disabled/Realmeenabled; chưa actualbackup | Xác nhận phạm vi NFR15 có cho system backup hay không và notice/consent cần có. Giữ backup là lựa chọn của người dùng, chưa thay quyết định PO. Nếu chưa chấp thuận, giữ PENDING và lập thay đổi riêng. |
| R02 infrastructure review | Nhân |48host tests; manual strict-offline/API26 fallback, UNKNOWN classification, QA-only fresh-data guards | Review diff/scope và quyết định cần CI/native smoke nào. Không đồng nghĩa nghiệm thu app. |
| B05 nghiệm thu | Nhân/acceptance owners | Matrix/source-bound app results/open R01,R03,A11Y,API35 | Review giới hạn và quyết định ghi nhận; full acceptance chưa ký, không đề nghị all-P0PASS/Done khi còn gap. |

WEEK-2: [UI01 validation](ui-01-validation.md), commit`aba854d8b91644dea7d4c4d3a1316c3458c04dc8` ghi xác nhận dependency của Thành. Không yêu cầu ký lại dependency đã ghi; không dùng thay approval snapshot/QA02.

- Người quyết định / vai trò / căn cứ thẩm quyền: **PENDING**.
- Quyết định, điều kiện, snapshot/APK/notice/evidence hashes được duyệt: **PENDING**.
- Từng kênh được phép, ngày và chữ ký/xác nhận có thể kiểm chứng: **PENDING**.

Benchmark mandatory trên approvedAPI30 trước đó PASS,target15ms chưa đạt; Realme supplemental mandatory PASS. API35 Discovery CI đã xác minh; full QA150 API35 NOT RUN. R01animation1x,R03video,fullTalkBack và reference-device search giữ đúng điểm dừng người dùng đã chọn, không hỏi chạy tiếp và không chuyển PASS.

---

## Hồ sơ lịch sử — các trạng thái dưới đã được thay bằng phiếu hiện tại

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


Historical opening status (superseded):
> Latest evidence: qa-02-blocker-retest-20261111.md. API35 CI verified; mandatory benchmark retest passed on user-approved API30; broad15ms target missed. Backup retained by user decision, pending PO privacy confirmation. Rights and reviewer remain unsigned; QA02 not Done. Older status below is historical.
