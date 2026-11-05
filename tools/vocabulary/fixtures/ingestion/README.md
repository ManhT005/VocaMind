# Isolated ingestion fixture

Synthetic four-sense content derived from the VOC-DATA-02 contract fixture.
Two fictional source snapshots exercise lexical/meaning versus example-pair
provenance and separate offline credits. FIXTURE-ONLY is not a license grant.
No actual PO, author, approval or production source is represented here.

records.jsonl is UTF-8 enriched staging, examples.txt is synthetic pair metadata,
identities.json is an explicit preallocated synthetic registry. config.json binds
both corpus hashes, four packs and sixteen ordered memberships. Never use these
files as a published baseline. Production-gate integration tests create synthetic
approvals only in TemporaryDirectory using the existing source-gate test helper;
they never write to docs/data/source-registry.json or real approval evidence.

`verified/` is a separate synthetic snapshot using referenceAdapter on both
sources. It enables actual key/content lookup; its examples.txt contains component
JSONL despite its retained fixture filename. No old fixture bytes were changed.
Native NGSL/TSL/Kaikki and linked Tatoeba reader examples run in temporary test
directories, with no claim that real sources have been imported or approved.
