# VOC-DATA-01 — External vocabulary source ledger / handoff

**Date:** 2026-10-25. **Scope:** research + reproducible acquisition scripts + provenance control, not live corpus download or acceptance. **Reviewers:** VocaMind Data/Content Lead + Tech Lead.

## Architecture observed

- GitHub `ManhT005/VocaMind`, `develop` commit `37f14ca3cdb9086e9960fef988290c0839d5aa3e`: Android Java 17/Clean MVVM shell; no production vocab ETL. `main` currently only README.
- `feature/data-01-catalog`: Room `CatalogDatabase`, entities `ContentSource`, `VocabularyEntry`, FTS4 and small `catalog.db` fixture; not assumed merged into develop.
- `feature/voc-data-02-contract-v1`: canonical envelope/schema validator; stableId keyed by `(N1(lemma), pos, senseIndex)`, one sense/entry and component provenance; document says awaiting reviewer acceptance.
- Excel task owner for VOC-DATA-01 is Thi; supporting Nhân. Follow-ons: VOC-DATA-02 canonical contract → VOC-DATA-03 ingestion → VOC-DATA-04 QC → VOC-DATA-06 release pack. `DATA-01` is a separate Room task.

## Registry and decisions (not release approvals)

| ID | Official resource | License evidence | Initial decision | Packaging rights / obligations |
|---|---|---|---|---|
| ngsl-12 | https://www.newgeneralservicelist.com/new-general-service-list | https://www.newgeneralservicelist.com/new-general-service-list | CANDIDATE | CC BY-SA 4.0; attribute Browne/Culligan/Phillips; SA requirements for adapted data. Download exact 1.2 file and snapshot hash. |
| tsl-12 | https://www.newgeneralservicelist.com/toeic-service-list | https://www.newgeneralservicelist.com/toeic-service-list | CANDIDATE | CC BY-SA 4.0; attribute Browne/Culligan. TOEIC list is a word selection aid; not publisher book contents. |
| kaikki-viwiktionary | https://kaikki.org/viwiktionary/rawdata.html | https://kaikki.org/viwiktionary/ | CANDIDATE | Wiktionary contributor rights: CC BY-SA and GFDL context. Preserve page URL, dump snapshot, Kaikki/Wiktextract description and attribution; legal/release review required. |
| tatoeba-text | https://tatoeba.org/en/downloads | https://en.wiki.tatoeba.org/articles/show/using-the-tatoeba-corpus | CANDIDATE | Sentence text generally CC BY 2.0 FR; CC0 where explicitly marked. Credit each non-CC0 sentence's author and link. EN and VI sentences each have rights. |
| fvdp | Source identity/redistribution permission not established for MVP | n/a | EXCLUDE | Not downloaded or bundled. |
| tatoeba-audio | https://tatoeba.org/en/downloads | https://en.wiki.tatoeba.org/articles/show/using-the-tatoeba-corpus | EXCLUDE | License is contributor-dependent; not included in MVP, as instructed. |
| commons-audio | https://commons.wikimedia.org/wiki/Commons:Licensing | Per-file media metadata + Commons file description page | RESEARCH ONLY | Script queries metadata without fetching media; potential reuse only after per-file rights audit and review. |

**Important:** The NGSL project says word lists are free to use commercially, but specific CC BY-SA obligations continue to apply. The presence of a downloadable dump does not override license conditions. Exact snapshots are dynamic: snapshotDate/hash must be calculated by the script from real downloaded bytes. No checksum in this plan is fabricated. An audit decision can be `include`, `exclude`, or `hold`; `candidate` is NOT `include`.

## Evidence URLs and reference versions seen 2026-10-25

- NGSL 1.2 (April 2023 update according to official page), 2809 words: https://www.newgeneralservicelist.com/new-general-service-list
- TSL 1.2, 1250 words: https://www.newgeneralservicelist.com/toeic-service-list
- Kaikki viwiktionary raw data page (at research time: extraction on 2026-10-21 from viwiktionary dump 2026-10-02; changes over time): https://kaikki.org/viwiktionary/rawdata.html
- Tatoeba per-language English exports: https://downloads.tatoeba.org/exports/per_language/eng/
- Tatoeba per-language Vietnamese exports: https://downloads.tatoeba.org/exports/per_language/vie/
- Tatoeba CSV/CC0 official index: https://downloads.tatoeba.org/exports/
- Tatoeba text vs audio terms: https://en.wiki.tatoeba.org/articles/show/using-the-tatoeba-corpus

## Provenance contract

`SourceRegistry(sourceId, title, officialUrl, licenseCode, licenseUrl, evidenceUrl, attributionText, decision, redistribution)`. Each `SourceArtifact` has official URL, local filename, download UTC timestamp, byteSize, SHA256. Each candidate component maintains locator: NGSL/TSL wordlist identity, Wiktionary page URL and extracted sense candidate, Tatoeba sentence IDs/username/CC0 flag and linked text. Audio candidate metadata never means automatic permission.

The frozen `snapshot.json` is intended as operational evidence. It is *not* license permission by itself. `gate_sources.py` blocks when source approvals or any checksums are absent; downstream content QA is still compulsory. Only after reviewed canonical content passes `validate_contract.py --release` should `VOC-DATA-06` consider building a Room asset.

## Sequential handoff

**D1 VOC-DATA-01:** ledger + metadata-only audio strategy + tested source fetch/verification, decision candidate/exclude, prohibit unknown rights. Human source approval remains outstanding.

**VOC-DATA-02:** reconcile contract branch (not merged), single sense, `meaningVi`, examples, nullable IPA, identity registry. Current code does not include a permanent audio entity: consider audio later via ADR, not by adding unreviewed columns.

**VOC-DATA-03:** adapters for NGSL/TSL shortlist; ViWiktionary lexicon/IPA; Tatoeba EN/VI pairing; stage candidates with all component provenance; deduplicate via canonical lemma/POS and sense review, not raw row position.

**VOC-DATA-04:** human QA meaning/sentence/IPA/license; reject mismatched example translation, unknown POS, uncertain rights; audit sampling and coverage logs.

**VOC-DATA-06:** reviewed 150+ unique stableId, 4 packs min. 40/40/40/30, local attribution, release version + SHA-256; build compatible `catalog.db` separately and test airplane mode/FTS.

## Release blockers

Any source missing reviewer identity, date/evidence or correct attribution; any artifact hash mismatch or missing snapshot; any unreviewed license/media; unreviewed sense mapping; generated translations presented as verified; unapproved `AudioFile` for assets; lack of offline attribution/license notices; incompatible Room schema or stableId drift. Do not mark VOC-DATA-01 Done based on scripts alone: it requires accepted include/exclude decisions backed by evidence.
