# Local content credits contract v1

The user authorized the ATTR-01 companion proposal on 2026-11-02. This contract
adds offline author/notice metadata without changing Room schema v1, database
identity or the existing catalog asset. DATA-01/reviewer acceptance and legal
source approval remain independent gates.

File: `app/src/main/assets/database/content-credits.json`.

```json
{
  "schemaVersion": 1,
  "sources": [
    {
      "sourceId": "synthetic-example-only",
      "snapshotDate": "2000-01-01",
      "authors": ["Synthetic test author"],
      "creditText": "Optional synthetic notice"
    }
  ]
}
```

Each row has an exact, unique `(sourceId, snapshotDate)` pair. IDs/dates and each
author must be nonblank strings. Authors is an ordered array; an empty array is
explicitly missing information, allowed for debug/legacy use and rejected by the
release credit check. creditText is optional/null or a nonblank string. Notices
are supplemental credit, not a license approval or a required full license text.
Neither names nor notices may be inferred from repository authors or research
registry data. Thi/Nhân must supply verified values for real snapshots.

ContentCredits validates/parses on the repository worker. Both source-list and
vocabulary-source APIs enrich CatalogSource consistently. The six-field domain
constructor remains available with empty authors/null notice. Mapper retains the
Room fields; the repository joins companion metadata after mapping. UI reads
domain values only, and renders missing authors transparently.

Missing companion file on a legacy package returns missing credit. Malformed,
unsupported-version or unreadable metadata raises a catalog error, allowing retry.
Missing or mismatched pairs never borrow credit from another source/snapshot.
Unused companion rows never become displayed catalog sources.

Companion enrichment itself does not modify the database. VOC-DATA02 now uses
Room schema 2; this does not provide a schema 1 to 2 migration. Installed schema 1
copies fail closed until a reviewed upgrade path exists. Supported copies with
exact matching pairs are enriched from the APK asset. Snapshots without a matching row show
missing credit. Current tooling rejects extra/old pairs against the packaged DB;
there is **no historical-row support** in v1. A future requirement to ship old
snapshot credits needs an explicit history section/validator extension, not an
unmarked extra row. No userdata or learning progress is touched.

```powershell
python tools/catalog/check_credits.py
python -m unittest discover -s tools/catalog -p 'test_*.py' -v
python tools/catalog/check_credits.py --release
```

CI runs the first two commands to check consistency and validation logic. The
third deliberately fails for today's fixture-authored-20261023 with missing authors.
Even success of `--release` checks only credit completeness: per-snapshot
ledger, provenance, permissions and real PO/Content Lead approval are still
required. It is not a Gradle release publication authorization or a replacement
for AC-12. The canonical fixture remains synthetic, four words, not release data.
