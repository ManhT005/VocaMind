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
        quizzes = new RoomQuizRepository(catalog.catalogDao(), user.userDao(), QuizDistractorPolicy::unrestricted);
    }

    @After public void close() {
        catalog.close(); user.close();
        context.deleteDatabase(catalogName); context.deleteDatabase(userName);
    }

    @Test public void acceptedPolicyTravelsThroughRoomSnapshotAndGenerationWithoutWrites() throws Exception {
        org.json.JSONObject data = new org.json.JSONObject(
                com.vocamind.app.domain.quiz.AcceptedQuizPolicyTest.read("docs/data/voc-data-04/accepted-v1/canonical-preview.json"));
        String companion = com.vocamind.app.domain.quiz.AcceptedQuizPolicyTest.read("app/src/test/resources/quiz/accepted-policy.json");
        SupportSQLiteDatabase sql = catalog.getOpenHelper().getWritableDatabase();
        sql.execSQL("DELETE FROM PackWord");
        sql.execSQL("DELETE FROM VocabularyEntry");
        sql.execSQL("DELETE FROM LearningPack");
        org.json.JSONArray entries = data.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            org.json.JSONObject v = entries.getJSONObject(i);
            sql.execSQL("INSERT INTO VocabularyEntry (id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                    + "VALUES (?,?,?,?,?,?,?,NULL,'src','1')", new Object[]{v.getString("id"), v.getString("lemma"),
                    v.getString("normalizedLemma"), v.getString("pos"), v.getString("meaningVi"),
                    v.getString("exampleEn"), v.getString("exampleVi")});
        }
        org.json.JSONArray acceptedPacks = data.getJSONArray("packs");
        for (int i = 0; i < acceptedPacks.length(); i++) {
            org.json.JSONObject p = acceptedPacks.getJSONObject(i);
            sql.execSQL("INSERT INTO LearningPack (id,title,description,category,orderNo,version) VALUES (?,?,?,?,?,?)",
                    new Object[]{p.getString("id"), p.getString("title"), p.getString("description"),
                            p.getString("category"), p.getInt("orderNo"), p.getString("version")});
            assertTrue(packs.activatePack(p.getString("id"), i + 1).isSuccess());
        }
        org.json.JSONArray memberships = data.getJSONArray("packWords");
        for (int i = 0; i < memberships.length(); i++) {
            org.json.JSONObject m = memberships.getJSONObject(i);
            member(m.getString("packId"), m.getString("vocabularyId"), m.getInt("orderNo"));
        }
        quizzes = new RoomQuizRepository(catalog.catalogDao(), user.userDao(), () -> {
            List<QuizVocabulary> full = new ArrayList<>();
            for (var row : catalog.catalogDao().allVocabulary())
                full.add(UserDataMapper.quizVocabulary(com.vocamind.app.data.catalog.CatalogMapper.vocabulary(row)));
            return com.vocamind.app.data.catalog.QuizPolicyAsset.parse(companion, full);
        });
        assertEquals(150, quizzes.activeSnapshot().get().getVocabulary().size());
        org.json.JSONArray pairs = new org.json.JSONObject(companion).getJSONArray("forbiddenDistractorPairs");
        String beforeCatalog = snapshot(sql), beforeUser = snapshot(user.getOpenHelper().getWritableDatabase());
        GenerateQuiz generate = new GenerateQuiz(quizzes);
        for (int seed : new int[]{0, 60, 262, 357, 473, 786, 900}) {
            QuizGenerationOutcome outcome = generate.execute(new Random(seed)).get();
            assertEquals(QuizGenerationOutcome.Status.READY, outcome.getStatus());
            assertEquals(10, outcome.getQuestions().size());
            for (QuizQuestion q : outcome.getQuestions()) for (QuizOption option : q.getOptions())
                for (int i = 0; i < pairs.length(); i++) {
                    String a = pairs.getJSONArray(i).getString(0), b = pairs.getJSONArray(i).getString(1);
                    assertFalse((q.getId().equals(a) && option.getId().equals(b)) ||
                            (q.getId().equals(b) && option.getId().equals(a)));
                }
        }
        assertEquals(beforeCatalog, snapshot(sql));
        assertEquals(beforeUser, snapshot(user.getOpenHelper().getWritableDatabase()));
        // Installed catalog changed without updating its required policy: fail closed.
        sql.execSQL("UPDATE VocabularyEntry SET meaningVi='changed' WHERE id=?", new Object[]{entries.getJSONObject(0).getString("id")});
        assertEquals(RepoError.INVALID_DATA, generate.execute(new Random(60)).getError());
    }

    @Test public void absentOrMalformedPolicyIsRepositoryErrorEvenWhenNoPacksAreActive() {
        for (java.util.function.Supplier<QuizDistractorPolicy> loader : Arrays.<java.util.function.Supplier<QuizDistractorPolicy>>asList(
                () -> { throw new IllegalArgumentException("missing policy"); }, () -> null)) {
            RoomQuizRepository repository = new RoomQuizRepository(catalog.catalogDao(), user.userDao(), loader);
            assertEquals(RepoError.INVALID_DATA, new GenerateQuiz(repository).execute(new Random(1)).getError());
        }
    }

    private void member(String pack, String word, int order) {
        catalog.getOpenHelper().getWritableDatabase().execSQL("INSERT INTO PackWord VALUES (?,?,?)", new Object[]{pack, word, order});
    }

    @Test public void pack02ValidationNoOpAndFailedWriteNeverCreateOrphan() {
        for (String id : new String[]{null, "", "  "}) {
            assertEquals(RepoError.INVALID_ARGUMENT, packs.pausePack(id).getError());
            assertEquals(RepoError.INVALID_ARGUMENT, packs.activatePack(id, 0).getError());
        }
        assertEquals(RepoError.NOT_FOUND, packs.pausePack("unknown").getError());
        assertEquals(RepoError.NOT_FOUND, packs.activatePack("unknown", 0).getError());
        assertFalse(packs.pausePack("A").get());
        assertTrue(user.userDao().packs().isEmpty());
        user.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER block_pack BEFORE INSERT ON UserPack BEGIN SELECT RAISE(ABORT,'blocked'); END");
        assertEquals(RepoError.WRITE_FAILED, packs.activatePack("A", 0).getError());
        assertTrue(user.userDao().packs().isEmpty());
        user.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER block_pack");
        assertTrue(packs.activatePack("A", 0).get().isActive());
    }

    @Test public void pack02PauseSharedAndExclusiveDueAndNewWordsThenReopenPreservesUserdata() {
        assertTrue(study.newWords(50).get().isEmpty());
        assertTrue(user.userDao().packs().isEmpty());
        packs.activatePack("A", 0); packs.activatePack("A", 100); packs.activatePack("B", 1);
        packs.pausePack("A");
        assertEquals(List.of(SHARED, W4, W5), ids(study.newWords(50).get()));
        packs.activatePack("A", 200);
        long now = 10 * DAY;
        WordProgress original = reviews.recordReview(W1,
                schedule(LeitnerState.newWord(), Rating.FORGOT, now), now).get();
        reviews.recordReview(SHARED, schedule(LeitnerState.newWord(), Rating.FORGOT, now), now);
        user.userDao().setSaved(W1, true);
        assertTrue(packs.pausePack("A").get());
        assertFalse(packs.pausePack("A").get());
        assertEquals(Arrays.asList(W4, W5), ids(study.newWords(50).get()));
        assertEquals(List.of(SHARED), study.dueItems(now + DAY, 50).get().stream()
                .map(i -> i.getVocabulary().stableId).collect(Collectors.toList()));
        // Close real file-backed Room and reopen; never delete/reset app userdata.
        user.close(); user = UserDatabase.open(context, userName);
        packs = new RoomPackRepository(catalog.catalogDao(), user.userDao());
        study = new RoomStudyRepository(catalog.catalogDao(), user.userDao());
        assertFalse(packs.getPackStates().get().get(0).isActive());
        assertEquals(0L, user.userDao().pack("A").activatedAt);
        assertEquals(original.getDueAtEpochMillis(), user.userDao().progress(W1).dueAt);
        assertEquals(original.getBox(), user.userDao().progress(W1).box);
        assertEquals(original.getLearnedAtEpochMillis(), user.userDao().progress(W1).learnedAt);
        assertEquals(original.getLastReviewedAtEpochMillis(), user.userDao().progress(W1).lastReviewedAt);
        assertTrue(user.userDao().progress(W1).isSaved);
        assertEquals(2, user.userDao().reviews().size());
        packs.activatePack("A", 999);
        assertEquals(Long.valueOf(0), packs.getPackStates().get().get(0).getActivatedAtEpochMillis());
        List<String> due = study.dueItems(now + DAY, 50).get().stream()
                .map(i -> i.getVocabulary().stableId).collect(Collectors.toList());
        assertEquals(2, due.size()); assertEquals(2, new HashSet<>(due).size());
        assertTrue(due.containsAll(List.of(W1, SHARED)));
        packs.pausePack("A"); packs.pausePack("B");
        user.userDao().activatePack("orphan", 999); // legacy logical row cannot add catalog words
        assertTrue(packs.getPackStates().get().stream().noneMatch(PackState::isActive));
        assertTrue(study.newWords(50).get().isEmpty());
        assertTrue(study.dueItems(now + DAY, 50).get().isEmpty());
        assertEquals(2, user.userDao().reviews().size());
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

    @Test public void vocabularyBrowsingIsReadOnlyAcrossInactivePausedAndFileReopen() {
        packs.activatePack("B", 1L); packs.pausePack("B");
        reviews.recordReview(SHARED, schedule(LeitnerState.newWord(), Rating.HARD, DAY), DAY);
        user.userDao().setSaved(SHARED, true);
        String beforeCatalog = snapshot(catalog.getOpenHelper().getWritableDatabase());
        String beforeUser = snapshot(user.getOpenHelper().getWritableDatabase());
        List<StudyItem> a = packs.getPackWords("A").get(), b = packs.getPackWords("B").get();
        assertEquals(Arrays.asList(SHARED, W1, W2, W3),
                a.stream().map(i -> i.getVocabulary().stableId).collect(Collectors.toList()));
        assertEquals(Arrays.asList(SHARED, W4, W5),
                b.stream().map(i -> i.getVocabulary().stableId).collect(Collectors.toList()));
        assertEquals(a.get(0).getProgress().getBox(), b.get(0).getProgress().getBox());
        assertTrue(a.get(0).getProgress().isSaved());
        assertNull(user.userDao().pack("A"));
        assertFalse(user.userDao().pack("B").isActive);
        assertEquals(beforeCatalog, snapshot(catalog.getOpenHelper().getWritableDatabase()));
        assertEquals(beforeUser, snapshot(user.getOpenHelper().getWritableDatabase()));
        catalog.close(); user.close();
        catalog = CatalogDatabase.open(context, catalogName);
        user = UserDatabase.open(context, userName);
        packs = new RoomPackRepository(catalog.catalogDao(), user.userDao());
        assertEquals(beforeCatalog, snapshot(catalog.getOpenHelper().getWritableDatabase()));
        assertEquals(beforeUser, snapshot(user.getOpenHelper().getWritableDatabase()));
        assertEquals(Long.valueOf(DAY), packs.getPackWords("A").get().get(0).getProgress().getLearnedAtEpochMillis());
        user.getOpenHelper().getWritableDatabase().execSQL("UPDATE UserWordProgress SET box=5 WHERE vocabularyId=?", new Object[]{SHARED});
        assertEquals(5, packs.getPackWords("B").get().get(0).getProgress().getBox());
        assertEquals(RepoError.INVALID_ARGUMENT, packs.getPackWords(null).getError());
        catalog.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM PackWord WHERE packId='C'");
        assertTrue(packs.getPackWords("C").get().isEmpty());
        user.close();
        assertEquals(RepoError.READ_FAILED, packs.getPackWords("A").getError());
    }

    private String snapshot(SupportSQLiteDatabase db) {
        StringBuilder result = new StringBuilder();
        List<String> tables = new ArrayList<>();
        try (android.database.Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (c.moveToNext()) tables.add(c.getString(0));
        }
        for (String table : tables) {
            List<String> rows = new ArrayList<>();
            try (android.database.Cursor c = db.query("SELECT * FROM [" + table + "]")) {
                while (c.moveToNext()) {
                    StringBuilder row = new StringBuilder();
                    for (int n=0; n<c.getColumnCount(); n++) row.append(c.isNull(n) ? "<NULL>" : c.getType(n) == android.database.Cursor.FIELD_TYPE_BLOB ? Arrays.toString(c.getBlob(n)) : c.getString(n)).append('|');
                    rows.add(row.toString());
                }
            }
            Collections.sort(rows); result.append(table).append(rows);
        }
        return result.toString();
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

    @Test public void generatedQuizUsesFreshActiveSnapshotAndNeverWrites() {
        GenerateQuiz generate = new GenerateQuiz(quizzes);
        assertEquals(QuizGenerationOutcome.Status.EMPTY, generate.execute(new Random(42)).get().getStatus());
        packs.activatePack("A", 1L);
        packs.activatePack("B", 2L);
        packs.activatePack("C", 3L);
        packs.pausePack("C");
        user.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO UserPack (packId,isActive,activatedAt) VALUES ('orphan',1,1)");
        String beforeCatalog = snapshot(catalog.getOpenHelper().getWritableDatabase());
        String beforeUser = snapshot(user.getOpenHelper().getWritableDatabase());
        QuizGenerationOutcome out = generate.execute(new Random(42)).get();
        assertEquals(QuizGenerationOutcome.Status.READY, out.getStatus());
        assertEquals(6, out.getQuestions().size());
        Set<String> expected = new HashSet<>(Arrays.asList(SHARED, W1, W2, W3, W4, W5));
        Set<String> actual = new HashSet<>();
        for (QuizQuestion q : out.getQuestions()) {
            assertTrue(actual.add(q.getId()));
            for (QuizOption option : q.getOptions()) assertTrue(expected.contains(option.getId()));
        }
        assertEquals(expected, actual);
        assertEquals(beforeCatalog, snapshot(catalog.getOpenHelper().getWritableDatabase()));
        assertEquals(beforeUser, snapshot(user.getOpenHelper().getWritableDatabase()));
        packs.pausePack("A");
        assertEquals("INSUFFICIENT_VOCABULARY", generate.execute(new Random(42)).get().getReason());
        packs.pausePack("B");
        assertEquals(QuizGenerationOutcome.Status.EMPTY, generate.execute(new Random(42)).get().getStatus());
        catalog.close();
        assertEquals(RepoError.READ_FAILED, generate.execute(new Random(42)).getError());
    }

    @Test public void productionNormalizerVectorsOnAndroid() {
        new QuizGenerationTestVectors().verify();
    }

    private static final class QuizGenerationTestVectors {
        void verify() {
            MeaningNormalizer n = new UnicodeMeaningNormalizer();
            assertEquals("b\u00e0n", n.normalize("\u00a0BA\u0300N\u0085"));
            assertEquals("strasse", n.normalize("Stra\u00dfe"));
            assertEquals("\u03c3\u03c3\u03c3", n.normalize("\u03a3\u03c3\u03c2"));
            assertEquals("i\u0307", n.normalize("\u0130"));
            assertEquals("\ud801\udc28", n.normalize("\ud801\udc00"));
            assertNotEquals(n.normalize("ban"), n.normalize("b\u00e0n"));
        }
    }

    @Test public void quizVocabularyWithNonHexStableIdIsInvalidData() {
        catalog.getOpenHelper().getWritableDatabase().execSQL("INSERT INTO VocabularyEntry "
                + "(id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                + "VALUES ('not-hex','x','x','noun','m','e','v',NULL,'src','1')");
        member("B", "not-hex", 9);
        packs.activatePack("B", 1L);
        assertEquals(RepoError.INVALID_DATA, quizzes.activeVocabulary().getError());
    }

    private RoomVocabularyRepository detailRepository() {
        return new RoomVocabularyRepository(new com.vocamind.app.data.catalog.RoomCatalogRepository(catalog.catalogDao()), user);
    }

    @Test public void vocab02SaveUnsaveReopenOnlyChangesFlagAndCatalogIsReadOnly() {
        var repository = detailRepository();
        var get = new com.vocamind.app.domain.vocabulary.GetVocabularyDetail(repository);
        var save = new com.vocamind.app.domain.vocabulary.ToggleSaved(repository);
        reviews.recordReview(SHARED, schedule(LeitnerState.newWord(), Rating.FORGOT, DAY), DAY);
        String catalogBefore = snapshot(catalog.getOpenHelper().getWritableDatabase());
        String userBefore = snapshot(user.getOpenHelper().getWritableDatabase());
        var before = user.userDao().progress(SHARED);
        assertEquals("w" + SHARED, get.execute("A", SHARED).get().word.lemma);
        assertNull(get.execute("A", SHARED).get().word.ipa);
        assertTrue(save.execute("A", SHARED, true).get());
        assertTrue(save.execute("A", SHARED, true).get());
        assertTrue(get.execute("B", SHARED).get().saved);
        var after = user.userDao().progress(SHARED);
        assertEquals(before.box, after.box); assertEquals(before.dueAt, after.dueAt);
        assertEquals(before.learnedAt, after.learnedAt); assertEquals(before.lastReviewedAt, after.lastReviewedAt);
        assertEquals(before.consecutiveMistakes, after.consecutiveMistakes);
        assertEquals(before.isLeech, after.isLeech); assertEquals(before.reviewCount, after.reviewCount);
        assertTrue(user.userDao().packs().isEmpty());
        user.close(); user = UserDatabase.open(context, userName);
        repository = detailRepository();
        assertTrue(repository.getDetail("B", SHARED).get().saved);
        assertFalse(repository.setSaved("B", SHARED, false).get());
        assertEquals(userBefore, snapshot(user.getOpenHelper().getWritableDatabase()));
        user.close(); user = UserDatabase.open(context, userName);
        assertFalse(detailRepository().getDetail("A", SHARED).get().saved);
        assertEquals(catalogBefore, snapshot(catalog.getOpenHelper().getWritableDatabase()));
    }

    @Test public void vocab02MissingProgressIsBlockedNotFalseSuccessAndPoolsAreUntouched() {
        var repository = detailRepository();
        packs.activatePack("A", DAY);
        String before = snapshot(user.getOpenHelper().getWritableDatabase());
        var newBefore = ids(study.newWords(50).get());
        for (boolean target : new boolean[]{true, false, true}) {
            assertEquals(RepoError.SAVED_POLICY_REQUIRED, repository.setSaved("A", W1, target).getError());
        }
        assertFalse(repository.getDetail("A", W1).get().saved);
        assertNull(user.userDao().progress(W1));
        assertEquals(newBefore, ids(study.newWords(50).get()));
        assertTrue(study.dueItems(Long.MAX_VALUE, 50).get().isEmpty());
        assertEquals(before, snapshot(user.getOpenHelper().getWritableDatabase()));
        for (String id : new String[]{null, "", "  "}) {
            assertEquals(RepoError.INVALID_ARGUMENT, repository.getDetail("A", id).getError());
            assertEquals(RepoError.INVALID_ARGUMENT, repository.setSaved("A", id, true).getError());
        }
        assertEquals(RepoError.NOT_FOUND, repository.setSaved("A", "unknown", true).getError());
        assertEquals(RepoError.NOT_FOUND, repository.getDetail("B", W1).getError());
        assertEquals(RepoError.NOT_FOUND, repository.getDetail("A", " " + W1).getError());
        assertEquals(RepoError.INVALID_ARGUMENT, repository.getDetail(null, W1).getError());
        assertEquals(before, snapshot(user.getOpenHelper().getWritableDatabase()));
    }

    @Test public void vocab02WriteFailureReadFailureAndIdempotentReconciliation() {
        reviews.recordReview(W1, schedule(LeitnerState.newWord(), Rating.HARD, DAY), DAY);
        var repository = detailRepository();
        user.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER block_saved BEFORE UPDATE OF isSaved ON UserWordProgress BEGIN SELECT RAISE(ABORT,'blocked'); END");
        assertEquals(RepoError.WRITE_FAILED, repository.setSaved("A", W1, true).getError());
        assertFalse(repository.getDetail("A", W1).get().saved);
        user.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER block_saved");
        assertTrue(repository.setSaved("A", W1, true).get());
        assertTrue(repository.setSaved("A", W1, true).get());
        user.close();
        assertEquals(RepoError.READ_FAILED, repository.getDetail("A", W1).getError());
        user = UserDatabase.open(context, userName);
        assertTrue(detailRepository().getDetail("A", W1).get().saved);
        catalog.close();
        assertEquals(RepoError.READ_FAILED, detailRepository().setSaved("A", W1, false).getError());
    }

    @Test public void vocab02SaveConcurrentWithReviewRetainsBothAndHistory() throws Exception {
        reviews.recordReview(W1, schedule(LeitnerState.newWord(), Rating.HARD, DAY), DAY);
        var repository = detailRepository();
        for (int i = 0; i < 8; i++) {
            final long now = (i + 2) * DAY;
            var previous = user.userDao().progress(W1);
            var scheduled = schedule(new LeitnerState(previous.box, previous.consecutiveMistakes, previous.isLeech), Rating.FORGOT, now);
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread saveThread = new Thread(() -> {
                try { start.await(); assertTrue(repository.setSaved("A", W1, true).get()); }
                catch (Throwable e) { failure.set(e); }
            });
            Thread reviewThread = new Thread(() -> {
                try { start.await(); assertTrue(reviews.recordReview(W1, scheduled, now).isSuccess()); }
                catch (Throwable e) { failure.set(e); }
            });
            saveThread.start(); reviewThread.start(); start.countDown();
            saveThread.join(10000); reviewThread.join(10000);
            assertFalse(saveThread.isAlive()); assertFalse(reviewThread.isAlive());
            if (failure.get() != null) throw new AssertionError(failure.get());
            var stored = user.userDao().progress(W1);
            assertTrue(stored.isSaved); assertEquals(i + 2, stored.reviewCount);
            assertEquals(now, stored.lastReviewedAt.longValue());
            assertEquals(scheduled.getDueAtEpochMillis(), stored.dueAt);
            assertEquals(scheduled.getConsecutiveMistakes(), stored.consecutiveMistakes);
            assertEquals(scheduled.isLeech(), stored.isLeech);
            assertEquals(i + 2, user.userDao().reviews().size());
            assertFalse(repository.setSaved("A", W1, false).get());
        }
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
