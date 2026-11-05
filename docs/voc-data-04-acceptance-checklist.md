# VOC-DATA-04 acceptance and downstream handoff

Owner: Vương Quang Thành. Reviewer: Trường (`Letruong18`, author of VOC-DATA-03
PR #18). Integration base: `develop` at `752cce4`.
This checklist distinguishes executed technical checks from external approvals.

| Plan Definition of Done | Evidence/status |
| --- | --- |
| VOC-DATA-02/03 input and VOC-DATA-06 consumer compatibility | Canonical v1.0.0 unchanged; replay verified ingestion; `load_validated` consumer checksum/mode gate; isolated existing catalog-builder integration test passes. Owner acceptance pending. |
| Deterministic/idempotent normalization, Vietnamese preserved | QC-07/14/15 and two independent CLI outputs; comparison evidence. |
| StableId preserved for wording edits, no FTS rowid identity | QC-06, identity mismatch tests and unchanged contract helper. |
| Required meaningVi/example pair, nullable IPA | QC-02/03/04. Empty IPA follows contract rejection. |
| Duplicate/provenance/license/pack gates | QC-05/09/10/11/12/13/18/19 and tamper/custody tests. |
| License metadata alone is insufficient | Replay uses actual `authorize()` plus frozen inventory; unapproved sources remain blocked. |
| Rejects traceable and repaired under control, rerun succeeds | Error schema carries recordRef/stableId/field/reason/provenance; wording correction/renewed review test passes; rejected CLI emits no success artifact. Real corpus remediation not yet possible. |
| Important tests pass and reports supplied | 140 Python tests local and CI; Android unit tests/debug APK/instrumentation APK pass. CI #90 and artifact linked in validation. |
| MVP content reviewed per record | **Pending**: no approved production corpus or actual semantic review sidecar supplied. Synthetic decisions are test data only. |
| Reviewer can reproduce | `tools/vocabulary/QUALITY.md` provides exact CLI and review schema. Reviewer execution/confirmation remains pending. |
| Trường reviews PR, required CI passes before merge | [PR #27](https://github.com/ManhT005/VocaMind/pull/27), CI #90 success at implementation caa59a8; requested Letruong18, actual review pending. |

## Verified blockers, 2026-11-05

The checked-in source registry and audited snapshot have zero pipeline-eligible
sources. NGSL/TSL are on hold awaiting snapshot rights/technical approval;
Kaikki/Tatoeba lack approved corpus/credit routes. FVDP/audio are excluded or
research-only. Existing local NGSL/TSL research wordlists cannot supply reviewed
Vietnamese meanings or translated example pairs. Neither metadata nor merged
VOC-DATA-03 establishes semantic approval or permission to publish a real dataset.

GitHub PR #18 was checked: merged into develop, body still explicitly reports
zero eligible sources and pending custody decisions, no comment confirming
production source/content approval was found. No such decision is fabricated here.

## Concrete handoff

1. VOC-DATA-01 / PO or Content Lead provides source snapshots with the artifact
   roles, review and bound checksum required by `source_gate.authorize()`.
2. VOC-DATA-03 owner provides reviewed staging JSONL, registered sense identities,
   verified component adapters and the exact successful ingestion config/manifest.
   The staging itself needs an approved artifact route, not just the wordlist.
3. Content reviewer inspects single sense, EN usage, corresponding VI translation
   and meaning distinction in each pack. The sidecar must contain actual reviewer,
   date, explicit decisions, entry SHA-256 and full dataset SHA-256.
4. Run QC without `--fixture` in a fresh directory. Any error blocks the entire
   publication. Fix upstream, renew affected evidence and rerun all pack gates.
5. VOC-DATA-05/06 calls `quality.load_validated(directory)` with production default,
   preserves the entire envelope/manifest/reports and confirms the handoff in PR.
   Enrichment must not reuse stale QC decisions after changing the envelope.
6. Trường reviews the implementation and evidence. CI success alone does not
   authorize source rights, substitute content review or close overall AC-04/10/12.

The implementation and PR can be reviewed now. Full task acceptance depends on
steps 1–6, including decisions outside the implementation author's authority.
