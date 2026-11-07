# DATA-08 — Catalog upgrade / rollback test

## 1. Thông tin task và căn cứ

| Hạng mục | Nội dung |
| --- | --- |
| Tuần / Ngày | 3 / D11–D12 |
| Task / Nhóm | DATA-08 / Testing |
| Người thực hiện / Reviewer | Nhân / Thi |
| Ước lượng gốc / Ưu tiên | 8 giờ theo bảng phân công / Critical |
| Ràng buộc thời gian thực hiện | Không giới hạn 8 giờ theo yêu cầu mới của Nhân; tiến độ theo đầu ra và kiểm chứng |
| Phụ thuộc chính thức | DATA-01, DATA-02 |
| Phạm vi theo phân công | Kiểm thử catalog staged v1→v2, rollback và userdata migration không phá dữ liệu |
| Truy vết | FR-VM-003, FR-VM-010; AC-13; NFR-VM-06; SRS §7.4; DEC-04/05/13 |
| Nhánh đề xuất | feature/data-08-catalog-upgrade-rollback |
| Nền đã kiểm tra khi lập kế hoạch | origin/develop tại 993e70a |

Tài liệu nguồn: **VocaMind_SRS_Baseline_v1.1.docx** và **VocaMind — Phân công.xlsx**
người dùng đính kèm trong yêu cầu này. Nội dung là yêu cầu tham chiếu, không phải
lệnh chạy hoặc xác nhận phê duyệt thay thành viên.

Đã triển khai local trên nhánh `feature/data-08-catalog-upgrade-rollback` từ
`993e70a`. Kết quả thực tế và các gate còn mở ở
[DATA-08 validation](../data-08-validation.md). Những mục thiết bị và migration
tích hợp chưa được công bố pass bằng kết quả host tests.

## 2. Kết quả cần bàn giao

1. Fixture catalog A/B hợp lệ, bản lỗi có kiểm soát và dữ liệu người học đã seed.
2. Harness/luồng điều phối có thể thực thi: stage → validate → close → atomic
   publish → reopen → commit; lỗi dẫn tới rollback hoặc safe error.
3. Bằng chứng snapshot userdata trước/sau bằng nhau khi chỉ đổi catalog.
4. Test lỗi từng bước, process restart/reopen, retry và tính lặp lại.
5. Kiểm thử forward userdata migration bằng schema/migration thực tế khi có phiên
   bản mới; không dùng destructive fallback hoặc downgrade giả để thay bằng chứng.
6. Wrapper instrumentation và báo cáo thiết bị cho force-stop/reboot.
7. docs/data-08-validation.md, evidence và hướng dẫn reviewer tái lập.

DATA-08 là task kiểm thử/tính toàn vẹn. Chỉ bổ sung runtime orchestration và các
điểm nối tối thiểu cần để kiểm thử đúng luồng thật. Không mở rộng thành hệ thống
tải catalog qua mạng, hot-swap khi người học đang thao tác, cloud backup hoặc
framework migration tổng quát.

## 3. Hiện trạng đã xác minh trên Git

| Thành phần | Hiện trạng / tác động tới kế hoạch |
| --- | --- |
| CatalogDatabase | Room schema **2**, mở catalog.db qua createFromAsset |
| UserDatabase trên develop | Room schema **1**, không destructive fallback |
| CatalogProvider | Mỗi owner mở catalog; credits vẫn đọc từ AssetManager |
| RepositoryProvider | Giữ DAO catalog, mở userdata; Quiz policy đọc từ APK assets |
| PacksViewModelFactory | Có nhiều owner/lazy database; không thể giả định chỉ một catalog handle |
| Asset fixture của APK debug | 4 file: catalog.db, catalog.metadata.json, content-credits.json, quiz-policy.json |
| Bundle QA được build từ VOC-DATA-06 | Có thêm release-gate-report.json và manifest.json; app ID QA riêng |
| Userdata v2 trên nhánh QUIZ-03 | Có completionKey, MIGRATION_1_2 và unique index; chưa ở develop khi lập kế hoạch |

