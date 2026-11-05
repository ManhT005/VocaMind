# VOC-DATA-03 local ingestion

Implementation candidate for Trường / reviewer Nhân. Contract v1.0.0 remains
pending team acceptance. This tool does not approve sources or establish AC-12.
Python 3.11+ and `docs/data/vocabulary-contract-v1/requirements-validation.txt`
are required; no Android dependency is added.

From repository root (Windows with Python launcher: replace `python3` by `py -3`):

```sh
python3 tools/vocabulary/ingest.py --help
python3 tools/vocabulary/ingest.py --fixture --config tools/vocabulary/fixtures/ingestion/config.json --output build/vocabulary-ingestion/run-a
python3 tools/vocabulary/ingest.py --fixture --config tools/vocabulary/fixtures/ingestion/config.json --output build/vocabulary-ingestion/run-b
python3 docs/data/vocabulary-contract-v1/validate_contract.py build/vocabulary-ingestion/run-a/canonical-catalog.json
python3 tools/vocabulary/ingest.py --fixture --dry-run --config tools/vocabulary/fixtures/ingestion/config.json --output build/vocabulary-ingestion/check-a
```

Output must be a **new** directory below `build/vocabulary-ingestion/`.
Existing directories, raw, evidence, baselines and app asset paths are refused.
Resolved paths enforce the boundary, including parent symlinks. Input paths are
repository-relative. Inputs, registries and baselines are read only.
`--dry-run` verifies gate/checksums but parses zero records and only writes
`check.json`. It cannot authorize a hold/exclude source.

Exit 0: complete development-contract batch, or gate-only dry run.
Exit 1: gate, encoding, parse, mapping or validation failure; inspect
`failure.json` and `reject-report.json`. Exit 2: CLI/path/preflight misuse.
An output `manifest.json` is the completion marker and exists only for a
successful import. A process/disk failure may leave incomplete files without
this marker; downstream consumers must require manifest and verify its hashes.
Operational timestamps are not part of deterministic artifacts.

## Adapter and configuration

`reviewed-jsonl-v1` is an **enriched, reviewed staging format**, not a native
NGSL, TSL, Kaikki or Tatoeba parser. Only explicit UTF-8 is supported; no encoding
guessing, scraping, language inference, sense merging or invented translations.
Native component readers described below resolve NGSL/TSL/Kaikki evidence;
the orchestration input remains reviewed staging JSONL. In particular TSL
cp1252 raw cannot be passed as the orchestration input.

The checked-in config is a runnable example. It selects tool/adapter/schema/content
versions, encoding, input artifact, identity registry array, optional full canonical
baseline, source mappings, packs and memberships. Pack fields follow the canonical
schema; pack version must match contentVersion. Mapping keys:

| Key | Meaning |
| --- | --- |
| registryId | VOC-DATA-01 ID or supported alias (ngsl-12 → ngsl, tsl-12 → tsl) |
| snapshotId / upstreamVersion | Exact reviewed snapshot/version, not latest |
| canonicalId | Canonical source slug for this snapshot; change ID when snapshot changes |
| artifact / sha256 | Exact corpus data artifact path and byte checksum |
| metadata | Fixture-only source metadata; ignored for real sources |

For real use omit `--fixture`, set fixtureOnly=false and provide sourceRegistry
and inventory paths. Every mapped source passes existing audit, frozen inventory
and `authorize()` before any record parsing. Corpus artifact must have role=data
in the approved snapshot. Main JSONL itself must be one of those verified artifacts;
an independently transformed staging file needs its own reviewed evidence route.
This deliberately cannot turn an approved wordlist into approved translations.
Production metadata comes from the authorized registry, corpus bytes, reviewed
attribution artifact and approval evidence, never from config approval strings.
snapshotDate is UTC retrieval date, not an invented upstream release date.
One corpus artifact per canonical mapping is supported in this version.

