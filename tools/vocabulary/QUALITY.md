# VOC-DATA-04 normalization and quality gates

The [150-sense editorial corpus](../../docs/data/voc-data-04/README.md) supplies
substantive candidate meanings and example pairs, a full review worksheet and
record-bound pending decisions. It is distinct from the four synthetic fixtures
below and cannot be consumed as approved production output.

Build-time Python 3.11+; install the existing contract requirements first.
Integration base: `develop` at `752cce4`. Uses VOC-DATA-01 source authorization,
VOC-DATA-02 canonical v1.0.0 and VOC-DATA-03 verified component adapters unchanged.
No Android schema, UI, runtime network or stableId algorithm changes.

## Reproduce

From repository root (Windows: substitute `py -3` for `python`):

```sh
python tools/vocabulary/ingest.py --fixture --config tools/vocabulary/fixtures/ingestion/verified/config.json --output build/vocabulary-ingestion/qc-input
python tools/vocabulary/quality.py --fixture --input build/vocabulary-ingestion/qc-input --config tools/vocabulary/fixtures/ingestion/verified/config.json --reviews tools/vocabulary/fixtures/quality/reviews.json --output build/vocabulary-quality/qc-a
python tools/vocabulary/quality.py --fixture --input build/vocabulary-ingestion/qc-input --config tools/vocabulary/fixtures/ingestion/verified/config.json --reviews tools/vocabulary/fixtures/quality/reviews.json --output build/vocabulary-quality/qc-b
python docs/data/vocabulary-contract-v1/validate_contract.py build/vocabulary-quality/qc-a/canonical-catalog.json
python -m unittest discover -s tools/vocabulary -p 'test_*.py' -v
```

Use new output directories on every invocation. Exit 0 = pass (explicitly
`fixture-pass` for synthetic data), 1 = failed gate with reports, 2 = CLI/path
misuse. A failed run never writes canonical output, validated JSONL or manifest.
Existing directories are refused so a prior success cannot be reused as a new run.
Consumers must require `manifest.json`, match fixture mode and verify **all** its
output hashes before using a dataset. A partial directory without this last-written
marker is not complete, even if some output files exist after an interrupted write.
`quality.load_validated(directory)` implements this consumer check and returns the
canonical envelope; production is the default. Tests may explicitly pass
`fixture=True`. It checks completion status/version, the exact artifact set,
every checksum, report bindings and canonical contract. Catch failed validation
and stop consumption; never fall back to an older output directory implicitly.
This validates producer artifacts, not a human's identity or source-rights signature.
On a caught publication error QC removes success payloads and records failure
where the filesystem remains writable. A process/power loss can leave partial
files, so consumers must still use the completion marker.

The manifest and all input artifact hashes are checked. QC replays ingestion in
an automatically cleaned temporary directory to recheck actual ledger approvals,
snapshot checksums and component content, and compares canonical bytes, source
mappings, input and tool hashes. It does not trust a claimed approvalStatus or
a rewritten output hash. Original input artifacts remain read only. A change to
the ingestion implementation requires a fresh ingestion run.

## Normalization and gates

T1 is contract `clean_text`: NFC, explicit Unicode White_Space → ASCII space,
collapse and trim. Vietnamese accents and punctuation remain. Display lemma stays
in contract form; normalizedLemma and ID are validated against the existing
identity tuple, never silently repaired. Existing explicit ingestion POS aliases
are reused; unknown POS fails. Non-whitespace controls, surrogates and prohibited
invisible characters fail contract validation rather than being silently deleted.
Normalization is deterministic and idempotent. IPA null passes; empty IPA fails
existing contract rules. Identity registry is never rewritten.

