# DATA-01 catalog fixture

This is **synthetic test content authored for this implementation**, not data
imported from NGSL, Kaikki or Tatoeba, and not a production MVP vocabulary pack.
No external source license or content approval is claimed. The source record's
`LicenseRef-Project-Test-Fixture` is a local provenance label, **not** a standard
license grant or a release approval. Group review and the approved source ledger
remain required for VOC-DATA-06. Do not distribute this as the final catalog.

Four original example pairs and four distinct senses are reused in all four
packs: **4 unique vocabulary IDs, 16 memberships, 4 members per pack**. This
intentionally makes it impossible to mistake membership count for unique count.
IPA is null to exercise the nullable data path. No placeholder is stored as IPA.

## Rebuild

From repository root, using the project's JDK 17 and Android SDK:

```bash
bash ./gradlew :app:compileDebugJavaWithJavac
python3 tools/catalog/build_fixture.py
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

The Python script uses only the standard library and exported Room JSON in
`app/schemas/com.vocamind.app.data.catalog.CatalogDatabase/1.json`. DDL, indices,
Room metadata and identity hash come from that compiler output; no example hash
is embedded. Run twice and compare the metadata SHA-256 to verify repeatability.
The checked-in asset is needed so normal builds/tests do not depend on Python.
Regenerate after schema changes; review database version/migration implications.

The script writes `app/src/main/assets/database/catalog.db` atomically after
checking SQLite integrity, foreign keys and English/Vietnamese FTS mapping, and
writes `catalog.metadata.json` with checksum, version and counts. It operates
only on build-time assets; it never touches an installed app or userdata.db.

Stable IDs use SHA-256 UTF-8 `lemma_pos_senseIndex`, truncated to 16 lowercase
hex characters; these lowercase ASCII fixture lemmas use senseIndex **1**.
This is compatible with the proposed VOC-DATA-02 contract at remote commit
`feb275cb41e00a500d6f3081306249949ff93037` for the fixture's identity inputs.
That contract is an implementation candidate awaiting team review; this script
is not a replacement for its general normalization/identity registry/validator
or the production ETL. Fixture contentVersion is `1.0.0`, schema version is `1`.

## Runtime boundary

`CatalogProvider` is an application-scoped owner of `CatalogDatabase` and exposes
only the domain `CatalogRepository`. Inject that interface into use cases and
ViewModels; do not inject the DAO. Calls block and must run on a worker executor.
Close the provider when its owning scope is disposed. No main-thread queries are
enabled, no business write API exists, and database failures are wrapped in
`CatalogReadException` with the cause retained. Missing IDs return empty values;
collection results are immutable.

`createFromAsset` copies an asset; it does not enforce SQLite file read-only mode
or replace an already installed catalog on every launch. This implementation
provides **business read-only** access. No destructive fallback is configured.
Catalog upgrades still need the staged validation/swap/rollback workflow; this
task does not implement that workflow or modify userdata.db.

FTS is a standalone Room `@Fts4` table, populated at asset build time with the
same integer rowid as VocabularyEntry's hidden SQLite rowid. VocabularyEntry.id
remains TEXT PRIMARY KEY. There are no runtime writes to either table, so no
external-content triggers are needed. The fixture MATCH query proves the join;
DATA-05 owns the complete search contract and performance work.

## Tests

`src/catalogTest/assets/fixtures/catalog.db` is a fixed test-only asset, included
as JVM test resources and instrumentation APK assets, never application assets.
The existing 11 `CatalogAssetContract` tests load that fixture through
`createFromInputStream`, so their exact counts, meanings and null-IPA assertions
remain deterministic when VOC-DATA-06 replaces the production catalog.
The device wrapper opens the fixture from the instrumentation package assets;
the JVM wrapper reads it from the test classpath.

`ProductionCatalogContract` independently opens the application's actual
`database/catalog.db` using `CatalogDatabase.open` / `createFromAsset`. Its four
checks validate schema opening and model/source mapping, integrity and all FTS
row mappings, MATCH for a word selected from the asset, and reopen. It does not
require exact fixture words/counts, one source, or null IPA. It retains the
DATA-01 minimum of four packs with queryable content. Full MVP quotas and
content/license QA remain VOC-DATA-06 acceptance work, not asserted as passed.

Both contracts run on Robolectric native SQLite API 26/35: 22 fixed-fixture
executions plus 8 production-asset executions. The device wrappers are ready for:

```bash
bash ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.data.catalog.CatalogAssetInstrumentedTest,com.vocamind.app.data.catalog.ProductionCatalogInstrumentedTest
```

Tests use isolated names `catalog-data01-test.db` and
`catalog-production-contract-test.db`; cleanup never targets `catalog.db` or
userdata.db. The fixed fixture covers ordering, missing IDs, nullable IPA,
mapping, metadata, MATCH English/Vietnamese, integrity, reopen, immutable lists
and rejection of corrupted identity metadata without deleting the test copy.

The builder explicitly closes SQLite before replacing or removing its temporary
file. Its regression tests check the closed-connection prerequisite even on Linux
(where renaming open files is allowed), repeatability, and failure cleanup while
preserving the old asset/metadata:

```bash
python3 -m unittest discover -s tools/catalog -p 'test_*.py' -v
```

These two Python tests also run in Android CI. They do not claim native Windows
execution; they enforce the file-lifetime requirement that Windows needs.

To deliberately refresh the fixed test fixture after a compatible schema change,
review the generated main asset and copy it to the test fixture path. Do not
replace that fixture with production data as part of normal catalog refreshes.

Robolectric native SQLite validates Room on a host runtime; it is not evidence
of execution on an emulator/physical device, UI behavior, release data licensing,
FTS performance or catalog upgrade safety.
