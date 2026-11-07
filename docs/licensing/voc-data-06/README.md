# VOC-DATA-06 — Rights provenance and public notice preparation

**State: audited technical history; public rights and APK release still HOLD.**

This folder reconstructs 135 historical NGSL 1.2 lexical/rank references and 15 no-NGSL-reference cases for the accepted 150-word VocaMind dataset. It preserves original `corpus-v1` and `accepted-v1` files, avoiding a silent source-ID rewrite that would invalidate approved hashes and tests.

- `historical-provenance-audit.json`: 150 individually traceable rows, ranks, locators, editorial components and source references.
- `release-rights-decision.json`: source-scoped rights statuses and reasons a release is blocked.
- `THIRD_PARTY_NOTICES.md`: NGSL attribution and modification notice draft.
- `PUBLIC_DATA_RIGHTS_POLICY.md`: proposed public data licensing policy, **not an operative public grant**.
- `APPROVAL_CHECKLIST.md`: decisions/signatures that must exist before promotion.
- `validate_rights.py`: read-only validation, with a fail-closed release mode.

Run from repository root:

```sh
python docs/licensing/voc-data-06/validate_rights.py
python docs/licensing/voc-data-06/validate_rights.py --require-release # Must fail until approvals and all release blockers are resolved
```

Do not replace `accepted-v1` or `app/src/main/assets/database/catalog.db` just to pass checks. A new signed snapshot, ContentSource, offline attribution and separate Android QA gates must be produced before release. The SRS obligations remain BR-12, DATA-06, AC-12, NFR-VM-12 and DEC-09.
