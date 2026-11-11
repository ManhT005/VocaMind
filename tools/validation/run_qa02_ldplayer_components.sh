#!/system/bin/sh
BASE=/sdcard/qa02-components
mkdir -p "$BASE"
restore() {
  settings put global airplane_mode_on 0
  svc data disable
  svc wifi enable
}
trap restore EXIT
settings put global airplane_mode_on 1
settings put global mobile_data 0
svc data disable
svc wifi disable
sleep 3
for CASE in \
  attribution.AttributionScreenInstrumentedTest#attributionAvailableBeforeLearningSurvivesRecreationAndBack \
  attribution.AttributionRenderingInstrumentedTest#rendersLoadingEmptyErrorRetryAndMultipleLongSources \
  search.SearchScreenInstrumentedTest#catalogSearchEnglishVietnameseDetailBackRecreateClearAndNoWrites \
  search.SearchScreenInstrumentedTest#catalogDetailSaveRequiresExplicitClickAndPersistsAcrossBack \
  search.SearchScreenInstrumentedTest#opaqueNavigationArgumentsAndReboundHolder \
  search.SearchReadRecoveryInstrumentedTest#realRoomReadFailureShowsErrorAndRetryRecoversCurrentQuery \
  quiz.QuizScreenInstrumentedTest#fullFlowKeepsFeedbackThroughRecreationAndSavesOnce \
  quiz.QuizScreenInstrumentedTest#toolbarAndSystemBackShareCancelAndConfirmExit
do
  pm clear com.vocamind.app.flash03validation
  am instrument -w -r -e class "com.vocamind.app.feature.$CASE" \
    com.vocamind.app.flash03validation.test/androidx.test.runner.AndroidJUnitRunner \
    >"$BASE/$CASE.txt" 2>&1
done
am instrument -w -r -e class com.vocamind.app.data.catalog.upgrade.CatalogUpgradeInstrumentedTest \
  com.vocamind.app.flash03validation.test/androidx.test.runner.AndroidJUnitRunner \
  >"$BASE/catalog-upgrade.txt" 2>&1
echo complete >"$BASE/complete.txt"
