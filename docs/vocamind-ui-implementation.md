# VocaMind · Đặc tả giao diện Android và hướng dẫn triển khai

**Kho `ManhT005/VocaMind` · Nhánh `develop` · Android Java 17, XML, Material 3, ViewBinding**

Bản thiết kế đối chiếu với commit `5e5f2c7`. Agent có thể dùng tài liệu này như tệp đính kèm hoặc đọc bản trong `docs/` khi làm việc trên repo. Nếu nhánh đã tiến thêm, đối chiếu lại tên resource và trạng thái ở checkout trước khi chỉnh.

> **Prompt chạy agent**
>
> Đọc toàn bộ file Markdown này và triển khai trực tiếp đủ 10 màn vào ứng dụng Android trong repo `ManhT005/VocaMind` trên nền `develop`. Ưu tiên Trang chủ → Hôm nay → Flashcard → Quiz, rồi hoàn tất các màn còn lại theo mục 4. Áp dụng token, bố cục, điều hướng và trạng thái trong tài liệu. Dùng ViewModel, dữ liệu và nghiệp vụ của repo. Kết quả cần có mã Java/XML/resources đã chỉnh, bản build debug và tóm tắt bàn giao theo mục 5. Để đợt QA chức năng theo SRS sang lượt riêng.

## 1. Hướng thiết kế

VocaMind là ứng dụng học từ vựng Anh–Việt theo từng phiên ngắn. Mỗi màn học có một việc chính. Người dùng luôn thấy mình đang ở đâu, bước kế tiếp là gì và kết quả vừa được ghi nhận ra sao.

- **Nhận diện** · Chàm đậm, tím nhạt, nền giấy sáng và điểm nhấn cam đào. Hình chủ đạo là hai thẻ từ xếp lệch, có dấu sao bốn cánh nhỏ. Dùng khối màu phẳng, nét mềm và một lớp viền rõ trên các bề mặt tương tác.
- **Chất học qua trò chơi** · Thẻ lớn, tiến độ nhìn thấy được, phản hồi ngay sau thao tác và màn hoàn thành gọn. Dùng hình học, icon và tài nguyên của VocaMind để hình thành nhận diện riêng.
- **Ngôn ngữ** · Tiếng Việt ngắn, trực tiếp, động viên vừa phải. Giữ từ, IPA và câu ví dụ tiếng Anh nguyên dữ liệu. Dùng câu thường, có dấu, đặt toàn bộ copy tĩnh trong `strings.xml`.
- **Phạm vi** · Hoàn thiện giao diện của 10 màn hiện có, các row và shell chung. Dữ liệu hiển thị lấy từ catalog, phiên học và tiến độ có thật. XP, chuỗi ngày, trái tim, bảng xếp hạng, cửa hàng, âm thanh mới và luồng đăng nhập nằm ngoài phạm vi lượt này. Nguồn thiết kế là mã trên `develop`. Việc đối chiếu tài liệu SRS trên Drive dành cho đợt rà soát cuối.

## 2. Token và thành phần

### Màu

Đặt tên theo vai trò trong `res/values/colors.xml` và `values-night/colors.xml`. Dùng cặp chữ và nền cùng hàng. Với chữ trên mặt thẻ, dùng `Ink` hoặc `Muted`.

