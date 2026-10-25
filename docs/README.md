# VocaMind documentation

Project documentation lives in `docs/`. The repository root README is the
project entry point; generated build outputs stay in ignored build directories.

| Document | Purpose |
| --- | --- |
| [Android setup](android-setup.md) | Environment requirements, Android Studio setup and build commands |
| [Android foundation](android-foundation.md) | SDK, dependency and architecture baseline |
| [ARC-01 plan](plans/arc-01-android-foundation.md) | Original implementation plan and acceptance criteria |
| [ARC-01 validation](arc-01-validation.md) | Recorded verification results and remaining manual checks |
| [ARC-02 plan](plans/arc-02-navigation-shell.md) | Navigation shell scope and acceptance criteria |
| [ARC-02 validation](arc-02-validation.md) | Navigation, lifecycle, offline and Material UI verification |
| [ARC-03 plan](plans/arc-03-ci-foundation.md) | CI build and local unit test gate scope and acceptance criteria |
| [ARC-03 validation](arc-03-validation.md) | Local checks, CI verification and pending branch protection setup |
| [QUIZ-01 plan](plans/quiz-01-domain-model.md) | Pure Java Quiz models, generator contract, state invariants and planned validation |
| [QUIZ-01 validation](quiz-01-validation.md) | Implemented domain contract, test evidence, limitations and integration handoff |
| [PROGRESS-01 plan](plans/progress-01-contract.md) | Progress summary, Quiz history, use case/view-state contracts and planned validation |
| [PROGRESS-01 handoff](progress-01-handoff.md) | Progress models, repository/use case/view state contracts and integration responsibilities |
| [PROGRESS-01 validation](progress-01-validation.md) | Executed checks, test counts and remaining delivery steps |
| [UI sketch](sketchs.png) | Visual reference for future feature screens |

Use lowercase kebab-case filenames for new documentation. Put task plans in
`plans/` and link new documents from this index. Keep historical plans and
validation records separate from the current setup and foundation guides.

[Project README](../README.md)
