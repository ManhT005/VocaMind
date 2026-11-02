# ATTR-01 PR #15 review fixes and R2 contract proposal

Status: **R2 proposal awaiting confirmation by Nhân (DATA-01) and Thành
(reviewer / VOC-DATA-02)**. This is not an accepted storage contract or license
approval. R1 is independent and can be implemented while R2 is pending.

Read the complete supplied `FIX_PLAN_ATTR01_PR15.md`, including section 8 which
replaces the earlier R1-only conclusion. Baseline is SRS v1.1, including UC-09's
authors requirement. Local and remote PR head were both
`0955c9b99a12d1c4dfce83dc14952ca31e7a2c2f`; working tree was clean.
PR review/comment endpoints contained no existing contract decision when checked.

## R1

Normalize only the URI scheme before ACTION_VIEW dispatch. Preserve path, query
and fragment. Regression dispatches uppercase and mixed-case HTTP/HTTPS through
`open()` and matches a lowercase browser IntentFilter, as well as checking all
case-sensitive URI components. Run it against old code to record failure before
applying the fix. Existing invalid URL and handler failure tests must still pass.

## R2 contract for Nhân/Thành to confirm

Current `ContentSource` entity and exported Room schema v1 have six fields:
id, title, url, licenseCode, licenseUrl, snapshotDate. `CatalogSource` and
`CatalogMapper.source()` expose exactly those fields. No author/credit exists.
`createFromAsset` does not replace previously copied databases. Changing only the
entity or binary asset would therefore break or omit installed catalogs.

Proposed option: a versioned companion asset, `database/content-credits.json`,
parsed in the data layer and joined inside the existing RoomCatalogRepository.
No second CatalogRepository implementation and no UI access to storage.

Proposed JSON contract (illustrative test metadata only, **not production credit**):

```json
{
  "schemaVersion": 1,
  "sources": [
    {
      "sourceId": "test-source-a",
      "snapshotDate": "2000-01-01",
      "authors": ["Synthetic test author A"],
      "creditText": "Synthetic test credit, not release material"
    }
  ]
}
```

Decisions proposed for confirmation:

1. Identity is the exact `(sourceId, snapshotDate)` pair from the installed
   catalog, not source title, aliases or a research registry row. Require unique
   pairs. Authors is an ordered list of nonblank strings; creditText is optional
   additional verified notice, not full license text or approval status.
2. Extend the immutable domain CatalogSource with authors and optional creditText;
   retain the six-argument constructor for legacy callers, producing explicitly
   missing credit. Mapper/repository enrich both getSources and
   getSourceForVocabulary consistently. GetContentSources/ViewModel keep their
   contracts and offline worker execution.
3. The data layer reads the asset locally, validates its version/structure and
   matches only sources queried from the installed DB. Do not list unused
   companion rows as packaged sources. Never attach mismatched snapshot credit.
   Malformed/unsupported metadata or disk read failure propagates a catalog read
   error with retry. Absent legacy metadata or an unmatched source remains visible
   with missing authors/credit; it is never presented as release compliant.
4. Build/QA validation compares every companion pair with the actual packaged
   SQLite catalog and rejects duplicate, unknown or mismatched pairs. Missing
   authors required by a source blocks release acceptance. Legacy compatibility
   rows must be explicitly distinguished from the current packaged source set.
   This consistency check is separate from ledger approval and must not fabricate
   approval or infer it from a nonempty authors list.
5. Keep Room schema v1, database identity, `catalog.db` and userdata unchanged.
   App upgrades enrich already copied catalogs only when the companion contains
   their exact source/snapshot pair. Retain verified historical pairs explicitly
   if supporting older snapshots; otherwise render missing metadata for them.
   No destructive reset, catalog replacement or progress migration is needed.
6. UI displays a local “Tác giả” field and optional credit notice, both wrapping
   without truncation. Missing authors displays “Chưa có thông tin tác giả”; this
   is transparency, not a passing release acceptance result.
7. Thi/Nhân supplies actual evidence-backed author/credit values for each real
   snapshot. Synthetic authors may be used only in clearly labeled test fixtures.
   Do not infer a person's authorship of project_fixture from Git commits. Until
   fixture credit is explicitly supplied, use synthetic sources in tests and show
   the real fixture's credit as missing. No external hold source is imported.

Alternative: extend Room to v2 with a real migration, exported identity/schema,
updated asset generation and existing-install tests. This is more invasive and
requires DATA-01 owner agreement and a catalog upgrade design. Do not implement
this alternative without that decision. Companion asset is recommended because
it preserves the installed catalog contract and isolates attribution metadata.

## R2 traceability and implementation after confirmation

| Requirement | Storage/domain/UI change | Validation |
| --- | --- | --- |
| UC-09 authors offline | Companion pair → repository enrichment → CatalogSource.authors → local text | Two sources with independently expected different authors; offline first launch |
| Verified notice when needed | Optional creditText through same path | Long authors/credit, distinct per-source notices; no cross-source leakage |
| Snapshot accuracy | Exact composite key, uniqueness and build consistency validation | Missing metadata, wrong sourceId/date, duplicate key, unsupported version, stale installed catalog |
| Existing state/lifecycle behavior | Existing use case/ViewModel state handling | Error/retry, rotation, recreation and Back; source list remains readable |
| Compatibility | No DB/schema or userdata writes | Reopen existing v1 DB with new app; unchanged DB identity and user progress |
| AC-12 | Separate ledger/evidence/PO/Content Lead approval | BLOCKED until real approvals and eligible catalog exist |

After confirmation, implement storage/domain/UI and tests in a separate R2 commit,
run full checks and record the actual tested SHA. Keep prior evidence bound to its
original SHA. Do not claim R2 implemented or UC-09 fully met while this decision
or verified metadata is missing.

## Publication checkpoint

User explicitly requested a preview before updating PR #15. Commit local reviewable
changes and show diff, evidence and outstanding R2 decision first. Wait for the
user's approval before push, PR description/comments or requesting review again.
After approval, update the existing feature PR into develop and request
`thanhcodeai` review. Do not create a new PR, merge, edit SRS/assignment status or
mark the task complete. AC-12/release remains BLOCKED.