| Vai trò | Sáng | Tối | Cách dùng |
|---|---|---|---|
| Canvas | `#F7F7FC` | `#151523` | Nền màn hình |
| Surface | `#FFFFFF` | `#202034` | Thẻ, thanh điều hướng |
| Ink | `#211B35` | `#F1EDF9` | Chữ chính, số liệu |
| Muted | `#594F6E` | `#C4BBD6` | Chữ phụ, ví dụ dịch |
| Outline | `#DCD5EB` | `#47405C` | Viền thẻ, phân cách nhẹ |
| Control outline | `#827494` | `#9284A7` | Viền ô tìm kiếm và lựa chọn khi viền là dấu hiệu tương tác chính |
| Primary / onPrimary | `#5637B7 / #FFFFFF` | `#CBB9FF / #24114F` | CTA chính và dấu nhấn |
| Primary tint / onTint | `#EEE8FF / #402487` | `#362559 / #E7DBFF` | Chọn tab, chip, vùng học |
| Peach / onPeach | `#FFE4CB / #703B11` | `#4A2C23 / #FFD2AE` | Minh họa và phân loại |
| Success / onSuccess | `#DDF5EA / #155F48` | `#203F36 / #A5E8C9` | Đã kích hoạt, đáp án đúng |
| Error / onError | `#FFE7E8 / #8C2838` | `#492531 / #FFC0C9` | Lỗi, đáp án chưa đúng |

Gradient tùy chọn cho hero dùng hai đầu cùng tông tím nhạt, với chữ trên phần nền đồng đều. Các trục tiến độ và lựa chọn bắt buộc có chữ, icon hoặc trạng thái truy cập được bên cạnh màu. Kiểm tra tương phản tối thiểu 4.5:1 cho chữ thường và 3:1 cho chữ lớn, viền điều khiển có ý nghĩa và icon.

### Kích thước, chữ và chuyển động

| Mục | Thông số |
|---|---|
| Lưới | Bước 4 dp. Lề ngang 20 dp, khoảng giữa các section 24 dp, khoảng trong nhóm 8–12 dp, padding thẻ 16–20 dp. |
| Bề rộng | Một cột trên điện thoại. Trên `sw600dp`, căn giữa nội dung tối đa 560 dp và giữ cùng thứ tự đọc. Hero luôn một cột nội dung chính. |
| Chữ | Dùng sans-serif hệ thống hỗ trợ dấu tiếng Việt. Tiêu đề màn 28/34 sp bold, tiêu đề section 20/26 semibold, nội dung 16/24, phụ 14/20, nhãn nút 15/20 semibold. Lemma 36/42 trên thẻ và chi tiết. |
| Bo góc | Hero và flashcard 28 dp, thẻ nội dung 20 dp, nút và lựa chọn 16 dp, chip 999 dp. |
| Bề mặt | Viền 1 dp ở row/thẻ, elevation 0–2 dp. CTA có thể dùng viền đáy 3 dp cùng tông đậm để tạo cảm giác nhấn. Trạng thái nhấn không làm đổi kích thước layout. |
| Chạm | Vùng chạm tối thiểu 48×48 dp. Cao tối thiểu CTA 54 dp, lựa chọn Quiz 56 dp, row từ 64 dp. Chiều cao nội dung tự tăng khi chữ dài. |
| Chuyển động | Ripple của Material và chuyển trạng thái opacity 120–180 ms. Flashcard đổi nội dung bằng fade ngắn, sau đó đưa trọng tâm về tiêu đề thẻ khi có từ mới. Tôn trọng thiết lập tắt animation của hệ thống. |

### Thành phần dùng lại

- **App bar** 56 dp tối thiểu, tiêu đề 20 sp, nút quay lại hoặc đóng ở đầu hàng khi có màn cha. Trang chủ dùng wordmark VocaMind trên app bar. Một màn dùng tối đa một tiêu đề cấp trang.
- **Nút** · Primary nền chàm, secondary viền, tertiary dạng text. Disabled xử lý bằng selector state, giữ ngữ nghĩa disabled cho TalkBack. CTA ở vùng cuối màn dùng padding 16 dp. Nội dung dài vẫn cuộn tới được CTA ở cỡ chữ lớn.
- **Chip** · Dùng cho loại từ và trạng thái kích hoạt. Chip thông tin là nhãn, chip có hành động dùng vùng chạm đạt 48 dp. Icon dùng đường nét đồng bộ 24 dp, màu theo tint Material.
- **Tiến độ phiên** · Thanh 8 dp bo tròn và nhãn hiện rõ “Thẻ 3/12” hoặc “Câu 3/10”. Dùng tổng và vị trí của phiên hiện tại, gán mô tả truy cập được.
- **Bảng trạng thái** · Một icon hoặc minh họa, một dòng tiêu đề, tối đa hai dòng mô tả ưu tiên, một CTA khi có hành động. Loading có nhãn ngắn, lỗi có “Thử lại” khi ViewModel hỗ trợ, empty chỉ ra bước kế tiếp nếu có. Khi đang ghi dữ liệu, giữ ổn định nội dung đang thao tác và khóa đúng nút liên quan.
- **Minh họa** · Vẽ icon vector Android và hình thẻ nguyên bản bằng drawable, không cần tải font hoặc hình qua mạng. Ảnh trang trí `importantForAccessibility=no`.