Each JSONL line is an object with lemma, pos, senseIndex, meaningVi, exampleEn,
exampleVi, nullable/omitted ipa, sourceId, provenance and a stable `locator`.
Records are separated by LF; CRLF is also accepted. The last record may omit its
terminal newline. A terminal LF does not add an empty record, but actual blank or
whitespace-only lines are records rejected as INVALID_PARSE with physical line
numbers. An empty file has zero records and fails final envelope validation.
Unicode U+0085/U+2028/U+2029 inside JSON strings are content handled by T1,
whether written literally in UTF-8 or as JSON escapes; they never split records.
`sourceId` uses canonicalId. Provenance follows canonical component/sourceId/locator;
each locator starts with the mapped `artifact#` followed by a stable source record
key. The key must be nonblank under contract T1 normalization; `artifact#` and
`artifact#   ` are rejected. Valid locators can be shared by records/components;
legacy fixture mode checks only locator structure. Production requires
`referenceAdapter` for every mapping and verifies both key existence and component
content. This mechanical comparison does not replace content QA.
Keep edition/extraction/sense or EN/VI sentence IDs, authors, licenses and
pair links in the raw artifact and attribution notice. A physical line locator
identifies parse/locator errors and is the fallback for invalid row locators;
it never allocates identity.

POS accepts all canonical enum values and explicit aliases n/n./NOUN, v/v./VERB,
adj/adj., adv/adv. Unknown labels are rejected; `other` must be explicitly reviewed.
Normalization/identity/meaningKey and validation use VOC-DATA-02 helpers unchanged.
T1 transformations are listed by locator and field in normalization.json; raw bytes
remain intact. Identity registry is copied including retired history, never extended
or rewritten. Unregistered senses are quarantined; proposed new indices must be
reviewed separately before supplying a new registry version.

## Artifacts and failure policy

Successful import produces canonical-catalog.json (schema envelope and field order),
normalization.json, reject-report.json and manifest.json. Sources/entries/registry
sort by ID; packs by (orderNo,id); memberships by (packId,orderNo,vocabularyId).
All files are UTF-8 without BOM, LF, indent=2. Manifest binds exact input/config/
registry/baseline hashes, code/schema/gate hashes, Python/Unicode/jsonschema
versions, mapping, counts and output hashes. Its own hash is computed externally.
Code hashes normalize Git CRLF to LF; input hashes always represent actual bytes.

Strict batch policy: any record reject blocks canonical publication. Multiple
reasons may refer to one row; read counts physical JSONL lines, accepted counts
mapped nonconflicting records, rejected = read - accepted. Duplicate identity
claims reject **all** claimants, including identical duplicates. No first row wins.
Source failures (including INVALID_ENCODING) are separate from record rejects.
Malformed field types are record-level `INVALID_FIELD_TYPE` rejects, with indexed
fields such as `provenance[0].sourceId`; scanning continues through later lines.
Invalid row locators use `input-path#line=N` for diagnostics. Malformed provenance
locators always report that physical input line and the specific reference field.
Stable record reasons: INVALID_PARSE, UNSUPPORTED_POS, MISSING_FIELD,
UNRESOLVED_SENSE, IDENTITY_CONFLICT, MISSING_PROVENANCE,
INVALID_SOURCE_REFERENCE, CANONICAL_VIOLATION. Final validator errors preserve
contract reason codes, including baseline drift/removal and pack diversity.

Same bytes/config/versions produce identical outputs in independent directories.
Changing raw row order changes snapshot SHA-256, so canonical **source metadata**
must reflect that change; entries/identity/pack order stay identical. Preserving
the old source hash would violate the snapshot contract. Nhân needs to confirm
this interpretation of ING-03; no snapshot hash is concealed to claim equal bytes.

Tests are automatically included by existing CI `tools/vocabulary/test_*.py`
discovery. Run all vocabulary, contract and catalog suites as in their READMEs.
`--release` against fixture output must fail. A development pass does not prove
content QA, 150 real words, approved rights or Android asset readiness.

## Verified component adapters (2026-11-05)

Run the new isolated example (the original fixtures are preserved):

