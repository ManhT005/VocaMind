# ARC-03 validation

Verification date: October 18, 2026. Implementation and local checks are complete;
GitHub-hosted verification and branch protection remain pending.

## Baseline and environment

- Branch: `feature/arc-03-ci-foundation`, based on `origin/develop` at `7597b53`.
- ARC-01 and ARC-02 are merged into this baseline (PRs #2 and #3).
- Local: Windows, Temurin JDK 17.0.20.1, Gradle 8.9, AGP 8.7.3, SDK 35.
- CI configuration: `ubuntu-latest`, Temurin JDK 17, repository Gradle wrapper.
- [Plan](plans/arc-03-ci-foundation.md) preserved from the ignored
  `app/build/VocaMind_ARC-03_CI_Foundation_Plan.md` input.

## Workflow

File: [android-ci.yml](../.github/workflows/android-ci.yml).
Workflow: `Android CI`. Job: `build-test`, named `Build & Test`.

Pull requests targeting `develop` or `main` and pushes to `develop`, `main` or
`feature/*` trigger the workflow. It uses read-only repository permissions,
cancels superseded runs on the same ref, validates the wrapper, configures
Gradle caching, verifies the
toolchain, then runs unit tests and builds the debug APK in separate steps.
There is no `continue-on-error` or secret requirement.

Only `setup-gradle` manages Gradle caches. Cache writes are enabled for pushes
to `develop` and `main`; PRs and feature branch pushes use read-only caches,
following the
[Gradle action documentation](https://github.com/gradle/actions/blob/v4/docs/setup-gradle.md#using-the-cache-read-only).

## Local evidence

| Check | Result |
| --- | --- |
| Baseline `assembleDebug testDebugUnitTest` | Passed; unit tests `NO-SOURCE` |
| `java -version` / wrapper `--version` | Temurin 17.0.20.1 / Gradle 8.9, JVM 17 |
| [actionlint 1.7.12](https://github.com/rhysd/actionlint/releases/tag/v1.7.12) on the workflow | Exit 0, no diagnostics; downloaded archive SHA-256 verified |
| Wrapper JAR SHA-256 | `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`, matches the checksum saved during ARC-01 |
| Linux wrapper readiness | Git mode `100755`; LF enforced by `.gitattributes` |
| Intentional Java compile error + `assembleDebug` | Exit 1 at `:app:compileDebugJavaWithJavac` |
| Intentional JUnit assertion failure + `testDebugUnitTest` | Exit 1 at `:app:testDebugUnitTest`; `1 test completed, 1 failed` |
| Remove both probes, rerun `testDebugUnitTest` | Exit 0, `BUILD SUCCESSFUL` |
| Restored `assembleDebug` | Exit 0, `BUILD SUCCESSFUL`; debug APK generated |

The temporary compile probe was a separate Java class with invalid syntax.
The temporary JUnit 4 probe called `Assert.fail("ARC-03 temporary failure probe")`;
its XML report recorded one test and one failure. Both probe files were removed
before the final successful commands; no application or test source changes are
part of ARC-03. The permanent test tree still has no JUnit classes.

Local scripts, command logs and the copied failure XML remain ignored under
`.gradle/arc-03-reports/`. These results prove that the underlying Gradle commands
return nonzero on compile/test failures. They do not constitute a GitHub run.

## GitHub verification still required

Run URL: pending; this change has been committed locally without a push or PR.

1. Push the branch and open a PR targeting `develop`. Record the workflow run
   URL here and confirm checkout, JDK 17, wrapper validation, Gradle cache setup,
   unit tests and debug assembly succeed on Ubuntu.
2. Run again and verify Gradle cache restore/setup succeeds. The first run may
   miss the cache; runtime speed is not an acceptance gate.
3. On a disposable PR branch, introduce a temporary compile failure and confirm
   the job and PR check turn red. Compilation is also a dependency of unit tests,
   so the failure may surface in `Run unit tests` before the assemble step.
   Remove the probe and confirm the next GitHub run turns green. Repeat with a
   temporary failing JUnit test to check test failure propagation remotely.
4. Verify PR triggers for `develop` and `main` and push triggers for `develop`,
   `main` and `feature/*` as those branches receive changes. No remote trigger
   has been exercised locally.

## Branch protection / rulesets

State: pending repository admin confirmation for both `develop` and `main`.
No branch protection settings were read or changed during this task.

After the first workflow run, require pull requests and passing status checks
for `develop`, and review/apply the same rule to `main`. Select the `Build & Test`
check emitted by `Android CI` (often displayed as `Android CI / Build & Test`).
Verify that a failed required check blocks merging. A failing workflow alone
does not establish merge protection.

The remote green/red runs, cache behavior and protected-branch merge rejection
remain acceptance checks; ARC-03 must not be represented as fully verified on
GitHub until those results and the run URL are recorded.

[Documentation index](README.md)
