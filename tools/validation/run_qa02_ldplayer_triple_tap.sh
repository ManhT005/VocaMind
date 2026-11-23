#!/system/bin/sh
OUT=/sdcard/qa02-triple-tap-retest.txt
rm -f "$OUT"
pm clear com.vocamind.app.flash03validation >>"$OUT" 2>&1
settings put global airplane_mode_on 1
settings put global mobile_data 0
svc data disable
svc wifi disable
sleep 3
am instrument -w -r -e class \
  com.vocamind.app.feature.flashcard.FlashcardDuplicateReviewInstrumentedTest \
  com.vocamind.app.flash03validation.test/androidx.test.runner.AndroidJUnitRunner \
  >"$OUT" 2>&1
echo $? >/sdcard/qa02-triple-tap-retest.status
settings put global airplane_mode_on 0
svc data disable
svc wifi enable
