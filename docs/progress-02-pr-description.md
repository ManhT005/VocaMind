# PROGRESS-02: progress dashboard and completed Quiz history

Base: `develop` at `b299cab`. Implementation commit: `5872cff`; final device-test head: `8c91fbd`.

Learners can now open Progress to see real local userdata metrics, all six Leitner boxes, the Mastered disclaimer and the complete persisted Quiz history. Empty sections remain independent; loading hides old values and read errors expose retry. The retained ViewModel reads through the existing production use case off main, refreshes on resume, coalesces overlapping requests, and discards superseded/cleared completions. A RecyclerView/ListAdapter keeps stable attempt identities and device-local completion dates.

The opt-in `progress02DeviceIsolation` flag reuses the existing accepted-corpus device fixture under its own application ID. Normal APK catalog assets, database schemas, DAO SQL, Quiz scoring/writer and CI catalog preparation are unchanged.

Validation: 483 host tests passed (including native Room screen coverage on API 26/35); ordinary debug APK, AndroidTest APK and lint passed. LDPlayer Android 9/API 28 passed production fixture/UI, saved-only/paused/cross-pack counts, review and Today/Flashcard writes, real ten-question Quiz completion, completed-only history, corruption/retry, recreation, actual landscape/portrait transitions, force-stop/reopen and reboot persistence. Small-screen/dark/200% font screenshots were reviewed after rejecting an initial System UI ANR overlay. Logs, APK hashes and screenshots are linked from [validation](progress-02-validation.md).

The real Quiz happy path uses the opt-in accepted 150-word QA catalog, as QUIZ-03 does; it does not claim a production catalog release. Final reviewer acceptance is still required. No reviewer approval is represented by automated tests or this description.
