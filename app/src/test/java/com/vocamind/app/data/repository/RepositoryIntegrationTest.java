package com.vocamind.app.data.repository;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.*;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.domain.quiz.*;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

/** Real file-backed catalog.db + userdata.db coordinated only by stable IDs / pack IDs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class RepositoryIntegrationTest {
    private static final String W1 = "0000000000000001", W2 = "0000000000000002", W3 = "0000000000000003",
            W4 = "0000000000000004", W5 = "0000000000000005", SHARED = "00000000000000aa", PAUSED = "00000000000000bb";
    private static final long DAY = 86_400_000L;

    @Rule public org.junit.rules.TestRule workerThread = (base, description) ->
            new org.junit.runners.model.Statement() {
                @Override public void evaluate() throws Throwable {
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    Thread worker = new Thread(() -> {
                        try { base.evaluate(); } catch (Throwable e) { failure.set(e); }
                    }, "data04-test-worker");
                    worker.start(); worker.join(60000);
                    if (worker.isAlive()) { worker.interrupt(); fail("timed out"); }
                    if (failure.get() != null) throw failure.get();
                }
            };

    private Context context;
    private CatalogDatabase catalog;
    private UserDatabase user;
    private String catalogName, userName;
    private RoomPackRepository packs;
    private RoomStudyRepository study;
    private RoomReviewRepository reviews;
    private RoomQuizRepository quizzes;

    @Before public void open() {
        context = ApplicationProvider.getApplicationContext();
        String token = UUID.randomUUID().toString();
        catalogName = "data04-catalog-" + token + ".db";
        userName = "data04-user-" + token + ".db";
        catalog = CatalogDatabase.open(context, catalogName);
        user = UserDatabase.open(context, userName);
        SupportSQLiteDatabase sql = catalog.getOpenHelper().getWritableDatabase();
        for (String t : new String[]{"PackWord", "VocabularyFts", "VocabularyEntry", "LearningPack", "ContentSource"}) {
            sql.execSQL("DELETE FROM " + t);
        }
        sql.execSQL("INSERT INTO ContentSource (id,title,url,licenseCode,licenseUrl,snapshotDate,attributionText) "
                + "VALUES ('src','Test','https://example.invalid','LicenseRef-Test','https://example.invalid/l','2026-11-01','Test')");
        int order = 1;
        for (String id : Arrays.asList("A", "B", "C")) {
            sql.execSQL("INSERT INTO LearningPack (id,title,description,category,orderNo,version) VALUES (?,?,?,?,?,?)",
                    new Object[]{id, "Pack " + id, "d", "test", order++, "1"});
        }
        for (String id : Arrays.asList(W1, W2, W3, W4, W5, SHARED, PAUSED)) {
            sql.execSQL("INSERT INTO VocabularyEntry (id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                    + "VALUES (?,?,?,?,?,?,?,NULL,'src','1')", new Object[]{id, "w" + id, "w" + id, "noun", "nghia " + id, "ex", "vd"});
        }
        member("A", SHARED, 0); member("A", W1, 1); member("A", W2, 2); member("A", W3, 3);
        member("B", SHARED, 0); member("B", W4, 1); member("B", W5, 2);
        member("C", PAUSED, 0);
        packs = new RoomPackRepository(catalog.catalogDao(), user.userDao());
        study = new RoomStudyRepository(catalog.catalogDao(), user.userDao());
        reviews = new RoomReviewRepository(catalog.catalogDao(), user);
        quizzes = new RoomQuizRepository(catalog.catalogDao(), user.userDao());
    }

    @After public void close() {
        catalog.close(); user.close();
        context.deleteDatabase(catalogName); context.deleteDatabase(userName);
    }

    private void member(String pack, String word, int order) {
        catalog.getOpenHelper().getWritableDatabase().execSQL("INSERT INTO PackWord VALUES (?,?,?)", new Object[]{pack, word, order});
    }

    private static ScheduleResult schedule(LeitnerState state, Rating rating, long now) {
        return new LeitnerScheduler().schedule(state, rating, now, ZoneOffset.UTC);
    }

    private static List<String> ids(List<CatalogVocabulary> list) {
        return list.stream().map(v -> v.stableId).collect(Collectors.toList());
    }

    @Test public void packStatesMergeCatalogWithUserByPackId() {
        List<PackState> before = packs.getPackStates().get();
        assertEquals(Arrays.asList("A", "B", "C"), before.stream().map(s -> s.getPack().id).collect(Collectors.toList()));
        assertEquals(4, before.get(0).getPack().wordCount);
        assertFalse(before.get(0).isActive());
        assertNull(before.get(0).getActivatedAtEpochMillis());

        PackState a = packs.activatePack("A", 1000L).get();
        assertTrue(a.isActive());
        assertEquals(Long.valueOf(1000L), a.getActivatedAtEpochMillis());
        assertTrue(packs.pausePack("A").get());
        assertFalse(packs.pausePack("A").get());
        PackState again = packs.activatePack("A", 9000L).get();
        assertEquals("first activation time survives", Long.valueOf(1000L), again.getActivatedAtEpochMillis());
    }

    @Test public void packWordsKeepOrderNoAndMergeProgressByStableId() {
        long now = 10 * DAY;
        reviews.recordReview(W2, schedule(LeitnerState.newWord(), Rating.REMEMBERED, now), now);
        List<StudyItem> words = packs.getPackWords("A").get();
        assertEquals(Arrays.asList(SHARED, W1, W2, W3),
                words.stream().map(i -> i.getVocabulary().stableId).collect(Collectors.toList()));
        assertNull(words.get(0).getProgress());
        assertEquals(1, words.get(2).getProgress().getBox());
        assertEquals(RepoError.NOT_FOUND, packs.getPackWords("nope").getError());
        assertEquals(RepoError.INVALID_ARGUMENT, packs.getPackWords(" ").getError());
    }

    @Test public void unknownPackIsNotFoundAndNothingIsCreated() {
        assertEquals(RepoError.NOT_FOUND, packs.activatePack("nope", 1L).getError());
        assertTrue(user.userDao().packs().isEmpty());
        assertEquals(RepoError.INVALID_ARGUMENT, packs.activatePack(" ", 1L).getError());
    }

    @Test public void learnedCountIsDistinctWordsWithLearnedAt() {
        packs.activatePack("A", 1L);
        reviews.recordReview(SHARED, schedule(LeitnerState.newWord(), Rating.REMEMBERED, 5 * DAY), 5 * DAY);
        reviews.recordReview(W1, schedule(LeitnerState.newWord(), Rating.FORGOT, 5 * DAY), 5 * DAY);
        List<PackState> states = packs.getPackStates().get();
        assertEquals(2, states.get(0).getLearnedCount());
        assertEquals("shared word counts in both packs", 1, states.get(1).getLearnedCount());
        assertEquals(0, states.get(2).getLearnedCount());
    }

    @Test public void newWordsFollowActivePackOrderWithoutDuplicates() {
        assertTrue(study.newWords(5).get().isEmpty());
        packs.activatePack("B", 1L);
        packs.activatePack("A", 2L);
        packs.activatePack("C", 3L);
        packs.pausePack("C");
        assertEquals(Arrays.asList(SHARED, W1, W2, W3, W4, W5), ids(study.newWords(50).get()));
        assertEquals(Arrays.asList(SHARED, W1), ids(study.newWords(2).get()));
        assertEquals(RepoError.INVALID_ARGUMENT, study.newWords(0).getError());
    }

    @Test public void reviewedWordLeavesNewListAndBecomesDueByDate() {
        packs.activatePack("A", 1L);
        long now = 10 * DAY + 5000;
        RepoResult<WordProgress> saved = reviews.recordReview(W1, schedule(LeitnerState.newWord(), Rating.REMEMBERED, now), now);
        assertTrue(saved.isSuccess());
        WordProgress p = saved.get();
        assertEquals(1, p.getBox());
        assertEquals(Long.valueOf(now), p.getLearnedAtEpochMillis());
        assertEquals(1, p.getReviewCount());
        assertFalse(ids(study.newWords(10).get()).contains(W1));

        assertTrue(study.dueItems(now, 10).get().isEmpty());
        long later = p.getDueAtEpochMillis();
        List<StudyItem> due = study.dueItems(later, 10).get();
        assertEquals(1, due.size());
        assertEquals(W1, due.get(0).getVocabulary().stableId);
        assertEquals(1, due.get(0).getProgress().getBox());
        assertEquals(RepoError.INVALID_ARGUMENT, study.dueItems(later, 0).getError());
    }

    @Test public void pausedPackProgressIsKeptButNotStudied() {
        packs.activatePack("A", 1L);
        long now = 10 * DAY;
        reviews.recordReview(W1, schedule(LeitnerState.newWord(), Rating.FORGOT, now), now);
        long due = now + DAY;
        assertEquals(1, study.dueItems(due, 10).get().size());
        packs.pausePack("A");
        assertTrue(study.dueItems(due, 10).get().isEmpty());
        assertTrue(study.getProgress(W1).get().isPresent());
        packs.activatePack("A", 99L);
        assertEquals(1, study.dueItems(due, 10).get().size());
    }

    @Test public void reviewKeepsLearnedAtAndSavedFlagAndCounts() {
        long t1 = 10 * DAY, t2 = 12 * DAY;
        WordProgress first = reviews.recordReview(W2, schedule(LeitnerState.newWord(), Rating.REMEMBERED, t1), t1).get();
        assertEquals(1, user.userDao().setSaved(W2, true));
        WordProgress second = reviews.recordReview(W2, schedule(first.toState(), Rating.FORGOT, t2), t2).get();
        assertEquals(Long.valueOf(t1), second.getLearnedAtEpochMillis());
        assertEquals(Long.valueOf(t2), second.getLastReviewedAtEpochMillis());
        assertEquals(2, second.getReviewCount());
        assertEquals(0, second.getBox());
        assertEquals(1, second.getConsecutiveMistakes());
        assertTrue(second.isSaved());
        assertEquals(2, user.userDao().reviews().size());
    }

    /** Reviews W1 with each rating in turn, feeding the stored progress back into the scheduler. */
    private WordProgress review(WordProgress previous, Rating rating, long now) {
        LeitnerState state = previous == null ? LeitnerState.newWord() : previous.toState();
        return reviews.recordReview(W1, schedule(state, rating, now), now).get();
    }

    private WordProgress stored() { return study.getProgress(W1).get().get(); }

    @Test public void leechFlagPersistsOnlyAtFourthConsecutiveForgot() {
        WordProgress p = null;
        for (int i = 1; i <= 3; i++) {
            p = review(p, Rating.FORGOT, i * DAY);
            assertEquals(i, p.getConsecutiveMistakes());
            assertFalse(p.isLeech());
        }
        p = review(p, Rating.FORGOT, 4 * DAY);
        assertEquals(4, p.getConsecutiveMistakes());
        assertTrue(p.isLeech());

        WordProgress stored = stored();
        assertEquals(4, stored.getConsecutiveMistakes());
        assertTrue(stored.isLeech());
        assertTrue(user.userDao().progress(W1).isLeech);
    }

    @Test public void forgotKeepsLeechAndHardOrRememberedClearsItInStorage() {
        for (Rating clearing : new Rating[]{Rating.HARD, Rating.REMEMBERED}) {
            user.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM UserWordProgress");
            WordProgress p = null;
            for (int i = 1; i <= 4; i++) p = review(p, Rating.FORGOT, i * DAY);
            assertTrue(p.isLeech());

            p = review(p, Rating.FORGOT, 5 * DAY);
            assertTrue("FORGOT must not clear leech", stored().isLeech());
            assertEquals(5, stored().getConsecutiveMistakes());

            p = review(p, clearing, 6 * DAY);
            WordProgress stored = stored();
            assertFalse(clearing + " must clear leech", stored.isLeech());
            assertEquals(0, stored.getConsecutiveMistakes());
        }
    }

    @Test public void interruptedForgotStreakRestartsTowardLeech() {
        WordProgress p = null;
        for (int i = 1; i <= 3; i++) p = review(p, Rating.FORGOT, i * DAY);
        p = review(p, Rating.HARD, 4 * DAY);
        for (int i = 5; i <= 7; i++) p = review(p, Rating.FORGOT, i * DAY);
        assertEquals(3, stored().getConsecutiveMistakes());
        assertFalse(stored().isLeech());
    }

    @Test public void reviewRejectsUnknownWordStaleBoxAndBadArguments() {
        ScheduleResult fresh = schedule(LeitnerState.newWord(), Rating.REMEMBERED, 10 * DAY);
        assertEquals(RepoError.NOT_FOUND, reviews.recordReview("ffffffffffffffff", fresh, 10 * DAY).getError());
        assertEquals(RepoError.INVALID_ARGUMENT, reviews.recordReview(W1, null, 10 * DAY).getError());
        assertEquals(RepoError.INVALID_ARGUMENT, reviews.recordReview(W1, fresh, -1).getError());

        reviews.recordReview(W1, fresh, 10 * DAY);
        // Same result replayed: boxBefore=0 but stored box is now 1.
        assertEquals(RepoError.INVALID_ARGUMENT, reviews.recordReview(W1, fresh, 11 * DAY).getError());
        assertEquals(1, user.userDao().reviews().size());
        assertEquals(1, user.userDao().progress(W1).reviewCount);
    }

    @Test public void failedReviewWriteRollsBackEventAndProgress() {
        user.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER block_progress BEFORE INSERT ON UserWordProgress BEGIN SELECT RAISE(ABORT,'blocked'); END");
        RepoResult<WordProgress> r = reviews.recordReview(W1, schedule(LeitnerState.newWord(), Rating.HARD, 10 * DAY), 10 * DAY);
        assertEquals(RepoError.WRITE_FAILED, r.getError());
        assertTrue(user.userDao().reviews().isEmpty());
        assertNull(user.userDao().progress(W1));
    }

    @Test public void corruptStoredProgressIsInvalidDataNotEmpty() {
        user.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO UserWordProgress (vocabularyId,box,dueAt,learnedAt,lastReviewedAt,consecutiveMistakes,isLeech,reviewCount,isSaved) "
                        + "VALUES (?,9,0,NULL,NULL,0,0,0,0)", new Object[]{W3});
        assertEquals(RepoError.INVALID_DATA, study.getProgress(W3).getError());
    }

    @Test public void quizVocabularyIsActivePacksOnlyAndAttemptsPersist() {
        assertTrue(quizzes.activeVocabulary().get().isEmpty());
        packs.activatePack("A", 1L);
        packs.activatePack("C", 2L);
        packs.pausePack("C");
        List<QuizVocabulary> words = quizzes.activeVocabulary().get();
        assertEquals(Arrays.asList(SHARED, W1, W2, W3),
                words.stream().map(QuizVocabulary::getStableId).collect(Collectors.toList()));
        assertEquals(QuizPool.Availability.READY,
                new QuizPool(words, text -> text).getAvailability());

        QuizResult result = completedResult(5, 4);
        long id = quizzes.saveAttempt(result, 20 * DAY).get();
        assertTrue(id > 0);
        assertEquals(80, user.userDao().quizzes().get(0).scorePercent);
        assertEquals(RepoError.INVALID_ARGUMENT, quizzes.saveAttempt(null, 1L).getError());
    }

    @Test public void quizVocabularyWithNonHexStableIdIsInvalidData() {
        catalog.getOpenHelper().getWritableDatabase().execSQL("INSERT INTO VocabularyEntry "
                + "(id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                + "VALUES ('not-hex','x','x','noun','m','e','v',NULL,'src','1')");
        member("B", "not-hex", 9);
        packs.activatePack("B", 1L);
        assertEquals(RepoError.INVALID_DATA, quizzes.activeVocabulary().getError());
    }

    private static QuizResult completedResult(int n, int correct) {
        List<QuizQuestion> qs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            QuizVocabulary v = new QuizVocabulary(String.format("%016x", i + 1), "w" + i, "noun", "m0", "");
            qs.add(new QuizQuestion(v, new ArrayList<>(Arrays.asList(new QuizOption("a", "m0", "m0"),
                    new QuizOption("b", "m1", "m1"), new QuizOption("c", "m2", "m2"), new QuizOption("d", "m3", "m3"))), "a", meaning -> meaning));
        }
        QuizSession s = new QuizSession(qs);
        for (int i = 0; i < n; i++) {
            s = s.answer(s.getCurrentQuestion().getId(), i < correct ? "a" : "b").getSession();
            if (i < n - 1) s = s.next().getSession();
        }
        return s.getResult().get();
    }
}
