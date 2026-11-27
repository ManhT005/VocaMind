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

1. Checkout `develop` or the feature branch you want to build. ARC-01 and the
   ARC-02 navigation shell are merged into `develop`.
2. Open the repository root in Android Studio.
3. Use JDK 17 for Gradle.
4. Let Gradle Sync finish.
5. Run the `app` configuration on an Android API 26+ emulator/device.

For the internal demo, select the `debug` build variant, choose the `app` run
configuration and press Run. Debug uses the verified 150-entry catalog and
installs VocaMind (`com.vocamind.app`). It uses
`.tools/python/python.exe` when present; on other machines, install the catalog
build dependencies and set `catalogPython` to your Python executable if needed.
Use `-PvocamindDemo=false` to build the small synthetic fixture for its existing
checks. The release variant does not pick up the internal catalog.

## Build and inspect dependencies

Run these commands from the repository root in PowerShell:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean assembleDebug
.\gradlew.bat :app:compileDebugJavaWithJavac testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat :app:dependencies --configuration debugUnitTestRuntimeClasspath
```

On macOS/Linux, use `./gradlew` in place of `.\gradlew.bat`.
Both Java and the Gradle JVM must report version 17. Unit tests may report
`NO-SOURCE` until test classes are added. The debug APK is generated at
`app/build/outputs/apk/debug/app-debug.apk` and is ignored by Git.

See [ARC-01 validation](arc-01-validation.md) and
[ARC-02 validation](arc-02-validation.md) for recorded results.

## Optional account setup

The app opens as a guest when Firebase client configuration is absent. To enable
email/password accounts in a QA or development build:

1. In a project you control, register the exact Android application ID of the
   variant you intend to test (for example, `com.vocamind.app.qa` for Catalog QA).
2. In Firebase Authentication, enable the Email/Password provider and configure
   the verification and password-reset email templates as needed.
3. Set all three of these Gradle properties in your local Gradle user properties
   or through the build environment. Use the Android client values of that
   registered app, never a service-account key.

   | Property | Android client value |
   | --- | --- |
   | `vocamindFirebaseProjectId` | Firebase project ID |
   | `vocamindFirebaseAppId` | Firebase Android app ID |
   | `vocamindFirebaseApiKey` | Firebase Android client API key |

4. Rebuild. In **Cá nhân → Tài khoản**, use a test email to check registration,
   verification, sign-in, rejection of a wrong current password, password
   change, reset email and sign-out. Check that the existing progress remains
   available on the device.

Account identity is separate from the local Room database. This client does not
copy progress into the account or restore it on another device.

[Firebase email/password setup](https://firebase.google.com/docs/auth/android/password-auth)

## Progress sync acceptance criteria

Keep Room as the offline source of truth. Before publishing a sync build, verify
the following with a test backend and two isolated app installations:

- A verified account can opt in to importing the guest data on the device. Show
  which data will be imported before sending it. Confirm that changing accounts
  cannot upload data previously associated with another account.
- Vocabulary and pack IDs are shared across devices. Room row IDs for Flashcard
  and Quiz are local. Give each review a persistent event key before upload and
  reuse the existing Quiz completion key. For older attempts without a key,
  assign and persist one before upload. Importing the same events twice must
  leave the totals unchanged.
- Keep the earliest recorded learning date per word. Use the most recent
  review for its next due date and Leitner state. When timestamps match, use
  a stable tie-breaker so both devices converge. A newer explicit removal of a
  bookmark or pause of a pack must win over its older state.
- Keep the device's display settings and reminder schedule local. Define an
  explicit account preference for the daily new-word goal before syncing it.
- An offline edit remains local until upload succeeds. Verify retries after an
  interrupted transfer, and prevent a queued upload from a signed-out session
  from writing to a different account.
- A learning reset must invalidate older cloud learning results before sync can
  resume. Verify that a device that was offline during the reset cannot bring
  back old reviews or Quiz attempts. Bookmarks and active packs must survive.

Accounts currently authenticate separately from learning data. A configured
account build must continue to label progress as local until these cases pass.

[Documentation index](README.md)