**Không đồng nhất phiên bản nội dung với schema.** A/B là hai thế hệ nội dung
kiểm thử; ví dụ contentVersion 1.0.0→1.0.1 nhưng cùng CatalogDatabase schema 2.
Không hạ database schema xuống 1 chỉ để đặt tên fixture “v1”. Nếu thử schema thay
đổi, phải có migration/schema tương thích thật và tách ca đó khỏi content update.

createFromAsset chỉ hỗ trợ lần khởi tạo phù hợp, không tự thay database đã sao
chép. Tăng version hoặc thay file trong APK không đủ đáp ứng DATA-08.

## 4. Quyết định thiết kế để giữ phạm vi hợp lý

### 4.1. Chỉ nâng cấp trước khi mở các consumer

Ưu tiên **startup-only upgrade trên worker**: chạy bootstrap trước khi phát DAO/
repository cho ViewModel. Trong một process, mọi owner CatalogProvider đi qua
cùng một startup coordinator/single-flight và cùng chọn một generation.

- Không thay catalog trong khi pack/search/review/Quiz đang chạy.
- Không đóng một handle rồi coi như mọi handle/executor đã đóng.
- Nếu đã có consumer hoạt động, hoãn nâng cấp tới lần khởi động kế tiếp; không
  ép đóng DB dưới truy vấn đang chạy hoặc đẩy Room exception ra UI.
- Atomic publication chỉ bắt đầu khi các handle validation/catalog liên quan đã
  đóng và không còn transaction/read đang dùng bản bị thay.
- Tích hợp tối thiểu tại core/CatalogProvider và RepositoryProvider; không đổi
  hợp đồng domain hoặc để Fragment trực tiếp điều khiển file/Room.

### 4.2. Catalog và companion metadata là một generation

Không thay riêng catalog.db rồi tiếp tục đọc credits/policy của APK khác phiên bản.
Thiết kế ưu tiên: mỗi generation là thư mục có danh tính cố định trong app-private storage,
chứa bundle gốc đã xác minh và bản SQLite runtime riêng. Bundle gồm DB prepack,
metadata, credits, policy và manifest phù hợp profile. Publish bằng **một
active-generation pointer** cập nhật nguyên tử, thay vì rename nhiều file độc lập.

- Ví dụ vị trí: noBackupFilesDir/catalog-generations/<generation-id>/; đây là đề
  xuất kỹ thuật, phải xác nhận Room mở DB tại đường dẫn nội bộ đó trên API26.
- Dùng android.util.AtomicFile hoặc primitive được chứng minh tương đương cho
  pointer; flush/sync dữ liệu staging trước publication. Không coi File.renameTo
  hoặc commit một SharedPreferences key là bằng chứng crash durability tự thân.
- Pointer chứa generation ID/digest, không nhận đường dẫn tùy ý từ input.
- DB và tất cả loader metadata phải dùng **cùng generation đã resolve**. Cần
  overload/source abstraction nhỏ cho ContentCredits/QuizPolicyAsset; giữ adapter
  AssetManager hiện có để tương thích tests/legacy bootstrap.
- Generation cũ giữ làm last-known-good. Không dọn nó ngay sau đổi pointer.
- Cấu trúc một generation: `bundle/` giữ nguyên byte đã xác minh, `runtime/` chứa
  bản DB cho Room mở. Room có thể ghi metadata/journal/WAL ngay cả khi nghiệp vụ
  chỉ đọc. Không mở bundle/catalog.db trực tiếp bằng Room rồi kỳ vọng hash file
  không đổi; validation Room dùng bản sao runtime/staging.
- Pointer tham chiếu digest manifest của **bundle**, không hash động của runtime DB.
  Hash bundle kiểm lại khi recovery; runtime DB kiểm bằng Room/integrity/FTS và
  logical probes. Nếu cần tái tạo runtime do hỏng, chỉ copy từ bundle đã xác minh,
  không bao giờ reset userdata.
- Nếu lựa chọn cách khác, Thi phải review POC atomicity cho toàn bundle trước khi
  viết integration test; không chấp nhận nhiều lần copy/rename rời rạc là atomic.

