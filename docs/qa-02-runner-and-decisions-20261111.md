> Publication timeline: User authorized commit/push after the local review. Statements below about uncommitted work describe the testing phase; source hashes bind that tested diff. Publication does not imply new APK/E2E results or approval.

# QA-02 — runner và hồ sơ quyết định hiện tại

Baseline source của lượt sửa: `1f1defb6fc5f76ff46a39c786e7b5363e43031d5`, remote trùng local, working tree sạch khi bắt đầu. Thay đổi hiện tại **chưa commit/push**. Không sửa SRS, Java/resources/manifest/build profile, không gửi yêu cầu phê duyệt.

Đây là trạng thái hiện tại cho runner/quyền/privacy/sign-off. [Matrix](qa-02-requirement-test-matrix.csv) bổ sung `record_role`, `technical_result`, `approval_gate`: các dòng lịch sử giữ kết quả của đúng run; `status` cũ không phải chữ ký nghiệm thu. [Review thiết bị trước](qa-02-review-5713ba6-20261111.md) vẫn là bằng chứng app source5713ba6, không được gán thành một lượt app test cho diff runner mới.

## Runner — FIXED, HOST RETEST PASS

`run_qa02_device.py` bỏ preflight đòi modern airplane command. Managed mode dùng shared controller với fallback chỉ cho phản hồi unsupported rõ ràng trên API26/27; không bỏ kiểm effective network. Chế độ `--network-mode manual` không gọi controller, ghi settings hoặc svc: người thao tác đặt offline trước, runner kiểm đủ airplane1/WiFi0/mobile0, Wi-Fi effective disabled và duy nhất default network none trước mỗi phase. Không đọc được/không chứng minh được thì BLOCKED, exit2; không chạy instrumentation để giả PASS.

Manual mode chỉ **read-only với mạng**, không có nghĩa cả runner read-only. Ngoài `--preflight-only`, runner vẫn install APK QA và reset **đúng package** `com.vocamind.app.qa02validation.qa` theo policy mặc định. Hai APK được kiểm applicationId trước; không thao tác package gốc. Reset thất bại dừng, không tự xóa bằng run-as/uninstall. `--fresh-data new-install` yêu cầu package vắng trước install; ghi provenance riêng, native journey vẫn phải kiểm defaults trước setup quota10. Package mới có thể chịu system restore; absence không thay assertion fresh/defaults.

Manual mode không tự thay mạng sau reboot. Nếu mạng không còn offline thì probe bị chặn. Original state là entry state **sau thao tác thủ công**, không phải trạng thái trước khi người dùng tự bật máy bay. Cuối scope runner đối chiếu flags/effective state với entry, ghi `mutatedByRunner=false`; nếu khác thì báo cần operator restore. Người dùng khôi phục sở thích trước manual sau khi chạy. Managed mode giữ snapshot/restore bounded hiện có, kể cả failure. Không đổi tài khoản/backup.

Phân loại dùng qemu/hardware và ADB transport evidence. Emulator marker → EMULATOR. PHYSICAL chỉ khi USB transport, qemu explicit0 và hardware khác emulator; thiếu bằng chứng → UNKNOWN. Model hoặc serial đơn lẻ không chứng minh máy thật; phân loại không cấp quyền reference/acceptance.

Ví dụ kiểm preflight không install/reset/test/reboot, sau khi tự đặt offline:

```powershell
python tools/validation/run_qa02_device.py --adb <adb.exe> --serial <selected-serial> --aapt <aapt.exe> --output build/qa02/new-preflight --network-mode manual --preflight-only
```

Full run chỉ khi được phép làm mới QA sandbox; bỏ `--preflight-only`, chọn reset hoặc new-install đúng provenance. Không chạy lại full journey trong lượt này.

