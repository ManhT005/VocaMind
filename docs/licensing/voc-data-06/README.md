# VOC-DATA-06 — Rights provenance and public notice preparation

**State: audited technical history; public rights and APK release still HOLD.**

This folder reconstructs 135 historical NGSL 1.2 lexical/rank references and 15 no-NGSL-reference cases for the accepted 150-word VocaMind dataset. It preserves original `corpus-v1` and `accepted-v1` files, avoiding a silent source-ID rewrite that would invalidate approved hashes and tests.

- `historical-provenance-audit.json`: 150 individually traceable rows, ranks, locators, editorial components and source references.
- `release-rights-decision.json`: source-scoped rights statuses and reasons a release is blocked.
- `THIRD_PARTY_NOTICES.md`: NGSL attribution and modification notice draft.
- `PUBLIC_DATA_RIGHTS_POLICY.md`: proposed public data licensing policy, **not an operative public grant**.
- `APPROVAL_CHECKLIST.md`: decisions/signatures that must exist before promotion.
- `validate_rights.py`: read-only validation, with a fail-closed release mode.

Run the provenance audit and the separate app release gate from the repository root.

```sh
python docs/licensing/voc-data-06/validate_rights.py --verify-audit
python docs/licensing/voc-data-06/validate_rights.py --verify-app-release
```

The app release gate currently exits 1 with `APP_RELEASE_BLOCKED`. To validate a real decision against the QA snapshot, prepare the snapshot and supply the decision and intended channel.

```sh
python tools/catalog/package_qa.py --prepare
python docs/licensing/voc-data-06/validate_rights.py --verify-content-rights \
  --snapshot build/vocabulary-corpus/voc06-qa-v2 \
  --approval /path/to/approval.json --channel internal-qa
```

The approval record binds its PO or Content Lead, date, review and channels to
the snapshot manifest and dataset SHA-256. `signedDecision` and `reviewedNotice`
reference sibling evidence files and their hashes. The `components` ledger
covers editorial lexical material, meanings, paired examples and the NGSL
reference. A redistribution decision for NGSL requires attribution, license,
change notice and ShareAlike treatment. `sourceCredits` identifies verified
authors or a responsible publisher with an explicit authorship status. Supported
channels are `internal-qa`, `public-repository` and `public-apk`.

The verifier checks the evidence binding and record consistency. The PO or
Content Lead verifies the signer's authority and supplies the genuine decision.
The recorded project-use decision allows internal QA. A public decision and
editorial sign-off on the four revised pack descriptions are still pending.
See [QA validation](../../voc-data-06-qa-validation.md) for the packaged profile.
