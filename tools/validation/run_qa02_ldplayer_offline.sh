#!/system/bin/sh
# Runs entirely inside the approved LDPlayer instance because disabling its
# virtual Wi-Fi also disconnects host ADB. Always restores the network flags.
OUT=/sdcard/qa02-offline-journey.txt
STATUS=/sdcard/qa02-offline-journey.status
rm -f "$OUT" "$STATUS"
pm clear com.vocamind.app.qa02validation.qa >>"$OUT" 2>&1
settings put global airplane_mode_on 1
settings put global mobile_data 0
svc data disable
svc wifi disable
sleep 3
am instrument -w -r -e qa02Phase journey \
  -e class com.vocamind.app.feature.qa.Qa02JourneyInstrumentedTest#offlineJourney \
  com.vocamind.app.qa02validation.qa.test/androidx.test.runner.AndroidJUnitRunner \
  >>"$OUT" 2>&1
CODE=$?
echo "$CODE" >"$STATUS"
settings put global airplane_mode_on 0
svc data disable
svc wifi enable
exit "$CODE"