### 4.3. Legacy install và quyền phát hành

- Máy đã có database tại context.getDatabasePath("catalog.db"): nhận diện và xác
  minh bản đó; không tự bỏ nó để coi như first install.
- Legacy schema 1 phải được kiểm bằng schema/validator phù hợp phiên bản; không
  ép mở bằng Room schema 2 rồi dùng fallback để vượt lỗi. Nếu không xác minh được
  tương thích thì hoãn update, giữ nguyên bản legacy và userdata.
- Chỉ import/ghi nhận legacy generation khi DB và companion metadata có thể xác
  nhận tương thích. Nếu không xác minh được, giữ bản cũ và báo safe error/hoãn
  update; không đoán snapshot hoặc ghép metadata mới cho bản cũ.
- Fixture, QA nội bộ và production là profile riêng. Không dùng bundle QA
  releaseAuthorized=false để tự promote production hoặc trộn userdata giữa app IDs.
- Hash bảo đảm byte khớp bản dự kiến, không thay quyền/phê duyệt. Candidate phải
  đến từ asset/bundle tin cậy đã đóng gói; task không triển khai download runtime.

## 5. Quy trình nâng cấp và khôi phục

| Bước | Hành động và điều kiện |
| --- | --- |
| 1 · Discover | Resolve active/previous generation, profile, content/schema version và trạng thái recovery |
| 2 · Stage | Copy candidate vào thư mục riêng cùng filesystem; không sửa active hoặc userdata |
| 3 · Validate | Kiểm hash/manifest, Room/schema, SQLite/FTS, IDs, credits/policy và profile/license gate |
| 4 · Compatibility | Đối chiếu candidate với mọi stableId/packId đang được userdata tham chiếu |
| 5 · Close/flush | Đóng DB validation, xử lý WAL đúng cách, bảo đảm generation staging tự chứa đủ dữ liệu |
| 6 · Publish | Ghi recovery state đủ để xác định previous/candidate; đổi active pointer nguyên tử |
| 7 · Reopen/probe | Mở đúng generation mới bằng Room, thử pack/vocab/FTS/credits/Quiz policy |
| 8 · Commit | Chỉ công bố ready và tạo consumer sau khi probe đạt; giữ previous để recovery |
| 9 · Rollback | Nếu probe/commit lỗi, khôi phục pointer cũ, mở và probe lại; userdata không bị reset |

Recovery sau restart phải dựa trên trạng thái persisted/digest hợp lệ, không dựa
vào PID, biến static hoặc việc “còn file staging”. Các điểm dừng phải có hành vi
xác định: trước publish → dùng old; sau publish nhưng chưa xác nhận → verify new
rồi finalize hoặc rollback old. Nếu cả hai không xác minh được, báo safe error,
cho retry; không tự chọn bundle bất kỳ hoặc xóa userdata.

### 5.1. State persisted tối thiểu

Một AtomicFile record versioned lưu `activeGeneration`, `previousGeneration`,
`bundleManifestDigest` và `phase` (`PENDING` hoặc `COMMITTED`). Việc thiết kế field
cụ thể có thể đổi khi POC, nhưng không được dùng nhiều marker độc lập không có
recovery rule. Không cần thêm một hệ thống journal tổng quát bên cạnh AtomicFile.

1. Stage/probe trước publication: record cũ không đổi; staging dang dở không active.
2. Ghi record PENDING nguyên tử với candidate và previous; consumer vẫn bị chặn.
3. Reopen/probe candidate thành công thì ghi COMMITTED nguyên tử rồi mới phát
   dependency cho consumer. Không phát DAO trước khi hoàn tất bước này.
4. Restart gặp PENDING: verify candidate bundle + runtime + references để finalize,
   hoặc verify previous để rollback; cả hai lỗi thì safe error.
5. Exception trước khi đổi pointer giữ old; exception sau đổi pointer phải ghi lại
   pointer old hoặc giữ PENDING có thể recovery, không báo thành công giả.

