# Catalog builders and integration checks

## Owner-accepted release candidate (VOC-DATA-06)

`build_release.py` consumes `accept_editorial.load_handoff`, validates the release
contract, reproduces the frozen owner acceptance and rechecks review/config
bindings. It never accepts a fixture or reads a preview as its QC input.

```powershell
python tools/vocabulary/ingest.py --config docs/data/voc-data-04/accepted-v1/ingestion-config.json --output build/vocabulary-ingestion/rc-01
python tools/vocabulary/quality.py --input build/vocabulary-ingestion/rc-01 --config docs/data/voc-data-04/accepted-v1/ingestion-config.json --reviews docs/data/voc-data-04/accepted-v1/reviews.json --output build/vocabulary-quality/rc-01
python tools/catalog/build_release.py --qc-dir build/vocabulary-quality/rc-01 --output-dir build/catalog-release/rc-01
python tools/catalog/build_release.py --qc-dir build/vocabulary-quality/rc-01 --verify-dir build/catalog-release/rc-01
```

Check each command's exit code before continuing in PowerShell. Every output run
must use a fresh directory. Publication is a single directory rename after SQL,
FTS and companion verification. The builder refuses paths outside
`build/catalog-release/`, including packaged main assets. Failed builds leave no
successful candidate directory. The existing fixture builder keeps its fixture
guard and is independent of this production route.

The six-file bundle contains SQLite, metadata, Quiz policy, credits, a release
gate report and a completion manifest. Verification checks every checksum,
compares all projected SQL rows with accepted canonical content, checks 150
distinct IDs / 150 memberships / pack counts 40/40/40/30, and runs EN/VI FTS
queries for every entry. Metadata includes frozen input hashes, schema hash,
dataset digest, DB checksum, SQLite version and a logical SQL dump digest.
Two builds on the same SQLite engine must produce identical bundles; compare
logical content separately when engines differ. No timing claim comes from this
150-word host check.

Technical success always produces `releaseStatus=blocked` and
`releaseAuthorized=false`. The accepted source grants project use, and its
attribution text is preserved. It supplies no verified author list: `authors=[]`
is intentional, and the release credit completeness check still fails. An
approver is not inferred to be an author. Distribution rights/ledger, device
acceptance and DATA-08 upgrade/rollback remain independent release gates.

Gradle generates a separate candidate for `ReleaseCatalogTest` (Robolectric API
26/35) and `ReleaseCatalogInstrumentedTest`. Host tests use classpath resources;
instrumented tests use test APK assets. The application APK keeps its current
fixture. Python with `requirements-validation.txt` installed is required for
these tests; select a virtual environment with `-PcatalogPython=<python-path>`.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.ReleaseCatalogInstrumentedTest'
```

See [VOC-DATA-06 validation](../../docs/voc-data-06-validation.md) for executed
results and the outstanding release gates.

## Synthetic integration fixture

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

## Quiz policy companion

`build_fixture.py` also emits `database/quiz-policy.json`. The synthetic fixture
explicitly has no exclusions. Missing policy files never imply unrestricted
content at runtime. `RepositoryProvider` loads this required companion against
the complete installed Room vocabulary, then `RoomQuizRepository.activeSnapshot`
returns active words together with that policy. Generation does not use the
older vocabulary-only read. No Room schema or stable IDs are changed.

For accepted real content, retain the policy from
`accept_editorial.load_handoff(qc_directory)` and export the companion from that
same validated canonical envelope, for example:

```sh
python tools/catalog/build_quiz_policy.py --canonical build/vocabulary-quality/ci-accepted/canonical-catalog.json --policy docs/data/voc-data-04/accepted-v1/quiz-policy.json --output build/quiz-policy.json
```

The exporter checks the handoff's canonical dataset SHA-256 and all pair
references. It refuses real content without an explicit policy. Ship its output
as `database/quiz-policy.json` alongside the matching catalog when that catalog
is packaged; exporting a companion does not publish or replace the catalog.
The accepted 150-word corpus is exercised in tests, while the bundled database
continues to be the existing synthetic fixture.

Runtime verifies `projectionSha256` against every installed vocabulary row,
including inactive rows, so activation changes do not invalidate the policy and
an old installed catalog cannot silently use a new incompatible companion.
The projection sorts stable IDs and hashes five fields per row: ID, lemma, POS,
Vietnamese meaning, English context. Each field is UTF-8, prefixed with its byte
length as a four-byte unsigned big-endian integer. `datasetSha256` preserves the
full canonical envelope identity verified by the exporter; Room does not contain
all provenance fields needed to recompute that separate digest.

Policy errors return `RepoError.INVALID_DATA`. A valid snapshot with too few
safe distractor meanings returns `INVALID_INPUT / AMBIGUOUS_DISTRACTORS`, never a
shortened quiz. Content QA owns identifying restrictions; QUIZ-02 enforces them.

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
