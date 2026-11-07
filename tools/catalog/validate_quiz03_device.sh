#!/usr/bin/env bash
# Run only on a disposable emulator. The application ID and corpus asset are test-only.
set -euo pipefail
evidence=app/build/quiz03-device-evidence
mkdir -p "$evidence"
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable
adb shell svc data disable
adb shell getprop > "$evidence/device-properties.txt"
adb shell dumpsys connectivity > "$evidence/connectivity-before-launch.txt"
adb shell settings get global airplane_mode_on > "$evidence/airplane-mode.txt"
adb shell settings get global wifi_on > "$evidence/wifi-state.txt"
git rev-parse HEAD > "$evidence/tested-checkout.txt"
set +e
./gradlew -Pquiz03DeviceIsolation=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vocamind.app.feature.quiz.QuizScreenInstrumentedTest,com.vocamind.app.data.user.QuizCompletionInstrumentedTest,com.vocamind.app.feature.quiz.QuizProductionFlowInstrumentedTest \
  connectedDebugAndroidTest --console=plain > "$evidence/gradle-device.log" 2>&1
status=$?
set -e
cat "$evidence/gradle-device.log"
printf '%s\n' "$status" > "$evidence/exit-status.txt"
sha256sum app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk > "$evidence/apk-sha256.txt"
for name in 01-offline-empty 02-feedback 03-result; do
  adb pull "/data/local/tmp/quiz03-$name.png" "$evidence/$name.png" || true
done
exit "$status"