## 3. Điều hướng và shell

Dùng `MainActivity`, `activity_main.xml`, `nav_graph.xml` và Material `BottomNavigationView`. Bốn tab có icon và nhãn cố định.

| Tab | Destination hiện có | Nội dung |
|---|---|---|
| Trang chủ | `homeFragment` | Điểm bắt đầu, nút sang Hôm nay, Khám phá gói, Quiz và Nguồn nội dung |
| Học | `todayFragment` | Kế hoạch học ngày, vào Flashcard |
| Từ vựng | `vocabularyFragment` | Tìm từ và mở chi tiết |
| Tiến độ | `progressFragment` | Số liệu học và lịch sử Quiz |

Hiện thanh tab ở bốn destination trên, ẩn ở Gói từ, Chi tiết gói, Chi tiết từ, Flashcard, Quiz và Nguồn nội dung. Tab Học mang tiêu đề trang “Hôm nay”. Gán ID menu trùng ID destination và đồng bộ trạng thái chọn qua `NavController`. Các CTA dẫn tới Hôm nay sử dụng cùng thao tác chọn tab. Chạm lại tab đang chọn giữ nguyên màn và vị trí cuộn. Chuyển tab không tạo bản sao destination hay mở thêm phiên học. Back từ màn con trả về đúng màn mở nó, ví dụ Chi tiết từ trả về kết quả tìm hoặc danh sách từ của gói. Back từ tab gốc khác đưa về Trang chủ; kiểm tra back stack bằng Navigation Component thay vì tự lưu bản sao lịch sử ở Fragment. Quiz giữ nguyên dialog xác nhận thoát giữa phiên.

Trong `activity_main.xml`, bố trí NavHost và thanh tab theo trục dọc, NavHost chiếm phần còn lại. Khi thanh tab ẩn, NavHost mở rộng đến hết vùng nội dung. Ở `MainActivity`, đăng ký listener đích và quản lý trạng thái hiển thị tab tại một chỗ. Giữ nguyên ID `shell_root` và `nav_host_fragment`. Với chuyển tab, dùng cơ chế lưu và khôi phục back stack của Navigation Component cùng `launchSingleTop`. Đảm bảo khi Back về Trang chủ, tab Trang chủ được cập nhật theo destination thay vì theo lần chạm gần nhất.

Giữ xử lý `systemBars`, `displayCutout`, `ime` ở shell, áp dụng mỗi inset đúng một lần. Khi nhập tìm kiếm, ẩn thanh tab trong lúc IME mở để dành chỗ cho kết quả, hiện lại sau khi bàn phím đóng nếu route vẫn là tab. Tài nguyên màu hệ thống cần tương thích cả sáng và tối.

## 4. Màn hình theo thứ tự triển khai

### 4.1. Trang chủ · `fragment_home.xml`

Bố cục từ trên xuống:

```text
VocaMind
╭─────────────────────────────╮
│ Học từ vựng hôm nay    ▱✦  │
│ Học từ mới và ôn từ đã gặp. │
│ [       Vào học ngay      ] │
╰─────────────────────────────╯
Khám phá
[ biểu tượng ]  Gói từ vựng       ›
[ biểu tượng ]  Quiz             ›
Nguồn nội dung
[Trang chủ] [Học] [Từ vựng] [Tiến độ]
```

