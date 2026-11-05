# VOC-DATA-04 editorial corpus for review

This directory now contains substantive learning material: **150 candidate senses**,
each with one selected Vietnamese meaning and an English/Vietnamese example pair.
The four proposed packs contain **40/40/40/30 memberships**, all unique across this
draft. These counts are an early handoff aid, not VOC-DATA-06 release acceptance.

## Authorship and rights disclosure

`editorial-v1.tsv` is an **AI-assisted editorial draft prepared in this session**.
Meanings and example pairs were drafted for this project, not extracted from
NGSL, Kaikki, Tatoeba or another dictionary. No human author, translator, rights
approver or content reviewer is asserted. No license grant is fabricated.

135 headwords also occur in the existing checksum-verified NGSL 1.2 research
snapshot; these records carry a separate lexical/rank reference. NGSL is never
credited as supplying the Vietnamese gloss, English POS choice or example pair.
The other 15 headwords were selected editorially and do not claim an NGSL match.
Each entry has an editorial lexical reference for its selected POS and sense.
IPA is null, with no pronunciation or audio copied or invented.

The proposed editorial source has **pending rights**, and NGSL remains on hold
in the actual VOC-DATA-01 ledger. The existing seven-source gate has no approved
editorial route. A Content Lead/PO must decide the rights and owner responsibilities,
and the source-gate/registry owner must review the new route before production
ingestion. Do not relabel editorial text as NGSL/Kaikki to evade that decision.
The `licenseUrl` on the draft source points to this disclosure, not a license grant;
`PENDING-EDITORIAL-RIGHTS` explicitly means no approved license route exists yet.

## Files to review

- [editorial-v1.tsv](editorial-v1.tsv): editable source, one selected sense per row.
- [Review worksheet](corpus-v1/review-worksheet.md): all 150 meanings and example pairs,
  source line references, proposed identities and reviewer focus notes.
- [staging.jsonl](corpus-v1/staging.jsonl): exact VOC-DATA-03 staging shape, with
  stable lexical/component locators and explicit sense proposals.
- [candidate-catalog.json](corpus-v1/candidate-catalog.json): **preview only**, complete
  contract v1 envelope for inspecting pack membership and mechanical QC.
- [proposals.json](corpus-v1/proposals.json): each published `stableId` is null;
  `proposedStableId` and `proposedSenseIndex` require identity-owner acceptance.
- [identity-registry-proposal.json](corpus-v1/identity-registry-proposal.json): proposed
  registry format to review; it does not change the existing identity registry.
- [reviews-pending.json](corpus-v1/reviews-pending.json): all 150 decisions pending,
  reviewer/date null, semantic flags false; checksums prefilled for this preview.
- [candidate-qc-report.json](corpus-v1/candidate-qc-report.json): machine findings,
  source authorization failures and record-level content review blockers.
- [ingestion-proposal.json](corpus-v1/ingestion-proposal.json): exact proposed artifact
  mappings, adapters and packs. It intentionally fails existing production gates.
- [candidate-manifest.json](corpus-v1/candidate-manifest.json): input/tool/output
  checksums, `candidate-only`, `releaseEligible=false`, no production completion marker.

The candidate envelope uses contract IDs internally solely to test references.
`identityRegistry.status=active` there is a contract preview representation, not
allocation or publication. The proposal list is the authoritative draft status.
The English noun `parent`, for example, has one selected family-relation sense even
though Vietnamese expresses father/mother alternatives; review these cases rather
than interpreting every Vietnamese alternative as a different English sense.
Review near synonyms such as academic research/study in pack context. Distinct
display strings alone never establish good Quiz distractors.

## Reproduce and inspect gates

Python 3.11+ with the existing contract validation dependencies:

```sh
python tools/vocabulary/prepare_corpus.py --output build/vocabulary-corpus/review-a
python tools/vocabulary/prepare_corpus.py --output build/vocabulary-corpus/review-b
python -m unittest discover -s tools/vocabulary -p 'test_prepare_corpus.py' -v
python tools/vocabulary/prepare_corpus.py --require-approved --output build/vocabulary-corpus/approval-check
python tools/vocabulary/ingest.py --config docs/data/voc-data-04/corpus-v1/ingestion-proposal.json --output build/vocabulary-ingestion/editorial-approval-check
```

Draft preparation returns **0 only for preparing candidate material**. The
`--require-approved` invocation returns **1**, with findings for the full 150-entry
preview. Actual ingestion returns **1** at source authorization before parsing
records. Neither command creates approved vocabulary. Never count a preparation
exit 0 as QC approval. New directories are required on every run.

All nine generated files are reproducible and compared against the committed
snapshot by tests and CI. The manifest records raw hashes for the authored TSV
and frozen upstream bytes, normalized tool code hash, and a semantic JSON hash
for the research registry to avoid platform-only CRLF differences. Canonical
locators use the fixed versioned staging path; build directories are reproductions
of that logical snapshot, not new source identities.

## Decisions needed to promote the draft

1. Review/approve actual rights and attribution for lexical NGSL selection and the
   separately authored editorial snapshot. Register the editorial route explicitly
   through a reviewed source-gate extension; do not weaken current gate rules.
2. Identity owner checks the proposed senses against the accepted registry/baseline,
   resolves collisions and publishes the accepted registry version. Do not allocate
   sense indices from rank or copy this proposal over a published registry.
3. Ingest only the approved staging snapshot through VOC-DATA-03. Keep all component
   source bindings, exact bytes and approved attribution evidence.
4. A human content reviewer checks one sense, POS, EN usage, VI correspondence and
   distinct pack meanings per entry; inspect the worksheet and record decisions.
   Generate/rebind the review sidecar against the **actual approved ingestion
   envelope**, whose source metadata differs from this pending preview. Do not
   reuse draft checksums as though they approved the final envelope.
5. Run `quality.py` on that completed ingestion. Supply actual reviewer identity,
   date and decisions. Only successful production QC may hand off to VOC-DATA-05/06
   via `quality.load_validated()`. Final schema/Room/FTS/asset acceptance stays downstream.

No existing source decision, content approval, published registry or Android asset
is modified by preparing this corpus. The files make those review decisions concrete.
