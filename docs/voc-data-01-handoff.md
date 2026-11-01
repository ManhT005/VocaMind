# VOC-DATA-01 — Bàn giao theo plan mới

Căn cứ: [plan người dùng cung cấp](plans/voc-data-01-source-ledger-and-handoff.md),
ngày 2026-10-25. Bản plan được chép nguyên văn để review; các mô tả remote branch
trong đó là quan sát của tài liệu, chưa được kiểm tra lại ở lần triển khai này.

## Những thay đổi đã thực hiện

| Yêu cầu của plan | Kết quả |
| --- | --- |
| Candidate không đồng nghĩa include | Bốn nguồn text `researchStatus=candidate`, `decision=hold`; gate không cho import |
| Giữ FVDP/Tatoeba audio exclude | Exclusion độc lập cờ registry; không có route fetch cho hai nguồn này |
| Commons audio research only | Source thứ bảy, hold; CLI chỉ hỏi API metadata cho một File title, không tải media |
| Source registry và locator | Giữ ID `ngsl`/`tsl` đã tồn tại, hỗ trợ alias `ngsl-12`/`tsl-12`; không đổi provenance cũ |
| Frozen snapshot | `docs/data/snapshot.json` gắn semantic digest registry và inventory artifact; kiểm tra hash/file trước handoff |
| Acquisition có thể kiểm chứng | Capture CLI mặc định dry-run, chỉ tải khi `--execute`; test dùng response tổng hợp, không gọi mạng |
| Không tự nghiệm thu | Nguồn thật đều bị chặn; phiếu phê duyệt vẫn pending |

Không tải thêm live corpus hoặc media ở lần này. Raw NGSL/TSL và evidence từ lần
trước được giữ nguyên byte; manifest mới chỉ kiểm kê chúng, không cấp quyền.
Metadata Commons chỉ được kiểm thử bằng response tổng hợp; chưa hỏi API thực.
Các source ghi hold có thể tiếp tục nghiên cứu evidence, không đi vào ingestion.

## Thao tác bàn giao

Chạy từ repository root (Linux dùng `python3` thay `py -3`):

```powershell
py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json
py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json --source ngsl-12
py -3 tools/vocabulary/capture_sources.py --source ngsl
py -3 tools/vocabulary/commons_audio_metadata.py --title 'File:Example.ogg'
```

Lệnh thứ nhất chỉ kiểm tra inventory, exit 0. Lệnh thứ hai exit 1 vì hold,
không cho downstream đọc dữ liệu để import. Hai lệnh sau chỉ in kế hoạch.
Nếu thực sự cần thu nhận sau này, thêm `--execute`; capture có `--id` riêng,
metadata có `--output` riêng. Không ghi đè bằng chứng cũ. Capture mới xuất
`snapshot.json` với URL, UTC, bytes và SHA-256; bất kỳ lỗi nào phải xử lý trước
khi đăng ký. Không dùng endpoint export index làm checksum cho corpus.

Khi registry thay đổi sau review, tạo inventory **phiên bản mới**:

```powershell
py -3 tools/vocabulary/gate_sources.py --freeze docs/data/snapshot-next.json
```

Không ghi đè snapshot.json hiện có. Sau review, cập nhật đường dẫn inventory
trong CI và tài liệu; audit sẽ chặn manifest không còn khớp registry.

## Provenance phải chuyển tiếp

Ánh xạ tên trong contract của plan sang registry hiện có: `sourceId → id`,
`officialUrl → url`, `evidenceUrl → evidence[].url`, `attributionText →`
notice role `attribution` và `obligations`; `redistribution` là trạng thái quyền
được khai báo riêng. Artifact dùng `path` làm local filename, `bytes` làm byteSize,
`retrievedAt` làm download UTC, `sha256` làm SHA256. Không tạo hai registry cạnh tranh.

| Component | Locator cần giữ ở VOC-DATA-03 |
| --- | --- |
| NGSL/TSL selection | Source ID, snapshot ID, SHA-256 CSV, lemma và rank; không dùng row index làm sense ID |
| ViWiktionary meaning/IPA | Edition viwiktionary, dump/extraction version, page URL/history, sense candidate và field nguồn |
| Tatoeba EN / VI | Sentence ID, username/author, actual license/CC0 evidence của từng câu, direct link/pair metadata |
| Commons audio metadata | File description page, version/timestamp, per-file artist/license, nguyên response và hash; luôn hold |

Metadata có thể chứa HTML từ bên ngoài; giữ như evidence không render trực tiếp
vào ứng dụng. `LicenseShortName` hoặc uploader không tự chứng minh quyền/author.
API và chính sách: [MediaWiki imageinfo](https://www.mediawiki.org/wiki/API:Imageinfo),
[Commons Licensing](https://commons.wikimedia.org/wiki/Commons:Licensing).

## Ranh giới và nghiệm thu

VOC-DATA-02 chốt canonical contract/single sense/identity registry; không merge
branch khác hoặc tạo AudioFile entity trong task này. VOC-DATA-03 xây adapters
và gọi gate trước import; Kaikki/Tatoeba chưa có corpus snapshot được duyệt nên
chưa thể công bố adapters hoặc ingestion đã chạy. VOC-DATA-04 QA nội dung và
license cấp record. VOC-DATA-06 chỉ đóng gói sau contract release validation,
150+ stableId hợp lệ, quota 40/40/40/30, offline attribution, schema Room và QA.

Task chưa Done chỉ vì tests pass. Data/Content Lead cùng Tech Lead cần chấp nhận
quyết định include/exclude có evidence; release source approval phải đáp ứng
PO/Content Lead theo baseline. Hiện chưa có chữ ký hay accepted include thật.
