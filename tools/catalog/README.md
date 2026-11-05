# Canonical catalog integration fixture

This is synthetic test content, not approved production vocabulary. The single
input is `docs/data/vocabulary-contract-v1/fixtures/valid/catalog.json`: four
unique senses, sixteen memberships, nullable IPA, four stable `pack_*` IDs.
The builder validates the contract before writing, copies attributionText into
Room, and keeps approval/evidence/provenance in canonical JSON only.

## Rebuild and verify

```bash
python -m pip install -r docs/data/vocabulary-contract-v1/requirements-validation.txt
./gradlew :app:compileDebugJavaWithJavac
python tools/catalog/build_fixture.py
python -m unittest discover -s tools/catalog -p 'test_*.py' -v
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

DDL, indexes and identity hash come from compiler-exported Room schema `2.json`.
Do not edit that export or invent the hash. `catalog.metadata.json` records both
canonical-input and database checksums. Canonical files use LF for reproducible
checksums across Windows/Linux. Rebuilding twice must produce identical bytes.
Metadata records the SQLite version because engine versions can change binary
serialization. CI compares logical SQL dumps across engines and verifies the
committed file checksum; two builds on the same engine must be byte-identical.

Python integration tests cover exact row mapping, nullable IPA, deterministic
build/checksums, integrity/foreign keys, ordering, negative uniqueness, rowid FTS
joins in English/Vietnamese, invalid-input atomicity and the release gate.
`src/catalogTest` opens the same generated asset through Room and the repository,
compares the bundled canonical fixture, checks attribution/checksums and rejects
duplicate ordering. Robolectric executes this shared contract on API 26 and 35.
The AndroidJUnit4 wrapper uses the same tests on a device:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.CatalogAssetInstrumentedTest
```

Building a test APK is not evidence of device execution. The test database is
`catalog-data01-test.db`; tests never target installed catalog.db or userdata.db.

## Runtime and upgrade boundary

CatalogProvider exposes the immutable domain CatalogRepository. Blocking calls
run on a worker executor; no business write API exists. createFromAsset copies a
catalog for a new database; it does not refresh an existing installed copy.
FTS has the same integer rowid as VocabularyEntry; userdata stores only TEXT
stable IDs, never rowid. Full search behavior/performance remains DATA-05 scope.

Room schema 2 adds attributionText and unique ordering indexes. Version 1 export
is retained. No destructive fallback or unverified migration is supplied; old
installed catalogs fail closed. Release history and any real baseline must be
confirmed by integration owners before distribution. Catalog staging/swap/
rollback and UserPack/progress preservation tests are still required if version
1 was distributed. No user data or stable ID is rewritten here.

Source approval, real content QA, quota 150, sense registry custody, published
baseline location and cross-team acceptance remain release/review gates. See
`docs/data/vocabulary-contract-v1/REVIEW.md`.