All required text fields, identity registry/hash, duplicate IDs (all claimants),
component provenance, source references, pack membership and baseline compatibility
are checked. Every pack needs four distinct meaning keys. The key is contract
BR-08 applied after T1 (`NFC + whitespace normalization + trim + casefold`), the
same input convention as canonical Quiz data; accents/punctuation are preserved.
Distinct strings alone cannot establish distinct senses: human review is required.
150 words and pack quotas 40/40/40/30 belong to VOC-DATA-06 release acceptance,
not these development gates.

Example lengths outside 5–20 whitespace-delimited words and simple multi-sense
signals (`;`, numbered lists, spaced slash) generate warnings. These heuristics
do not establish semantic correctness. A warning does not itself reject a record;
the content review decision remains mandatory.

## Content review evidence

Supply a repository-local UTF-8 JSON sidecar with `schemaVersion: "1.0.0"`,
`fixtureOnly` matching the dataset, `datasetSha256` (using the same digest function
on the complete normalized envelope) and a `records` array. Binding the envelope
forces renewed review when pack context changes. Each record contains:

```json
{
  "stableId": "90562a98e8686267",
  "entrySha256": "<SHA-256 of normalized canonical entry>",
  "reviewer": "<actual reviewer>",
  "reviewedAt": "2026-11-05",
  "decision": "approved",
  "singleSense": true,
  "exampleSense": true,
  "translationEquivalent": true,
  "distinctMeaning": true
}
```

Use `quality.entry_digest(entry)` on the normalized canonical record: UTF-8 JSON,
sorted keys, no ASCII escaping, separators `(',', ':')`, no trailing newline.
The digest includes all entry fields and component provenance. Wording changes
keep stableId but invalidate this decision until reviewed again. Missing, stale,
duplicate, malformed or pending decisions fail QC. Reviewers must inspect one
sense, EN usage, VI translation and distinction from other pack answers. Hashes
bind a supplied decision to bytes; they do not authenticate the reviewer or
replace team approval. Never generate production approvals automatically.

`fixtures/quality/reviews.json` contains **synthetic test decisions only** for
the four verified ingestion fixtures. No real person is represented as approving
these records, and they cannot pass production mode. Real source approvals and
semantic evidence are still outstanding; current source holds are not relaxed.

## Outputs and handoff

`canonical-catalog.json` is the complete unchanged v1 envelope consumed by
VOC-DATA-05/06 (sources, identityRegistry, packs and packWords retained).
`validated_vocab.jsonl` is a convenience entry stream, not a replacement for
that envelope or provenance. `qc_report.json` and `.md` include input/manifest/
config/review/tool checksums, versions, source snapshots, rerun command, pack
statistics and deterministic errors/warnings. `rejected_records.jsonl` contains
one row per blocking issue, including dataset-level issues, record reference,
stableId where available, field, reason and original component provenance.

Counts partition records: accepted only when the entire batch passes; rejected
counts distinct records with record-level errors; withheld counts other records
blocked by batch/dataset errors. A custody failure before trusted parsing reports
zero input records rather than trusting an unverified envelope. Warnings are
separate. Output/report order is stable and no operational timestamps are added.

Fix rejected records upstream, preserve stableId for wording-only edits, refresh
snapshot/config evidence and content decisions as required, rerun ingestion and
QC in new directories, and inspect pack counts again. Do not silently drop a
record, pick the first duplicate or publish a passing subset of a failed batch.
Fixtures and integration tests cover QC-01…19, deterministic artifacts, stale
reviews, tampered snapshots and refusal to overwrite previous runs.

Final content sign-off, source approvals, Trường's PR review and published CI
remain external handoff steps. This tool does not claim overall AC-04/10/12 or
MVP acceptance and does not build catalog.db.

The consumer integration test builds only an isolated temporary synthetic
database through the existing fixture builder, to prove envelope compatibility.
It never changes app assets or establishes VOC-DATA-06 release acceptance.
The production authorization integration test uses temporary synthetic rights
evidence and exercises the actual source gate, including a revoked approval;
passing that test does not approve the checked-in real source registry.
