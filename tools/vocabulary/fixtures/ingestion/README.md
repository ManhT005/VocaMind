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
