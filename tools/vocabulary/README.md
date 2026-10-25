# Vocabulary source gate

Requires Python 3.11+; standard library only. Run from repository root:

```powershell
py -3 tools/vocabulary/source_gate.py
py -3 -m unittest discover -s tools/vocabulary -p 'test_*.py' -v
py -3 tools/vocabulary/source_gate.py --source ngsl
```

On Linux/macOS use `python3` instead of `py -3`. The first command validates
ledger structure and verifies artifacts of any source declared eligible.
Pending sources may remain in the research ledger. The last command exits 1
for the current registry, intentionally: no actual snapshot has been approved.
Every saved research artifact and pending snapshot is also checksum-verified.
The future ETL must stop on any nonzero status before reading source records.

After downloading authorized raw data and evidence, compute each artifact:

```powershell
(Get-Item -LiteralPath 'path/to/raw-file').Length
(Get-FileHash -Algorithm SHA256 -LiteralPath 'path/to/raw-file').Hash.ToLowerInvariant()
```

Record actual URLs, timestamps, versions and artifact metadata in the source's
snapshot per the [plan](../../docs/plans/voc-data-01-external-sources.md).
Preserve exact downloaded bytes. Do not clear blockers or add review metadata
until the evidence and attribution have actually been reviewed. The gate accepts
only supported license routes and verifies every listed local file. It does not
download content or validate record-level licensing semantics. FVDP and Tatoeba
audio remain excluded even if somebody changes the eligibility flag.

No fixture grants rights to external content. Tests create temporary synthetic
artifacts and never modify the app catalog or userdata.

## Research capture

```powershell
py -3 tools/vocabulary/capture_sources.py --id unique-research-capture-id
```

By default this prints a dry-run only. Add `--execute` to download fixed official
NGSL/TSL CSVs and source/license pages; for Kaikki
and Tatoeba it captures only research pages. It refuses an existing destination,
records final URL, exact bytes, UTC timestamp and SHA-256, reports unavailable
inputs in snapshot.json, and never changes registry approval. Review snapshot.json
before registering new artifacts. CI runs offline and never calls this command.
Archived bytes have Git text conversion disabled to preserve hashes on all OSes.

The checked-in capture contains 12 downloads and two generated attribution
notices. Raw TSL is decoded with cp1252 for inspection, without modifying bytes.
See the [approval template](../../docs/data/source-approval-template.md) and
[requirements traceability](../../docs/voc-data-01-requirements-traceability.md).

## Revised handoff

```powershell
py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json
py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json --source ngsl-12
py -3 tools/vocabulary/commons_audio_metadata.py --title 'File:Example.ogg'
```

The first command verifies frozen inventory, not release permission. The second
must fail for a candidate/hold source. The third prints the planned metadata API
query; `--execute --output new-file.json` explicitly captures metadata only, never
the media URL. The result stays hold even when license fields are present.
The registry adds Commons as research only and retains ngsl/tsl IDs with aliases
ngsl-12/tsl-12. See [handoff](../../docs/voc-data-01-handoff.md) for provenance,
review and versioned freeze procedures. No live corpus download is part of this
handoff update; existing captures remain unchanged.

## Optimized audit and status

```powershell
py -3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json --json
```

The JSON report lists raw row counts, actual eligibility and blockers separately.
Counts describe raw input, not QA-approved vocabulary. Each local artifact is
hashed once per command, shared across registry and frozen-inventory checks.
The cache lives only within that invocation; file identity/size/timestamps are
checked on reuse and each declared checksum is still compared. Files changed
during hashing are rejected. No persistent checksum cache is used.

Source approval now requires `review.snapshotSha256` matching the report's
`snapshotContentSha256`, in addition to snapshot ID and the actual approval
artifact. This prevents reusing approval after changing content under the same
ID. The content digest excludes the approval artifact to avoid a circular hash.
