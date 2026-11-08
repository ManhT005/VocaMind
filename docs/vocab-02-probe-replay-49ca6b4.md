# VOCAB-02 — recheck probe/source binding, 07/11/2026

Phụ trách Trường; gửi Thành kiểm tra lại PR #32. Không tự đánh dấu Done/approve/merge.
Thực hiện theo đủ mục 4 của [hướng dẫn recheck](vocab-02-recheck-993e70a.md).

## 1. Phục hồi original và giữ nguyên lịch sử

Tìm lại được file bytes gốc trong build directory local tồn tại từ lượt trước. Bản mới lưu riêng:
[historical-original-089ab4.java](validation/vocab-02/replay-49ca6b4-20261107/historical-original-089ab4.java).
SHA256 **089ab4b2171040bff1dd9d62f4b050cba3d77487b9e763c2d7aa77730c8393e1** khớp digest báo cáo cũ.

File gốc có **1 CRLF** xen trong các dòng LF; Git archive có 0 CRLF. So nội dung sau chuẩn hóa CRLF→LF
cho kết quả bằng nhau. Đây là đối chiếu bytes mới có file gốc trong tay, không sửa expected hash của
báo cáo cũ hoặc suy ra các lần chạy lịch sử chỉ từ log. [Recovery receipt](validation/vocab-02/replay-49ca6b4-20261107/original-recovery.json).

Giữ nguyên toàn bộ device-9f2dfd9-20261107, metadata, log fail và probe-binding-review.json cũ.
Không đổi trạng thái historical binding hoặc lấy log cũ làm PASS cho source mới.

## 2. Replay đúng source tích hợp mới

- Source commit: **49ca6b4434470443524ebfaeff56406b206dada4**; app tree **b2f07dd6f3f807e8ad72f404df377490fbb81b05**.
- Replay dùng đúng probe archive lấy bytes bằng `git show HEAD:<probe path>`; **không đổi assertion hoặc fixture logic**.
- Probe archive và file sourceSet trước/sau chạy đều SHA256
  **4f1461beda68ac415abfb16cf45c877eaad29b202b9b8b8de34af5bf2c558737**.
- Init script SHA256 **a21089125b450d0494e6ee62165415f04e9c4bbcfbc9cce68854355978563be0**.
- App QA APK mới: **44899c8eeedaaf4f70210e14015a150dbd3975e83544fdeadcb5b56f556b0597**.
- Test APK mới: **9a2c7cd54b57dae63982ae8dfbadbc31f2ac8883df6186f03cf8798c53370db1**.

Không dùng APK cũ. Build một lần; **cùng một test APK cho mọi a/b/c/d/e/f/h/g**, không thêm method
hoặc rebuild giữa run. Mỗi phase kiểm tra probe archive/sourceSet, APK host và SHA256 base.apk đã
cài bằng `pm path` + `sha256sum`, trước và sau instrumentation. Các hash đều khớp.

[Prebuild 223 app/config/assets hashes](validation/vocab-02/replay-49ca6b4-20261107/prebuild-inputs.json),
[APK binding ngay sau build](validation/vocab-02/replay-49ca6b4-20261107/build-binding.json),
[postrun không input nào đổi](validation/vocab-02/replay-49ca6b4-20261107/postrun-inputs.json),
[APK asset hashes](validation/vocab-02/replay-49ca6b4-20261107/apk-assets.json).

## 3. Điều kiện và thứ tự chạy

Dùng **Small_Phone hiện có, Android11/API30, emulator-5554**. QA package riêng
com.vocamind.app.vocab02qa; chỉ clear package QA để bắt đầu sạch. Không wipe model hoặc clear
com.vocamind.app. Airplane Mode enabled, Wi-Fi/data disabled trước launch; mỗi phase assert airplane=1.
Fixture của probe giữ nguyên: một từ chưa có progress và một từ có progress/history synthetic đã biết.

Thứ tự đã chạy thực tế:

1. a — detail offline, first-save, repeated tap/pending/rotation, Back/reopen.
2. Force-stop/relaunch QA → b — saved vẫn đúng.
3. Reboot thật, đợi boot/unlock/offline → c — saved vẫn đúng rồi Unsave.
4. Force-stop/relaunch QA → d — unsaved vẫn đúng.
5. Reboot thật/offline → e — unsaved vẫn đúng; existing Save/Unsave giữ learning/history/catalog.
6. f — controlled VM read/write errors, Retry reconcile không replay; present IPA render fixture.
7. h — loading/confirmed rotation và ví dụ Việt cuộn được.
8. g — chỉ export evidence, **không tính là acceptance**.