AtomicFile giải quyết publication record, không tự khóa nhiều thread/process hoặc
đảm bảo toàn bundle đã xuống storage. Single-flight trong process và thứ tự flush/
sync/close cần test riêng. MVP hiện một process; không thêm multi-process update
nếu chưa có consumer đa process, nhưng lưu đủ state để process mới recovery.

“Atomic swap” ở đây là đổi **generation được chọn**, không sửa SQLite đang mở.
Không copy độc lập userdata.db trong khi nó còn WAL/transaction; dùng logical
snapshot hoặc cơ chế backup SQLite hợp lệ cho evidence.

## 6. Các kiểm tra candidate bắt buộc

- File set đúng profile, không file ngoài manifest/đường dẫn vượt thư mục; checksum
  khớp expected manifest. Bản fixture cũ không có release manifest cần adapter
  bootstrap rõ ràng, không tự coi nó là production-approved.
- user_version/schema và identity hash lấy từ schema Room compiler thực; **buộc
  query để Room mở và validate**, không chỉ tạo object builder thành công.
- SQLite integrity_check/foreign_key_check thành công.
- Mỗi VocabularyEntry có đúng mapping FTS bằng INTEGER rowid; không orphan/missing
  posting; thử MATCH Anh/Vi cho các từ fixture và đối chiếu stableId TEXT.
- Unique stableId, membership hợp lệ và thứ tự/nguồn phù hợp; credits sourceId +
  snapshotDate và Quiz policy tương ứng đúng nội dung/semantic keys.
- Schema version, content version, canonical digest và hash companion được kiểm
  riêng; không hard-code identity_hash minh họa.
- Xác nhận không còn dữ liệu phụ thuộc tệp -wal/-shm chưa được checkpoint/đóng.
  Không xóa WAL của DB đang mở để cố “dọn sạch”.

## 7. Bảo toàn userdata và stable references

Seed đủ **UserPack, UserWordProgress, ReviewEvent, QuizAttempt, UserPreference**.
Có pack active/paused, progress box0..5, saved-only/saved+learned, leech/mistakes,
review lặp, quiz history và preference đã sửa khác mặc định.

Snapshot canonical so sánh từng bảng/bản ghi/cột theo PK và giữ cả NULL/type;
không chỉ so COUNT, không dùng hash file userdata vật lý để thay logical equality.
Chặn consumer/writer trong cửa sổ test để thay đổi hợp lệ của người dùng không
làm sai phép đối chiếu. Không log dữ liệu người học thật vào report.

Tham chiếu phải kiểm bao gồm UserWordProgress.vocabularyId,
ReviewEvent.vocabularyId và UserPack.packId. Không chỉ kiểm các từ đang active.

- Giữ stableId khi sửa cách diễn đạt nghĩa/ví dụ; không dùng rowid làm ID tiến độ.
- Candidate xóa ID/pack đang được tham chiếu phải bị chặn hoặc giữ record tương
  thích trong candidate. **MVP ưu tiên chặn removal**; không tự thêm alias format
  chưa có contract/lookup để giả vờ đã hỗ trợ.
- Rollback ngay khi startup probe lỗi diễn ra trước khi cho user ghi dữ liệu mới.
- Nếu rollback sau khi đã học từ chỉ có trong generation mới, phải kiểm ngược mọi
  reference với generation cũ: thiếu ID thì từ chối rollback hoặc cần bản tương
  thích được review; tuyệt đối không xóa progress mới để làm rollback qua test.
- Không sửa/xóa userdata, không fallbackToDestructiveMigration ở bất kỳ nhánh lỗi.

## 8. Userdata migration: dùng phiên bản thật

Trên develop hiện chỉ có UserDatabase schema 1. Nhánh feature/quiz-03-ui-session
có schema 2 và MIGRATION_1_2 thêm QuizAttempt.completionKey nullable cùng unique
index. Đây là migration thật có thể làm mục tiêu kiểm chứng **sau khi nhánh/code
đích được Thi xác nhận tích hợp**.

Trước khi code phần này, chốt target HEAD/schema với Thi:

1. Nếu userdata v2 đã vào nền: dùng chính MIGRATION_1_2 đó, schema JSON 1/2 và
   MigrationTestHelper; seed dữ liệu v1 rồi mở bằng Room v2, đối chiếu mọi cột cũ,
   IDs/counts/timestamps/score/preferences; completionKey cũ phải NULL đúng thiết kế.
