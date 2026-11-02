# Kiểm chứng sau review — 02/11/2026

Checkout: `D:/hoc/android/demo/Vocamind`, nhánh
`feature/voc-data-02-contract-v1`; đã fetch và ghép develop
`fff380127b0808b203165e649e22eaf714027afb` vào working tree.
Merge chưa commit; chưa push hoặc có kết quả CI remote.

## Kết quả chạy mới

| Kiểm tra | Kết quả |
|---|---|
| Contract unittest discover (`test_contract.py`) | 39/39 PASS; invalid fixtures và stableId/casefold vectors trong subtests |
| Canonical builder integration (`tools/catalog/test_*.py`) | 3/3 PASS; round-trip từng field, attributionText, deterministic bytes, committed checksum/logical SQL dump, unique negative tests, FTS/null IPA/order/integrity/foreign keys, invalid-input atomicity và release rejection |
| Source tooling suite (`tools/vocabulary/test_*.py`) | 46/46 PASS |
| Validator CLI fixture | exit 0, 4 unique entries / 4 packs / 16 memberships |
| Release gate fixture | Automated assertion chặn FIXTURE_NOT_RELEASE, RELEASE_UNIQUE_WORDS, RELEASE_SOURCE_NOT_APPROVED; fixture không được release |
| Room compile/export | PASS; compiler tạo schema 2, identityHash `6704c4cc4f3db1ef916af009d7de4b98`; giữ export schema 1 |
| `testDebugUnitTest` | 76/76 PASS, 0 skipped; gồm 26 lượt CatalogAssetTest trên Robolectric API 26/35, 16 UserDatabase, 24 Leitner, 10 Quiz |
| `assembleDebug assembleDebugAndroidTest` | BUILD SUCCESSFUL; test APK được build, chưa chạy instrumentation |
| `adb devices` | Không có emulator/device kết nối; chưa chạy connectedDebugAndroidTest |
| `git diff --check` | PASS |

Môi trường: Windows, Python portable 3.13.9, jsonschema 4.26.0,
Unicode 15.1.0, SQLite 3.50.4, JBR 21.0.11, Gradle wrapper 8.9.
Project vẫn biên dịch source/target Java 17; dependency Android không đổi.
Python nằm trong `.tools/` được gitignore, không cài vào hệ thống.

Asset được build trực tiếp từ canonical fixture, không copy lại DATA-01 fixture
riêng. Canonical SHA-256:
`c2b7c643c4b61c865f4b34bbc9d875d2661a33739ef7efd5b1f4e1ac221636a8`.
Catalog DB SHA-256:
`7f90d0b11b14ce73932231dc7c4506bdf30af3788b66d0fde536d08fa7f753e9`.
Metadata ghi Room schema 2 và contentVersion 1.0.0 riêng biệt.
Canonical fixture được bundle **chỉ trong debug assets** để cùng test Room chạy
trên host và device. Không coi test fixture là dữ liệu nguồn đã được duyệt.

Lần chạy Android đầu có 2 test mới lỗi vì fixture chưa nằm trong application
assets; đã sửa debug sourceSet và chạy lại toàn bộ suite thành công. Builder
cũng được sửa đóng SQLite connection trước atomic replace trên Windows.

## Giới hạn còn lại

- Chưa có phê duyệt Nhân/Trường/Thi/Thành, nguồn thật/license/evidence/content QA,
  quota 150 hay baseline production. Giữ trạng thái candidate, không Accepted.
- Chưa có instrumentation thực tế hoặc kiểm thử nâng cấp/rollback trên máy đã
  cài schema 1. Không có migration 1→2/destructive fallback; database cũ bị từ
  chối đến khi có upgrade path được duyệt. Cần xác minh release history trước
  quyết định migration. Không remap stableId/packId hoặc sửa userdata.
- CI đã bổ sung nhưng chưa chạy remote; không tạo PR hoặc push trong phiên này.
- Kết quả host Room/FTS không chứng minh performance, UI hoặc chất lượng nguồn.

## Bản ghi lịch sử trước integration

Phần bên dưới giữ nguyên bằng chứng cũ theo thời điểm, không mô tả checkout
hiện tại. Những ghi nhận “chưa build/ghép Android” được thay bằng kết quả ở trên.

# Kết quả kiểm chứng hiện tại

Ngày thực hiện: 23/10/2026. Phạm vi: tài liệu và tooling VOC-DATA-02, trong checkout `/workspace/VocaMind`.

## Đã thực hiện

- `python3 -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v`: **39 tests, tất cả PASS**, có 12 invalid fixtures và các vector ID/casefold chạy dưới subtests. Không phải 39 yêu cầu SRS hay 39 test Android.
- JSON Schema: `Draft202012Validator.check_schema`: **PASS**.
- Validator CLI trên `fixtures/valid/catalog.json`: **exit 0**, 4 ID duy nhất, 4 gói, 16 memberships.
- Cùng fixture với `--release`: **exit 1 như mong đợi**; chặn fixture flag, thiếu 150 từ, quota 40/40/40/30 và source chưa approved. Không coi rejection này là release đã đạt.
- Các test baseline xác nhận giữ ID khi sửa wording/đổi thứ tự; phát hiện thay index/hash, removal, tái dùng retired ID và đổi source snapshot dưới ID cũ.
- Một test boundary tạo object synthetic 150 từ trong RAM để xác minh nhánh release-metadata hợp lệ; không lưu thành học liệu thật, không xác nhận quyền hoặc chất lượng ngữ nghĩa.

Reference tooling dùng `jsonschema 4.26.0`, Python Unicode data `15.0.0`. Requirements đã pin jsonschema; dependency app/lockfile không bị thay đổi.

## Chưa được chứng minh bởi task này

- Nhân/Trường/Thi/Thành đã đọc hoặc phê duyệt contract.
- Adapter nguồn thực có đủ trường và provenance; nguồn/snapshot đã có quyền phân phối hoặc được PO/Content Lead duyệt.
- Checksum nguồn thực khớp artifact; validator chỉ kiểm tra metadata định dạng.
- Dữ liệu đủ 150 từ thật và đạt content QA.
- Java implementation tương thích test vector; Android project, Room schema/asset/FTS, migration, UI và performance chạy được.

Nhánh contract được tạo từ main khi main chỉ có README/.gitignore. Lần rà soát bổ sung đã đọc develop tại commit 4df8d979f395d646d220bb420f50c35cbf124340: có foundation Java 17, minSdk 26, compile/target SDK 35 và dependency Room; các package data/domain vẫn là placeholder, chưa có Room entities/DAO. Chưa ghép hoặc build Android foundation cùng contract. Bộ file nằm riêng dưới docs/data/vocabulary-contract-v1; bản ghi kiểm chứng này không xác nhận việc push lên remote.

## Rà soát bổ sung

Hai trường hợp biên được tái hiện và sửa: numeric token `1.0`/`1e0` ở field integer trả INTEGER_REPRESENTATION; Unicode surrogate đơn lẻ trả lỗi contract trước khi UTF-8 hashing. Có 3 test bổ sung, bao gồm CLI exit 1 không traceback. Các field/quy tắc stableId và schemaVersion không thay đổi.
