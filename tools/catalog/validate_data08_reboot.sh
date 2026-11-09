#!/usr/bin/env bash
# Use a disposable emulator. Only the isolated DATA-08 application ID is installed or cleared.
set -euo pipefail

app=com.vocamind.app.data08validation
test_app="$app.test"
runner="$test_app/androidx.test.runner.AndroidJUnitRunner"
test_class=com.vocamind.app.data.catalog.upgrade.CatalogRebootInstrumentedTest
evidence=app/build/data08-reboot-evidence
apks=$(mktemp -d)
trap 'rm -rf "$apks"' EXIT
mkdir -p "$evidence"
: > "$evidence/phases.txt"
: > "$evidence/boot-ids.txt"
git rev-parse HEAD > "$evidence/tested-checkout.txt"
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
{
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.sdk
  adb shell getprop ro.build.fingerprint
} > "$evidence/device.txt"

./gradlew -Pdata08DeviceGeneration=A assembleDebug assembleDebugAndroidTest --console=plain
cp app/build/outputs/apk/debug/app-debug.apk "$apks/A.apk"
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$apks/test.apk"
./gradlew -Pdata08DeviceGeneration=B assembleDebug --console=plain
cp app/build/outputs/apk/debug/app-debug.apk "$apks/B.apk"
(cd "$apks" && sha256sum A.apk B.apk test.apk) > "$evidence/apk-sha256.txt"
test "$(unzip -p "$apks/A.apk" assets/database/catalog.db | sha256sum)" != \
     "$(unzip -p "$apks/B.apk" assets/database/catalog.db | sha256sum)"

run_phase() {
  local label=$1 phase=$2 log="$evidence/$1.log"
  adb shell am instrument -w -e class "$test_class" -e data08Phase "$phase" "$runner" | tee "$log"
  grep -Eq '^OK \(1 test\)' "$log"
  local marker
  marker=$(grep -Eo "DATA08_PASS $phase [0-9a-f]{64} [0-9a-f]{64}" "$log")
  printf '%s %s\n' "$label" "$marker" >> "$evidence/phases.txt"
}

cold_stop() {
  adb shell am force-stop "$app"
  ! adb shell pidof "$app"
}

reboot_device() {
  local before after
  before=$(adb shell cat /proc/sys/kernel/random/boot_id | tr -d '\r')
  test -n "$before"
  adb reboot
  timeout 180 adb wait-for-device
  for ((attempt=0; attempt<120; attempt++)); do
    if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] &&
       adb shell pm path "$app" >/dev/null; then
      after=$(adb shell cat /proc/sys/kernel/random/boot_id | tr -d '\r')
      test -n "$after"
      test "$before" != "$after"
      printf '%s -> %s\n' "$before" "$after" >> "$evidence/boot-ids.txt"
      return 0
    fi
    sleep 1
  done
  echo 'Timed out waiting for Android after reboot.' >&2
  return 1
}

adb install -r "$apks/A.apk"
adb install -r "$apks/test.apk"
adb shell pm clear "$app" | tr -d '\r' | grep -x Success
run_phase seed seed
cold_stop
adb install -r "$apks/B.apk"
run_phase upgrade upgrade
cold_stop
run_phase force-stop verify-upgrade
reboot_device
run_phase reboot verify-upgrade
cold_stop
run_phase rollback rollback
adb install -r "$apks/A.apk"
reboot_device
run_phase rollback-reboot verify-rollback