2. Nếu nền vẫn v1: vẫn làm được toàn bộ catalog-upgrade/userdata-preservation.
   Không tăng production schema chỉ để có test, không chép migration thứ hai lệch
   production và không dùng test downgrade 2→1 để chứng minh forward upgrade.
3. Có thể dùng fixture/test-only forward migration để POC công cụ, nhưng report
   phải gọi đúng là synthetic; nó **không đóng gate migration production**.

Catalog content rollback không rollback schema userdata đã nâng. Không thử mở
userdata v2 bằng app chỉ hiểu v1 rồi xóa dữ liệu khi lỗi. Thiếu migration/hash lỗi
phải fail an toàn, giữ nguyên DB và có evidence dữ liệu còn đọc được bằng schema đúng.

## 9. Test matrix

Các mã DATA08-Txx là mã đề xuất cho task, không phải test ID có sẵn trong SRS.

| Test | Fixture/hành động | Kết quả bắt buộc |
| --- | --- | --- |
| T01 | First install, không có userdata trước đó | Mở bundle hợp lệ; seed mặc định đúng DATA-02, không xóa DB khác |
| T02 | Install cũ, content A→B, schema catalog vẫn 2 | Nội dung/version đổi, logical snapshot userdata giữ nguyên |
| T03 | Lặp bootstrap cùng candidate/digest | No-op/idempotent; không copy lại, nhân dữ liệu hoặc reset prefs |
| T04 | Legacy catalog.db hiện hữu | Nhận diện/xác minh đúng, không coi như DB trắng |
| T05 | Hỏng/missing DB, hash, manifest, sidecar hoặc schema | Chặn trước publish; old vẫn dùng được; userdata không đổi |
| T06 | FTS mất row, orphan, sai rowid hoặc credits/policy lệch | Chặn candidate và giữ old; không trả dữ liệu pha trộn |
| T07 | Candidate bỏ từ/gói có reference | Chặn removal; hoặc kiểm rõ compatibility record đã được review |
| T08 | Inject IO/low-storage ở copy, flush, publish, reopen | Không có half-generation; rollback/old hoặc safe error đúng state |
| T09 | Process restart tại từng boundary persisted | Recovery chọn đúng verified generation; retry hoàn tất idempotent |
| T10 | Hai owner gọi startup đồng thời | Cùng generation, không mở DAO sớm, không publish hai lần |
| T11 | Candidate mới lỗi sau publish | Pointer quay về old, pack/vocab/FTS/credits/policy đồng nhất |
| T12 | User đã có reference mới rồi yêu cầu rollback | Không orphan: reject/compat, không xóa progress |
| T13 | Forward userdata v1→v2 thực tế | Mọi dữ liệu cũ giữ nguyên; Room schema/unique index đích hợp lệ |
| T14 | Thiếu migration/userdata hỏng | Safe error/retry; không xóa, không tự seed lại như thành công |
| T15 | Force-stop và reboot thiết bị sau upgrade/rollback | Active/paused, progress/review/quiz/prefs và generation đúng còn nguyên |
| T16 | Compare file set/profile và DB schema sau toàn bộ suite | Chỉ DB test bị tác động; không thay asset sản phẩm/approval thật |
| T17 | Lỗi upgrade tới boundary UI hiện có | Tầng data trả lỗi có cấu trúc, không lộ SQLite exception; UI cho retry, không reset prefs/progress |
| T18 | Room mở runtime copy, đóng/mở lại và kiểm bundle | Byte/hash bundle vẫn giữ nguyên; journal/runtime không bị tính nhầm thành candidate checksum lỗi |

Fault hooks là seam nội bộ/harness được inject trong test, không là nút reset DB
hoặc debug backdoor ở UI. Phân biệt exception injection với process death thật.
Nếu filesystem wrapper giả lỗi ENOSPC, không công bố đã test thiết bị đầy ổ đĩa.
Chạy có kiểm soát, không cố tình lấp đầy máy hoặc tác động dữ liệu thật.

