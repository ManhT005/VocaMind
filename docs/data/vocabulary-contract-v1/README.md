# VOC-DATA-02 — Bộ bàn giao contract v1.0.0

Đọc [CONTRACT.md](CONTRACT.md) để hiểu quy định và [REVIEW.md](REVIEW.md) để review. Đây là implementation candidate cho Nhân/Trường triển khai; chưa ghi nhận review của con người, chưa xác nhận nguồn thực hoặc build Android.

## Thành phần

- `CONTRACT.md`: yêu cầu và các quyết định kỹ thuật đề xuất, mapping Room/domain, quy tắc ID/version/nguồn.
- `catalog.schema.json`: canonical DTO/envelope có thể kiểm tra bằng máy.
- `validate_contract.py`: reference Python cho ID/normalization và cross-record validator.
- `test_contract.py`: acceptance tests.
- `fixtures/valid/catalog.json`: 4 từ, 4 pack, 16 memberships synthetic; **không phải học liệu release**.
- `fixtures/invalid/`: 12 fixture lỗi và expected error codes.
- `fixtures/stable-id-vectors.json`, `meaning-key-vectors.json`: đối chiếu implementation khác.
- `fixtures/source_snapshot.txt`: snapshot synthetic để giải thích provenance/hash.
- `requirements-validation.txt`: dependency của tooling, không sửa dependency app.

## Chạy từ checkout hiện có

Không cần tạo worktree. Từ `/workspace/VocaMind`:

```sh
# Nếu Python chưa có jsonschema, dùng virtualenv của tooling:
python3 -m venv /tmp/vocamind-contract-venv
/tmp/vocamind-contract-venv/bin/python -m pip install -r docs/data/vocabulary-contract-v1/requirements-validation.txt
/tmp/vocamind-contract-venv/bin/python -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v
/tmp/vocamind-contract-venv/bin/python docs/data/vocabulary-contract-v1/validate_contract.py docs/data/vocabulary-contract-v1/fixtures/valid/catalog.json
```

Trong máy hiện tại jsonschema đã có, các lệnh `python3` test/validate trực tiếp đã được chạy. Nếu máy khác dùng venv khác, thay executable tương ứng. Chưa thực thi lại bước tạo venv/cài pip vì dependency đã có; phiên bản reference được ghi trong VALIDATION.md.

Đầu ra validator fixture phải là `PASS_CONTRACT_CHECKS`, development mode, uniqueWords=4, memberships=16, packs=4. Chạy `--release` trên fixture phải exit 1; đó là rejection mong đợi, không phải học liệu đã sẵn sàng.

```sh
python3 docs/data/vocabulary-contract-v1/validate_contract.py /path/to/canonical-catalog.json --release
python3 docs/data/vocabulary-contract-v1/validate_contract.py /path/to/new-catalog.json --release --previous /path/to/previous-published-catalog.json
```

`--previous` dùng baseline thật cho upgrade. Không có baseline thì validator không chứng minh ID không đổi qua bản phát hành; trường `baselineCompared` phản ánh điều đó. Tool không fetch URL hoặc mở evidenceRef; người phụ trách xác minh nguồn/checksum/quyền và content QA riêng.

Exit code: 0 = contract checks qua; 1 = vi phạm; 2 = lỗi đọc/parse input. Không pipe để vô tình mất exit code. Output nêu giới hạn, không tuyên bố app/release đã đạt QA.

## Cách dùng cho từng thành viên

- **Thành:** làm chuẩn VOC-DATA-02; bổ sung review; dùng error/fixture làm đầu vào VOC-DATA-04.
- **Nhân:** triển khai Room theo mapping; kiểm tra asset/schema/FTS bằng tests Android sau khi có project.
- **Trường:** viết adapter ETL xuất canonical-catalog.json, giữ registry, map POS rõ và ghi provenance; dùng validator trước bàn giao.
- **Thi:** xây catalog asset từ canonical output, giữ source/attribution metadata và baseline/checksum artifacts.
- **Đạo:** dùng stableId và domain model qua repository, không dùng FTS rowid hoặc metadata ETL làm ID nghiệp vụ.

Không tự commit fixtures là học liệu production. Không đổi source fixture thành approved để vượt gate. Các test synthetic chỉ kiểm tra hành vi code, không xác minh nguồn/giấy phép hay ngữ nghĩa.
