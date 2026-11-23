# Quyết định thiết bị QA-02 ngày 11/11/2026

Trong phiên làm việc, người dùng chọn: **“Chấp thuận Small Phone API30 hiện có thay LDPlayer cho cả nghiệm thu và benchmark”.**

Phạm vi: QA-02, thiết bị Small_Phone hiện có, ADB emulator-5554, Google sdk_gphone_x86, Android11/API30, fingerprint `google/sdk_gphone_x86/generic_x86_arm:11/RSR1.201013.001/6903271:user/release-keys`, màn720×1280/320dpi. Inventory đầy đủ phải được ghi lại mỗi run; RAM quan sát trước đó2028236kB.

Quyết định cho phép các lượt tiếp theo trên cấu hình này phục vụ nghiệm thu và benchmark. Không chuyển các run lịch sử thành PASS, không phê duyệt APK/source chưa chạy, không đổi ngưỡng startup<1500ms hoặc FTS<50ms/corpus5000 (mục tiêu<15ms), không thay quyền nguồn B03 hoặc review/sign-off B05. Không sửa baseline SRS hoặc phân công.
