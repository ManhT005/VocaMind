package com.vocamind.app.feature.qa;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import com.vocamind.app.R;
import com.vocamind.app.data.user.entity.UserWordProgress;
import org.json.JSONObject;
import org.junit.Test;

/** Declared prelaunch state fixtures, followed by production UI actions; not continuous E2E. */
public final class Qa02ControlledInstrumentedTest extends Qa02Harness {
    @Test public void emptyProgressAndHistory() throws Exception {
        launch(); await("Home", a -> visible(a, R.id.open_progress));
        tapScroll(R.id.open_progress);
        await("Progress", a -> visible(a, R.id.progress_list));
        visibleListChild(R.id.progress_list, R.id.progress_counts);
        onView(withId(R.id.progress_counts)).check(matches(withText(context.getString(R.string.progress_counts, 0, 0, 0, 0))));
        visibleListChild(R.id.progress_list, R.id.progress_empty);
        onView(withId(R.id.progress_empty)).check(matches(withText(R.string.progress_empty)));
        visibleListChild(R.id.progress_list, R.id.history_empty);
        onView(withId(R.id.history_empty)).check(matches(withText(R.string.progress_history_empty)));
        assertTrue(user.userDao().allProgress().isEmpty()); assertTrue(user.userDao().quizzes().isEmpty());
        checkpoint("empty-progress", new JSONObject().put("fixture", "fresh empty userdata; no writes"));
    }

    @Test public void noActivePackDoesNotActivateAutomatically() throws Exception {
        launch(); await("Home", a -> visible(a, R.id.open_today)); tapScroll(R.id.open_today);
        await("Choose pack", a -> text(a, R.id.message, a.getString(R.string.today_choose_pack)));
        assertTrue(user.userDao().packs().isEmpty());
        checkpoint("no-active-pack", new JSONObject().put("fixture", "fresh userdata, no active pack"));
    }

    private String dueFixture(int mistakes, boolean leech) throws Exception {
        var pack = oracle.catalogDao().packs().get(0);
        var words = oracle.catalogDao().words(pack.id);
        long now = System.currentTimeMillis();
        user.userDao().activatePack(pack.id, now);
        for (int i = 0; i < words.size(); i++) {
            var row = new UserWordProgress(); row.vocabularyId = words.get(i).id;
            row.box = 0; row.dueAt = i == 0 ? now - 1000 : now + 86400000L;
            row.learnedAt = now - 86400000L; row.lastReviewedAt = now - 86400000L;
            row.reviewCount = i == 0 ? mistakes : 1;
            row.consecutiveMistakes = i == 0 ? mistakes : 0; row.isLeech = i == 0 && leech;
            user.userDao().saveProgress(row);
        }
        write("controlled-fixture.json", new JSONObject().put("setup", "one active pack; all words already learned; first due, others future; no initial ReviewEvents")
                .put("initialMistakes", mistakes).put("initialLeech", leech).put("userdata", userdata()));
        launch(); await("Home", a -> visible(a, R.id.open_today)); tapScroll(R.id.open_today);
        await("one due", a -> text(a, R.id.summary, a.getString(R.string.today_summary, 1, 0)));
        tapScroll(R.id.start); await("card", a -> visible(a, R.id.card));
        tapScroll(R.id.card); await("flipped", a -> visible(a, R.id.rate_forgot));
        return words.get(0).id;
    }

    @Test public void fourthForgotEnablesLeechThenHardClears() throws Exception {
        String id = dueFixture(3, false);
        tapScroll(R.id.rate_forgot);
        await("relearn card", a -> visible(a, R.id.card) && !visible(a, R.id.card_back));
        var leech = user.userDao().progress(id); assertEquals(4, leech.consecutiveMistakes); assertTrue(leech.isLeech);
        onView(withId(R.id.result_hint)).check(matches(withText(R.string.flashcard_leech)));
        debounceWindow(android.os.SystemClock.elapsedRealtime());
        tapScroll(R.id.card); tapScroll(R.id.rate_hard);
        await("finished", a -> !visible(a, R.id.card));
        var cleared = user.userDao().progress(id); assertFalse(cleared.isLeech); assertEquals(0, cleared.consecutiveMistakes); assertEquals(0, cleared.box);
        assertEquals(2, user.userDao().reviews().size());
        checkpoint("leech-hard", new JSONObject().put("setup", "prior3 mistakes from declared multi-session fixture; UI fourth FORGOT then HARD relearn"));
    }

    @Test public void rememberedClearsExistingLeech() throws Exception {
        String id = dueFixture(4, true); tapScroll(R.id.rate_remembered);
        await("finished", a -> !visible(a, R.id.card));
        var cleared = user.userDao().progress(id); assertFalse(cleared.isLeech); assertEquals(0, cleared.consecutiveMistakes); assertEquals(1, cleared.box);
        assertEquals(1, user.userDao().reviews().size());
        checkpoint("leech-remembered", new JSONObject().put("setup", "declared pre-existing leech4; production REMEMBERED action"));
    }

    @Test public void reviewWriteFailureKeepsCardAndRecoversWithoutDuplicate() throws Exception {
        String id = dueFixture(0, false);
        String before = canonical(userdata());
        String lemma = oracle.catalogDao().vocabulary(id).lemma;
        var sql = user.getOpenHelper().getWritableDatabase();
        sql.execSQL("CREATE TRIGGER qa02_review_abort BEFORE INSERT ON ReviewEvent BEGIN SELECT RAISE(ABORT, 'QA02 controlled review failure'); END");
        try {
            tapScroll(R.id.rate_forgot);
            await("write failure", a -> text(a, R.id.result_hint, a.getString(R.string.flashcard_save_failed)));
            assertEquals(before, canonical(userdata()));
            onView(withId(R.id.card_lemma)).check(matches(withText(lemma)));
            onView(withId(R.id.card_back)).check(matches(isDisplayed()));
            onView(withId(R.id.rate_forgot)).check(matches(isEnabled()));
            checkpoint("review-failure", new JSONObject().put("fault", "SQLite BEFORE INSERT abort trigger; full logical userdata unchanged"));
        } finally { sql.execSQL("DROP TRIGGER qa02_review_abort"); }
        debounceWindow(android.os.SystemClock.elapsedRealtime());
        tapScroll(R.id.rate_remembered);
        await("finished", a -> !visible(a, R.id.card));
        assertEquals(1, user.userDao().reviews().size());
        assertEquals(1, user.userDao().progress(id).box);
        assertEquals(1, user.userDao().progress(id).reviewCount);
        checkpoint("review-recovered", new JSONObject().put("retry", "explicit UI REMEMBERED after removing declared fault; exactly one committed event"));
    }
}
