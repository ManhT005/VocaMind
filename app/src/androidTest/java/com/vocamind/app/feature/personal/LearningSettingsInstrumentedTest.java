package com.vocamind.app.feature.personal;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.data.user.entity.QuizAttempt;
import com.vocamind.app.data.user.entity.ReviewEvent;
import com.vocamind.app.data.user.entity.UserWordProgress;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LearningSettingsInstrumentedTest {
    private static final String ISOLATED_PACKAGE = "com.vocamind.app.qa02validation.qa";

    @Test public void resetRemovesRecordedHistoryAndKeepsBookmarksPacksAndGoal() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "reset-test-" + UUID.randomUUID() + ".db";
        UserDatabase db = UserDatabase.open(context, name);
        try {
            var dao = db.userDao();
            dao.updateDailyNewWords(10);
            dao.activatePack("test-pack", 100L);
            UserWordProgress bookmark = progress("saved-test", true);
            dao.saveProgress(bookmark);
            dao.saveProgress(progress("other-test", false));

            ReviewEvent review = new ReviewEvent();
            review.vocabularyId = bookmark.vocabularyId;
            review.rating = "REMEMBERED";
            review.reviewedAt = 100L;
            review.boxAfter = 1;
            review.nextDueAt = 500L;
            dao.insertReview(review);

            QuizAttempt quiz = new QuizAttempt();
            quiz.completionKey = UUID.randomUUID().toString();
            quiz.completedAt = 110L;
            quiz.questionCount = 4;
            quiz.correctCount = 3;
            quiz.scorePercent = 75;
            dao.insertQuizOnce(quiz);
            assertEquals(1, dao.completedQuizHistory().size());

            dao.resetLearningResults(200L);
            assertTrue(dao.completedQuizHistory().isEmpty());
            assertTrue(dao.reviews().isEmpty());
            assertNull(dao.progress("other-test"));
            assertEquals(List.of("saved-test"), dao.savedVocabularyIds());
            UserWordProgress stored = dao.progress("saved-test");
            assertNotNull(stored);
            assertEquals(0, stored.reviewCount);
            assertNull(stored.learnedAt);
            assertNull(stored.lastReviewedAt);
            assertEquals(1, dao.activePacks().size());
            assertEquals(10, dao.preference().dailyNewWords);
        } finally {
            db.close();
            context.deleteDatabase(name);
        }
    }

    @Test public void reminderPostsNotificationAndItsTapOpensToday() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Assume.assumeTrue(ISOLATED_PACKAGE.equals(context.getPackageName()));
        if (Build.VERSION.SDK_INT >= 33 && !StudyReminder.hasPermission(context)) {
            instrumentation.getUiAutomation().grantRuntimePermission(
                    context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
        assertTrue(StudyReminder.canNotify(context));
        boolean enabled = StudyReminder.enabled(context);
        LocalTime time = StudyReminder.time(context);
        var launch = instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                BottomNavigationView tabs = launch.findViewById(R.id.bottom_tabs);
                tabs.setSelectedItemId(R.id.homeFragment);
            });
            StudyReminder.configure(context, true, LocalTime.of(20, 0));
            // Deliver the receiver directly to test the posted notification without waiting for Doze.
            new StudyReminderReceiver().onReceive(context, new Intent(StudyReminder.FIRE));
            var manager = context.getSystemService(NotificationManager.class);
            android.app.PendingIntent content = null;
            for (int i = 0; i < 30 && content == null; i++) {
                for (var posted : manager.getActiveNotifications()) {
                    if ("daily_study".equals(posted.getNotification().getChannelId())) {
                        content = posted.getNotification().contentIntent;
                        break;
                    }
                }
                if (content == null) android.os.SystemClock.sleep(100);
            }
            assertNotNull("Reminder notification posts a tap action", content);
            content.send();
            boolean[] openedToday = {false};
            for (int i = 0; i < 50 && !openedToday[0]; i++) {
                instrumentation.runOnMainSync(() -> {
                    for (var activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                        if (!(activity instanceof MainActivity)) continue;
                        var host = ((MainActivity) activity).getSupportFragmentManager()
                                .findFragmentById(R.id.nav_host_fragment);
                        if (host instanceof androidx.navigation.fragment.NavHostFragment nav) {
                            var destination = nav.getNavController().getCurrentDestination();
                            openedToday[0] |= destination != null && destination.getId() == R.id.todayFragment;
                        }
                    }
                });
                if (!openedToday[0]) android.os.SystemClock.sleep(100);
            }
            assertTrue("Notification tap opens Today", openedToday[0]);
        } finally {
            instrumentation.runOnMainSync(() -> {
                for (var activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof MainActivity) activity.finish();
                }
                if (!launch.isFinishing()) launch.finish();
            });
            // The isolated build is used for this test, so its notification can be cleared safely.
            StudyReminder.configure(context, false, time);
            if (enabled) StudyReminder.configure(context, true, time);
        }
    }

    private static UserWordProgress progress(String id, boolean saved) {
        UserWordProgress row = new UserWordProgress();
        row.vocabularyId = id;
        row.isSaved = saved;
        row.learnedAt = 100L;
        row.lastReviewedAt = 100L;
        row.reviewCount = 1;
        row.box = 1;
        row.dueAt = 500L;
        return row;
    }
}
