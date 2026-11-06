# VOC-DATA-04 acceptance and downstream handoff

Owner: Vương Quang Thành / thanhcodeai. Reviewer: Trường (`Letruong18`).
PR: #27 into develop. This records local task delivery, not a GitHub review or merge.

## Owner decision — 2026-11-05

The user explicitly accepted all 150 records according to the worksheet and
confirmed that the project's AI-generated corpus does not need external-source
approval. [owner-acceptance.json](data/voc-data-04/owner-acceptance.json) records
the actual instruction, reviewer/date, TSV/worksheet hashes and scope.
This supersedes the previous source/content blockers for the accepted editorial
snapshot only. It does not approve NGSL or any other external dataset, attribute
a new independent review to Codex/Trường, or grant an external license.

| Task delivery | Evidence/status |
| --- | --- |
| Canonical v1 compatibility | Same schema/IDs/wording/memberships; content-verified ingestion and production consumer test. |
| Deterministic normalization, required text, nullable IPA, duplicate/pack gates | Existing QC tests retained; new accepted-snapshot reproduction test. |
| Project-generated content route | Separate single-source registry; exact artifact bytes, owner decision and snapshot digest checked. Seven-source research registry unchanged. |
| 150 content decisions | Owner worksheet acceptance materialized in accepted-v1/reviews.json; every entry and the final envelope have fresh checksums. |
| Identity | First local accepted editorial registry; preserves the 150 proposed tuples and IDs. No published production registry overwritten; baseline comparison not claimed. |
| Production ingestion/QC | Non-fixture run: 150 read/accepted, QC pass with zero errors. See accepted-delivery.md for commands/results. |
| Ambiguous distractors Q1–Q3 | Dataset-bound policy excludes research/study, home/house, customer/client in both directions across packs. Handoff loader and helper tested; Android QUIZ-02 must apply the policy when implementing generation. |
| D1 scope correction | Validation/QUALITY now disclose the accompanying Quiz #19 change and constructor migration. |
| Downstream deliverable | accepted-v1 contains staging, registry, config, review sidecar, attribution and policy; QC output under build/vocabulary-quality/editorial-accepted-final. Use accept_editorial.load_handoff to retain policy. |
| Reviewer/CI | New local tests and evidence recorded separately. Prior CI #96 applies to 4ade2a1 only; a new CI run and Trường's submitted review/merge are not claimed. |

The owner-approved content and pipeline are ready for downstream use within
VOC-DATA-04. This does not establish VOC-DATA-06 asset/Room/FTS acceptance,
end-to-end Quiz behavior, or sign-off by another team member.

## Reproduction and downstream contract

Follow [accepted delivery](data/voc-data-04/accepted-delivery.md).
The draft corpus-v1 remains unchanged as a historical research/review snapshot.
Its pending flags and negative tests are intentional and do not describe accepted-v1.

For this corpus call `accept_editorial.load_handoff(qc_directory)` to get
`(canonical, quiz_policy)`. Apply `can_be_distractor(policy, targetId, candidateId)`
before sampling; do not use string inequality alone. The helper checks both
directions, including customer/client from separate packs. QUIZ-02 remains
responsible for enforcing this in the Android production generator.

Any wording, provenance or pack-context edit invalidates review checksums.
Renew the owner's affected decisions, rebind reviews and rerun ingestion/QC.
Do not copy old success manifests, mark synthetic fixtures as approved, or
overwrite an existing run directory.
