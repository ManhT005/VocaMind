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
