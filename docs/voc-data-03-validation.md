# VOC-DATA-03 validation and review handoff

Ngày thực chạy: 2026-11-03. Phụ trách theo task: Trường; reviewer: Nhân.
Trạng thái: implementation candidate sẵn sàng kiểm tra, **không đánh dấu Hoàn thành**.
Chưa tạo PR theo yêu cầu người dùng dừng trước PR. Không sửa SRS/phân công.

## Code và môi trường

- Working tree ban đầu sạch; nhánh cũ feature/voc-data-02-contract-v1 được giữ nguyên.
- Fetch origin/develop thực tế; base: `c2ea69ec7d1589653660eab9164e70285faff666`.
- Nhánh: `feature/voc-data-03-ingestion-pipeline`.
- Implementation đã chạy: `d52544b717b5e0b922e5d257c5cf03fc948ab3ab`.
  Commit handoff tiếp theo chỉ thêm tài liệu/evidence và Git LF attributes;
  không sửa implementation đã kiểm thử. Manifest giữ hash code/schema/gate thực tế.
- Windows host, Ubuntu WSL2 Linux 6.18.33.2, Python 3.14.4,
  jsonschema 4.19.2; chi tiết trong [summary](evidence/voc-data-03/summary.json).
- Gradle chạy trên Windows với Temurin JDK 21.0.12.1; CI dùng JDK 17/Python 3.13.
  Chưa khẳng định đã chạy local trên đúng hai version của CI.

Đã đọc yêu cầu VOC-DATA-03 đính kèm, SRS v1.1 (BR-01/12, §7.3,
AC-12/DEC-09), VOC-DATA-01 ledger/handoff/registry/inventory, gate và tests,
VOC-DATA-02 contract/schema/validator/vectors/REVIEW và ranh giới catalog builder.
Không có AGENTS.md tại repository hoặc thư mục cha trực tiếp khi kiểm tra.

## Phạm vi đã triển khai

Core local: frozen inventory + authorization + corpus checksum → enriched JSONL
→ normalization helpers → registry lookup → component provenance → reject report
→ canonical validator/baseline comparison → deterministic intermediate/manifest.
Adapter `reviewed-jsonl-v1` UTF-8 là format staging đã biên soạn đủ field,
không phải native NGSL/TSL/Kaikki/Tatoeba adapter. Không cấp senseIndex mới,
không tạo nghĩa/ví dụ, không ghi catalog.db hoặc app assets.

[CLI/config/input/output và ví dụ chạy](../tools/vocabulary/INGESTION.md).
Fixture có 4 entries, 4 packs, 16 memberships, 2 nguồn synthetic có credit riêng;
IPA null, giữ nguyên identity registry. Production metadata lấy từ gate và artifact
attribution/approval. Synthetic approval chỉ nằm trong test tạm, không ghi registry thật.

## Kiểm thử thực chạy

| Lệnh từ repository root | Kết quả |
| --- | --- |
| `python3 -m unittest discover -s tools/vocabulary -p 'test_*.py' -v` | 59 pass, 0 fail/skip; gồm 13 ingestion tests |
| `python3 -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v` | 39 pass, 0 fail/skip |
| `python3 -m unittest discover -s tools/catalog -p 'test_*.py' -v` | 5 pass, 0 fail/skip |
| `python3 tools/catalog/check_credits.py` | exit 0, consistency only |
| `python3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json --json` | exit 0, inventory hợp lệ; eligibleSourceCount=0 |
| `gradlew.bat testDebugUnitTest assembleDebug --console=plain` | BUILD SUCCESSFUL, unit test task thực chạy; 125 tests, 0 failure/error/skip từ JUnit XML |
| validator development trên output pipeline | exit 0 |
| validator `--release` trên output fixture | exit 1 như mong đợi |
| Pipeline CLI với registry/inventory thật cho từng 7 source | exit 1; read=0, không có canonical/manifest thành công |

Log Python lưu tại [evidence](evidence/voc-data-03/); JUnit XML/HTML Android
nằm tại `app/build/test-results/testDebugUnitTest` / `app/build/reports/tests`.
Không tính NO-SOURCE là bằng chứng unit test. Không chạy instrumentation/device,
lint hoặc release build trong task này. CI discovery sẵn có nhận test_ingest.py
và đã cài jsonschema; không thêm runtime Android dependency.

Ma trận ING-01..14 nằm trong test_ingest.py: offline integration, same-input
reproducibility, reorder invariance của entries/identity, hash/missing artifacts,
gate-before-parse/dry-run, parse/encoding/POS/field reasons, multisource credit,
wording giữ ID, duplicate collision/retired history/baseline drift-removal,
normalization, final envelope failure, simulated publish I/O failure, path protection
và release fixture rejection. Full ID/meaning vectors và new-sense identity cases
chạy thêm trong suite contract. Chưa kiểm thử ingestion native nguồn thật.

