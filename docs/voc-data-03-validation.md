# VOC-DATA-03 validation and review handoff

**Cập nhật mới nhất: sửa R3 local trên head 187927d**, xem mục cuối tài liệu.
Các kết quả implementation commit, Gradle và checksum ở những mục ban đầu bên
dưới là evidence cũ, không phải kết quả chạy lại cho bản sửa R3.

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

## Sửa review R1/R2 trên local ngày 2026-11-03

Head kiểm tra lại: `6780e23534b57e7813e6e58b347f81a9332eb9c8`, đúng bản
review `6780e23`; working tree ban đầu sạch, không có commit mới hoặc thay đổi
của người khác cần ghép. Tại thời điểm kiểm thử, bản sửa chưa commit/push/PR.
Sau kiểm thử, người dùng cho phép commit và push; vẫn chưa tạo PR.
Các kết quả sau thuộc bản sửa local trên head này, không gán cho commit cũ.

- R1: tách provenance locator thành mapped artifact và record key bằng dấu `#`;
  key rỗng hoặc chỉ whitespace theo helper T1 bị reject `MISSING_PROVENANCE`,
  field cụ thể như `provenance[0].locator`, locator diagnostic là input file/line.
  Locator có key hợp lệ và dùng chung giữa nhiều record/component vẫn được nhận.
- R2: kiểm tra kiểu required fields và cấu trúc provenance trước dict lookup,
  set hoặc sort. `sourceId=[]`, `provenance[0].component=[]` và
  `provenance[0].sourceId=[]` thành record reject `INVALID_FIELD_TYPE` ở mapping,
  có field/locator/reason. Row locator sai kiểu hoặc blank dùng file/line dự phòng.
  Pipeline tiếp tục các dòng sau; một record có nhiều lỗi vẫn chỉ tính rejected
  một lần. Strict batch policy giữ nguyên: có reject thì không có canonical/manifest.

Đã thêm 5 regression test methods (có subtests), tái hiện lỗi trước sửa rồi pass
sau sửa. Có ca trộn ba record sai kiểu và một record hợp lệ: read=4, accepted=1,
rejected=3, năm diagnostic cho ba record, sourceFailures=[]; không có output thành
công. Ca sai kiểu ở dòng đầu và unsupported POS ở dòng sau cũng thu đủ cả hai.

Evidence mới tách khỏi `docs/evidence/voc-data-03/` cũ, lưu local trong
`build/vocabulary-ingestion/p2-review-20261103/` (ignored, chưa đưa vào Git).
Script chạy local: `build/verify-ingestion-p2.py`; summary.json ghi base head,
environment và SHA-256 LF của implementation/test để nhận diện bản chưa commit:

- ingest.py: `5f929f6edc84649fe5e51de7431e40e385f47bfa941faf1040bd7efac8ab51c9`
- test_ingest.py: `7a452ea1d9975c0164fd0847c4a75777056a0675872b2fd70058d80d363b6d72`

Môi trường thực chạy: Windows host, Ubuntu WSL2, Python 3.14.4, jsonschema 4.19.2.

| Kiểm tra mới | Kết quả |
| --- | --- |
| `python3 -m unittest discover -s tools/vocabulary -p 'test_*.py' -v` | 64 pass, gồm 18 ingestion tests, 0 fail/error/skip |
| `python3 -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v` | 39 pass, 0 fail/error/skip |
| `python3 -m unittest discover -s tools/catalog -p 'test_*.py' -v` | 5 pass, 0 fail/error/skip |
| `python3 tools/catalog/check_credits.py` | exit 0, consistency only |
| frozen inventory audit với registry thật | exit 0; eligibleSourceCount=0 |
| CLI fixture vào hai thư mục mới `cli-a`, `cli-b` trong thư mục evidence mới | exit 0 cả hai; so sánh từng byte của đúng 4 file, tất cả giống nhau |
| Validator development / release trên canonical mới | exit 0 / exit 1 đúng mong đợi |
| CLI từng 7 nguồn thật, cả import và dry-run | 14 lượt exit 1 tại gate, read=0, không canonical/manifest |
| `git diff --check` | pass |

SHA-256 mới, giống ở cả hai thư mục CLI:

| File | SHA-256 |
| --- | --- |
| canonical-catalog.json | `2628626d9cd4410a3241b2a27bf0cfa00720675b9f0d2d07c18bb6e0c3f7cfbe` |
| manifest.json | `7d6d679ed33086dfc97c5518ad1efb8e08e57396e2bddd17230c7325b676656a` |
| normalization.json | `37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570` |
| reject-report.json | `4f44a5f77f943804cf0536c35a4fdee46905dba6036f108fe0ab00bb58ea9930` |

