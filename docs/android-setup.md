# Android setup

## Requirements

- JDK 17 for both the terminal and Android Studio Gradle JDK.
- Android SDK Platform 35 and Android SDK Build Tools 34.0.0.
- Android Gradle Plugin 8.7.3 and Gradle 8.9 (pinned in the repository).
- Set `ANDROID_HOME` to your SDK directory, or configure `sdk.dir` in an ignored
  `local.properties` file.

Set `JAVA_HOME` to your JDK 17 installation and put its `bin` directory first in
`PATH`. The committed Gradle wrapper honors `JAVA_HOME` and verifies the Gradle
distribution checksum. No system Gradle installation is needed.

## Open in Android Studio

1. Checkout `develop` or your feature branch based on `develop`.
2. Open the repository root in Android Studio.
3. Use JDK 17 for Gradle.
4. Let Gradle Sync finish.
5. Run the `app` configuration on an Android API 26+ emulator/device.

The application currently starts with a blank screen. Business features are
not implemented yet.

## Build and inspect dependencies

Run these commands from the repository root in PowerShell:

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

See [ARC-01 validation](arc-01-validation.md) for recorded results.

[Documentation index](README.md)
