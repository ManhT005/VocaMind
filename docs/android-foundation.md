# Android foundation

## Technical baseline

| Setting | Value |
| --- | --- |
| Application module | `app` |
| Namespace / application ID | `com.vocamind.app` |
| Language | Java 17 |
| UI | Android XML/View system with ViewBinding enabled |
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

Room uses `annotationProcessor`. No Kotlin, KSP, KAPT or Safe Args plugin is
configured.

## Architecture packages

The application package contains `core/`, `domain/`, `data/` and `feature/`
placeholders. Empty packages are kept with `.gitkeep` until implementation
begins.

ARC-01 provides build configuration and dependencies. Navigation graphs and the
application shell belong to ARC-02; Room entities, DAOs, databases and schema
export belong to the DATA tasks.

See the [ARC-01 plan](plans/arc-01-android-foundation.md) for the original scope
and [Android setup](android-setup.md) for build instructions.

[Documentation index](README.md)