Canonical fixture hợp lệ không đổi bytes; manifest đổi so với evidence cũ vì
hash implementation đã đổi. Không dùng manifest cũ làm evidence bản sửa.
Không có kiểm tra được yêu cầu nào bị bỏ hoặc bị chặn. Không chạy lại Android/
Gradle/instrumentation hay remote CI trong lần sửa tooling local này; kết quả cũ
không đại diện cho bản sửa mới. Source gate/checksum/approval binding, fixture
isolation, stableId, canonical contract, userdata và runtime Android không đổi.
Phạm vi vẫn **core pipeline + reviewed-jsonl-v1**; native adapters không được
bổ sung. Việc xác minh record key thực sự tồn tại/nội dung đúng vẫn cần QA nguồn;
nhập nguồn thật, contract acceptance và AC-12 vẫn **PENDING/BLOCKED**.
Không đánh dấu task Done chỉ vì tests pass.

## Sửa rereview R3 trên local ngày 2026-11-03

Review lại xác nhận R1/R2 đạt, phát hiện R3 ở việc dùng `text.splitlines()`.
Đã kiểm tra head thực tế `187927de048f28da78b37a5deb0e4efdfb7ef720` và working
tree sạch trước sửa. Tại thời điểm kiểm thử, bản sửa R3 chưa commit/push/PR.
Sau kiểm thử, người dùng cho phép commit và push; vẫn chưa tạo PR. Các kết quả
bên dưới thuộc bản sửa local, không gán cho commit 187927d hoặc evidence R1/R2 cũ.

Implementation chỉ tách JSONL theo LF, chấp nhận CRLF bằng whitespace của JSON
parser. U+0085/U+2028/U+2029 nguyên dạng trong JSON string được giữ để parser và
helper T1 xử lý. Một LF cuối không sinh thêm record; record cuối không có LF vẫn
được đọc. Dòng trống/whitespace thật bị INVALID_PARSE với physical file/line và
được tính rejected; pipeline tiếp tục các dòng còn lại, không xuất batch thành công.
File rỗng có zero records và không đạt canonical envelope.

Đã bổ sung 2 regression methods: sáu ca literal/escaped cho ba codepoint với
entries/registry sau chuẩn hóa bằng fixture gốc, counts=4/4/0; bốn ca LF/CRLF
có/không LF cuối; ba ca dòng trống ở giữa/cuối và whitespace-only, counts=5/4/1,
không canonical/manifest. Trước sửa implementation, ba ca literal tái hiện lỗi;
ba ca escaped pass. Sau sửa toàn bộ pass, R1/R2 regressions tiếp tục pass.

Evidence local mới: `build/vocabulary-ingestion/r3-review-20261103/`, script
`build/verify-ingestion-r3.py` (ignored, chưa đưa vào Git). Môi trường thực chạy:
Ubuntu WSL2 trên Windows, Python 3.14.4, jsonschema 4.19.2. summary.json ghi base
head và SHA-256 LF nhận diện implementation/test chưa commit:

- ingest.py: `1c3188c51886a545c878463bff182ae5abdfa02deb23011c9ef5b007d59ac627`
- test_ingest.py: `f6b90f93e6d3a98393cf6f717cbe6ba1234998b827bb5374fc931a5ab510c02e`

| Kiểm tra thực chạy mới | Kết quả |
| --- | --- |
| Vocabulary/source/ingestion suite | 66 pass, gồm 20 ingestion methods |
| Canonical contract suite | 39 pass |
| Catalog tooling suite | 5 pass |
| Tổng | 110 pass, 0 failure/error/skip |
| Credit consistency / frozen inventory audit | exit 0; eligibleSourceCount=0 |
| Hai lần CLI fixture vào hai thư mục sạch cli-a/cli-b | exit 0, so từng byte cả 4 file: giống nhau |
| Validator development / fixture release | exit 0 / exit 1 đúng mong đợi |
| Bảy nguồn thật, mỗi nguồn import và dry-run | 14 lượt chặn ở gate, read=0, không canonical/manifest |
| git diff --check | pass |

SHA-256 của cả hai lượt CLI: canonical
`2628626d9cd4410a3241b2a27bf0cfa00720675b9f0d2d07c18bb6e0c3f7cfbe`, manifest
`cc6202707691eee2b8892fb208dc62943e163bfacf89333c0e9dadbfb91f3b37`, normalization
`37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570`, reject report
`4f44a5f77f943804cf0536c35a4fdee46905dba6036f108fe0ab00bb58ea9930`.
Manifest mới khác các lượt trước vì tool hash thay đổi; không yêu cầu source hash
hay toàn bộ canonical bytes bằng nhau khi đổi raw bytes literal thành escaped.

