#!/system/bin/sh
BASE=/sdcard/qa02-benchmark
rm -rf "$BASE"
mkdir -p "$BASE"
cmd package compile -m speed-profile -f com.vocamind.app.data09validation \
  >"$BASE/profile-install.txt" 2>&1
pm clear com.vocamind.app.data09validation >"$BASE/fresh-data.txt" 2>&1
settings put global airplane_mode_on 1
settings put global mobile_data 0
svc data disable
svc wifi disable
sleep 3
i=1
while [ "$i" -le 11 ]; do
  am force-stop com.vocamind.app.data09validation
  sleep 1
  am start -W -n com.vocamind.app.data09validation/com.vocamind.app.MainActivity \
    >"$BASE/startup-$i.txt" 2>&1
  sleep 2
  i=$((i + 1))
done
am instrument -w -r -e class \
  com.vocamind.app.data.catalog.FtsSearchBenchmarkInstrumentedTest \
  -e data09Report true -e data05EnforceLatency true \
  com.vocamind.app.data09validation.test/androidx.test.runner.AndroidJUnitRunner \
  >"$BASE/fts.txt" 2>&1
echo $? >"$BASE/fts.status"
settings put global airplane_mode_on 0
svc data disable
svc wifi enable