## 10. Cấu trúc dự kiến cho lần triển khai

```text
app/src/main/java/com/vocamind/app/
├── data/catalog/upgrade/
│   ├── CatalogStartupCoordinator.java   # Orchestration, single-flight/recovery
│   ├── CatalogGenerationStore.java      # Staging, pointer, previous, file lock/state
│   ├── CatalogBundleValidator.java      # Hash/Room/FTS/metadata/profile
│   └── CatalogReferenceChecker.java     # Stable references, đọc userdata an toàn
├── data/catalog/                        # Source adapters/overloads tối thiểu nếu cần
└── core/                               # Nối bootstrap và cùng-generation loaders
```

Tên class là đề xuất thiết kế, không phải tên bắt buộc của SRS. Không tạo lớp
không cần dùng hoặc một framework generic chỉ để đủ sơ đồ. Đường dẫn/ownership
được chốt ở POC trước; domain và ViewModel không import file/Room operations.

Test dùng cấu trúc đã có: shared contract tại src/catalogTest/java, host wrapper
ở src/test và AndroidJUnit4 wrapper ở src/androidTest. Fixture A/B và sidecars nằm
trong test resources/assets hoặc generated test outputs, không thay catalog sản
phẩm để tạo ca lỗi. Evidence lưu docs/evidence/data-08; không commit APK/log lớn
hoặc dữ liệu người dùng thật.

## 11. Triển khai theo mốc, không ép vào 8 giờ

8 giờ là ước lượng ban đầu trong Excel, **không phải deadline hay giới hạn scope**.
Theo yêu cầu mới của Nhân, chỉ chuyển mốc khi đầu ra đạt, không cắt migration,
recovery hoặc evidence thiết bị để vừa giờ. Ước lượng lại sau POC nếu cần.

| Mốc | Công việc | Điều kiện chuyển tiếp |
| --- | --- | --- |
| M1 · Đối chiếu | Kiểm HEAD/schema/profile, các owner DB và migration target thật | Có danh sách dependency, loader và quyết định phạm vi; migration chưa sẵn sàng được ghi riêng |
| M2 · Fixture/POC | Dựng A/B, snapshot năm bảng; thử bundle/runtime path + AtomicFile trên API26 | Room mở đúng runtime copy; metadata cùng generation; không thay bundle hash |
| M3 · Luồng bình thường | Bootstrap single-flight, stage/validate/reference-check/publish/probe | Upgrade và no-op dùng code thật, userdata giữ nguyên, chưa có UI consumer chạy sớm |
| M4 · Failure/recovery | Inject từng bước, process restart, rollback và rollback có reference mới | Mỗi trạng thái có kết quả xác định; không half-generation hoặc mất userdata |
| M5 · Migration | Kiểm forward migration thật theo target đã chốt | Có schema/SQL/report và field-level comparison; synthetic POC không thay gate thật |
| M6 · Thiết bị | Force-stop/reboot, safe error/retry và offline reopen | Có inventory, commands, snapshot trước/sau trên thiết bị/emulator; phân biệt với host tests |
| M7 · Bàn giao | Full regression, lint, evidence và review Thi | Các gate còn thiếu ghi rõ; không tự đánh dấu nghiệm thu |

M2/M3 là điểm quyết định kiến trúc. Nếu các owner hiện tại không thể đi qua cùng
bootstrap, hoàn thiện đường ownership trước; không bù bằng sleep hoặc đóng DB
trong khi query còn chạy. UI mở rộng chỉ làm nếu cần cho safe error/retry; giao diện
nâng cấp riêng, downloader, automatic alias migration và hot-swap là ngoài scope.

Nếu migration target hoặc thiết bị chưa sẵn sàng, tiếp tục các mốc độc lập. Chỉ
đánh dấu phần đó blocked sau khi đã hoàn tất việc có thể kiểm chứng; không biến
blocked thành pass, cũng không lấy migration làm điều kiện chặn toàn bộ catalog POC.

## 12. Lệnh dự kiến và evidence

