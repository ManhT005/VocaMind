package com.vocamind.app.feature.flashcard;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.user.UserDatabase;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Fragment/factory/Room; opt-in test installation never erases the user's app data. */
@RunWith(AndroidJUnit4.class)
public class FlashcardDuplicateReviewInstrumentedTest {
    @Test public void threeTaps150msApartWriteOnceThenNextCardWorksAfterRotation() {
        Context context = ApplicationProvider.getApplicationContext();
        Assume.assumeTrue("Requires -Pflash03DeviceIsolation=true",
                "com.vocamind.app.flash03validation".equals(context.getPackageName()));
        UserDatabase user = UserDatabase.open(context);
        CatalogDatabase catalog = CatalogDatabase.open(context, "catalog.db");
        try {
            assertTrue("Use a fresh isolated installation", user.userDao().reviews().isEmpty());
            assertTrue(user.userDao().allProgress().isEmpty());
            for (var pack : catalog.catalogDao().packs()) {
                user.userDao().activatePack(pack.id, System.currentTimeMillis());
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> ((NavHostFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment)).getNavController().navigate(R.id.flashcardFragment));
                awaitReady(scenario, 1);
                FlashcardViewModel original = model(scenario);
                String firstWord = state(scenario).card.getVocabulary().stableId;
                onView(withId(R.id.card)).perform(click());
                assertTrue(state(scenario).flipped);
                long firstId = state(scenario).presentationId;
                // Inject from the instrumentation thread: Espresso's injection path can
                // drain async UI work between events, stretching a 150ms burst into seconds.
                tripleTap(scenario);
                awaitReady(scenario, 2);
                assertEquals(1, user.userDao().reviews().size());
                var event = user.userDao().reviews().get(0);
                assertEquals(firstWord, event.vocabularyId);
                assertEquals("REMEMBERED", event.rating);
                var first = user.userDao().progress(firstWord);
                assertEquals(1, first.reviewCount);
                assertEquals(1, first.box);
                assertEquals(0, first.consecutiveMistakes);
                assertEquals(event.reviewedAt, first.lastReviewedAt.longValue());
                assertEquals(event.nextDueAt, first.dueAt);
                assertFalse(state(scenario).flipped);

                onView(withId(R.id.card)).perform(click());
                long secondId = state(scenario).presentationId;
                String secondWord = state(scenario).card.getVocabulary().stableId;
                assertTrue(secondId > firstId);
                assertNull(user.userDao().progress(secondWord));
                scenario.recreate();
                awaitReady(scenario, 2);
                assertSame("Rotation retains the guard and session", original, model(scenario));
                assertEquals(secondId, state(scenario).presentationId);
                assertTrue(state(scenario).flipped);
                // Even after the debounce interval, an old occurrence cannot rate the new card.
                SystemClock.sleep(600);
                scenario.onActivity(activity -> original.rate(firstId,
                        com.vocamind.app.domain.learning.Rating.FORGOT));
                assertEquals(1, user.userDao().reviews().size());
                onView(withId(R.id.rate_hard)).perform(click());
                awaitReady(scenario, 3);
                assertEquals(2, user.userDao().reviews().size());
                var second = user.userDao().progress(secondWord);
                assertEquals(1, second.reviewCount);
                assertEquals(0, second.box);
                assertEquals(0, second.consecutiveMistakes);
                assertEquals(1, user.userDao().progress(firstWord).reviewCount);
            }
        } finally {
            user.close();
            catalog.close();
        }
    }

    private static void tripleTap(ActivityScenario<MainActivity> scenario) {
                int[] location = new int[2];
                float[] center = new float[2];
                scenario.onActivity(activity -> {
                    View view = activity.findViewById(R.id.rate_remembered);
                    assertTrue(androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast(90).matches(view));
                    assertTrue(view.isEnabled());
                    view.getLocationOnScreen(location);
                    center[0] = location[0] + view.getWidth() / 2f;
                    center[1] = location[1] + view.getHeight() / 2f;
                });
                var instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
                var automation = instrumentation.getUiAutomation();
                long[] taps = new long[3];
                for (int i = 0; i < taps.length; i++) {
                    if (i > 0) {
                        long remaining = taps[i - 1] + 150 - SystemClock.uptimeMillis();
                        if (remaining > 0) SystemClock.sleep(remaining);
                    }
                    taps[i] = SystemClock.uptimeMillis();
                    MotionEvent down = MotionEvent.obtain(taps[i], taps[i], MotionEvent.ACTION_DOWN, center[0], center[1], 0);
                    MotionEvent up = MotionEvent.obtain(taps[i], taps[i] + 1, MotionEvent.ACTION_UP, center[0], center[1], 0);
                    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    try {
                        // Do not wait for LDPlayer to finish dispatching each event: synchronous
                        // injection adds the device's render/dispatch latency between taps and no
                        // longer represents a user producing input at 150 ms intervals.
                        assertTrue(automation.injectInputEvent(down, false));
                        assertTrue(automation.injectInputEvent(up, false));
                    } finally { down.recycle(); up.recycle(); }
                }
                for (int i = 1; i < taps.length; i++) {
                    long gap = taps[i] - taps[i - 1];
                    Log.i("FLASH03", "tap gap ms=" + gap);
                    assertTrue("Tap gap must be 150ms (+50ms device scheduling), was " + gap,
                            gap >= 150 && gap <= 200);
                }
                instrumentation.waitForIdleSync();
    }

    private static FlashcardViewModel model(ActivityScenario<MainActivity> scenario) {
        AtomicReference<FlashcardViewModel> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            var fragment = host.getChildFragmentManager().getPrimaryNavigationFragment();
            if (fragment instanceof FlashcardFragment && fragment.getView() != null) {
                result.set(new ViewModelProvider(fragment).get(FlashcardViewModel.class));
            }
        });
        return result.get();
    }

    private static FlashcardViewModel.State state(ActivityScenario<MainActivity> scenario) {
        AtomicReference<FlashcardViewModel.State> result = new AtomicReference<>();
        FlashcardViewModel model = model(scenario);
        if (model == null) return null;
        scenario.onActivity(activity -> result.set(model.state().getValue()));
        return result.get();
    }

    private static void awaitReady(ActivityScenario<MainActivity> scenario, int position) {
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        do {
            FlashcardViewModel.State state = state(scenario);
            if (state != null && state.status == FlashcardViewModel.Status.READY
                    && !state.saving && state.position == position) return;
            if (state != null) assertFalse("Review write failed", state.saveFailed);
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Timed out awaiting card " + position);
    }
}
