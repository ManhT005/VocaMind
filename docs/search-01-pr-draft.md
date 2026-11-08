## M?c ti?u v? ph?m vi
Tri?n khai SEARCH-01 Search UI + ViewModel, ph? tr?ch Tr??ng, reviewer Th?nh, ph? thu?c DATA-05. Search Anh?Vi?t offline m? full detail VOCAB-02 b?ng stableId TEXT; pack route gi? membership validation. Kh?ng ??nh d?u task Ho?n th?nh ho?c s?a SRS/ph?n c?ng.

## ?? tri?n khai
- SearchView ? ViewModel ? SearchVocabularyUseCase ? VocabularySearchRepository ? FTS4, t?i s? d?ng parser/SearchProvider c?a DATA-05; UI kh?ng query DAO/Room.
- Debounce 600ms theo handoff; generation t?ng m?i l?n input/clear/reset/retry ?? ch?n response, l?i v? click c?, g?m A?B?A. Kh?ng d?a v?o cancel ?? d?ng SQLite.
- NoInput/Loading/Content/NoMatch/Error, retry/reset; raw query ph?c h?i qua Back/recreation. Worker ch?y DB/I/O; main thread nh?n callback; process owner gi? shared catalog.
- Catalog route kh?ng packId d?ng full detail/Save c?a VOCAB-02 qua API explicit; kh?ng b?a pack/lo?i inactive. Search/navigation kh?ng ghi business state; Save ch? ghi khi click theo policy VOCAB-02.
- T?i ?u hydration b?ng Cursor n?i b? DAO: gi? nguy?n MATCH, rowid?stableId, th? t?, 10 tr??ng v? ??y ?? k?t qu?; ??ng Cursor ??ng ownership. Room scalar connection barrier gi? READ_FAILED sau owner close.
- S?a regression test pack ?ang ch? preview route_message c?: ki?m tra full detail lemma/ngh?a, Save enabled, stableId v? packId; gi? Back/rotation/pause checks.
- T?ch h?p develop m?i nh?t (PROGRESS-02); resolve conflict docs/README.md b?ng c?ch gi? c? SEARCH-01 v? PROGRESS-02. Kh?ng merge feature l?n develop/main.

## Ki?m ch?ng ?? ch?y tr??c l?n t?ch h?p develop cu?i
- Host Android/JVM: 519 tests / 51 suites PASS (API26/35); build QA APK/test APK PASS; lintCatalogQa 0 errors / 13 warnings.
- QA Small Phone API30: full catalog EN/VI/uppercase/NFC/NFD, rapid/clear, full detail/Save/Back/recreation, real Room Error?Retry, ??5000 unique rows/scroll/clear ?? c? instrumentation PASS.
- Ch?y l?i tr??c publish: 46/46 instrumentation pack/quiz/atomic review/progress/migration PASS; first-launch ri?ng tr?n package m?i 1/1 PASS; QA150 search/detail/retry/5000 rows 3/3 PASS. Kh?ng tuy?n b? c? m?t l??t suite47 xanh: first-launch c?n fresh-state ri?ng.
- C?c l??t l?i fixture QA150 vs fixture4, ?i?u ki?n first-launch v? assertion preview c? ??u gi? trong evidence; kh?ng ??i y?u c?u s?n ph?m ?? cho test PASS.
- Host coverage g?m Today, Flashcard, Quiz, packs, detail, progress/repository. Kh?ng coi host tests l? QA th? c?ng; ch?a manual to?n b? Flashcard/Quiz production tr?n b?n search n?y. CI/reviewer approval s? ki?m tra ri?ng.

## Hi?u n?ng v? AC-05/NFR-VM-03 ? HOLD
SRS baseline v1.1 y?u c?u query <50ms (m?c ti?u <15ms) tr?n Helio G85 / RAM3GB / Android10 v?i5000 m?c t?. Benchmark DATA-05 gi? nguy?n fixture SHA256 `5fac69d4a5776130346be14747d81af3354a9220c2f9675ed95740c5058f54a7`, 5 workloads,20 warmups v?100 m?u/workload; kh?ng truncate ho?c lo?i outlier.

