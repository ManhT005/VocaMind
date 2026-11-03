# VOC-DATA-01 — 1,000-word research candidate staging

This is an **extension of** `feature/voc-data-01-external-sources`, not an alternative registry, not a release catalog, and not a bypass of `source_gate.py`. Python 3.11+, standard library.

## Start with existing audited NGSL data

The branch already has frozen source evidence at `docs/data/evidence/voc-data-01-research-20261025/ngsl/words.csv`. Its declared SHA-256 is checked **before** parsing.

```bash
python3 -m unittest discover -s tools/vocabulary -p 'test_*.py' -v
python3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json
python3 tools/vocabulary/prepare_1000_candidates.py --limit 1000 --output data/research/ngsl-1000-v1
```

Produces `candidates.jsonl` (ranked NGSL headwords with provenance and empty enrichment fields) and `report.json` (coverage and blockers). Add `data/research/` to your local exclude list or keep it untracked; never commit generated unreviewed artifacts to `app/src/main/assets`.

The source gate **must** continue to reject production ingestion of sources whose `decision=hold` even when this research-only generator can parse a frozen, checksum-verified input. It does not mark sources approved.

## Optional research enrichment

```bash
python3 tools/vocabulary/prepare_1000_candidates.py --limit 1000 \
  --kaikki-jsonl /absolute/path/to/verified-viwiktionary-raw.jsonl.gz \
  --tatoeba-pairs /absolute/path/to/curated-linked-pairs.jsonl \
  --commons-metadata /absolute/path/to/commons-audio-metadata.jsonl \
  --output data/research/ngsl-1000-enriched-v1
```

- **Kaikki**: adapter reads viwiktionary raw JSONL English entries, retaining candidate glosses and IPA only. No source approval inferred; source snapshot, extraction version and field-level provenance still must be verified in VOC-DATA-03. Dictionary gloss can be polysemous or wrong; a reviewer must choose an exact sense.
- **Tatoeba**: the pairs JSONL is a human-assembled *linked* sentence-candidate input: `{"lemma":"book","linked":true,"en":{"id":1,"text":"...","author":"...","license":"..."},"vi":{"id":2,"text":"...","author":"...","license":"..."}}`. This tool requires both author/license fields and IDs, but does not verify the link against upstream or grant rights. That validation remains a required downstream gate. Do not use Tatoeba audio.
- **Commons audio**: `commons_audio_metadata.py --title 'File:...' --execute --output ...` researches *one file only*. For this adapter, combine individually captured metadata into JSONL, adding the explicit lemma. Metadata alone is not the audio and **does not grant permission to download or package it**. Each recording needs rights, artist, attribution, quality, accent and file integrity review.
- If any optional input is absent, the respective candidate array is empty rather than invented.

## Release readiness criteria

VOC-DATA-01 only guarantees a reproducible headword research shortlist, not 1,000 usable learning entries. To claim release quality: record source approval matched to actual snapshot/hash; verify the exact permission to package and redistribute derivative text/audio; curate 1,000 unique stable senses with accurate Vietnamese meaning, POS, English example and verified Vietnamese paired translation; accept IPA nullable; test audio clips for licensed reuse and speaker/word alignment; provide offline attribution and license notices; run the approved VOC-DATA-02 canonical validator and then Android catalog/FTS asset tests in VOC-DATA-06.

**Until then all exported candidates stay `releaseEligible=false` and `stableId=null`**. Never auto-issue stable IDs from NGSL rank or audio matches. No Room schema or existing source registry has been changed.

## Tests

Offline synthetic tests cover deterministic ranking, checksum tampering, Kaikki candidate-only enrichment, Commons metadata hold and rejection of unlinked Tatoeba examples. The generator doesn't access the network; it only reads explicit local inputs.