Regression host: **13 QA02 +27 shared UI02 +8 CI device =48 PASS,0 fail/error/skip**. Bao gồm CLI fake-ADB API26 managed/manual, unsupported modern controller, active/ambiguous/unreadable default, effective Wi-Fi/flags, negative no-launch, không install/reset ở preflight, manual không mutation/không tự restore, classification thiếu/mâu thuẫn evidence, fresh install/reset denial và existing skip/crash rejection. Không có smoke trên Android thật trong lượt này; không truy cập Realme đã bàn giao. Đây là host runner proof, không phải E2E mới. R02 được FIXED_HOST_RETESTED; native smoke/CI của diff chưa chạy, không gọi CI5713 là CI của runner mới.

Evidence local: `build/qa02/runner-governance-1f1defb/` chứa commands/exit, raw test logs, source/input hashes và snapshot binding. `regression-qa02.log`, `regression-ui02.log`, `regression-ci-device.log`; `source.json` xác nhận app diff rỗng. `rights-audit` exit0; `release-gate` exit1 là gate HOLD có chủ đích, không phải test regression fail. [Evidence index](evidence/qa-02/runner-governance-1f1defb/index.json).

## Snapshot và quyền — PENDING PUBLIC DECISION

| Đối tượng | Identity và căn cứ |
|---|---|
| Accepted-v1/release target hiện lưu | Dataset `c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8`; canonical file `f27e9d2de700fadf13b480df26933c61333ce6261ed872d285e5a111a88110c1`. Không tự promote. |
| QA đã chạy source5713 | `editorial-qa-v2`,1.0.1; dataset `12aac7bfbe037c9136c3973cc724eee68205b88673ad25f40f7881de50d5398e`; canonical file `1a4b3da8396834868b82955a1e03384e6263876302a82774e6677f786e27b60d`; manifest `b80099721f565e579cea83de92a16a55c076e9f3347e12a280f1a5b7e9104ed8`. |
| Catalog thực trong QA/DATA09 APK | DB `41bcfacfcebdc42c8059c95f396c738fdabab62c4cbd8a255499e9c446502709`. Metadata dataset/canonical khớp kết quả compile read-only. |
| QA app/test APK | `970216a5fcdd53b9e95dce97205c5e745001f06fd8e91de9d6593f8ea33fbfe0` / `e0b80ea076b8c568eeec7479b3d010c790a2e73a6aaeb6a712dc94d560e03ca5`. QA150,4packs40/40/40/30. |
| DATA09 app/test APK | `d2337d288c6e5283b3cfc9c45234baa289eaa91bec6ee1f9a02bca4d718416c7` / `372860e1e7506db807342116b33c971cef2c79ed4ec995aea7e1f0ee8b2be280`. App chứa QA150; FTS test fixture5000 `5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`. |

Read-only compile đối chiếu toàn bộ frozen-v1 byte hashes với `baselineHashes` QA manifest;150 entries equal sau khi bỏ riêng contentVersion. QA đổi version,4 pack descriptions và locator/registry metadata theo `catalog_snapshot.py`. Vì vậy dataset/canonical/DB hashes khác loại không tự chứng minh nội dung khác hoặc đã được quyền public. QA descriptions có `packDescriptionReview=pending`. Kết quả mới tại `snapshot-binding.json`; không xuất corpus khỏi local.

| Phần quyền | Có bằng chứng | Còn cần quyết định |
|---|---|---|
| Nội bộ project | [Owner acceptance](data/voc-data-04/owner-acceptance.json), reviewer thanhcodeai, ghi nhận05/11/2026; [QA packaging decision/handoff](voc-data-06-qa-validation.md) ghi đường hướng07/11/2026 | Căn cứ internal QA; không phải public grant hay chữ ký Nhân. |
|135 NGSL references +15 no-reference | [Historical audit](licensing/voc-data-06/historical-provenance-audit.json), nguồn/hash NGSL `2098bab8955a120a9766c6282a51d7d578c6cb0a7d946600d2ffb73ba25a0b44`; audit exit0 | Content Lead/PO xác định phần nào history-only hay redistributed/adapted; duyệt attribution/change notice và nghĩa vụ tương ứng. Không tự đưa ý kiến pháp lý thành approval. |
|150 meanings/150 bilingual pairs/editorial lexical | Accepted owner instruction và component provenance | Xác nhận quyền/chủ thể chịu trách nhiệm, căn cứ AI/editorial và terms public nếu cấp; không bịa tác giả hoặc license. |
|4 mô tả pack mới và promotion | QA manifest/code chuyển đổi có hash | Content Lead duyệt wording và mapping accepted-v1 → đúng QA v2/accepted release snapshot mới. Không sửa accepted-v1. |
| Kênh | Internal QA được hồ sơ project ghi nhận | GitHub/public repository, ZIP, APK phân phối, store: chưa có quyết định public hợp lệ được tìm thấy. ZIP/store không nằm trong tập channel verifier hiện tại; cần quyết định riêng và tooling phù hợp, không tự map. |