- Hero tím nhạt, minh họa chiếm tối đa 88×88 dp ở phần trên bên phải và nhường chỗ cho tiêu đề ở màn hẹp. CTA chính full width ở đáy hero, mở tab Học. Dùng nội dung tĩnh vì Home hiện là màn điều hướng.
- Gói từ và Quiz là hai row rộng, icon trên nền tint khác nhau, chữ rõ và chevron cuối row. Nguồn nội dung là link dạng row nhỏ dưới nhóm. Từ vựng và Tiến độ truy cập trực tiếp qua tab.
- Khi bỏ các nút trùng với tab từ layout cũ, cập nhật các binding và listener tương ứng cùng lúc. Giữ những lần vào màn từ các đường dẫn khác.

### 4.2. Hôm nay · `fragment_today.xml`

- **Sẵn sàng** · Heading “Kế hoạch hôm nay”. Thẻ kế hoạch gồm hai ô “Từ đến hạn” và “Từ mới” với số 28 sp lấy từ `dueCount` và `newCount`. Hai ô chuyển thành hai hàng khi màn hẹp hoặc chữ lớn. CTA “Bắt đầu học” mở Flashcard. Một số bằng 0 vẫn hiển thị đúng giá trị.
- **Chưa kích hoạt gói** · Minh họa thẻ trống, “Chọn gói từ đầu tiên”, CTA “Khám phá gói từ” dẫn tới danh sách gói. Sau khi bật gói, Back từ chi tiết về danh sách rồi Back về Hôm nay. Giữ cơ chế refresh hiện tại để cập nhật kế hoạch.
- **Đã hoàn tất hoặc hết mục ôn hôm nay** · Thẻ success, “Bạn đã học xong phần hôm nay”, CTA “Làm Quiz”, nút phụ “Khám phá gói từ”. Tôn trọng trạng thái `DONE` do model cung cấp, kể cả khi số liệu phiên không có.
- **Đang tải hoặc lỗi** · Giữ tiêu đề màn, thay vùng kế hoạch bằng trạng thái chuẩn. Lỗi dùng `retry` hiện có để tải lại cả gói và kế hoạch.

### 4.3. Flashcard · `fragment_flashcard.xml`

```text
[‹] Ôn từ                    Thẻ 3/12
[████████░░░░░░░░░░░░░░░░░░░░]
╭─────────────────────────────╮
│            [danh từ]        │
│             journey         │
│       Chạm để xem nghĩa     │
╰─────────────────────────────╯
```

- **Mặt trước** · Thẻ rộng, chiều cao tối thiểu 220 dp, nền tint, lemma căn giữa và loại từ trong chip. Cả thẻ có thể chạm, có action label “Xem nghĩa”. Dữ liệu nghĩa và ví dụ chưa lộ ra trong cây accessibility.
- **Mặt sau** · Giữ lemma và loại từ. Bên dưới là IPA nếu có, nghĩa Việt nổi bật, ví dụ Anh rồi bản dịch Việt. Từ dài tự xuống dòng. Dùng câu mặc định của app cho IPA thiếu. Toàn màn cuộn nếu cần, kể cả khi mặt sau dài.
- **Đánh giá** · Sau lật, hiển thị ba nút “Quên”, “Khó”, “Nhớ” dưới mục “Bạn nhớ từ này thế nào?”. Dùng màu nhấn khác nhau và giữ chữ rõ; khi hẹp xếp dọc. Trạng thái enabled chỉ lấy từ `ratingEnabled()`. Tiếp tục gửi đúng `presentationId` của thẻ đã render và chỉ ghi đánh giá qua listener hiện có.
- **Phản hồi** · Dùng vùng `result_hint` cho kết quả lên lịch hoặc lỗi lưu. Lỗi để người dùng nhìn thấy thẻ đang thao tác, mở lại lựa chọn đúng theo model. Khi đến thẻ mới, cuộn về đầu như logic hiện tại.
- **Hoàn thành** · Thẻ thành công, “Đã hoàn thành phiên học”, nút “Về hôm nay” quay về phiên bản Hôm nay đã mở trong back stack. Từ Hôm nay, người dùng có thể mở Quiz theo trạng thái hiện có. `NO_ACTIVE_PACK` và `NOTHING_TO_STUDY` có giải thích và CTA quay lại. Lỗi tải có “Thử lại”. Giữ loại root cuộn hoặc cập nhật phần code phụ thuộc `scrollTo` trong cùng thay đổi.

