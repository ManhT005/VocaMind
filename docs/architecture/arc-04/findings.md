# ARC-04 — Findings và điều kiện retest

| ID | Mức độ | Quan sát | Owner và điều kiện đóng |
| --- | --- | --- | --- |
| A04-F01 | P0 · artifact | Job Quiz device ở baseline upload `app/build/outputs/apk/`. Harness build APK cô lập từ catalog nội bộ 150 từ. Run baseline xác nhận bước upload thành công và artifact còn tồn tại lúc audit. | Thành/maintainer CI xác minh run mới chỉ upload allowlist. Xử lý artifact lịch sử theo phạm vi quyền. |
| A04-F02 | P0 · nghiệm thu luồng | Today chỉ có active-pack boundary. Progress còn shell. Thiếu bằng chứng UI Flash rating 600 ms và Today → Flash → Progress. | Owner TODAY/FLASH và Thành/PROGRESS-02. Kiểm AC-08/11 trên thiết bị, gồm tap lặp, rollback, restore và số liệu. |
| A04-F03 | P1 · PR #32 | Merge mô phỏng sạch và giữ API Quiz/Progress. | Trường, Thành review. Chạy Detail/Save, Save–Review conflict, Quiz policy và Progress snapshot trên tree cuối. |
| A04-F04 | P0 · release | `createFromAsset` chỉ tạo DB. DATA-08 ngoài baseline và chưa có bằng chứng benchmark DATA-09 trên thiết bị tham chiếu. | DATA-08/09 và QA-02. Kiểm swap/rollback, đóng/mở handle, giữ userdata, companion đồng bộ và đo FTS. |
| A04-F05 | P0 · release | Credits cục bộ có mặt. Ledger vẫn HOLD, gate app-release từ chối. | Thi và PO/Content Lead xác nhận quyền theo snapshot và retest AC-12. |

- **A04-F01.** Cấu hình trên nhánh này giữ instrumentation và giới hạn upload
  vào checkout SHA, exit status, trạng thái offline, checksum APK và metadata
  fixture. APK, ảnh và log thô nằm ngoài allowlist. Artifact lịch sử cần
  xử lý riêng. [Workflow trước sửa L49–72](https://github.com/ManhT005/VocaMind/blob/cd83f1ce874a899029a85879c3c40c97343683d6/.github/workflows/android-ci.yml#L49-L72) · [Metadata run](../../evidence/arc-04/20261108/github-baseline.json)
- **A04-F02.** [Today L16–39](../../../app/src/main/java/com/vocamind/app/feature/today/TodayFragment.java#L16-L39) · [Progress L7–12](../../../app/src/main/java/com/vocamind/app/feature/progress/ProgressFragment.java#L7-L12) · [RecordReviewUseCase L8–33](../../../app/src/main/java/com/vocamind/app/domain/learning/RecordReviewUseCase.java#L8-L33)
- **A04-F04/F05.** [CatalogDatabase L10–19](../../../app/src/main/java/com/vocamind/app/data/catalog/CatalogDatabase.java#L10-L19) · [Quyết định quyền](../../licensing/voc-data-06/release-rights-decision.json)
