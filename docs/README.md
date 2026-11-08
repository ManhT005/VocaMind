# VocaMind documentation

Project documentation lives in `docs/`. The repository root README is the
project entry point; generated build outputs stay in ignored build directories.

| Document | Purpose |
| --- | --- |
| [PROGRESS-02 plan](plans/progress-02-ui-history.md) | User-provided progress UI/history scope and acceptance matrix |
| [PROGRESS-02 validation](progress-02-validation.md) | Original acceptance and publication/review status |
| [PROGRESS-02 review corrections](progress-02-review-fix-validation.md) | Atomic UI snapshots, exact LD configuration rollback and fresh regression evidence |
| [VOC-DATA-05 validation](voc-data-05-validation.md) | Optional CEFR and topic metadata, evidence gates, and coverage |
| [ARC-04 integration review](arc-04-validation.md) | Week 2 architecture audit, CI evidence, findings and handoff |
| [VOC-DATA-06 QA validation](voc-data-06-qa-validation.md) | Versioned QA catalog in the main QA APK, asset verification, and acceptance gates |
| [VOC-DATA-06 validation](voc-data-06-validation.md) | Verified production QC to staged 150-word Room catalog, bundle/FTS gates, reproducibility and release limitations |
| [DATA-07 plan](plans/data-07-progress-aggregation-queries.md) | Five-hour plan, distinct counts, six-box and quiz history acceptance |
| [DATA-07 validation](data-07-validation.md) | Transactional snapshot reads, fixtures, error mapping and integration evidence |
| [DATA-07 PR #26 fix validation](data-07-fix-validation.md) | DATA-05/06 integration, scope audit, atomic review/progress regression and AC-11 handoff |
| [Local credit metadata contract](data/content-credits-contract.md) | Companion JSON, exact snapshot matching, existing-install compatibility and release limits |
| [ATTR-01 review fix plan](plans/attr-01-fix-plan.md) | R1 regression and proposed R2 offline author/credit contract awaiting owner/reviewer confirmation |
| [ATTR-01 validation](attr-01-validation.md) | Offline attribution UI, executed tests, screenshots and separate AC-12 release blocker |
| [DATA-06 plan](plans/data-06-atomic-review-transaction.md) | Atomic review scope, six-hour plan, test matrix and boundaries |
| [DATA-06 validation](data-06-validation.md) | Canonical writer, snapshot contract, current verification and handoff |
| [DATA-06 PR #24 fix validation](data-06-fix-validation.md) | DATA-04 compatibility, atomic races/rollback, DEC-12 red/green proof and remaining gates |
| [DATA-05 plan](plans/data-05-fts4-search.md) | FTS4 search, async boundary, test matrix and seven-hour plan |
| [DATA-05 validation](data-05-validation.md) | Actual prepack integration, raw 5k profiling, reference-device gate and handoff |
| [DATA-05 PR #23 fix validation](data-05-fix-validation.md) | Develop integration, combined DAO regression, current evidence and remaining acceptance gates |
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
| [QUIZ-03 plan](plans/quiz-03-ui-session.md) | Quiz UI/session, answer locking, idempotent completion and planned validation |
| [QUIZ-03 validation](quiz-03-validation.md) | Quiz UI/session implementation, migration, host/device evidence and review gates |
| [QUIZ-02 plan](plans/quiz-02-generation.md) | Production Quiz generation, normalization, sampling and planned validation |
| [QUIZ-02 validation](quiz-02-validation.md) | Generator, pinned Unicode NFC/casefold, test evidence and QUIZ-03 handoff |

Use lowercase kebab-case filenames for new documentation. Put task plans in
`plans/` and link new documents from this index. Keep historical plans and
validation records separate from the current setup and foundation guides.

[Project README](../README.md)

- [PACK-02 activation validation and handoff](pack-02-validation.md)
- [VOCAB-01 validation và handoff](vocab-01-validation.md)
- [PACK-02 / VOCAB-01 local integration and device QA](pack-02-integration-validation.md)
- [VOCAB-02 validation và handoff](vocab-02-validation.md)
- [VOCAB-02 saved-only decision pending](vocab-02-saved-only-decision.md)
- [VOCAB-02 PR draft](vocab-02-pr-draft.md)
- [VOCAB-02 UI refresh validation](vocab-02-ui-validation.md)
- [VOCAB-02 review follow-up / develop integration](vocab-02-review-followup.md)
- [VOCAB-02 first-save contract acceptance](vocab-02-first-save-validation.md)
- [VOCAB-02 DATA-06/DATA-07 integration](vocab-02-data0607-integration.md)
- [VOCAB-02 focused emulator QA](vocab-02-device-qa.md)
- [VOCAB-02 probe binding replay on 49ca6b4](vocab-02-probe-replay-49ca6b4.md)

- [VOCAB-02 integration and probe binding recheck](vocab-02-recheck-993e70a.md)