### 4.4. Quiz · `fragment_quiz.xml`

```text
[×] Quiz                         Câu 2/8
[██████░░░░░░░░░░░░░░░░░░░░░░░░]
╭─────────────────────────────────────╮
│ journey                  [danh từ]  │
│ We began our journey before dawn.   │
╰─────────────────────────────────────╯
Chọn nghĩa đúng
[A  chuyến đi                     ✓ ]
[B  điểm đến                        ]
[C  khoảng cách                     ]
[D  khởi đầu                        ]
[✓ Chính xác!]
[            Tiếp tục               ]
```

Sơ đồ minh họa trạng thái sau khi chọn đúng. Khi nội dung dài, vùng câu hỏi và đáp án cuộn trong một trục dọc. Vùng phản hồi đứng trước CTA theo thứ tự đọc. Nếu CTA dùng thanh dưới cố định, nội dung cuộn cần đủ khoảng trống để đáp án cuối và phản hồi hiện đầy đủ.


- **Câu hỏi** · Nút đóng, nhãn “Câu x/y” và thanh tiến độ. Thẻ prompt có lemma lớn, loại từ và ngữ cảnh bên dưới. Tiêu đề nhóm đáp án “Chọn nghĩa đúng”. Bốn nút 1 cột, có thể gắn ký tự A–D làm mốc nhìn. Mở Quiz vẫn theo `onOpened()` của luồng hiện có.
- **Chưa trả lời** · Lựa chọn nền Surface và viền Control outline, “Tiếp tục” disabled. Chạm đáp án vẫn chốt ngay theo hành vi hiện tại.
- **Đã trả lời** · Khóa bốn lựa chọn. Đáp án đúng có nền success, icon và nhãn “Đúng”. Đáp án người dùng chọn sai có nền error và nhãn “Bạn đã chọn”. Vùng phản hồi dưới các đáp án có “Chính xác!” hoặc “Chưa đúng” và nghĩa đúng. Dùng `accessibilityLiveRegion=polite` cho vùng phản hồi và chỉ cập nhật nội dung khi đáp án đổi; người dùng chủ động bấm “Tiếp tục”.
- **Đang lưu, lỗi lưu và kết quả** · Thay vùng câu hỏi bằng thẻ kết quả sau câu cuối. Số điểm phần trăm là yếu tố chính, số đúng và sai nằm dưới; lấy từ `QuizResult`. Hiện tình trạng lưu cạnh số liệu. Khi lưu lỗi, giữ điểm và hiện “Thử lưu lại” gọi `retrySave()`. Khi lưu thành công, CTA “Làm lại” gọi luồng hiện có; có thể thêm nút “Quay lại” gọi luồng thoát đã có.
- **Thiếu dữ liệu và lỗi tạo bài** · Dùng copy hiện có và hành động thử lại tương ứng. Cả nút đóng và Back hệ thống dùng cùng dialog thoát phiên. Tiếp tục phiên giữ nguyên đáp án, tiến độ và câu hỏi.

### 4.5. Gói từ · `fragment_packs.xml` + `item_pack.xml`

