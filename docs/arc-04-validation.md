# ARC-04 — Bàn giao architecture review tuần 2

Audit hồi cứu hoàn tất trên baseline `cd83f1ce874a899029a85879c3c40c97343683d6`.
Nghiệm thu vẫn `HOLD` và chờ Nhân sign-off.

- [Baseline và đối chiếu SRS](architecture/arc-04/review-baseline.md)
- [Checklist A04-T01–T15 và trace luồng](architecture/arc-04/checklist.md)
- [Findings, owner và điều kiện retest](architecture/arc-04/findings.md)
- [Bằng chứng baseline](evidence/arc-04/20261108/)

CI baseline xanh, gồm Quiz offline API 28. Audit trước khi dựng lại nhánh ghi
nhận 440 test Android/JVM đạt, build và lint debug/catalogQa đạt, APK debug có
fixture 4 từ, QA và Quiz cô lập có 150 từ. Raw log local của lần đó đã mất
khi workspace được dọn. Bản dựng lại kiểm tra lại static scan, merge-tree,
builder Quiz cô lập và metadata CI. [CI baseline](https://github.com/ManhT005/VocaMind/actions/runs/42385571766)

Workflow hiện giới hạn artifact Quiz công khai vào bản tóm tắt và checksum
để xử lý đường upload mới của A04-F01. Xử lý artifact lịch sử và nghiệm thu
luồng Today → Flash → Progress còn mở. DATA-08/09 nhận gate catalog/FTS,
QA-02 nhận smoke thiết bị, ARC-05 theo dõi artifact và cập nhật baseline
sau khi VOC-DATA-05 merge.