**7 phase chức năng PASS + export PASS**, raw JUnit log/exit/result và phase JSON đều mới.
Ba boot IDs chứng minh hai reboot:
`30fff1a1-d65e-4f73-9570-766785cc75d2` → `8d7a0e95-0244-42bb-b17e-73966bb61b82` →
`d66e2f2e-3d33-44cb-bae1-9a886aa5ef37`. Không gọi Activity recreation là reboot.

## 4. Kết quả V02-A..D và evidence

| Case | Kết quả replay / bằng chứng mới |
| --- | --- |
| V02-A | PASS route/list/detail/Back và đủ fields asset, null IPA fallback, offline; phase-a/h.log và ảnh detail/scroll Việt. IPA-present dùng synthetic renderer fixture ở f, không sửa IPA trong catalog. |
| V02-B | PASS Save/Unsave sau confirmed write, force-stop/relaunch và saved/unsaved qua hai reboot; phase-a..e.json/log và ảnh saved/unsaved-reboot. |
| V02-C | PASS full catalog equality assertion và existing learning/history trước–sau; phase-e.log, raw catalog baseline, learning/history fixture và QA DB snapshot. |
| V02-D | PASS pending lock/double tap/loading/pending/confirmed rotation; controlled read/write failures và Retry chỉ reread, writes=1; phase-a/f/h và ảnh pending/error/landscape. Không tuyên bố đã gây uncontrolled disk failure. |

[Run manifest — VERIFIED_REPLAY](validation/vocab-02/replay-49ca6b4-20261107/verification.json),
[phase runner](validation/vocab-02/replay-49ca6b4-20261107/run-phase.ps1),
[verify-artifacts.ps1](validation/vocab-02/replay-49ca6b4-20261107/verify-artifacts.ps1).
Mỗi phase JSON có timestamps, kernel boot ID, installed APK paths/hashes và byte hash log.

DB được thu từ riêng package QA sau force-stop: [raw QA databases](validation/vocab-02/replay-49ca6b4-20261107/qa-databases.tar),
[logical snapshot](validation/vocab-02/replay-49ca6b4-20261107/db-snapshot.json). Tổng cuối=2:
một saved-only row box0 (sau Unsave flag=false) + một fixture box3. Có đúng một ReviewEvent fixture,
không UserPack, learned-today=0. Phase e so learning/history với baseline; catalog giữ nguyên.

Ảnh tiêu biểu: [saved sau reboot](validation/vocab-02/replay-49ca6b4-20261107/captures/V02-B-saved-reboot.png),
[unsaved sau reboot](validation/vocab-02/replay-49ca6b4-20261107/captures/V02-B-unsaved-reboot.png),
[loading rotation ổn định](validation/vocab-02/replay-49ca6b4-20261107/captures/V02-D-loading-rotation-stable.png).
Giữ cả raw captures; các ảnh chụp giữa animation hệ thống không dùng làm steady-state layout evidence.

## 5. Byte integrity trong repository và phạm vi

Thêm gitattributes **chỉ cho thư mục replay mới**: không EOL conversion cho artifacts, raw logs và
original mixed-EOL giữ binary. Không đổi attributes/evidence cũ. Đối chiếu SHA256 của bytes đã stage
vào Git với file được chạy; xem git-staged-byte-verification.json trong run directory.
Reviewer có thể chạy `powershell -NoProfile -ExecutionPolicy Bypass -File <run>/verify-artifacts.ps1`
để kiểm tra lại manifest và binding của toàn bộ phase, không dựa vào việc chỉ sửa số hash.

Build QA mới PASS trên JDK17/Gradle8.9. Source mới yêu cầu prepareReleaseCatalogTests;
chuẩn bị Python portable3.12.10/jsonschema4.26.0 trong build để chạy task này, không bỏ task.
Một lượt build đầu lỗi đường import của Python embedded, đã giữ log riêng; chỉ sửa runtime ._pth,
không sửa app, Gradle source, test assertions hoặc probe. Build sau thành công.

Không sửa production source/schema/planner/stats; không gán 408 host tests lịch sử cho source49ca6b4.
CI source49ca6b4 trước replay đã SUCCESS; CI head bàn giao mới theo dõi riêng. Đây là emulatorAPI30,
không phải thiết bị vật lý hoặc ma trận mọi API/TalkBack. Chờ Thành kiểm tra commit và chốt Done;
không tự approve/merge hoặc đánh dấu task Hoàn thành.
