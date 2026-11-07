# VOC-DATA-06 — Build and QA the accepted offline catalog

Implementation follows the supplied
`app/build/VocaMind_WEEK2_Thi_VOC-DATA-06_Execution_Plan.md` (07 November 2026).
The original execution guide remains in the ignored build directory.

Base: `origin/develop`, `8209ef75bc882ca10367abd40900e44e537571cb`.
Branch: `feature/voc-data-06-release-catalog`.

## Input and invariants

- Use `docs/data/voc-data-04/accepted-v1` through fresh production ingestion/QC
  and `accept_editorial.load_handoff`. Reproduce frozen acceptance before use.
- Preserve all 150 stable IDs, meanings, examples, sense identities and pack
  membership/order. Keep the three symmetric Quiz exclusions.
- Use compiler-exported `CatalogDatabase/2.json` DDL, indices, setup queries and
  identity hash. Preserve integer FTS rowids joined to TEXT stable IDs.
- Keep the synthetic builder's guard, packaged application assets, schemas and
  all user database code/data intact.

## Implementation

1. Add a production candidate builder with a fresh output directory restricted
   to `build/catalog-release`. Stage the complete bundle, verify it, then publish
   by one directory rename. Failures must leave no successful candidate.
2. Export SQLite, dataset-bound Quiz policy, honest source credits, metadata and
   SQL/release reports. Write the manifest last. Verify each checksum and compare
   SQLite projections back to frozen canonical content.
3. Verify counts, pack quotas/distinct meanings, examples, nullable IPA,
   integrity/foreign keys and every entry's English/Vietnamese FTS result.
4. Add mutation/failure/rebuild tests. Generate an isolated candidate on demand
   for Room/repository/policy tests shared by host and instrumentation runners.
5. Run Python regressions, Android host tests, APK builds, lint and device tests;
   retain checksums and raw evidence. Add the candidate checks to CI.

## Acceptance boundaries

Technical validation produces a candidate with `releaseAuthorized=false`.
Project-use acceptance does not identify verified human authors or establish a
public distribution license. Credits must not infer authors from reviewers.
Publication still needs the rights/author ledger, physical reference-device QA,
APK acceptance and DATA-08 upgrade/rollback with userdata preservation.

See [validation and evidence](../voc-data-06-validation.md) for executed results.
