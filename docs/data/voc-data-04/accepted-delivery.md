# VOC-DATA-04 accepted editorial delivery — 2026-11-05

The owner `thanhcodeai` accepted the 150 worksheet records and instructed that the
project's AI-generated content needs no external-source approval. The recorded
decision is [owner-acceptance.json](owner-acceptance.json), bound to the original
TSV, worksheet and canonical candidate hashes. This is the user's acceptance,
not a fresh independent semantic review by Codex or Trường.

## What changed

- `accepted-v1` materializes a separate project-generated source registry, frozen
  inventory, editorial-only staging, first local identity registry and ingestion
  config. The original draft and seven-source research ledger remain unchanged.
- All 150 meanings, POS, examples, stable IDs and pack memberships are preserved.
  Provenance and source metadata are changed explicitly; review checksums are
  rebound to the final envelope rather than reusing pending draft checksums.
- Project-use acceptance is represented as `PROJECT-USE-OWNER-ACCEPTED`, not an
  invented CC/public-domain grant. Historical NGSL lexical/rank references remain
  visible in corpus-v1; they are not imported or marked approved by this route.
- The authorization gate still verifies exact artifact bytes, inventory, snapshot
  digest and the owner's acceptance. External-source holds/exclusions stay intact.
- `quiz-policy.json` excludes research/study, home/house and customer/client from
  being distractors for each other, in both directions across active packs.
  This is a handoff requirement for QUIZ-02; no Android generator exists yet.

The local identity registry registers the existing deterministic proposals for
this editorial snapshot. No released registry/asset is overwritten, and no
comparison against an unavailable published production baseline is claimed.

## Reproduce

Use Python 3.11+ with the contract validation dependencies installed. Run from
the repository root, using fresh output directories:

```sh
python tools/vocabulary/accept_editorial.py --output build/vocabulary-corpus/accepted-reproduction
# Compare the generated files with docs/data/voc-data-04/accepted-v1.
python tools/vocabulary/ingest.py --config docs/data/voc-data-04/accepted-v1/ingestion-config.json --output build/vocabulary-ingestion/editorial-accepted-final
python tools/vocabulary/quality.py --input build/vocabulary-ingestion/editorial-accepted-final --config docs/data/voc-data-04/accepted-v1/ingestion-config.json --reviews docs/data/voc-data-04/accepted-v1/reviews.json --output build/vocabulary-quality/editorial-accepted-final
python tools/vocabulary/accept_editorial.py --verify build/vocabulary-quality/editorial-accepted-final
python -m unittest discover -s tools/vocabulary -p 'test_*.py' -v
python -m unittest discover -s docs/data/vocabulary-contract-v1 -p test_contract.py -v
python -m unittest discover -s tools/catalog -p 'test_*.py' -v
```

On this machine, embedded Python 3.13.9 requires an explicit module search path.
The equivalent launcher used was:

```powershell
& '.tools/python/python.exe' -c "import sys,runpy; sys.path.insert(0,'tools/vocabulary'); runpy.run_path('tools/vocabulary/accept_editorial.py',run_name='__main__')" --verify build/vocabulary-quality/editorial-accepted-final
```

The checked-in snapshot can be reproduced; the exporter refuses to overwrite
it. After the owner renews acceptance for changed input, generate a new version
and rebind all evidence. Existing QC directories are immutable run outputs.

## Consumer contract

```python
data, policy = accept_editorial.load_handoff(qc_directory)
# Before adding a distractor:
eligible = accept_editorial.can_be_distractor(policy, target_id, candidate_id)
```

The loader calls the existing production QC consumer, checks the dataset digest,
all policy references and the accepted pair list. Tests cover reversed pairs,
cross-pack customer/client and a tampered restriction. Consumers must retain the
policy alongside the canonical envelope; the existing generic load_validated
returns only canonical content.

## Verification and scope

Final local results are recorded in
[accepted evidence](../../evidence/voc-data-04/accepted-verification.md).
A successful QC manifest is needed before consuming a run. Corpus quotas remain
40/40/40/30; output contains 150 accepted entries. No catalog.db/app asset is built
by VOC-DATA-04. Existing source/content-negative tests remain in place.

This completes the owner-approved local content/pipeline delivery. New CI and
Trường's review/merge are separate repository workflow steps, not approvals
fabricated by this change. Full end-to-end AC-04/10/12 acceptance is not claimed.
