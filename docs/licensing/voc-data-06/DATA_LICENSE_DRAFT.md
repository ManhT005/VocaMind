# VocaMind vocabulary data — PUBLIC DATA LICENSE (DRAFT / NOT EFFECTIVE)

**Version:** 0.1 draft, 2026-11-07
**Status:** IN REVIEW. **Not a public permission grant.**
**Dataset:** `editorial-accepted-v1`, 150 entries, dataset digest `c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8`.

## 1. Scope of this proposed document

The VocaMind vocabulary dataset combines historically referenced open word-list information and project-editorial content. Rights may differ per component. No rights are asserted or granted beyond those that the relevant licensor actually has authority to grant. This document concerns **vocabulary data only**, not software source code, UI, artwork, trademarks or audio.

## 2. Third-party material — existing license survives

**New General Service List (NGSL) 1.2** — Charles Browne, Brent Culligan, Joseph Phillips.

Source: https://www.newgeneralservicelist.com/new-general-service-list

Source license: **CC BY-SA 4.0 International**: https://creativecommons.org/licenses/by-sa/4.0/legalcode

Historical review of the candidate catalog identifies 135 lemma/rank references to NGSL. This is a *provenance finding*; PO/Content Lead must decide whether and which protected NGSL material is actually incorporated in the distributed dataset. Any NGSL material to which CC BY-SA 4.0 applies remains under that license, including required attribution, changed-material indication and ShareAlike treatment for covered adaptations. VocaMind is not affiliated with or endorsed by NGSL creators.

Reference snapshot: `NGSL_1.2_stats.csv`, archived `docs/data/evidence/voc-data-01-research-20261025/ngsl/words.csv`, SHA-256 `2098bab8955a120a9766c6282a51d7d578c6cb0a7d946600d2ffb73ba25a0b44`.

## 3. Project-original/editorial material — proposed grant, subject to authorization

**No license is granted yet.** The following language is an *option* for PO to approve **only for components that a verified rights-holder is legally able to license**:

> **Proposed option: Creative Commons Attribution 4.0 International (CC BY 4.0).**
> The authorized rights-holder `[VERIFIED RIGHTS-HOLDER NAME]` licenses its identified original/editorial portions in snapshot `[APPROVED VERSION AND CHECKSUM]`, namely `[EXACT INCLUDED FIELDS]`, under Creative Commons Attribution 4.0 International, https://creativecommons.org/licenses/by/4.0/legalcode, to the extent copyright and related rights subsist and are owned/controlled by that rights-holder. This statement does not relicense NGSL or any other third-party material. Required attribution: `[EXACT REVIEWED TEXT]`. Modifications: `[CHANGE HISTORY]`. Effective date and signed authorization: `[SIGNER, DATE, EVIDENCE LINK]`.

**The quotation above is NOT in force.** The team may instead choose a different lawful permission model after rights review. If a component of the combined output is an adaptation of NGSL protected by CC BY-SA, that component's ShareAlike obligations cannot be overridden by an unrelated proposed CC BY license.

AI generation alone is not evidence of authorship, ownership, originality, or eligibility for copyright. `PROJECT-USE-OWNER-ACCEPTED` documents internal acceptance only. No verified individual author or exclusive rights-holder has been identified for the editorial text in the current signed artifacts.

## 4. Source attribution to be shipped with any authorized snapshot

For NGSL-covered material, show the dataset title, creators, original URL, CC BY-SA link, and transformations (selection, four topical groups, stable IDs); provide this information offline in the VocaMind Attribution screen. For separately licensed editorial text, show the verified rights-holder/attribution terms **if confirmed**. Never label TSL, Kaikki or Tatoeba as imported sources without actual record-level evidence.

## 5. Preconditions for the status to change to EFFECTIVE

- [ ] Content Lead signs the component provenance and meaning/example QA records.
- [ ] PO or authorized rights-holder signs the editorial public permission and its exact scope.
- [ ] NGSL rights/ShareAlike assessment and attribution notice are approved for the exact snapshot.
- [ ] Final immutable data and legal notice hashes are recorded; approved release channels specified.
- [ ] New accepted snapshot is created instead of editing the frozen accepted-v1.
- [ ] Offline UI, APK and DATA-08 release gates pass independently.

**Decision:** HOLD. **Signer:** UNSIGNED. **Effective date:** NONE.

Until every applicable precondition has evidence, `releaseAuthorized=false`. This draft should **not** be distributed as an operative `LICENSE` file.
