# VocaMind

Initial Android foundation for VocaMind.

## Open in Android Studio

1. Checkout `develop` or your feature branch based on `develop`.
2. Open the repository root in Android Studio.
3. Use JDK 17 for Gradle.
4. Let Gradle Sync finish.
5. Run the `app` configuration on an Android API 26+ emulator/device.

The application intentionally starts with a blank screen. Business features are not implemented yet.

## Build requirements

- JDK 17 for both the terminal and Android Studio Gradle JDK.
- Android SDK Platform 35 and Android SDK Build Tools 34.0.0.
- Android Gradle Plugin 8.7.3 and Gradle 8.9 (pinned in the repository).
- Set `ANDROID_HOME` to your SDK directory, or configure `sdk.dir` in an ignored
  `local.properties` file.

Set `JAVA_HOME` to your JDK 17 installation and put its `bin` directory first in
`PATH`. The committed Gradle wrapper honors `JAVA_HOME` and verifies the Gradle
distribution checksum. No system Gradle installation is needed.

From PowerShell:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean assembleDebug
.\gradlew.bat :app:compileDebugJavaWithJavac testDebugUnitTest
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat :app:dependencies --configuration debugUnitTestRuntimeClasspath
```

On macOS/Linux, use `./gradlew` in place of `.\gradlew.bat`.
Both Java and the Gradle JVM must report version 17. Unit tests may report
`NO-SOURCE` until test classes are added. The debug APK is generated at
`app/build/outputs/apk/debug/app-debug.apk` and is ignored by Git.

## Foundation

- Android application module: `app`
- Language: Java
- UI baseline: Android XML/View system
- Minimum SDK: 26
- Compile / target SDK: 35
- ViewBinding enabled for future XML layouts
- Lifecycle ViewModel, LiveData and runtime: 2.11.0
- Navigation Fragment and UI: 2.10.2
- Room runtime and Java annotation processor: 2.8.5
- JUnit: 4.13.2
- Architecture placeholders:
  - `core/`
  - `domain/`
  - `data/`
  - `feature/`

Empty architecture packages are kept with `.gitkeep` until implementation begins.

ARC-01 provides build configuration and dependencies. Navigation graphs and the
application shell belong to ARC-02; Room entities, DAOs, databases and schema
export belong to the DATA tasks. Room uses `annotationProcessor`; no Kotlin,
KSP, KAPT or Safe Args plugin is configured.

## ARC-01 validation

Verified on Windows with JDK 17.0.20.1 and Gradle 8.9:

- `clean assembleDebug` and `:app:compileDebugJavaWithJavac`: passed.
- `testDebugUnitTest`: `NO-SOURCE`, as no test classes exist yet.
- Runtime and unit test dependencies: resolved with no failures.
- Java precompile registered `room-compiler:2.8.5` as an annotation processor.
- APK metadata: application ID `com.vocamind.app`, minSdk 26, targetSdk 35;
  compiled Java classes use Java 17 bytecode.
- APK installed and `MainActivity` launched on a Pixel 7 Pro API 37 emulator;
  the activity remained resumed with a live process and no crash log entries.

Interactive Android Studio Gradle Sync still needs manual confirmation.
