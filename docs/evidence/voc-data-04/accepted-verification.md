# Accepted editorial verification — 2026-11-05

Scope: current local VOC-DATA-04 owner-acceptance changes on
feature/voc-data-04-quality-gates, based on 4ade2a1. No new commit/CI is claimed.

Runtime: embedded Python 3.13.9, existing jsonschema dependency. Local commands
use the runpy launcher documented in
[accepted-delivery.md](../../data/voc-data-04/accepted-delivery.md).

| Check | Result |
| --- | --- |
| Vocabulary suite, unittest discover tools/vocabulary test_*.py | 112 tests, OK |
| Contract suite, unittest discover docs/data/vocabulary-contract-v1 test_contract.py | 39 tests, OK |
| Catalog suite, unittest discover tools/catalog test_*.py | 5 tests, OK |
| Actual ingestion, accepted-v1/ingestion-config.json | Exit 0; output build/vocabulary-ingestion/editorial-accepted-final |
| Actual QC, non-fixture, accepted-v1/reviews.json | Exit 0; pass; 150 accepted, 0 rejected/withheld; no errors/warnings |
| Consumer, accept_editorial.py --verify build/vocabulary-quality/editorial-accepted-final | Exit 0; fixtureOnly=false; 150 entries; release contract pass; all three pair restrictions verified |
| Snapshot reproduction | Test compares every accepted-v1 file byte-for-byte with compile_accepted() |
| Stale approval, revoked decision, altered artifacts/policy, external-source bypass | Negative tests pass |
| git diff --check | Pass; only line-ending conversion warnings for existing text files |

Logs: [vocabulary](accepted-vocabulary-tests.txt),
[contract/catalog](accepted-contract-catalog-tests.txt).
Actual [QC report](accepted-qc-report.json) includes checksums and pack counts
40/40/40/30. The report is copied from the final actual run, not the temporary
synthetic integration test.

Consumer dataset SHA-256:
`c066d4a363d58c4c7226b716f1080b25a9e47b124a4007f4c3d16d67283a7fb8`.

Tests confirm research/study, home/house and cross-pack customer/client are
excluded in both directions by the handoff helper. They do not establish that
the future Android QUIZ-02 generator has been implemented or integrated.

Android files/dependencies/assets are unchanged by this acceptance update, so
Gradle/device tests were not rerun. Earlier CI #96 and 195 Android unit tests
belong to 4ade2a1 and are not presented as verification of these new changes.
The workflow now verifies production editorial ingestion/QC/consumer on its
next run. No Trường review, merge or VOC-DATA-06 asset acceptance is asserted.
