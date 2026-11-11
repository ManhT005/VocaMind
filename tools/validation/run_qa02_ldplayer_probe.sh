#!/system/bin/sh
# Persistence probe for the state created by run_qa02_ldplayer_offline.sh.
OUT=/sdcard/qa02-offline-probe.txt
STATUS=/sdcard/qa02-offline-probe.status
rm -f "$OUT" "$STATUS"
settings put global airplane_mode_on 1
settings put global mobile_data 0
svc data disable
svc wifi disable
sleep 3
am force-stop com.vocamind.app.qa02validation.qa
am instrument -w -r -e qa02Phase probe \
  -e class com.vocamind.app.feature.qa.Qa02JourneyInstrumentedTest#committedSnapshotSurvivesReopen \
  com.vocamind.app.qa02validation.qa.test/androidx.test.runner.AndroidJUnitRunner \
  >>"$OUT" 2>&1
CODE=$?
echo "$CODE" >"$STATUS"
settings put global airplane_mode_on 0
svc data disable
svc wifi enable
exit "$CODE"
