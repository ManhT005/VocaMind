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
    @Test public void longestPackDetailAtLargeFontKeepsFullTextAndIpaFallback() throws Exception {
        var packs=oracle.catalogDao().packs(); int bestPack=0,bestWord=0,bestLength=-1;
        for (int p=0;p<packs.size();p++) {
            var words=oracle.catalogDao().words(packs.get(p).id);
            for (int w=0;w<words.size();w++) {
                var word=words.get(w); int length=word.lemma.length()+word.meaningVi.length()+word.exampleEn.length()+word.exampleVi.length();
                if (length>bestLength) { bestLength=length;bestPack=p;bestWord=w; }
            }
        }
        var pack=packs.get(bestPack); var words=oracle.catalogDao().words(pack.id);var word=words.get(bestWord);
        String before=canonical(userdata());
        launch(); scenario.onActivity(a -> assertEquals("Declared large-font setup must be effective",1.3f,a.getResources().getConfiguration().fontScale,.01f));
        await("Home",a -> visible(a,R.id.open_packs));tapScroll(R.id.open_packs);
        await("packs",a -> a.findViewById(R.id.pack_list)!=null && ((android.view.ViewGroup)a.findViewById(R.id.pack_list)).getChildCount()==4);
        onView(withText(pack.title)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.click());
        listCount(R.id.words,words.size());row(R.id.words,bestWord,word.meaningVi,true);
        await("detail",a -> text(a,R.id.detail_lemma,word.lemma));
        onView(withId(R.id.detail_meaning)).perform(androidx.test.espresso.action.ViewActions.scrollTo()).check(matches(withText(word.meaningVi)));
        onView(withId(R.id.detail_example_en)).perform(androidx.test.espresso.action.ViewActions.scrollTo()).check(matches(withText(word.exampleEn)));
        onView(withId(R.id.detail_example_vi)).perform(androidx.test.espresso.action.ViewActions.scrollTo()).check(matches(withText(word.exampleVi)));
        assertNull("Current corpus exercises missing IPA",word.ipa);
        onView(withId(R.id.detail_ipa)).perform(androidx.test.espresso.action.ViewActions.scrollTo()).check(matches(withText(R.string.word_ipa_missing)));
        assertEquals(before,canonical(userdata()));
        checkpoint("large-font-longest-detail",new JSONObject().put("effectiveFontScale",1.3)
                .put("selection","maximum combined lemma/meaning/example EN+VI character count across packaged corpus")
                .put("textCharacters",bestLength).put("scope","longest actual MVP detail; not arbitrary synthetic text length"));
    }
    @Test public void searchWithVisibleImeKeepsResultsAndBackState() throws Exception {
        org.junit.Assume.assumeTrue("IME insets assertion requires API30+",android.os.Build.VERSION.SDK_INT>=30);
        String before=canonical(userdata());
        var expected=oracle.catalogDao().searchVocabulary("\"customer\"*"); assertFalse(expected.isEmpty());
        launch(); await("Home",a -> visible(a,R.id.open_vocabulary)); tapScroll(R.id.open_vocabulary);
        await("search input",a -> visible(a,R.id.search));
        onView(isAssignableFrom(android.widget.EditText.class)).perform(androidx.test.espresso.action.ViewActions.click(),
                androidx.test.espresso.action.ViewActions.typeText("customer"));
        await("IME actually visible",a -> a.getWindow().getDecorView().getRootWindowInsets()!=null
                && a.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()));
        listCount(R.id.results,expected.size());
        checkpoint("ime-results",new JSONObject().put("imeVisible",true).put("resultCount",expected.size()));
        androidx.test.espresso.Espresso.pressBack();
        await("IME dismissed, search retained",a -> visible(a,R.id.search)
                && !a.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()));
        onView(isAssignableFrom(android.widget.EditText.class)).check(matches(withText("customer")));
        row(R.id.results,0,expected.get(0).meaningVi,true);
        await("detail",a -> text(a,R.id.detail_lemma,expected.get(0).lemma));
        tapScroll(R.id.back); await("search returned",a -> visible(a,R.id.search));
        onView(isAssignableFrom(android.widget.EditText.class)).check(matches(withText("customer")));
        assertEquals(before,canonical(userdata()));
        checkpoint("ime-return",new JSONObject().put("queryRetained",true).put("userdataUnchanged",true));
    }
    private void measuredTargets(String label, int... ids) throws Exception {
        var measurements = new org.json.JSONArray();
        scenario.onActivity(a -> {
            float density = a.getResources().getDisplayMetrics().density;
            for (int id : ids) {
                var view = a.findViewById(id); assertNotNull(view);
                float width = view.getWidth()/density, height = view.getHeight()/density;
                assertTrue("Touch width >=48dp", width >= 48); assertTrue("Touch height >=48dp", height >= 48);
                String text = view instanceof android.widget.TextView ? ((android.widget.TextView)view).getText().toString() : "";
                assertTrue("Action has text or content description", !text.isEmpty() || view.getContentDescription()!=null);
                int[] location = new int[2]; view.getLocationOnScreen(location);
                try { measurements.put(new JSONObject().put("view",a.getResources().getResourceEntryName(id))
                        .put("widthDp",width).put("heightDp",height).put("label",text)
                        .put("screenY",location[1]).put("clickable",view.isClickable())); }
                catch (org.json.JSONException e) { throw new AssertionError(e); }
            }
        });
        write("touch-targets-"+label+".json",new JSONObject().put("scope","actual layout bounds and action labels; not a full TalkBack/focus-order audit").put("targets",measurements));
    }
    @Test public void plannerDueFirstRemainingQuotaAndPausedExclusionThroughUi() throws Exception {
        var packs = oracle.catalogDao().packs(); var words = oracle.catalogDao().words(packs.get(0).id);
        long now = System.currentTimeMillis();
        user.userDao().activatePack(packs.get(0).id, now);
        var paused = new com.vocamind.app.data.user.entity.UserPack(); paused.packId = packs.get(1).id;
        paused.activatedAt = now; paused.isActive = false; user.userDao().savePack(paused);
        var preference = user.userDao().preference(); preference.dailyNewWords = 2; user.userDao().savePreference(preference);
        for (int i=0;i<3;i++) {
            var row = new UserWordProgress(); row.vocabularyId = words.get(i).id; row.box = 1;
            row.dueAt = i<2 ? now-(2-i)*86400000L : now+86400000L;
            row.learnedAt = i<2 ? now-86400000L : now; row.lastReviewedAt = row.learnedAt; row.reviewCount = 1;
            user.userDao().saveProgress(row);
        }
        String pausedId = oracle.catalogDao().words(paused.packId).get(0).id;
        var excluded = new UserWordProgress(); excluded.vocabularyId = pausedId; excluded.box = 1;
        excluded.dueAt = now-3*86400000L; excluded.learnedAt = now-86400000L; excluded.reviewCount = 1;
        user.userDao().saveProgress(excluded);
        String[] expected = {words.get(0).id,words.get(1).id,words.get(3).id};
        write("controlled-fixture.json", new JSONObject().put("setup", "active pack:two overdue sorted oldest first;quota2 with one learned today;one older due word in paused pack excluded")
                .put("expectedIds", new org.json.JSONArray(expected)).put("userdata", userdata()));
        launch(); await("Home", a -> visible(a,R.id.open_today)); tapScroll(R.id.open_today);
        await("due2 new1", a -> text(a,R.id.summary,a.getString(R.string.today_summary,2,1)));
        tapScroll(R.id.start); long last = -10000;
        for (String id : expected) {
            await("expected card", a -> flash(a)!=null && flash(a).card!=null && id.equals(flash(a).card.getVocabulary().stableId));
            tapScroll(R.id.card); await("flipped", a -> visible(a,R.id.rate_remembered));
            debounceWindow(last); tapScroll(R.id.rate_remembered); last = android.os.SystemClock.elapsedRealtime();
        }
        await("finished", a -> !visible(a,R.id.card));
        assertEquals(3,user.userDao().reviews().size()); assertEquals(1,user.userDao().progress(pausedId).reviewCount);
        var ids = new java.util.HashSet<String>(); for (var review : user.userDao().reviews()) ids.add(review.vocabularyId);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(expected)),ids);
        checkpoint("planner-due-quota",new JSONObject().put("expectedIds",new org.json.JSONArray(expected)).put("reviewEvents",3));
    }
    @Test public void attributionExternalLinkReturnsOfflineWithoutUserdataChanges() throws Exception {
        String before = canonical(userdata());
        launch(); await("Home", a -> visible(a, R.id.open_attribution)); tapScroll(R.id.open_attribution);
        await("sources", a -> a.findViewById(R.id.sources) != null
                && ((android.view.ViewGroup)a.findViewById(R.id.sources)).getChildCount() > 0);
        var firstSource = new org.hamcrest.TypeSafeMatcher<android.view.View>() {
            @Override public void describeTo(org.hamcrest.Description d) { d.appendText("first rendered source row"); }
            @Override protected boolean matchesSafely(android.view.View view) {
                return view.getParent() instanceof android.view.ViewGroup
                        && ((android.view.ViewGroup)view.getParent()).getId() == R.id.sources
                        && ((android.view.ViewGroup)view.getParent()).indexOfChild(view) == 0;
            }
        };
        onView(org.hamcrest.Matchers.allOf(withId(R.id.open_source), isDescendantOfA(firstSource)))
                .perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click());
        var automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String external = ""; long deadline = android.os.SystemClock.uptimeMillis()+10000;
        while (android.os.SystemClock.uptimeMillis()<deadline) {
            var root = automation.getRootInActiveWindow();
            if (root != null && root.getPackageName() != null && !PACKAGE.contentEquals(root.getPackageName())) {
                external = root.getPackageName().toString(); break;
            }
            new java.util.concurrent.CountDownLatch(1).await(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        assertFalse("A browser/resolver window must actually open", external.isEmpty());
        // Give the external process time to render. A blank cold-start window
        // must not be presented as a verified browser network-error page.
        new java.util.concurrent.CountDownLatch(1).await(5, java.util.concurrent.TimeUnit.SECONDS);
        boolean networkError = false;
        var root = automation.getRootInActiveWindow();
        if (root != null) {
            for (String marker : new String[]{"ERR_INTERNET_DISCONNECTED","No internet","You're offline","Không có Internet"})
                networkError |= !root.findAccessibilityNodeInfosByText(marker).isEmpty();
        }
        var screenshot = automation.takeScreenshot();
        try (var stream = new java.io.FileOutputStream(file("external-offline.png"))) {
            assertNotNull(screenshot); assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));
        }
        long now = android.os.SystemClock.uptimeMillis();
        assertTrue(automation.injectInputEvent(new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK,0),true));
        assertTrue(automation.injectInputEvent(new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK,0),true));
        await("Attribution returned", a -> visible(a, R.id.sources));
        assertEquals(before, canonical(userdata()));
        checkpoint("external-return", new JSONObject().put("externalPackage", external)
                .put("browserNetworkErrorObserved", networkError)
                .put("scope", "actual offline external launch and Back; browser first-run/error screen retained, not proof of successful web fetch"));
    }
    @Test public void quizDoubleTapLocksOneAnswerWithoutPrematureAttempt() throws Exception {
        String packId = oracle.catalogDao().packs().get(0).id;
        user.userDao().activatePack(packId, System.currentTimeMillis());
        write("controlled-fixture.json", new JSONObject().put("setup", "one active packaged pack, no progress or QuizAttempt"));
        String before = canonical(userdata());
        launch(); await("Home", a -> visible(a, R.id.open_quiz)); tapScroll(R.id.open_quiz);
        await("Quiz ready", a -> quiz() != null && quiz().phase == com.vocamind.app.feature.quiz.QuizViewModel.Phase.ANSWERING);
        measuredTargets("quiz",R.id.quiz_option_1,R.id.quiz_option_2,R.id.quiz_option_3,R.id.quiz_option_4);
        String questionId = quiz().session.getCurrentQuestion().getId();
        onView(withId(R.id.quiz_option_1)).perform(androidx.test.espresso.action.ViewActions.scrollTo());
        float[] point = new float[2];
        scenario.onActivity(a -> {
            var view = a.findViewById(R.id.quiz_option_1); int[] location = new int[2]; view.getLocationOnScreen(location);
            point[0] = location[0] + view.getWidth()/2f; point[1] = location[1] + view.getHeight()/2f;
        });
        long[] times = new long[2]; long start = android.os.SystemClock.uptimeMillis();
        var automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation();
        for (int i=0;i<2;i++) {
            long delay = start + i*150 - android.os.SystemClock.uptimeMillis();
            if (delay > 0) new java.util.concurrent.CountDownLatch(1).await(delay, java.util.concurrent.TimeUnit.MILLISECONDS);
            long now = android.os.SystemClock.uptimeMillis(); times[i] = now;
            for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                var event = android.view.MotionEvent.obtain(now, now, action, point[0], point[1], 0);
                event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                try { assertTrue(automation.injectInputEvent(event, false)); } finally { event.recycle(); }
            }
        }
        assertTrue("Actual double tap gap150..200ms", times[1]-times[0]>=150 && times[1]-times[0]<=200);
        await("answer lock", a -> quiz().phase == com.vocamind.app.feature.quiz.QuizViewModel.Phase.ANSWERED);
        assertEquals(1, quiz().session.getAnswers().size()); assertEquals(questionId, quiz().session.getCurrentQuestion().getId());
        for (int id : new int[]{R.id.quiz_option_1,R.id.quiz_option_2,R.id.quiz_option_3,R.id.quiz_option_4})
            onView(withId(id)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
        assertEquals(before, canonical(userdata()));
        checkpoint("quiz-double-tap", new JSONObject().put("gapMs", times[1]-times[0]).put("answers", 1).put("attempts", 0));
    }
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
        androidx.test.espresso.Espresso.pressBack();
        await("Home", a -> visible(a, R.id.open_quiz)); tapScroll(R.id.open_quiz);
        await("Quiz empty pool0", a -> text(a, R.id.quiz_status, a.getString(R.string.quiz_empty)));
        assertTrue(user.userDao().quizzes().isEmpty());
        checkpoint("quiz-pool-zero", new JSONObject().put("pool", 0).put("attempts", 0));
    }

    @Test public void pausedOnlyKeepsChoosePackState() throws Exception {
        var pack = new com.vocamind.app.data.user.entity.UserPack();
        pack.packId = oracle.catalogDao().packs().get(0).id;
        pack.activatedAt = System.currentTimeMillis(); pack.isActive = false; user.userDao().savePack(pack);
        String before = canonical(userdata());
        write("controlled-fixture.json", new JSONObject().put("setup", "one paused pack; no active pack").put("userdata", userdata()));
        launch(); await("Home", a -> visible(a, R.id.open_today)); tapScroll(R.id.open_today);
        await("Choose pack", a -> text(a, R.id.message, a.getString(R.string.today_choose_pack)));
        assertEquals(before, canonical(userdata()));
        checkpoint("paused-only", new JSONObject().put("automaticActivation", false));
    }

    @Test public void activePackNoDueOrNewShowsCompleted() throws Exception {
        var pack = oracle.catalogDao().packs().get(0); long now = System.currentTimeMillis();
        user.userDao().activatePack(pack.id, now);
        for (var word : oracle.catalogDao().words(pack.id)) {
            var row = new UserWordProgress(); row.vocabularyId = word.id; row.box = 1;
            row.dueAt = now + 86400000L; row.learnedAt = now; row.lastReviewedAt = now; row.reviewCount = 1;
            user.userDao().saveProgress(row);
        }
        String before = canonical(userdata());
        write("controlled-fixture.json", new JSONObject().put("setup", "one active pack; all members have future dueAt and progress, no new words").put("userdata", userdata()));
        launch(); await("Home", a -> visible(a, R.id.open_today)); tapScroll(R.id.open_today);
        await("Completed", a -> text(a, R.id.message, a.getString(R.string.today_done)));
        onView(withId(R.id.done_quiz)).check(matches(isDisplayed()));
        assertEquals(before, canonical(userdata()));
        checkpoint("no-due", new JSONObject().put("due", 0).put("new", 0));
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
        measuredTargets("ratings",R.id.rate_forgot,R.id.rate_hard,R.id.rate_remembered);
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
