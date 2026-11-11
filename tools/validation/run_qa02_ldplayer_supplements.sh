#!/system/bin/sh
# LDPlayer loses host ADB when Wi-Fi is disabled, so run the accepted
# supplemental instrumentation fully inside the guest and restore afterward.
BASE=/sdcard/qa02-supplements
rm -rf "$BASE"
mkdir -p "$BASE"
settings put global airplane_mode_on 1
settings put global mobile_data 0
svc data disable
svc wifi disable
sleep 3
am instrument -w -r -e class \
  com.vocamind.app.data.user.AtomicReviewInstrumentedTest,com.vocamind.app.data.user.ProgressAggregationInstrumentedTest,com.vocamind.app.data.user.QuizCompletionInstrumentedTest \
  com.vocamind.app.flash03validation.test/androidx.test.runner.AndroidJUnitRunner \
  >"$BASE/sqlite-contracts.txt" 2>&1
echo $? >"$BASE/sqlite-contracts.status"
am instrument -w -r -e class \
  com.vocamind.app.feature.flashcard.FlashcardDuplicateReviewInstrumentedTest \
  com.vocamind.app.flash03validation.test/androidx.test.runner.AndroidJUnitRunner \
  >"$BASE/flash03-triple-tap.txt" 2>&1
echo $? >"$BASE/flash03-triple-tap.status"
settings put global airplane_mode_on 0
svc data disable
svc wifi enable