Hai c?p APK c?/m?i tr?n Small Phone emulator, m?i b?n200 m?u broad: mean44,103?37,794ms (gi?m quan s?t14,3%), p9549,552?45,595ms, max109,465?52,977ms. B?n m?i v?n c?3/200 m?u?50ms, mean broad v?n tr?n15ms. L??t new-b ri?ng max43,145ms kh?ng ???c d?ng ?? che l??t new-a max52,977ms.

Scope ?o: DAO MATCH + order + entity materialization (b?n m?i g?m scalar guard), lo?i asset opening/parse/queue/domain mapping/debounce/render. Trace UI t?ch ri?ng; kh?ng c?ng l?p submit-to-main v?i query ho?c g?i bind-to-post l? GPU frame.

**L? do HOLD:** (1) ch? c? emulator x86/ranchu Android11/API30 kho?ng2GB, kh?c SoC/RAM/OS c?a SRS; (2) v?n c? m?u emulator v??t50ms; (3) ch?a c? inventory v? raw samples t? c?u h?nh tham chi?u ho?c c?u h?nh thay th? ???c ch?p thu?n. Collector PASS kh?ng ??ng ngh?a performance PASS. Host benchmark kh?ng thay evidence thi?t b?.

**Thi?t b? thay th?:** Small Phone ?? d?ng ?? QA ch?c n?ng v? ?o tham kh?o. N?u d?ng thi?t b? v?t l? kh?c ?? nghi?m thu, c?n owner/reviewer ch?p thu?n c?u h?nh thay th? tr??c, ghi model/SoC/RAM/API/build/ngu?n-nhi?t-background load v? l? do ph? h?p; kh?ng t? g?i emulator/LDPlayer l? t??ng ???ng Helio G85. Evidence LDPlayer API28 c?a PROGRESS-02 kh?ng ch?ng nh?n search latency. Khi c? thi?t b? ph? h?p, ch?y c?ng fixture/harness v?i `data05EnforceLatency=true`, gi? m?i raw samples v? SHA source/APK/fixture; max b?t k??50ms l? FAIL ph?p ?o ??, b?o m?c ti?u15ms ri?ng. Ch?a c? quy?t ??nh ch?p thu?n thay th? n?n gate ti?p t?c HOLD.

## Evidence / handoff
- [Validation v? contract SEARCH-02/VOCAB-02](docs/search-01-validation.md)
- [Full VOCAB-02 integration](docs/search-01-vocab02-integration.md)
- [?i?u tra hi?u n?ng v? raw samples](docs/search-01-performance-validation.md)
- [Regression task li?n quan](docs/search-01-cross-task-regression.md)
- [Raw logs/JUnit/fixtures hashes/inventory/screenshots](docs/evidence/search-01-performance-local/)

Th? QA: Home?T? v?ng, t?m customer/kh?ch h?ng, clear/g? nhanh/NoMatch; ch?n k?t qu??full detail?Back gi? query; th? Save/Unsave/recreation. Fixtures5000 v? Error/Retry n?m trong instrumentation, kh?ng thay catalog QA150. SEARCH-02 gi? parser/generation/debounce/state contract; VOCAB-02 gi? hai route explicit catalog/pack v? Save policy.

## Ki?m ch?ng sau t?ch h?p develop m?i nh?t
Source commit `feed39ade6607293be2d3e97c6734e2dabd0c6e6`, develop `0afcdd6609cd505ed488f0be454f35f2906b6ac2`: **533 tests/54 suites PASS**, 0 failed/error/skipped; assembleDebug PASS; lintDebug 0 errors/14 warnings. Windows/JDK17/Gradle8.9, Robolectric API26/35. [Evidence merge](docs/evidence/search-01-pr47-merge/). Ch?a ch?y l?i emulator QA sau merge PROGRESS-02; evidence emulator tr??c merge gi? nguy?n SHA ri?ng. CI/reviewer approval PENDING. Ch? conflict docs index, ?? gi? c? hai nh?m t?i li?u.
