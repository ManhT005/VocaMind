# VOC-DATA-01 — Source/license ledger

Ngày khảo sát: 2026-10-25. Phạm vi: nghiên cứu nguồn bên ngoài cho MVP VocaMind.
Người thực hiện theo backlog: Thi, Nhân; reviewer: Trường. Chưa có xác nhận review.
Tham chiếu yêu cầu do backlog cung cấp: SRS §7.3 DATA-01/06; BR-12; AC-12;
NFR-VM-12; DEC-09. Checkout không có toàn văn SRS để xác minh các điều khoản.
Task này khác DATA-01 CatalogDatabase trong `plans/PlanDATA01.md`.

## Quyết định nguồn

`include` là lựa chọn nguồn cho MVP với điều kiện bên dưới, không phải quyền
đưa ngay một bản tải bất kỳ vào catalog. Registry máy đọc ở
[source-registry.json](data/source-registry.json). Tất cả snapshot hiện chưa tải,
nên checksum để null có chủ đích, pipeline chưa được phép nhập nguồn nào.

| Source ID | Nội dung sử dụng | Quyết định | Giấy phép và evidence | Quyền đóng gói / điều kiện |
| --- | --- | --- | --- | --- |
| ngsl | NGSL 1.2, lemma và thứ hạng, không lấy định nghĩa từ công cụ liên kết | include, chờ snapshot | [Trang NGSL, mục Creative Commons License](https://www.newgeneralservicelist.com/new-general-service-list): CC BY-SA 4.0; Browne, Culligan, Phillips | Cho phép sao chép, sửa đổi, thương mại theo giấy phép; giữ credit, URL, license và ghi thay đổi; phần dữ liệu phái sinh áp dụng ShareAlike |
| tsl | TOEIC Service List 1.2, lemma và thứ hạng | include, chờ snapshot | [Trang TSL, mục Creative Commons License](https://www.newgeneralservicelist.com/toeic-service-list): CC BY-SA 4.0; Browne, Culligan | Điều kiện như NGSL; không suy giấy phép này cho đề thi, audio hoặc dịch vụ liên kết |
| kaikki-viwiktionary | Mục từ tiếng Anh, gloss tiếng Việt, POS và IPA nếu có | include, blocked về quyền cụ thể | [Kaikki Vietnamese edition, Copyright and license](https://kaikki.org/viwiktionary/): CC-BY-SA và GFDL, không nêu phiên bản ở đoạn này | Chưa duyệt đóng gói. Phải chốt phiên bản/license route áp dụng cho snapshot và provenance từng mục trước ETL |
| tatoeba-text | Câu EN và bản dịch VI có liên kết trực tiếp | include, chờ snapshot và attribution từng câu | [Terms §6.2–6.5](https://tatoeba.org/en/terms_of_use): mặc định CC BY 2.0 FR, có giấy phép từng câu | Cho phép tái sử dụng theo license thực tế; giữ sentence ID, tác giả, URL và license của cả hai câu; chặn câu thiếu evidence |
| fvdp | Free Vietnamese Dictionary Project | exclude | Quyết định phạm vi MVP trong backlog VOC-DATA-01 ngày 2026-10-25 | Không tải, nhập hay đóng gói. Chưa khảo sát quyền; exclude không có nghĩa là kết luận nguồn không hợp pháp |
| tatoeba-audio | Mọi file ghi âm Tatoeba | exclude | Backlog và [Terms §6.2](https://tatoeba.org/en/terms_of_use): quyền audio khác text, phụ thuộc contributor | Không tải, nhập, cache hay đóng gói audio trong MVP; quyền text không cấp quyền audio |

## Evidence và giới hạn khảo sát

- NGSL: trang chính thức hiện ghi 1.2, 2.809 từ; chọn bản “with basic statistics”
  khi cần thứ hạng. TSL: 1.2, 1.250 từ và bản “with basic statistics”. Không gán
  ngày khảo sát thành ngày phát hành hoặc ngày snapshot.
- [Kaikki raw data](https://kaikki.org/viwiktionary/rawdata.html) hiện chỉ tới dump
  viwiktionary 2026-10-02; đây là edition tiếng Việt có gloss tiếng Việt, cần lọc
  ngôn ngữ mục từ tiếng Anh. Không nhầm với mục Vietnamese trong enwiktionary.
  [Trang Tiếng Anh](https://kaikki.org/viwiktionary/Ti%E1%BA%BFng%20Anh/index.html)
  ghi extraction 2026-10-21 và cảnh báo bản postprocessed có trộn nguồn bổ sung.
  Ưu tiên raw JSONL, giữ provenance, không lấy postprocessed làm mặc định.
- Kaikki dẫn trang copyright tiếng Anh; chưa đủ để tự khẳng định phiên bản
  giấy phép của toàn bộ nội dung edition tiếng Việt. Chặn đến khi lưu được
  điều khoản Wikimedia/viwiktionary áp dụng và kiểm tra ngoại lệ của snapshot.
  License mã nguồn Wiktextract không thay thế license nội dung từ điển.
- Tatoeba: Terms hiện hành ghi mặc định CC BY 2.0 FR; không đổi thành BY 4.0.
  [Hướng dẫn CC0](https://en.wiki.tatoeba.org/articles/show/cc0-contributions)
  cho biết có câu CC0. Giữ license thực tế, không gán toàn corpus là CC0.
  Endpoint [downloads](https://tatoeba.org/en/downloads) timeout khi khảo sát;
  chưa xác nhận tên file/schema export, ngày dump hay checksum. Khi thu nhận,
  chọn export có attribution và license, cùng bảng links; thiếu metadata thì chặn.
- Điều kiện đóng gói tham chiếu [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
  và [CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/).
  Đây là tóm tắt phục vụ quyết định; lưu legal code cùng snapshot trước release.

## Contract source registry và snapshot

Mỗi source có ID ổn định, title, URL gốc, content scope, quyết định, lý do,
license code/URL (null khi chưa rõ), evidence URL và ngày kiểm tra,
packaging/attribution obligations, cùng snapshot và blockers. Evidence hiện là
URL và ghi nhận khảo sát trong tài liệu này; chưa phải bản lưu bất biến upstream.

Snapshot sau thu nhận phải chứa `upstreamVersion`, `upstreamDate`,
`retrievedAt` (UTC ISO-8601), `artifacts[]`. Mỗi artifact phải có `url`
(URL chính xác sau redirect), `path` (tệp raw giữ nguyên byte), `bytes`,
`sha256` (64 hex chữ thường). Liệt kê riêng mọi tệp words, links, authors,
licenses và evidence. SHA-256 tính trên byte tải về, trước giải nén/chuyển mã;
nếu giữ tệp giải nén thì ghi thêm artifact riêng. Không dùng checksum trang web
hoặc fixture thay cho checksum dữ liệu. URL `latest` phải được khóa bằng bytes
đã lưu, retrieval timestamp và SHA-256; mọi lần tải mới là một snapshot mới.

## Gate bắt buộc cho ETL và đóng gói

Chỉ đi tiếp khi **tất cả** điều kiện đạt:

1. Source tồn tại trong registry, `decision=include`, không thuộc FVDP/audio.
2. Quyền đóng gói đã được chốt cho đúng phạm vi và snapshot; không có blocker.
   License chưa rõ, evidence thiếu hoặc license chưa được hỗ trợ đều dừng.
3. Có snapshot hoàn chỉnh, evidence điều khoản đã lưu và SHA-256 mọi artifact
   đối chiếu thành công. Không tự tải lại `latest` khi hash sai.
4. Mọi field giữ provenance source/snapshot/record. Tatoeba giữ hai sentence ID,
   hai authors/licenses, link trực tiếp; không ghép câu chỉ vì trùng lemma.
5. Có manifest attribution offline, license notices và change log; dữ liệu phái
   sinh BY-SA có cách phân phối đáp ứng ShareAlike. Không tự suy ShareAlike
   áp dụng toàn bộ mã Java; rà soát cách phân phối phần dữ liệu kết hợp.

Gate snapshot đã triển khai tại `tools/vocabulary/source_gate.py`, có kiểm tra
registry và unit tests trong cấu hình CI. ETL tương lai phải gọi `--source ID`;
chưa có production ETL để tích hợp. Kiểm tra role/hash không thay review nội dung
attribution và license từng record. Xem [validation](voc-data-01-validation.md).
Không có production ETL trong phạm vi task nghiên cứu này. Fixture hiện có
trong `tools/catalog` là dữ liệu tổng hợp và không chứng minh quyền nguồn ngoài.

## Attribution offline và bàn giao

Manifest đóng gói cần source ID, title, tác giả/nhóm đóng góp, source URL,
license code + URL + bản notice offline, snapshot ID/hash, mô tả chỉnh sửa;
thêm map từng record/field đến mục nguồn. Ví dụ credit cho NGSL:
“New General Service List — Browne, C.; Culligan, B.; Phillips, J. —
https://www.newgeneralservicelist.com/new-general-service-list — CC BY-SA 4.0.
Changes: selected lemmas and normalized ranks.” Chỉ ghi thay đổi thực sự.

Mỗi câu Tatoeba có credit riêng cho tác giả và URL
`https://tatoeba.org/en/sentences/show/{sentenceId}`; câu dịch có attribution
riêng. Kaikki cần credit Wiktionary contributors, URL bài nguồn/history và
Kaikki/Wiktextract theo route giấy phép đã xác minh. UI attribution phải đọc
được offline; không chỉ trỏ link web và không chỉ ghi “nguồn Internet”.

Thi/Nhân thu nhận snapshot, lưu evidence và checksum; Trường review ledger và
attribution trước khi bỏ blocker. Không ghi nhận review đã diễn ra.

Kết quả nghiên cứu: đủ sáu source records và quyết định; FVDP/audio bị loại.
Chưa hoàn tất acquisition, xác minh license Kaikki, attribution cấp record,
snapshot/hash, tích hợp vào ETL hay nghiệm thu AC-12 cấp sản phẩm. Các mục này
phải hoàn tất trước import/release; không nâng trạng thái thành Ready chỉ vì
có tài liệu này.