Không chạy lại Android/Gradle/instrumentation hoặc remote CI cho bản sửa local.
Không thay gate/checksum/approval binding, stableId, canonical contract, raw/evidence,
userdata hoặc runtime Android. Phạm vi vẫn **core pipeline + reviewed-jsonl-v1**;
nhập nguồn thật, native adapter scope/contract acceptance và AC-12 vẫn pending.
Không tự đánh dấu task Done hoặc đề nghị approve/merge từ kết quả fixture.

## Follow-up và tích hợp develop — 2026-11-05

Mục này là evidence mới; các section ngày 03/11 phía trên là lịch sử, không thay
bằng kết quả mới. Working tree ban đầu sạch tại b0ba6a6. Đã fetch và merge develop
82d438c qua merge commit c35e480; conflict duy nhất docs/README.md được giải bằng
cách giữ cả liên kết ingestion và DAO foundation, không bỏ thay đổi develop.

Bổ sung ingestion_refs.py: kiểm tra record key tồn tại và nội dung từng component
trong artifact đã gate/checksum. Production bắt buộc referenceAdapter; fixture cũ
vẫn chạy structural mode để giữ regression lịch sử. Manifest ghi rõ mức kiểm tra.
Bổ sung readers NGSL CSV UTF-8, TSL CSV cp1252, Kaikki English JSONL từ edition Việt,
component/reviewed JSONL và Tatoeba linked-pair export JSONL. Đây là component readers
cho reviewed staging, không tự chọn sense, join bản dịch hoặc cấp identity. Tatoeba
upstream TSV extraction chưa triển khai; không tuyên bố đã import native dump thật.
Source gate, approval digest, fixture isolation và canonical contract không đổi.

Fixture verified/ là snapshot mới; raw fixture cũ, registry và baseline giữ nguyên.
CLI verify_ingestion.py chạy lại hai lượt và so byte đủ 4 file, không ghi đè output.
INGESTION.md mô tả format/key, reason, ví dụ, lệnh audit và phương án custody/baseline
cụ thể để review; không tự ghi nhận acceptance hoặc người phê duyệt.

Kiểm chứng thực chạy trên Python 3.14.4/jsonschema 4.19.2 trong Ubuntu WSL2:

- Vocabulary: 71 tests, gồm 25 ingestion methods; contract: 39; catalog: 5.
  Tổng 115 pass, không failure/error/skip. Regression production thiếu reference
  adapter được bổ sung sau lượt full suite và chạy lại cả 25 ingestion methods: pass.
- Hai CLI verified fixture: so từng byte cả 4 file giống nhau. Development validator
  exit 0; release exit 1 đúng mong đợi. Credit consistency và frozen inventory pass.
- Cả 7 nguồn thật, mỗi nguồn import và dry-run: 14 lần gate failure, read=0,
  không canonical/manifest. eligibleSourceCount=0, raw declared rows=4059 không phải
  số từ đủ điều kiện học hoặc release.
- Gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest: BUILD SUCCESSFUL;
  158 unit tests, 0 failure/error/skip, có chạy test task và compile sau merge.
  Build instrumentation APK không có nghĩa đã chạy device/emulator instrumentation.
- git diff --check pass; git ls-files -u rỗng sau giải conflict.

Evidence mới: docs/evidence/voc-data-03/followup-20261105/ chứa summary với SHA-256
LF code/test, môi trường, counts, source status và comparison.json. Log chi tiết local
ở build/vocabulary-ingestion/merge-followup-20261105/ và followup-proof-20261105/.
Evidence được tạo trước commit implementation, nhận diện bằng code hashes; không gán
kết quả cho merge commit chỉ chứa develop. Remote CI được kiểm tra riêng sau push.

SHA-256 cả hai lượt:

- canonical-catalog.json: 329091f45d888d94a64ad1f56a776a5b66497f70b220ae6fb9b86cc43ca9b671
- manifest.json: fd3ab8a6c01104b8a3d44d224024313bf125ce4fa3c0c1b0834062f767057e98
- normalization.json: 37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570
- reject-report.json: 4f44a5f77f943804cf0536c35a4fdee46905dba6036f108fe0ab00bb58ea9930

Giới hạn còn lại: production staging phải là data artifact được phê duyệt, các nguồn
hiện hold/exclude vẫn chặn; cần raw corpus/metadata/links và sign-off theo từng nguồn.
Kiểm tra key/content chứng minh khớp bytes, không chứng minh dịch đúng hoặc license
hợp lệ ngoài hồ sơ gate. Contract acceptance, custody và baseline phát hành còn chờ
quyết định; semantic QA thuộc VOC-DATA-04, asset build thuộc VOC-DATA-06. Import nguồn
thật và AC-12 vẫn pending. Không đánh dấu task Done hoặc tự merge PR.
