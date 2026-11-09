package com.vocamind.app.feature.ui;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.feature.flashcard.FlashcardViewModel;
import com.vocamind.app.feature.quiz.QuizViewModel;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Focused real-factory regression; not the full UI-02/QA-02 matrix. Fresh QA app only. */
@RunWith(AndroidJUnit4.class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
public class Ui01SmokeInstrumentedTest {
    private Context isolated() {
        Context c = ApplicationProvider.getApplicationContext();
        Assume.assumeTrue("Requires -Pquiz03DeviceIsolation=true on a fresh QA emulator", c.getPackageName().equals("com.vocamind.app.quiz03validation"));
        return c;
    }

    private void await(BooleanSupplier ready) {
        long end = SystemClock.elapsedRealtime() + 30000;
        while (SystemClock.elapsedRealtime() < end) {
            if (ready.getAsBoolean()) return;
            SystemClock.sleep(60);
        }
        fail("UI-01 timed out waiting for state");
    }

    private FlashcardViewModel.State flash(ActivityScenario<MainActivity> s) {
        AtomicReference<FlashcardViewModel.State> result = new AtomicReference<>();
        s.onActivity(a -> {
            NavHostFragment host = (NavHostFragment) a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            var fragment = host.getChildFragmentManager().getPrimaryNavigationFragment();
            if (fragment instanceof com.vocamind.app.feature.flashcard.FlashcardFragment)
                result.set(new ViewModelProvider(fragment).get(FlashcardViewModel.class).state().getValue());
        });
        return result.get();
    }

    private QuizViewModel.State quiz(ActivityScenario<MainActivity> s) {
        AtomicReference<QuizViewModel.State> result = new AtomicReference<>();
        s.onActivity(a -> {
            if (a.findViewById(R.id.quiz_status) != null)
                result.set(new ViewModelProvider(a).get(QuizViewModel.class).state().getValue());
        });
        return result.get();
    }

    private void navigate(ActivityScenario<MainActivity> s, int id) {
        s.onActivity(a -> ((NavHostFragment) a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment))
                .getNavController().navigate(id));
    }

    private void screenshot(String name) throws Exception {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try (var stream = new java.io.FileOutputStream(new java.io.File(isolated().getExternalFilesDir(null), "ui01-" + name + ".png"))) {
            android.graphics.Bitmap bitmap = automation.takeScreenshot();
            assertNotNull(bitmap);
            try { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream); }
            finally { bitmap.recycle(); }
        }
    }

    @Test public void aTenCardsAndQuizKeepCommittedDataThroughRecreationOffline() throws Exception {
        Context c = isolated();
        assertEquals("Enable airplane mode before first launch", 1,
                Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
        assertEquals(0, Settings.Global.getInt(c.getContentResolver(), "wifi_on", -1));
        UserDatabase user = UserDatabase.open(c);
        CatalogDatabase catalog = CatalogDatabase.open(c, "catalog.db");
        try {
            assertTrue("Fresh UI-01 installation required", user.userDao().reviews().isEmpty());
            assertTrue(user.userDao().quizzes().isEmpty());
            assertTrue(user.userDao().activePacks().isEmpty());
            try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
                navigate(s, R.id.todayFragment);
                await(() -> {
                    boolean[] ready = {false};
                    s.onActivity(a -> { View v = a.findViewById(R.id.explore); ready[0] = v != null && v.getVisibility() == View.VISIBLE; });
                    return ready[0];
                });
                // Fixture setup is confined to the QA database; all learning writes use the UI.
                for (var pack : catalog.catalogDao().packs()) user.userDao().activatePack(pack.id, System.currentTimeMillis());
                // The production daily quota is five. This QA-only preference allows ten taps.
                user.getOpenHelper().getWritableDatabase().execSQL("UPDATE UserPreference SET dailyNewWords=10 WHERE id=1");
                navigate(s, R.id.flashcardFragment);
                await(() -> { var state = flash(s); return state != null && state.status == FlashcardViewModel.Status.READY; });
                int[] ratings = {R.id.rate_forgot, R.id.rate_hard, R.id.rate_remembered};
                for (int i = 0; i < 10; i++) {
                    final long occurrence = flash(s).presentationId;
                    s.onActivity(a -> assertFalse(a.findViewById(R.id.rate_remembered).isEnabled()));
                    onView(withId(R.id.card_lemma)).perform(scrollTo(), click());
                    await(() -> flash(s).flipped);
                    if (i == 0) {
                        s.recreate();
                        assertEquals(occurrence, flash(s).presentationId);
                        assertTrue(flash(s).flipped);
                        screenshot("flashcard");
                    }
                    SystemClock.sleep(650);
                    onView(withId(ratings[i % 3])).perform(scrollTo(), click());
                    await(() -> !flash(s).saving && flash(s).presentationId != occurrence);
                    assertEquals(i + 1, user.userDao().reviews().size());
                }
                navigate(s, R.id.quizFragment);
                await(() -> { var state = quiz(s); return state != null && state.phase == QuizViewModel.Phase.ANSWERING; });
                assertEquals(10, quiz(s).session.getQuestions().size());
                for (int i = 0; i < 10; i++) {
                    SystemClock.sleep(650);
                    onView(withId(R.id.quiz_option_1)).perform(scrollTo(), click());
                    if (i < 9) {
                        await(() -> quiz(s).phase == QuizViewModel.Phase.ANSWERED);
                        if (i == 0) {
                            String question = quiz(s).session.getCurrentQuestion().getId();
                            s.recreate();
                            assertEquals(question, quiz(s).session.getCurrentQuestion().getId());
                            assertEquals(QuizViewModel.Phase.ANSWERED, quiz(s).phase);
                            screenshot("quiz-feedback");
                        }
                        onView(withId(R.id.quiz_next)).perform(scrollTo(), click());
                    }
                }
                await(() -> quiz(s).phase == QuizViewModel.Phase.RESULT);
                s.recreate();
                assertEquals(QuizViewModel.Phase.RESULT, quiz(s).phase);
                assertEquals(10, user.userDao().reviews().size());
                assertEquals(1, user.userDao().quizzes().size());
                screenshot("quiz-result");
                String savedId = catalog.catalogDao().allVocabulary().get(50).id;
                s.onActivity(a -> ((NavHostFragment) a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment))
                        .getNavController().navigate(R.id.vocabularyDetailFragment,
                                com.vocamind.app.feature.vocabulary.VocabularyDetailFragment.searchArguments(savedId)));
                await(() -> {
                    boolean[] ready = {false};
                    s.onActivity(a -> { View v = a.findViewById(R.id.detail_save); ready[0] = v != null && v.isEnabled(); });
                    return ready[0];
                });
                onView(withId(R.id.detail_save)).perform(click());
                await(() -> {
                    boolean[] ready = {false};
                    s.onActivity(a -> ready[0] = ((TextView) a.findViewById(R.id.detail_save)).getText().toString().equals(a.getString(R.string.detail_unsave)));
                    return ready[0];
                });
                navigate(s, R.id.progressFragment);
                await(() -> {
                    boolean[] ready = {false};
                    s.onActivity(a -> { View v = a.findViewById(R.id.progress_list); ready[0] = v != null && v.getVisibility() == View.VISIBLE; });
                    return ready[0];
                });
                screenshot("progress");
                navigate(s, R.id.attributionFragment);
                await(() -> {
                    boolean[] ready = {false};
                    s.onActivity(a -> { android.view.ViewGroup v = a.findViewById(R.id.sources); ready[0] = v != null && v.getChildCount() > 0; });
                    return ready[0];
                });
                screenshot("attribution");
            }
        } finally { catalog.close(); user.close(); }
    }

    @Test public void bCommittedDataSurvivesReopenProbe() {
        Context c = isolated();
        UserDatabase user = UserDatabase.open(c);
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            assertEquals(10, user.userDao().reviews().size());
            assertEquals(1, user.userDao().quizzes().size());
            assertEquals(10, user.userDao().quizzes().get(0).questionCount);
            assertFalse(user.userDao().activePacks().isEmpty());
            assertEquals(1, user.userDao().allProgress().stream().filter(p -> p.isSaved).count());
        } finally { user.close(); }
    }
}