- App bar “Gói từ vựng” có Back. Danh sách một cột, mỗi gói là thẻ có icon trên nền tint, tên, mô tả hoặc số từ nếu row đã có dữ liệu đó, chip “Đang học” khi gói đang hoạt động và chevron.
- Mỗi thẻ là một vùng chạm mở Chi tiết gói với `packId` hiện tại. Ưu tiên thứ tự gói từ model. Mô tả dài trên row có thể giới hạn hai dòng nếu văn bản đầy đủ có trên màn chi tiết.
- Danh sách rỗng dùng thông báo nhẹ và Back. Khi lỗi, nút “Thử lại” gọi `PacksViewModel.retry()` như hiện tại.

### 4.6. Chi tiết gói · `fragment_pack_detail.xml` + `item_pack_word.xml`

- App bar Back, hero nhỏ tên gói, trạng thái và tác vụ “Bật gói” hoặc “Tắt gói” tùy model. Khi đang áp dụng, dùng nhãn “Đang cập nhật…” và khóa hành động; giữ nguyên danh sách từ đang đọc.
- Phần “Từ trong gói” nằm ngay dưới hero, row ưu tiên lemma, loại từ và nghĩa Việt nếu có. Tap mở Chi tiết từ giữ đúng cả `vocabularyId` và `packId`. Ưu tiên dùng chung typography/viền với row tìm kiếm.
- Lỗi danh sách và lỗi đổi kích hoạt hiển thị gần vùng liên quan, dùng cơ chế thử lại có sẵn. Giữ nguyên dấu đang học tới khi model xác nhận thao tác thành công. Danh sách rỗng có nhãn rõ.

### 4.7. Từ vựng · `fragment_vocabulary.xml` + `item_search_word.xml`

- App bar “Từ vựng”, thanh tìm nổi có icon kính lúp và hint “Nhập từ tiếng Anh”. Giữ `SearchView` nếu tiếp tục dùng binding và luồng query hiện tại. Nút xóa có nhãn TalkBack “Xóa nội dung tìm kiếm” và gọi reset hiện có.
- Chưa nhập: hiển thị gợi ý “Nhập một từ để xem nghĩa và ví dụ”. Có kết quả: mỗi row rộng, lemma semibold, nghĩa Việt bên dưới, loại từ ở cuối nếu đủ chỗ. Chạm mở Chi tiết từ. Ngăn bàn phím che kết quả.
- Không khớp: “Chưa tìm thấy từ phù hợp”, gợi ý kiểm tra chính tả và giữ nguyên query. Lỗi: “Không thể tìm từ lúc này”, nút “Thử lại” giữ query. Dùng state của `SearchViewModel` để nhận đúng kết quả khi gõ liên tục, không thêm cache kết quả ở Fragment.

### 4.8. Chi tiết từ · `fragment_vocabulary_detail.xml`

- App bar Back và bookmark 48 dp ở cuối. Mặt thẻ có lemma 36 sp, IPA và chip loại từ. Bên dưới là “Nghĩa tiếng Việt”, rồi thẻ “Ví dụ” với câu Anh đậm hơn và bản dịch bên dưới.
- Bookmark có icon viền hoặc icon đầy, nhãn hành động “Lưu từ” / “Bỏ lưu từ”, trạng thái chọn và pending từ ViewModel. Lỗi lưu hiện sát bookmark hoặc trong nội dung, người dùng vẫn đọc được từ. Giữ hành vi hiện có với từ mở từ gói hoặc tìm kiếm.
- Khi tải, giữ app bar và ẩn CTA lưu cho tới khi có dữ liệu. Lỗi đọc hoặc thiếu tham số hiển thị route message và hành động phù hợp từ màn hiện có. Các đoạn IPA, nghĩa và ví dụ cuộn tự nhiên, tránh thu nhỏ chữ để ép vào thẻ.

### 4.9. Tiến độ · `fragment_progress.xml` + các row tiến độ