Dùng JDK/SDK và scripts của nhánh nền tại thời điểm triển khai. Tại root dự án:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

# Khi đã có class instrumentation tương ứng và thiết bị API26+ kết nối:
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.upgrade.CatalogUpgradeInstrumentedTest
```

Tên class trên là đề xuất, chỉ chạy sau khi được tạo. Windows dùng gradlew.bat.
Test profile QA theo package_qa.py/Gradle hiện có; không giả định default debug APK
đang chứa catalog 150 từ hoặc đã được phép promotion.

Báo cáo phải ghi commit/base, file hashes và versions A/B, schema JSON, tên DB test,
state injection từng bước, snapshot trước/sau, test counts passed/failed/skipped,
model/API thiết bị, actual process restart/reboot, lệnh và output thực. Lưu cả
kết quả fail đã chẩn đoán và giới hạn; không sửa report cho “xanh”.

## 13. Definition of Done

- [ ] Đã chốt target nội dung/schema/migration và gate profile với Thi.
- [x] Stage→validate→close→publish→reopen/rollback hoạt động trên bundle thật trong host tests.
- [x] DB, credits và Quiz policy cùng generation; không dùng DAO/asset cũ sau swap.
- [x] Userdata logical snapshot giữ nguyên qua catalog upgrade/rollback và retry.
- [x] Removed stableId/packId có tham chiếu bị chặn hoặc có compatibility thực.
- [x] Fault từng boundary, concurrent startup và persisted recovery mô phỏng có test/evidence.
- [ ] Forward userdata migration thật được kiểm chứng hoặc gate ghi rõ còn thiếu.
- [ ] Force-stop/reboot thiết bị có evidence; host reopen không được gọi là reboot.
- [x] Không destructive fallback/reset, không đổi quyền/source approval thật.
- [x] Build/regression/lint và các file sản phẩm không có diff ngoài phạm vi.
- [ ] Thi review; chỉ sau đó cập nhật trạng thái phân công phù hợp.

Chỉ đóng AC-13/NFR-VM-06 theo phần thực sự đã chứng minh. Thiếu migration target,
thiết bị, quyền profile hoặc bằng chứng recovery là phần còn mở, không phải pass.

## 14. Truy vết và kết quả rà soát plan

| Căn cứ | Phần plan đáp ứng | Evidence khi triển khai |
| --- | --- | --- |
| FR-VM-003, FR-VM-010 | §4/5/7, T02/T03/T04/T15 | Tái sử dụng install cũ, không reset và giữ dữ liệu sau restart/reboot |
| SRS §7.4, DEC-04, ADR-01 | §4–6, T05/T08/T09/T10/T11/T18 | Bundle đồng bộ, stage/validate/close/publish/rollback thật |
| DEC-05 | §6/7, T06/T07/T12 | FTS integer rowid, stable references không orphan |
| NFR-VM-06 | §7/8, T02/T07/T11/T13/T15 | Snapshot mọi cột năm bảng; forward migration đúng target |
| DEC-13 và cột FE của DATA-08 | §5, T08/T14/T17 | Safe error/retry có quan sát được, không xóa userdata |
| AC-13 và DoD Excel | §8/9/12/13 | Chỉ chốt sau khi có migration/recovery/reboot evidence và review |

**Kết quả rà soát:** phù hợp phạm vi Testing của DATA-08 và cấu trúc Java/Room/MVVM.
Các lớp runtime là thiết kế đề xuất để có luồng thật kiểm thử, không phải yêu cầu
bắt buộc từ SRS. Tối ưu theo mốc đầu ra, giữ một pointer/state record và tái sử
dụng loader/schema/tools hiện có; không xây framework upgrade tổng quát.

Các điểm phải kiểm lại trước khi code: HEAD/schema userdata thực tế, nơi tạo mọi
catalog handle, cách dựng companion metadata cho legacy install và quyền profile.
Plan không giả định các quyết định này đã được reviewer duyệt hoặc code đã chạy.

**Trạng thái tài liệu:** đã triển khai và kiểm chứng local phần catalog; migration
tích hợp, thiết bị và review Thi còn mở. Chưa commit/push.
