# VOC-DATA02 integration recheck — 2026-11-03

Branch: `feature/voc-data-02-contract-v1`. Starting head:
`5d85572991b4508c25278202a3cd086e51e4b57b`. Integrated develop:
`95870b14e5efeef532ddf1dc8b9de95402641d46` (confirmed again with
`git ls-remote`). The resulting merge commit is the commit containing this report.
The working tree was clean before merging; no AGENTS.md was found in the checkout.

## Changes

- Resolved both conflicts in `CatalogSource.java` and `CatalogAssetContract.java`,
  keeping VOC-DATA02 canonical/checksum/ordering tests and both ATTR-01 behaviors.
- Added the full source constructor with attribution, authors and companion notice;
  retained six-, seven- and eight-argument overloads. Legacy overloads use empty
  attribution, empty authors and null notice as applicable; no credit is invented.
  Authors retain `List.copyOf` semantics and other null handling is unchanged.
- Kept `CatalogMapper.source()` mapping database attribution. Fixed
  `ContentCredits.enrich()` to preserve it. Both repository queries already enrich
  after mapping; no repository or UI behavior change was needed. Companion notice
  remains distinct from attribution. Checked all constructor call sites and UI
  authors/notice reads.
- Extended `CatalogAssetContract` to cover nonempty attribution, all core source
  fields, immutable authors through both queries, unmatched source/date metadata,
  and credit failure/retry through both queries. Renamed the existing-copy test
  to remove the unsupported schema-v1 migration implication; retained its byte
  comparison proving enrichment leaves the database unchanged.
- Extended `ContentCreditsTest` with attribution preservation and identity checks
  for unmatched sources, including sources with existing authors/notices.
- Updated `AttributionScreenContract` expectations to the existing canonical
  VOC-DATA02 fixture, retaining navigation/recreation/rendering assertions.
- Corrected the companion JSON source/date pair to the actual packaged catalog.
  This fixes the real CI consistency failure from merging the old ATTR-01 fixture
  reference. Authors remain empty and notice null. Canonical JSON, catalog.db,
  schemas, checksums and user data were not modified.
- Updated the companion contract documentation to describe the schema-2 boundary
  accurately. No migration or destructive fallback was introduced.

## Verification

Python executable: `.tools/python/python.exe` (Python dependencies already present).

| Command | Result |
| --- | --- |
| `python -m pip install -r docs/data/vocabulary-contract-v1/requirements-validation.txt` | Requirements satisfied |
| `python -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v` | 39 passed |
| `python docs/data/vocabulary-contract-v1/validate_contract.py docs/data/vocabulary-contract-v1/fixtures/valid/catalog.json` | PASS_CONTRACT_CHECKS; four words, sixteen memberships, four packs |
| `python -m unittest discover -s tools/catalog -p 'test_*.py' -v` | 5 passed |
| `python tools/catalog/check_credits.py` | One matched pair; missing authors reported |
| `python tools/catalog/check_credits.py --release` | Expected rejection: missing authors; fixture remains blocked |
| `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` | Passed; 125 unit tests, zero failures/errors/skips; both APKs built |

XML reports confirm catalog tests (32), credit tests (6), attribution rendering
and navigation (4), and attribution view-model/link/use-case tests (10) ran.
Robolectric runs the shared catalog/credit/screen contracts on API 26 and 35.

No JDK 17 was available in PATH. Verification used the installed
`C:\Users\thanh\.jdks\jbr-21.0.11` and existing user Gradle cache; source/target
compatibility remains Java 17. CI's JDK 17 execution has not been observed here.
`adb devices` listed no device/emulator. Instrumented tests were compiled and
packaged, but were not executed on a device.

Initial Android verification exposed stale fixture expectations in the imported
ATTR-01 tests; those failed assertions were corrected to the canonical fixture,
then verification was repeated. The final asset change was also rebuilt/retested.

## Delivery status

Resolved locally and committed as a merge of develop. No push was requested or
performed. PR #12 remains open on the remote head `5d85572`; GitHub reported
`mergeable=false`, `mergeable_state=dirty` before any push. Its target is develop;
the API returned an older base SHA, while `git ls-remote` confirmed the latest
develop SHA above. No PR was merged or edited. A normal feature-branch push is
still required to update the PR, followed by a fresh mergeability/CI check.
