# VocaMind documentation

Project documentation lives in `docs/`. The repository root README is the
project entry point; generated build outputs stay in ignored build directories.

| Document | Purpose |
| --- | --- |
| [VOC-DATA-04 editorial corpus](data/voc-data-04/README.md) | 150 drafted senses with EN/VI examples, source lineage, pending reviews and approval blockers |
| [VOC-DATA-04 validation](voc-data-04-validation.md) | Normalization, record/pack/source/content gates, reproducibility and test evidence |
| [VOC-DATA-04 acceptance checklist](voc-data-04-acceptance-checklist.md) | Definition of Done, real-source blockers and downstream owner handoff |
| [VOC-DATA-04 CLI](../tools/vocabulary/QUALITY.md) | QC commands, review sidecar and verified consumer entrypoint |
| [VOC-DATA-03 acceptance checklist](voc-data-03-acceptance-checklist.md) | SRS v1.1 trace, adapter decision, registry/baseline custody and merge/task/release gates |
| [VOC-DATA-03 validation and handoff](voc-data-03-validation.md) | Gated local ingestion, fixture checksums, real-source blockers and pending contract decisions |
| [VOC-DATA-03 CLI and adapter](../tools/vocabulary/INGESTION.md) | Config, reviewed JSONL mapping, deterministic output and reject policy |
| [DATA-03 DAO foundation plan](plans/data-03-dao-foundation.md) | Query contracts, active due/new selection, transaction boundaries, fixtures and 7-hour implementation plan |
| [DATA-03 validation](data-03-validation.md) | DAO contracts, batch/transaction semantics, fixture results and repository handoff |
| [DATA-03 acceptance checklist](data-03-acceptance-checklist.md) | Integration evidence, G-01…G-13 gates, contract decisions and pending reviewer acceptance |
| [DATA-03 acceptance evidence](data-03-acceptance-evidence.json) | Actual JUnit/Python counts, report/APK hashes and verified input digest |
| [Local credit metadata contract](data/content-credits-contract.md) | Companion JSON, exact snapshot matching, existing-install compatibility and release limits |
| [ATTR-01 review fix plan](plans/attr-01-fix-plan.md) | R1 regression and proposed R2 offline author/credit contract awaiting owner/reviewer confirmation |
| [ATTR-01 validation](attr-01-validation.md) | Offline attribution UI, executed tests, screenshots and separate AC-12 release blocker |
| [VOC-DATA-01 revised handoff plan](plans/voc-data-01-source-ledger-and-handoff.md) | User-provided candidate/hold semantics, metadata-only audio research and sequential handoff |
| [VOC-DATA-01 handoff](voc-data-01-handoff.md) | Frozen inventory commands, ID aliases, component provenance and human acceptance blockers |
| [VOC-DATA-01 requirements traceability](voc-data-01-requirements-traceability.md) | SRS and assignment evidence, task boundaries and approval responsibilities |
| [Source approval template](data/source-approval-template.md) | Per-snapshot PO/Content Lead sign-off, pending real approval |
| [VOC-DATA-01 plan](plans/voc-data-01-external-sources.md) | Research scope, registry/snapshot gate design, tests and DoD |
| [VOC-DATA-01 validation](voc-data-01-validation.md) | Implemented gate, local results and remaining acquisition/review blockers |
| [VOC-DATA-01 source/license ledger](voc-data-01-source-ledger.md) | Source evidence, MVP include/exclude decisions, attribution and ingestion gates |
| [Vocabulary source registry](data/source-registry.json) | Machine-readable source decisions and pending snapshot/license blockers |
| [DATA-02 plan](plans/data-02-user-database.md) | UserDatabase scope and acceptance criteria |
| [DATA-02 validation](data-02-validation.md) | Schema decisions, persistence tests and remaining acceptance checks |
| [Android setup](android-setup.md) | Environment requirements, Android Studio setup and build commands |
| [Android foundation](android-foundation.md) | SDK, dependency and architecture baseline |
| [ARC-01 plan](plans/arc-01-android-foundation.md) | Original implementation plan and acceptance criteria |
| [ARC-01 validation](arc-01-validation.md) | Recorded verification results and remaining manual checks |
| [ARC-02 plan](plans/arc-02-navigation-shell.md) | Navigation shell scope and acceptance criteria |
| [ARC-02 validation](arc-02-validation.md) | Navigation, lifecycle, offline and Material UI verification |
| [ARC-03 plan](plans/arc-03-ci-foundation.md) | CI build and local unit test gate scope and acceptance criteria |
| [ARC-03 validation](arc-03-validation.md) | Local checks, CI verification and pending branch protection setup |
| [QUIZ-01 plan](plans/quiz-01-domain-model.md) | Pure Java Quiz models, generator contract, state invariants and planned validation |
| [QUIZ-01 generator fix plan](plans/quiz-01-fix-plan.md) | Repeated-meaning fake regression, REG-01..07 and verification scope |
| [QUIZ-01 validation](quiz-01-validation.md) | Implemented domain contract, test evidence, limitations and integration handoff |
| [PROGRESS-01 plan](plans/progress-01-contract.md) | Progress summary, Quiz history, use case/view-state contracts and planned validation |
| [PROGRESS-01 handoff](progress-01-handoff.md) | Progress models, repository/use case/view state contracts and integration responsibilities |
| [PROGRESS-01 validation](progress-01-validation.md) | Executed checks, test counts and remaining delivery steps |
| [PROGRESS-01 SRS audit](progress-01-srs-audit.md) | Direct comparison of both supplied SRS documents, assignment, plan and implemented contracts |
| [PROGRESS-01 PR description draft](progress-01-pr-description.md) | Integration results, tested source and pending CI/data/review for PR #13 |
| [LEARN-01 plan](plans/learn-01-leitner-scheduler.md) | Pure Java Leitner scheduler rules, design and task boundaries |
| [LEARN-01 validation](learn-01-validation.md) | Implemented scheduler rules, test evidence and handoff |
| [LEARN-02 validation](learn-02-validation.md) | TC-SCHED-01..10 and boundary test coverage for the Leitner scheduler |
| [UI sketch](sketchs.png) | Visual reference for future feature screens |
| [QUIZ-02 plan](plans/quiz-02-generation.md) | Production Quiz generation, normalization, sampling and planned validation |
| [QUIZ-02 validation](quiz-02-validation.md) | Generator, pinned Unicode NFC/casefold, test evidence and QUIZ-03 handoff |

Use lowercase kebab-case filenames for new documentation. Put task plans in
`plans/` and link new documents from this index. Keep historical plans and
validation records separate from the current setup and foundation guides.

[Project README](../README.md)

- [PACK-02 activation validation and handoff](pack-02-validation.md)
- [VOCAB-01 validation và handoff](vocab-01-validation.md)
- [PACK-02 / VOCAB-01 local integration and device QA](pack-02-integration-validation.md)
