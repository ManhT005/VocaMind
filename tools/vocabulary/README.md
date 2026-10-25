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
