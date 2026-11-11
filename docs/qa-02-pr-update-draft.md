# QA-02 extend controlled UI coverage and apply offline controller state

The pushed head ba02a88 has green PR CI but its push workflow failed Discovery API35 before app tests: cellular network100 remained active after settings-only offline setup. The local change applies airplane mode through ConnectivityService for setup/restoration, retains strict effective-network checks, and handles explicit unsupported-command responses on API26/27 without accepting an active network.

Added controlled UI checks for paused/no-due planner states, due-first/remaining quota/paused exclusion, shared-pack dedup with quota8 over4 unique words, Quiz empty pools0/3, actual double-tap answer locking, offline external-link error/return, actual IME/Back state, longest packaged detail at effective font130%, and measured48dp action targets. Fixtures, renderer contracts and production journeys are reported separately.

Validation:27 network host tests,8 CI-device host tests and4 QA host tests passed; API30 Discovery independent cases plus suites twice/probe passed; font130% journey/reopen/reboot passed. CI artifacts for ba02a88 contain598 passing unit tests, including FTS22 and benchmark2. The new local helper still needs API35/API26 CI; no full QA02 PASS is claimed.

Final consolidated API30 regression:12/12 controlled UI cases passed with0 skips at effective font1.3, including actual IME. Per-case logs/video/snapshots and source/APK hashes retained privately; network/configuration restoration checked. Matrix now distinguishes historical emulator rows from current approved API30 technical evidence. Only API30 AVD is configured locally; full QA150-word API26/API35 journeys remain unrun.

Mandatory benchmark thresholds passed on the explicitly approved API30 release-based run; broad FTS15ms target remains missed. TalkBack is deferred with user permission and remains NOT RUN. Android backup is retained by user choice pending PO privacy confirmation. Content rights and Nhân sign-off remain open. No merge or approval requested by this draft.