- Mở đầu bằng hero “Từ đã học” và tổng thật, tiếp theo là ba nhóm “Đang học”, “Đang ôn”, “Nhớ vững”. Giữ mapping Leitner 0–1, 2–3, 4–5. Dùng tên nhóm và số đếm trực tiếp, có thể kèm thanh tỷ lệ theo tổng khi tổng lớn hơn 0. Trong vùng giải thích nhỏ, nêu đây là trạng thái ôn tập trong ứng dụng.
- Nếu UI hiện tại liệt kê cấp ôn 0–5, đặt bảng cấp ngay dưới ba nhóm hoặc phần mở rộng trong cùng màn; giữ từng số đếm đang có. “Lịch sử Quiz” là section riêng, mỗi row có ngày, số đúng trên tổng và điểm %, lấy từ snapshot.
- Tiến độ rỗng và lịch sử rỗng là hai trạng thái độc lập. “Bắt đầu học để xem tiến độ” có thể dẫn tới tab Học. Giữ hành động làm mới tại app bar và “Thử lại” trong trạng thái lỗi.

### 4.10. Nguồn nội dung · `fragment_attribution.xml` + `item_attribution_source.xml`

- App bar “Nguồn nội dung” với Back. Mỗi nguồn là một thẻ thẳng hàng, ưu tiên tên nguồn, tác giả/ghi công, giấy phép và ghi chú theo dữ liệu hiện có. Cho phép văn bản dài xuống dòng.
- “Mở nguồn” và “Xem giấy phép” là nút text có icon liên kết ngoài, dùng accessor và cơ chế mở URL hiện có. Giữ cách hiển thị thông tin thiếu hoặc liên kết không khả dụng. Loading, empty và retry theo `AttributionViewModel`.

## 5. Bàn giao triển khai

1. **Nền UI và luồng học.** Tạo token, styles, drawable, icon và shell trước. Chỉnh Home, Today, Flashcard và Quiz. Nhập copy vào resources và gắn bằng state hiện có. Khi thay layout, kiểm tra cả ViewBinding lẫn các chỗ Java dùng `findViewById`; giữ ID/kiểu view nếu không có lý do đổi.
2. **Khám phá và số liệu.** Chỉnh Gói từ, Chi tiết gói, tìm kiếm, chi tiết từ, Tiến độ và Nguồn nội dung. Chia sẻ style cho row và panel trạng thái, giữ các adapter, luồng truy cập dữ liệu và lifecycle quan sát hiện có. Các thao tác lưu, tính lịch, chấm Quiz, kích hoạt gói, nguồn dữ liệu và cấu trúc DB tiếp tục đi qua đường dẫn hiện có.
3. **Rà hiển thị để chốt UI.** Nếu có thiết bị hoặc emulator, xem Home, Today, hai mặt Flashcard, Quiz sau trả lời, tìm kiếm và Tiến độ ở chiều rộng khoảng 360 dp. Xem thêm dark mode, chữ lớn và bàn phím ở Từ vựng. Kiểm tra Back ở màn con, phần cuộn cuối trang và việc mỗi CTA chỉ nhận một lần bấm. Đây là rà bố cục và điều hướng phục vụ bàn giao giao diện. Đợt kiểm thử chức năng đầy đủ theo SRS thực hiện sau.
4. **Build từ checkout.** Dùng toolchain đã thiết lập trên cloud. Nếu shell mới, kích hoạt bằng các lệnh dưới đây. Ngoài cloud, dùng JDK 17, Android SDK và Gradle Wrapper theo `docs/android-setup.md` rồi chạy Gradle từ gốc repo. Bàn giao đường dẫn APK, kết quả build, danh sách màn đã chỉnh và phần hiển thị chưa xem trực tiếp nếu thiếu thiết bị.

```bash
cd /workspace/VocaMind
source /workspace/.vocamind/activate.sh
./gradlew --no-daemon --max-workers=4 --console=plain :app:assembleDebug
```

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Luồng bàn giao hoàn tất khi cả 10 màn đều dùng nhận diện thống nhất, các CTA hiện đúng theo state, copy rõ trên nền sáng/tối và build APK thành công. Nếu giới hạn thời gian, hoàn thiện từng cụm màn đến mức build được và báo chính xác phần còn lại.
