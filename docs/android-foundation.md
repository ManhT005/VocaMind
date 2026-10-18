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

See the [ARC-01 plan](plans/arc-01-android-foundation.md) for the original scope
and [Android setup](android-setup.md) for build instructions.

[Documentation index](README.md)
