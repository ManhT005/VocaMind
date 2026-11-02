# Android foundation

## Technical baseline

| Setting | Value |
| --- | --- |
| Application module | `app` |
| Namespace / application ID | `com.vocamind.app` |
| Language | Java 17 |
| UI | Material 3 Components for Java/XML with ViewBinding enabled |
| Architecture | Clean MVVM |
| Minimum SDK | 26 |
| Compile / target SDK | 35 |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 |

## Dependencies

Dependencies are declared in [app/build.gradle](../app/build.gradle).

| Dependency | Version | Purpose |
| --- | --- | --- |
| AndroidX Lifecycle | 2.11.0 | ViewModel, LiveData and lifecycle runtime |
| AndroidX Navigation | 2.10.2 | Fragment navigation and UI integration |
| AndroidX Room | 2.8.5 | Runtime and Java annotation processor |
| JUnit | 4.13.2 | Local unit tests |
| Material Components | 1.13.0 | Material 3 DayNight theme, buttons and text views |

Room uses `annotationProcessor`. No Kotlin, KSP, KAPT or Safe Args plugin is
configured.

## Architecture packages

The application package contains `core/`, `domain/`, `data/` and `feature/`
placeholders. Empty packages are kept with `.gitkeep` until implementation
begins.

## Navigation shell

ARC-01 provides build configuration and core dependencies. ARC-02 adds a single
`MainActivity`, one `NavHostFragment` and a [flat graph](../app/src/main/res/navigation/nav_graph.xml) with Home, Packs,
Vocabulary, Today, Quiz and Progress. Home is the start destination. Its five
temporary route buttons are replaced when the feature UI is implemented.

`MainActivity` extends `AppCompatActivity` to support Material Components and
uses ViewBinding to host the graph. `AppCompatActivity` extends
`FragmentActivity`, preserving the single-activity fragment architecture. View
insets keep content clear of system bars and cutouts; Navigation Component
owns the back stack and restores the active destination on recreation.

The [UI sketch](sketchs.png) provides the green accent and route naming
reference. ARC-02 implements placeholders; bottom navigation, splash screens,
pack contents, flashcards and result screens remain future feature work. Room
entities, DAOs, databases and schema export belong to the DATA tasks.

Material Components 1.13.0 keeps the existing SDK/Gradle baseline. See the
[official release notes](https://github.com/material-components/material-components-android/releases/tag/1.13.0)
and [Material setup guide](https://github.com/material-components/material-components-android/blob/1.13.0/docs/getting-started.md).

## Continuous integration

[Android CI](../.github/workflows/android-ci.yml) runs on pull requests targeting
`develop` or `main` and pushes to `develop`, `main` or `feature/*`. Its stable job
name is `Build & Test` under the `Android CI` workflow.

The Ubuntu runner uses Temurin JDK 17, validates the committed Gradle wrapper,
and runs `./gradlew testDebugUnitTest` followed by `./gradlew assembleDebug`.
Both steps block on failure. The wrapper pins Gradle 8.9 and verifies the
distribution checksum. Unit tests currently report `NO-SOURCE`; future JUnit
tests run through the same command.

`gradle/actions/setup-gradle@v4` owns Gradle caching; no second cache mechanism
is configured. Pushes to `develop` and `main` can write cache entries; PR runs
and feature branch pushes only read them. New runs cancel older runs for the
same Git ref. The workflow needs only `contents: read` and no repository secrets.

The Gradle action's [official cache documentation](https://github.com/gradle/actions/blob/v4/docs/setup-gradle.md#using-the-cache-read-only)
describes the branch-specific cache policy.

After the first GitHub run, a repository admin must require the `Build & Test`
check from `Android CI` on `develop` and review the same protection for `main`.
GitHub may display this as `Android CI / Build & Test`; select the check actually
reported by the run. Require pull requests and passing status checks in the
branch rules. A red workflow blocks merging only when those rules are active.
See [ARC-03 validation](arc-03-validation.md) for evidence and pending checks.

Emulator tests, coverage gates, release signing and deployment are outside
ARC-03. The CI gate covers the debug build and local JUnit tasks (NFR-VM-18).

See the [ARC-01 plan](plans/arc-01-android-foundation.md) for the original scope
and [Android setup](android-setup.md) for build instructions.

[Documentation index](README.md)