## Hai lượt chạy ổn định

Hai lần CLI độc lập, cùng config và input nguyên byte, exit 0:
`build/vocabulary-ingestion/review-a` và `build/vocabulary-ingestion/review-b`.
Đã so sánh **bytes của cả bốn file**, không chỉ logical JSON.

| Output | SHA-256 (giống ở cả hai lần) |
| --- | --- |
| canonical-catalog.json | `2628626d9cd4410a3241b2a27bf0cfa00720675b9f0d2d07c18bb6e0c3f7cfbe` |
| manifest.json | `0c45b086163bd22a074560531cb34b601ebdf2fa4a8ec2b730743026e86f00cd` |
| normalization.json | `37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570` |
| reject-report.json | `4f44a5f77f943804cf0536c35a4fdee46905dba6036f108fe0ab00bb58ea9930` |

Bản sao output fixture trong [fixture-output](evidence/voc-data-03/fixture-output/).
Đây là evidence mới, không phải baseline phát hành. baselineCompared=false.
Manifest ghi checksum tất cả input và tool; không có thời gian/random/absolute
output directory trong payload. Môi trường khác có manifest khác vì version
Python/Unicode được ghi rõ; không tuyên bố byte identity giữa version khác nhau.

## Nguồn thật và reject report

| Nguồn | Trạng thái / blocker |
| --- | --- |
| NGSL / TSL | hold; có 2809/1250 raw rows nhưng thiếu PO/Content Lead sign-off và technical review; wordlist thiếu meaning/examples |
| Kaikki viwiktionary | hold; thiếu corpus snapshot, exact license route/exceptions và record attribution |
| Tatoeba text | hold; thiếu text snapshot, EN/VI authors/licenses/direct links và sign-off |
| FVDP / Tatoeba audio | exclude theo phạm vi MVP |
| Commons audio | hold, chỉ research metadata; không có route import media |

Audit raw rows=4059 không phải 4059 vocabulary đã QA. Từng pipeline CLI bị chặn
ở stage gate trước record parse; [blocked-ngsl report](evidence/voc-data-03/blocked-ngsl/reject-report.json)
và các thư mục blocked-* giữ diagnostic tách source failures khỏi record rejects.
Report batch thành công có read=4/accepted=4/rejected=0. Record rejection chỉ được
chạy trên fixture/test tạm. Chưa có reject report corpus thật vì gate chưa cho đọc.

## Quyết định cụ thể chờ Nhân và handoff downstream

1. Xác nhận dùng nguyên contract candidate v1.0.0 cho output pipeline và scope
   adapter ưu tiên: core + reviewed JSONL trước; native Kaikki/Tatoeba/wordlist
   chờ snapshot được duyệt. Không tuyên bố đã chốt thay Nhân.
2. Xác nhận người giữ registry và nơi lưu published canonical baseline.
   Đề xuất ETL/content cấp index bằng review registry riêng, contract owner review,
   integration owner lưu baseline/hash. Pipeline chỉ nhận registry, reject sense mới.
3. Xác nhận ING-03: không phụ thuộc thứ tự enumerate cùng input bytes; nếu **sửa
   raw bytes** để đảo dòng thì entries/identity/order vẫn giống, nhưng checksum
   source phải đổi và cần canonical sourceId mới khi so published baseline.
   Không giữ hash sai để đạt byte equality. Đây là giới hạn giữa yêu cầu reorder
   và contract snapshot identity; chưa được Nhân xác nhận.
4. Xác nhận route staging thực: enriched JSONL phải là approved data artifact;
   quyền wordlist không cấp quyền nghĩa/ví dụ. Native adapters sau này cần metadata
   sense, edition/extraction, author/license của cả EN/VI và artifact binding rõ.

VOC-DATA-04 nhận canonical/registry/provenance/reject reports để QA single-sense,
nghĩa/ví dụ và attribution. VOC-DATA-06 chỉ build catalog sau nguồn thật được duyệt,
baseline/version và release gates; fixture không được thay app asset.
Import thật, AC-12, content QA, 150 từ/4 gói và cross-team sign-off vẫn **BLOCKED/PENDING**.
Không tự đánh dấu task Done hoặc sửa quyết định nguồn/SRS.

PR chưa tạo theo chỉ dẫn dừng để người dùng kiểm tra trước PR. Kết quả CI remote
và head bàn giao sẽ được báo riêng sau push; local pass không thay remote CI.