```sh
python3 tools/vocabulary/ingest.py --fixture --config tools/vocabulary/fixtures/ingestion/verified/config.json --output build/vocabulary-ingestion/verified-a
python3 tools/vocabulary/verify_ingestion.py --fixture --config tools/vocabulary/fixtures/ingestion/verified/config.json --output build/vocabulary-ingestion/proof-a
```

Each source binding may select `referenceAdapter`. It is mandatory outside fixture
mode. All source gates and corpus checksums pass before any adapter parses records;
dry-run parses none. The artifact is rehashed before reference parsing. No implicit
fallback is used for unsupported formats or encodings.

| referenceAdapter | Bytes / key after artifact# | Evidence compared |
| --- | --- | --- |
| reviewed-jsonl-v1 | UTF-8 JSONL / row.locator | lexical lemma/POS, meaningVi, example pair, IPA |
| component-jsonl-v1 | UTF-8 JSONL / key | Explicit component and its matching canonical text fields |
| ngsl-csv-v1 | UTF-8 with optional BOM; Lemma,SFI Rank / rank=N | Lexical lemma only |
| tsl-csv-v1 | cp1252; Word,TSL Rank / rank=N | Lexical lemma only |
| kaikki-jsonl-v1 | UTF-8 English entries from Vietnamese edition / line=N;sense=M or line=N;sound=M | Lemma/POS, one exact gloss or IPA; ordinals are 1-based source positions |
| tatoeba-linked-jsonl-v1 | UTF-8 reviewed pair export / en=N;vi=M | Both sentence texts, positive IDs, linked=true and nonblank author/license on each sentence |

Component JSONL records contain `key`, `component` and the fields for that component:
lexical: lemma/pos; meaningVi: meaningVi; examplePair: exampleEn/exampleVi; ipa: ipa.
Tatoeba pair export uses `{linked:true,en:{id,text,author,license},vi:{...}}`, the
existing research tool's explicit pair structure. It is **not** an adapter for
upstream sentences/links TSV files. Link/credit extraction into that export still
needs a reviewed snapshot route; author/license values are retained in the bound
artifact and offline attribution comes from its approved attribution artifact.

Lemma/POS comparison uses the existing contract helpers/aliases; text comparison
uses T1. Wordlist rank and Kaikki source sense ordinal never allocate senseIndex.
The reviewed staging must select an already registered sense and provide all
required components. Wordlists alone cannot produce a canonical learning entry.
Missing keys produce PROVENANCE_KEY_NOT_FOUND; differing component content produces
PROVENANCE_CONTENT_MISMATCH with input file/line and indexed provenance field.
Multiple records/components may share a source key. Strict batch rejection remains.
Malformed reference artifacts fail at source stage `references` before mapping.

`manifest.provenanceContentVerified` is true only when every mapped artifact was
indexed. The original structural fixture remains available for old regression
cases; it reports false and cannot be used in production mode.

## Remaining external inputs and handoff decisions

Obtain the current blockers with existing audited commands:

```sh
python3 tools/vocabulary/gate_sources.py --snapshot docs/data/snapshot.json --json
python3 tools/vocabulary/source_gate.py --source ngsl --json
```

The first command verifies inventory; it does not approve a source. The second
must fail while NGSL is hold. Repeat with the relevant registry source ID.
NGSL/TSL need per-snapshot rights/technical review; Kaikki needs corpus, license
route and credit; Tatoeba needs sentence metadata, authors/licenses, links and
sign-off. FVDP/audio remain excluded. Capture and approval belong to VOC-DATA-01.

Concrete proposals for contract review (not recorded as accepted): apply existing
canonical v1.0.0 unchanged; content/ETL proposes sense registry updates with contract
review; integration stores the first approved canonical envelope plus SHA-256 as
the release baseline. Until its location and custody are confirmed, pass a reviewed
baseline explicitly via config, or report baselineCompared=false. Do not invent a
baseline. Exact same snapshot/config/versions must match all four output files;
reordered raw bytes change source hash while identity and entry ordering stay fixed.
Semantic QA is VOC-DATA-04 and asset build is VOC-DATA-06. This task does not write
catalog.db or app assets. Real import and AC-12 remain pending real approval and QA;
passing fixture tests never closes those gates.
