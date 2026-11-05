# feat(vocabulary): danh sách từ theo pack và route stableId

Base đề xuất: develop. Phụ trách: Trường. Reviewer: Thành.

Danh sách PACK-01 thiếu IPA và chưa điều hướng từng từ. Hoàn thiện RecyclerView hiện có với IPA/placeholder, trạng thái theo learnedAt/box, DiffUtil stableId TEXT và route tối thiểu nhận vocabularyId/packId cho VOCAB-02. Giữ GetPackWords/PackRepository làm boundary GetVocabularyByPack cùng ordering/mapping hiện có; browsing inactive/paused chỉ đọc.

Refresh khi quay lại màn và generation guard ngăn kết quả tải cũ ghi đè. Không mở rộng search/full detail/save; không sửa catalog/schema.

Validation tại b09d08fa6f2f4da2a98d460abfa57e4de39b091e: 211 unit/Robolectric tests qua, 0 failure/error/skipped; assembleDebug, assembleDebugAndroidTest, lintDebug qua (0 errors/6 warnings), Gradle --offline. Bao phủ ordering/membership/shared progress, snapshot read-only/file reopen, IPA recycling/labels, real navigation/Back/recreation và late result.

Instrumentation NOT RUN: connectedDebugAndroidTest báo No connected devices. Airplane Mode first launch, reboot, device process reopen, font scale/scroll restoration cần QA. Full detail/AC-04 pending VOCAB-02. Nhãn learnedAt=null “Chưa có lượt ôn” cần Thành xác nhận. Khi tích hợp PACK-02 chú ý AsyncListViewModel/Factory/PackDetailFragment.

Evidence/contract/checklist: docs/vocab-01-validation.md và docs/validation/vocab-01/.
Theo mô phỏng reviewer cung cấp với PACK-02 tại 0512417, conflict nằm ở AsyncListViewModel.java, PackDetailFragment.java, strings.xml và docs/README.md. Đây là lưu ý giữa hai feature branches; PACK-02 chưa có trong develop đã fetch. Nhánh merge sau cần giữ generation/refresh/mutation guard và toàn bộ adapter/navigation/reload/strings của VOCAB-01. Factory auto-merge vẫn cần kiểm thử cả hai hành vi trên SHA tích hợp; chưa có evidence cho tổ hợp này.
Không tự đánh dấu task Hoàn thành. Đây là bản nháp, chưa tạo PR.