[Release decision](licensing/voc-data-06/release-rights-decision.json) vẫn HOLD, releaseAuthorized=false, approvedBy/At/Snapshot=null. Checklist có chuỗi `Content Lead approval/date: APPROVAL- 07/11/2026` nhưng không có tên/quyết định ký/binding; không coi chuỗi mơ hồ này là chữ ký hợp lệ. Không đổi record quyền trong lượt này. Content verifier yêu cầu PO/Content Lead, exact snapshot+manifest+dataset, signedDecision/reviewedNotice hashes, component/sourceCredits và channels. Passing audit hay verifier cấu trúc không tự chứng minh thẩm quyền người ký. Xem [phiếu quyết định](qa-02-approval-request.md).

## Privacy/backup — PENDING POLICY CONFIRMATION

SRS NFR-VM-15: “Không có yêu cầu gửi hoặc lưu kết quả học sang dịch vụ của dự án; dữ liệu thuộc vùng nhớ cục bộ ứng dụng.” Nhãn yêu cầu “Không lưu ngoài thiết bị”; verification kiểm quyền mạng/lưu trữ. SRS SHA `bc2d687bb1a70f28fe0e4566f77d07dc877d08269a6265c361a06fb816c0c7ec`. Không sửa hoặc tự diễn giải thành cấm backup mọi trường hợp, cũng không tự cho ngoại lệ thay PO.

| Đường dữ liệu | Rà soát hiện có | Backup implication |
|---|---|---|
| `databases/userdata.db` Room | UserPack,UserWordProgress,ReviewEvent,QuizAttempt,UserPreference: gói active/saved/tiến độ/lượt ôn/điểm lịch sử/quota. UserDatabase.NAME +5entities | Eligible theo mặc định hệ thống; chưa có rule loại dữ liệu học. Có thể đi qua transport ngoài project service. |
| Catalog generations | `CatalogStartupCoordinator` dùng `getNoBackupFilesDir()/catalog-generations` | Thuộc no-backup directory; không suy ra userdata cùng được loại. APK assets khác với user backup. |
| Legacy catalog DB;files/shared_prefs nếu có | Không custom fullBackupContent/dataExtractionRules/BackupAgent trong source manifest; current APK audit cùng kết quả | Những file thuộc vùng mặc định có thể eligible; không khẳng định tất cả file đều tồn tại hoặc thực sự được upload. QA-only files/qa02 snapshots nếu còn trên QA install cũng thuộc files thông thường, không phải release feature. |
| App transport | APK actual không INTERNET, không project backend/account; dữ liệu Room app-private | Chứng minh không có app network permission, không loại được system backup/transfer. |
| Quan sát thiết bị đã lưu | API30 BackupManager disabled; Realme enabled, Google transport selected; local/D2D transports có trong inventory | Không trigger/observe actual backup/transfer, không kiểm account/cloud contents, không đổi thiết lập. Không khẳng định dữ liệu đã upload hay không bao giờ ra ngoài. |

