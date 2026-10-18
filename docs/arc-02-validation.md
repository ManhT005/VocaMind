# ARC-02 validation

Environment: Windows, JDK 17.0.20.1, Gradle 8.9, AGP 8.7.3, compile/target SDK 35
and minSdk 26. Verification date: October 18, 2026.

## Branch and scope

`feature/arc-02-navigation-shell` is based on ARC-01 commit `1d72e06` because
ARC-01 has not been merged into `develop`. Update the base after ARC-01 is
merged before opening the final ARC-02 PR against `develop`.

The [plan](plans/arc-02-navigation-shell.md) is implemented with Material
Components 1.13.0, a Material 3 DayNight theme, `MaterialButton` and
`MaterialTextView`. `AppCompatActivity` supplies Material support while retaining
the single-activity fragment host architecture. The [sketch](sketchs.png) is a
visual reference; feature contents and final bottom navigation are future work.

## Build checks

The ARC-01 baseline passed `clean assembleDebug` before code changes. After
implementing the shell, this command passed:

```powershell
.\gradlew.bat clean assembleDebug :app:compileDebugJavaWithJavac testDebugUnitTest lintDebug
```

`testDebugUnitTest` reports `NO-SOURCE`; runtime verification is performed through
the emulator smoke flows below. Lint reports zero errors and two warnings:
Material has a newer version available, and the foundation has no launcher icon.
Material 1.13.0 is pinned to preserve the existing AGP/Gradle/SDK baseline.

## Runtime checks

ADB/UIAutomator smoke checks passed on a read-only Pixel 7 Pro API 37 emulator:
18 recorded checks passed with no application crash entries.

| Case | Flow | Result |
| --- | --- | --- |
| TC-ARC02-01 | Cold launch opens Home | Passed |
| TC-ARC02-02 | Home → Packs → system Back → Home | Passed |
| TC-ARC02-03 | Home → Vocabulary → system Back → Home | Passed |
| TC-ARC02-04 | Home → Today → system Back → Home | Passed |
| TC-ARC02-05 | Home → Quiz → system Back → Home | Passed |
| TC-ARC02-06 | Home → Progress → system Back → Home | Passed |
| TC-ARC02-07 | Rotate Packs, Today and Quiz to landscape and back; destination preserved; one Back returns Home | Passed for all three routes |
| TC-ARC02-08 | Airplane mode enabled, Wi-Fi and data disabled; cold launch and all five routes work | Passed |
| Home exit / relaunch | Back at Home finishes Activity; a new launch opens Home | Passed |
| Touch targets | Visible navigation buttons meet the 48dp minimum on a 360×640dp phone | Passed |
| Dark theme / large text | All five routes remain reachable at font scale 2.0 in portrait and landscape on a 360×640dp phone | Passed |
| Tablet | All five routes work at 800×1280dp; landscape preserves Today and Back returns Home | Passed |
| Crash check | Application process remains alive and the crash buffer contains no application crash entries | Passed |

The merged APK has no `android.permission.INTERNET` permission. Primary text,
secondary text, accent text and button labels meet a 4.5:1 contrast ratio in
both light and dark palettes.

Keep the emulator awake and unlocked during automated input. Use
`adb shell wm user-rotation lock 1` and `adb shell wm user-rotation lock 0` to
exercise real landscape/portrait changes; verify the UI hierarchy rotation
instead of relying only on the `user_rotation` setting. Build outputs, local
smoke scripts, screenshots and logs remain ignored under `app/build/` and
`.gradle/arc-02-reports/`.

## Remaining manual checks

- Interactive Android Studio Gradle Sync.
- Physical device and API 26 runtime smoke check.
- TalkBack traversal and announcements.
- Reviewer verification of the back stack before merge.

[Documentation index](README.md)
