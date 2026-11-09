# VOC-DATA-05 — Kiểm thử và bàn giao

Nhánh `feature/voc-data-05-enrichment-metadata` dựa trên `origin/develop` tại
`cd83f1ce874a899029a85879c3c40c97343683d6`. Bản dựng lại giữ đúng SHA-256 của
sidecar trước `44216f8c9e61199418b2345fe7c04265e514fb821f0c88114e6e1cae4b261c31`.

- 150 stable ID khớp một-một với canonical, pack 40/40/40/30. CEFR và topic
  đều có 150 unknown, 0 suggested và 0 verified. Taxonomy có 10 chủ đề.
- 199 test Python đạt, gồm 17 test enrichment. Canonical contract đạt,
  generator cho output xác định, và mẫu kiểm tra cấu trúc gồm 10 mục theo pack/POS.
- Canonical, Room, Quiz policy và runtime assets giữ nguyên. 440 test Android/JVM
  và build/lint đã đạt ở lần trước trên cùng baseline. Raw log lần đó không còn.
  Kết quả Android của nhánh dựng lại theo CI sau khi push.
- Workflow Quiz giữ instrumentation và chỉ tải bản tóm tắt/checksum để tránh
  đưa APK chứa catalog nội bộ vào artifact công khai.

[Đặc tả](data/voc-data-05/ENRICHMENT_SPEC.md) ·
[Coverage](evidence/voc-data-05/20261108/coverage.json) ·
[Hash và kiểm thử](evidence/voc-data-05/20261108/verification.json)

```sh
python tools/vocabulary/validate_enrichment.py \
  --input docs/data/voc-data-05/enrichment-v1.json \
  --canonical docs/data/voc-data-04/accepted-v1/canonical-preview.json \
  --taxonomy docs/data/voc-data-05/topic-taxonomy-v1.json \
  --sources docs/data/voc-data-05/label-sources.json \
  --reviews docs/data/voc-data-05/review-decisions.json
python -m unittest discover -s tools/vocabulary -p 'test_enrichment.py' -v
```

Thành review taxonomy và hợp đồng bằng chứng trước khi chốt task.