Theo [Android Auto Backup](https://developer.android.com/identity/data/autobackup), database/files/preferences mặc định có thể backup; cache/code-cache/no-backup bị loại. Applicability cloud/device transfer còn phụ thuộc OS/OEM/transport và user settings. Đây là đánh giá eligibility, không phải chứng nhận actual transfer hay migration test.

Giữ lựa chọn người dùng **giữ Android backup**. Kết luận kỹ thuật: storage cục bộ/project-service path có căn cứ; bảo đảm “luôn chỉ trên thiết bị” chưa được chứng minh. PO/người phụ trách privacy phải xác nhận phạm vi NFR15 đối với system backup và thông báo/consent cần có. Nếu không chấp thuận, giữ gate BLOCKED và lập thay đổi riêng; không tự đổi manifest hoặc tuyên bố ngoại lệ được duyệt. F03 chuyển PENDING_POLICY_CONFIRMATION, không biến thành lỗi SRS đã chứng minh hoặc privacy PASS.

## WEEK-2 và sign-off

Đã tìm thấy [UI01 validation](ui-01-validation.md) tại commit `aba854d8b91644dea7d4c4d3a1316c3458c04dc8`: ghi xác nhận trực tiếp của Thành rằng các task tuần2 đã done, **scope dependency**. QA02 validation cũng ghi dependency này. Vì vậy trạng thái hiện tại là RECORDED_DEPENDENCY_CONFIRMATION, không tiếp tục nói không tìm thấy căn cứ. Nó không bind QA v2 distribution, APK tested snapshot hoặc reviewer acceptance; các gate đó độc lập. Không tạo ngày/ký mới và không yêu cầu lặp lại quyết định dependency đã ghi.

| Người có thẩm quyền | Hồ sơ sẵn sàng review | Quyết định còn thiếu |
|---|---|---|
| PO + Content Lead/rights-authorized owner | Snapshot binding, LIC01..12, owner acceptance, notices/ledger, tested APK hashes ở trên | Chọn snapshot release, duyệt pack descriptions/component rights/credits; chỉ rõ từng channel và signed artifacts. B03 PENDING. |
| PO/người phụ trách privacy | NFR15 nguyên văn, userdata eligibility, manifest/APK và observed transport | Xác nhận system backup có nằm trong phạm vi cho phép; quyết định notice/consent và trách nhiệm. F03 PENDING; user giữ backup không thay quyết định này. |
| Nhân | Matrix kỹ thuật,48 host tests, prior app tests/raw paths, defects và giới hạn giữ lại | Review R02 diff/CI-device-smoke limits; disposition merge hạ tầng riêng; nghiệm thu B05 chưa ký. Không thể ghi toànP0PASS khi technical gaps còn tồn tại. |
| Thành/dependency owner | UI01 validation +commit nêu trên | Không thiếu xác nhận dependency trong hồ sơ hiện có. Chỉ bổ sung nếu owner sửa phạm vi; không gọi đây là phê duyệt QA02. |

Không gửi yêu cầu cho ai. [Approval request](qa-02-approval-request.md) là mẫu để người dùng chuyển, mọi trường chữ ký/ngày mới đều để trống/PENDING.

## Giới hạn cố định và kết luận

Theo yêu cầu người dùng, không tiếp tục Realme animation1x diagnostics (R01 OPEN, retest0x chỉ conditional), video (R03 OPEN), full TalkBack (PARTIAL/BLOCKED), API35 full journey (NOT RUN), hoặc tìm reference benchmark. Không đổi chúng thành PASS; không tự đóng defect ngoài phạm vi. Approved SmallPhone API30 decision chỉ áp dụng đúng cấu hình [đã lưu](evidence/qa-02/device-decision-20261111.md); Realme là supplemental.

**Merge PR55:** phần R02 được sửa và48host tests đạt; chưa có CI/native smoke cho diff mới. Không đề nghị merge PR nguyên trạng vô điều kiện vì R01/R03 và coverage limits vẫn còn, cùng production CatalogDao của PR cần reviewer đánh giá riêng. Có thể reviewer tách quyết định chấp nhận hạ tầng giới hạn khỏi nghiệm thu, nhưng hồ sơ này không cấp approval đó.

**QA-02 Done/MVP:** chưa đủ. Dừng theo phạm vi không chứng minh SRS đã đáp ứng. B03/F03/B05 còn PENDING; technical gaps không được thay bằng ký miễn trừ trong báo cáo. Không commit/push hoặc sửa PR trong lượt này.
