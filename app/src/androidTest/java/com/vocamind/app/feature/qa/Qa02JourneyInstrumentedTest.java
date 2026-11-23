package com.vocamind.app.feature.qa;

import static org.junit.Assert.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

import com.vocamind.app.R;
import com.vocamind.app.data.user.entity.UserWordProgress;
import com.vocamind.app.feature.flashcard.FlashcardViewModel;
import com.vocamind.app.feature.quiz.QuizViewModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

/** One continuous journey. Only the declared prelaunch quota fixture is written by the test. */
public final class Qa02JourneyInstrumentedTest extends Qa02Harness {
    private void home() throws Exception {
        androidx.test.espresso.Espresso.pressBack();
        await("Home", a -> visible(a, R.id.open_packs));
    }

    @Test public void offlineJourney() throws Exception {
        assertEquals("Run with qa02Phase=journey", "journey", phase);
        initialCatalog = catalog(oracle);
        launch();
        await("first Home", a -> visible(a, R.id.open_packs));
        checkpoint("01-home", new JSONObject().put("firstActivityOffline", true));
        var packs = oracle.catalogDao().packs();
        tapScroll(R.id.open_packs);
        for (int i = 0; i < packs.size(); i++) {
            String title = packs.get(i).title;
            await("loaded pack list", a -> a.findViewById(R.id.pack_list) != null
                    && ((android.view.ViewGroup)a.findViewById(R.id.pack_list)).getChildCount() == 4
                    && !visible(a, R.id.loading));
            onView(withText(title)).perform(scrollTo(), click());
            await("activation ready", a -> text(a, R.id.activation, a.getString(R.string.pack_activate)) && a.findViewById(R.id.activation).isEnabled());
            tapScroll(R.id.activation);
            await("active", a -> text(a, R.id.activation, a.getString(R.string.pack_pause)) && a.findViewById(R.id.activation).isEnabled());
            if (i == 0 || i == 3) {
                tapScroll(R.id.activation);
                await("paused", a -> text(a, R.id.activation, a.getString(R.string.pack_activate)) && a.findViewById(R.id.activation).isEnabled());
                if (i == 0) {
                    tapScroll(R.id.activation);
                    await("reactivated", a -> text(a, R.id.activation, a.getString(R.string.pack_pause)) && a.findViewById(R.id.activation).isEnabled());
                }
            }
            tapScroll(R.id.back);
        }
        assertEquals(3, user.userDao().activePacks().size());
        checkpoint("02-packs", new JSONObject().put("active", 3).put("paused", 1));
        home();

        // Search literal English and Vietnamese against a separate packaged-catalog oracle.
        tapScroll(R.id.open_vocabulary);
        for (String[] search : new String[][]{{"customer", "\"customer\"*"}, {"khách hàng", "\"khách\"* \"hàng\"*"}}) {
            var expected = oracle.catalogDao().searchVocabulary(search[1]);
            assertFalse("Fixture must exercise a nonempty search", expected.isEmpty());
            query(search[0]); listCount(R.id.results, expected.size());
            row(R.id.results, 0, expected.get(0).meaningVi, true);
            await("detail stable vocabulary", a -> text(a, R.id.detail_lemma, expected.get(0).lemma));
            onView(withId(R.id.detail_meaning)).check(matches(withText(expected.get(0).meaningVi)));
            var word = expected.get(0);
            onView(withId(R.id.detail_pos)).check(matches(withText(word.pos)));
            onView(withId(R.id.detail_example_en)).check(matches(withText(word.exampleEn)));
            onView(withId(R.id.detail_example_vi)).check(matches(withText(word.exampleVi)));
            onView(withId(R.id.detail_ipa)).check(matches(withText(word.ipa == null || word.ipa.trim().isEmpty()
                    ? context.getString(R.string.word_ipa_missing) : word.ipa)));
            tapScroll(R.id.back);
        }
        query("qa02zzzznoresult"); message(R.string.search_no_match);
        tap(R.id.reset);
        var savedWord = oracle.catalogDao().words(packs.get(3).id).get(0);
        query(savedWord.lemma);
        var matching = oracle.catalogDao().searchVocabulary("\"" + savedWord.normalizedLemma.replace("\"", "\"\"") + "\"*");
        int savedIndex = -1;
        for (int i = 0; i < matching.size(); i++) if (matching.get(i).id.equals(savedWord.id)) savedIndex = i;
        assertTrue(savedIndex >= 0);
        row(R.id.results, savedIndex, savedWord.meaningVi, true);
        await("save detail", a -> text(a, R.id.detail_save, a.getString(R.string.detail_save)) && a.findViewById(R.id.detail_save).isEnabled());
        for (boolean save : new boolean[]{true, false, true}) {
            tapScroll(R.id.detail_save);
            await("save committed", a -> text(a, R.id.detail_save, a.getString(save ? R.string.detail_unsave : R.string.detail_save)) && a.findViewById(R.id.detail_save).isEnabled());
            assertEquals(save, user.userDao().progress(savedWord.id).isSaved);
        }
        assertEquals(0, user.userDao().reviews().size());
        assertNull(user.userDao().progress(savedWord.id).learnedAt);
        checkpoint("03-search-save", new JSONObject().put("savedPausedPackWord", savedWord.id));
        tapScroll(R.id.back);
        onView(isAssignableFrom(android.widget.EditText.class)).perform(pressImeActionButton(), closeSoftKeyboard());
        home();

        // Expected fresh queue from ordered memberships and raw progress, independent of Today use case.
        Set<String> active = new HashSet<>();
        for (var pack : user.userDao().activePacks()) active.add(pack.packId);
        LinkedHashSet<String> eligible = new LinkedHashSet<>();
        for (var pack : packs) if (active.contains(pack.id)) for (var word : oracle.catalogDao().words(pack.id)) eligible.add(word.id);
        List<String> initial = new ArrayList<>();
        for (String id : eligible) if (user.userDao().progress(id) == null && initial.size() < 10) initial.add(id);
        assertEquals(10, initial.size()); assertFalse(initial.contains(savedWord.id));
        tapScroll(R.id.open_today);
        await("Today quota", a -> text(a, R.id.summary, a.getString(R.string.today_summary, 0, 10)));
        checkpoint("04-today", new JSONObject().put("initialIds", new JSONArray(initial)).put("due", 0));
        tapScroll(R.id.start);
        List<String> queue = new ArrayList<>(initial);
        Set<String> seen = new HashSet<>();
        long lastTap = -10000;
        JSONArray reviewOracle = new JSONArray();
        for (int position = 0; position < queue.size(); position++) {
            await("flash ready", a -> flash(a) != null && flash(a).status == FlashcardViewModel.Status.READY && !flash(a).saving);
            var state = flash(); String id = queue.get(position);
            assertEquals(id, state.card.getVocabulary().stableId);
            assertEquals(position + 1, state.position); assertFalse(state.flipped);
            var word = oracle.catalogDao().vocabulary(id);
            onView(withId(R.id.card_lemma)).check(matches(withText(word.lemma)));
            tapScroll(R.id.card_lemma);
            await("flipped", a -> flash(a).flipped);
            if (position == 0) {
                long occurrence = state.presentationId;
                rotateAndRestore();
                scenario.recreate();
                await("flipped after recreate", a -> flash(a) != null && flash(a).flipped && flash(a).presentationId == occurrence);
            }
            boolean first = seen.add(id);
            String rating = first ? new String[]{"FORGOT", "HARD", "REMEMBERED"}[initial.indexOf(id) % 3] : "REMEMBERED";
            UserWordProgress before = user.userDao().progress(id);
            int oldBox = before == null ? 0 : before.box;
            int oldCount = before == null ? 0 : before.reviewCount;
            int oldMistakes = before == null ? 0 : before.consecutiveMistakes;
            debounceWindow(lastTap);
            tapScroll(rating.equals("FORGOT") ? R.id.rate_forgot : rating.equals("HARD") ? R.id.rate_hard : R.id.rate_remembered);
            lastTap = android.os.SystemClock.elapsedRealtime();
            long occurrence = state.presentationId;
            await("review committed", a -> flash(a) != null && !flash(a).saving
                    && (flash(a).status == FlashcardViewModel.Status.FINISHED || flash(a).presentationId != occurrence));
            var events = user.userDao().reviews(); assertEquals(position + 1, events.size());
            var event = events.get(events.size() - 1); var after = user.userDao().progress(id);
            int box = rating.equals("FORGOT") ? 0 : rating.equals("HARD") ? oldBox : Math.min(oldBox + 1, 5);
            long due = Instant.ofEpochMilli(event.reviewedAt).atZone(ZoneId.systemDefault())
                    .plusDays(new int[]{1, 1, 3, 7, 14, 30}[box]).toInstant().toEpochMilli();
            assertEquals(id, event.vocabularyId); assertEquals(rating, event.rating);
            assertEquals(oldBox, event.boxBefore); assertEquals(box, event.boxAfter);
            assertEquals(due, event.nextDueAt); assertEquals(due, after.dueAt); assertEquals(box, after.box);
            assertEquals(oldCount + 1, after.reviewCount);
            int mistakes = rating.equals("FORGOT") ? oldMistakes + 1 : 0;
            assertEquals(mistakes, after.consecutiveMistakes);
            assertEquals(rating.equals("FORGOT") && ((before != null && before.isLeech) || mistakes >= 4), after.isLeech);
            assertEquals(Long.valueOf(event.reviewedAt), after.lastReviewedAt);
            assertEquals(before == null ? Long.valueOf(event.reviewedAt) : before.learnedAt, after.learnedAt);
            reviewOracle.put(new JSONObject().put("id", id).put("initial", first).put("rating", rating)
                    .put("box", box).put("nextDueAt", due).put("reviewedAt", event.reviewedAt));
            if (first && rating.equals("FORGOT")) queue.add(Math.min(queue.size(), position + 3), id);
            assertTrue("Relearn must be finite", queue.size() <= 20);
        }
        assertEquals(14, queue.size()); assertEquals(10, seen.size());
        assertEquals(FlashcardViewModel.Status.FINISHED, flash().status);
        write("review-oracle.json", new JSONObject().put("initialCount", 10).put("relearnCount", 4).put("events", reviewOracle));
        checkpoint("05-flashcards", new JSONObject().put("initial", 10).put("relearn", 4));
        tapScroll(R.id.back); home();

        JSONObject beforeQuiz = userdata();
        tapScroll(R.id.open_quiz);
        await("quiz", a -> quiz() != null && quiz().phase == QuizViewModel.Phase.ANSWERING);
        assertEquals(10, quiz().session.getQuestions().size());
        Set<String> questions = new HashSet<>(); int correct = 0; lastTap = -10000;
        JSONArray questionOracle = new JSONArray();
        int[] options = {R.id.quiz_option_1, R.id.quiz_option_2, R.id.quiz_option_3, R.id.quiz_option_4};
        for (int i = 0; i < 10; i++) {
            var question = quiz().session.getCurrentQuestion();
            assertTrue(questions.add(question.getId())); assertTrue(eligible.contains(question.getId()));
            assertEquals(4, question.getOptions().size());
            Set<String> meanings = new HashSet<>(), ids = new HashSet<>();
            String expectedMeaning = oracle.catalogDao().vocabulary(question.getId()).meaningVi;
            String correctId = null;
            for (var option : question.getOptions()) {
                assertTrue(ids.add(option.getId()));
                assertTrue(meanings.add(option.getMeaning().trim().toLowerCase(Locale.ROOT)));
                if (expectedMeaning.equals(option.getMeaning())) correctId = option.getId();
            }
            assertNotNull(correctId); assertEquals(correctId, question.getCorrectOptionId());
            int choice = i % 4;
            if (expectedMeaning.equals(question.getOptions().get(choice).getMeaning())) correct++;
            debounceWindow(lastTap);
            tapScroll(options[choice]);
            lastTap = android.os.SystemClock.elapsedRealtime();
            if (i < 9) {
                await("answer lock", a -> quiz().phase == QuizViewModel.Phase.ANSWERED);
                if (i == 0) {
                    rotateAndRestore();
                    scenario.recreate();
                    await("answer after recreate", a -> quiz() != null && quiz().phase == QuizViewModel.Phase.ANSWERED);
                    assertEquals(question.getId(), quiz().session.getCurrentQuestion().getId());
                    tapScroll(R.id.quiz_back);
                    androidx.test.espresso.Espresso.onView(withText(R.string.quiz_continue))
                            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                    assertEquals(question.getId(), quiz().session.getCurrentQuestion().getId());
                }
                tapScroll(R.id.quiz_next);
                final int next = i + 1;
                await("next question", a -> quiz().phase == QuizViewModel.Phase.ANSWERING && quiz().session.getPosition() == next);
            }
            questionOracle.put(new JSONObject().put("id", question.getId()).put("correctMeaning", expectedMeaning)
                    .put("chosenMeaning", question.getOptions().get(choice).getMeaning()));
        }
        await("saved result", a -> quiz().phase == QuizViewModel.Phase.RESULT);
        assertEquals(1, user.userDao().quizzes().size());
        var attempt = user.userDao().quizzes().get(0);
        assertEquals(10, attempt.questionCount); assertEquals(correct, attempt.correctCount); assertEquals(correct * 10, attempt.scorePercent);
        for (String key : new String[]{"UserPack", "UserPreference", "UserWordProgress", "ReviewEvent"})
            assertEquals(canonical(beforeQuiz.get(key)), canonical(userdata().get(key)));
        scenario.recreate(); await("result restored", a -> quiz() != null && quiz().phase == QuizViewModel.Phase.RESULT);
        assertEquals(1, user.userDao().quizzes().size());
        write("quiz-oracle.json", new JSONObject().put("questions", questionOracle).put("correct", correct).put("score", correct * 10));
        checkpoint("06-quiz", new JSONObject().put("attemptCount", 1).put("score", correct * 10));
        tap(R.id.quiz_back); await("Home", a -> visible(a, R.id.open_progress));
        tapScroll(R.id.open_quiz);
        await("completed Quiz reopened", a -> quiz() != null && quiz().phase == QuizViewModel.Phase.RESULT);
        assertEquals(1, user.userDao().quizzes().size());
        tapScroll(R.id.quiz_back); await("Home", a -> visible(a, R.id.open_progress));
        tapScroll(R.id.open_progress);
        await("progress loaded", a -> visible(a, R.id.progress_list));
        int[] boxes = new int[6];
        for (var progress : user.userDao().allProgress()) boxes[progress.box]++;
        int learning = boxes[0] + boxes[1], reviewing = boxes[2] + boxes[3], mastered = boxes[4] + boxes[5];
        visibleListChild(R.id.progress_list, R.id.progress_counts);
        onView(withId(R.id.progress_counts)).check(matches(withText(context.getString(R.string.progress_counts, learning + reviewing + mastered, learning, reviewing, mastered))));
        int[] boxViews = {R.id.box_0, R.id.box_1, R.id.box_2, R.id.box_3, R.id.box_4, R.id.box_5};
        for (int i = 0; i < 6; i++) {
            visibleListChild(R.id.progress_list, boxViews[i]);
            final int count = boxes[i]; final int view = boxViews[i];
            scenario.onActivity(a -> assertTrue(((android.widget.TextView)a.findViewById(view)).getText().toString().endsWith(": " + count)));
        }
        visibleListChild(R.id.progress_list, R.id.mastered_explanation);
        onView(withId(R.id.mastered_explanation)).check(matches(withText(R.string.progress_mastered_explanation)));
        visibleListChild(R.id.progress_list, R.id.history_result);
        onView(withId(R.id.history_result)).check(matches(withText(context.getString(R.string.progress_quiz_result,
                10, correct, 10 - correct, correct * 10))));
        checkpoint("07-progress", new JSONObject().put("total", user.userDao().allProgress().size()).put("boxes", new JSONArray(boxes)));
        int sourceCount = oracle.catalogDao().sources().size();
        home(); tapScroll(R.id.open_attribution);
        await("attribution offline", a -> a.findViewById(R.id.sources) != null
                && ((android.view.ViewGroup)a.findViewById(R.id.sources)).getChildCount() == sourceCount);
        checkpoint("08-attribution", new JSONObject().put("sources", sourceCount).put("externalLinkTest", "NOT RUN in this journey"));
        home(); assertRuntimeCatalogUnchanged(); saveExpected();
        checkpoint("09-committed", new JSONObject().put("logicalCatalogUnchanged", true));
    }

    @Test public void committedSnapshotSurvivesReopen() throws Exception {
        assertEquals("probe", phase);
        JSONObject expected = new JSONObject(new String(Files.readAllBytes(new java.io.File(context.getFilesDir(), "qa02/expected-userdata.json").toPath()), StandardCharsets.UTF_8));
        assertEquals(canonical(expected), canonical(userdata()));
        launch(); await("reopened Home", a -> visible(a, R.id.open_packs));
        assertEquals(canonical(expected), canonical(userdata()));
        JSONObject meta = new JSONObject(new String(Files.readAllBytes(new java.io.File(context.getFilesDir(), "qa02/journey/expected-meta.json").toPath()), StandardCharsets.UTF_8));
        var actual = com.vocamind.app.data.catalog.upgrade.CatalogStartupCoordinator.bootstrap(context).openDatabase(context);
        try { assertEquals(meta.getString("logicalCatalogSha256"), digest(catalog(actual))); }
        finally { actual.close(); }
        checkpoint("persistence", new JSONObject().put("allTablesEqual", true).put("snapshotSha256", digest(canonical(expected))));
    }
}
