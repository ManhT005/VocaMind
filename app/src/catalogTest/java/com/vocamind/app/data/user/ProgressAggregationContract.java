package com.vocamind.app.data.user;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.core.ProgressProvider;
import com.vocamind.app.core.ReviewProvider;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.repository.RoomReviewRepository;
import com.vocamind.app.domain.learning.*;
import java.time.*;
import com.vocamind.app.data.user.entity.*;
import com.vocamind.app.domain.progress.*;
import org.junit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** File-backed Room integration shared by native host SQLite and device instrumentation. */
public abstract class ProgressAggregationContract {
    private Context context;
    private UserDatabase db;
    private ExecutorService worker;
    private String name;
    @Before public void open() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        name = "data07-" + UUID.randomUUID() + ".db";
        worker = Executors.newSingleThreadExecutor();
        run(() -> { db = UserDatabase.open(context, name); return null; });
    }
    @After public void close() throws Exception {
        try { if (db != null) run(() -> { db.close(); return null; }); }
        finally { if (worker != null) worker.shutdownNow(); context.deleteDatabase(name); }
    }
    private <T> T run(Callable<T> operation) throws Exception {
        try { return worker.submit(operation).get(60, TimeUnit.SECONDS); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw new AssertionError(e.getCause());
        }
    }
    private UserWordProgress progress(String id, int box) {
        UserWordProgress p = new UserWordProgress(); p.vocabularyId = id;
        p.box = box; p.dueAt = 100; return p;
    }
    private long quiz(long time, int correct) {
        QuizAttempt q = new QuizAttempt(); q.completedAt = time;
        q.questionCount = 4; q.correctCount = correct; q.scorePercent = correct * 25;
        return db.userDao().insertQuiz(q);
    }
    private ProgressReadResult read() { return ProgressProvider.create(db).readProgress(); }
    private ProgressSnapshot success() {
        ProgressReadResult result = read(); assertTrue("Read must succeed", result.isSuccess());
        return result.getSnapshot();
    }
    @Test public void emptyDatabaseReturnsSixZerosAndEmptyHistory() throws Exception {
        run(() -> {
            ProgressSnapshot snapshot = success(); assertTrue(snapshot.isEmpty());
            assertEquals(0, snapshot.getSummary().getTotalLearned());
            assertArrayEquals(new long[6], snapshot.getSummary().getBoxCounts());
            assertTrue(snapshot.getQuizHistory().isEmpty()); return null;
        });
    }
    @Test public void fixtureAcrossAllSixBoxesMatchesSqlAndDomainGroups() throws Exception {
        run(() -> {
            long[] expected = {2, 3, 4, 5, 6, 7}; int id = 0;
            for (int box = 0; box < expected.length; box++)
                for (int i = 0; i < expected[box]; i++)
                    db.userDao().saveProgress(progress(String.format(Locale.ROOT, "%016x", id++), box));
            ProgressSummary summary = success().getSummary();
            assertEquals(27, db.userDao().distinctProgressCount());
            assertEquals(6, db.userDao().progressCountsByBox().size());
            assertEquals(27, summary.getTotalLearned()); assertArrayEquals(expected, summary.getBoxCounts());
            assertEquals(5, summary.getLearningCount()); assertEquals(9, summary.getReviewingCount());
            assertEquals(13, summary.getMasteredCount()); return null;
        });
    }
    @Test public void missingBoxesAreZeroAndNoActiveSavedOrLearnedAtFilterIsApplied() throws Exception {
        run(() -> {
            UserWordProgress p = progress("0123456789abcdef", 5);
            p.isSaved = true; p.learnedAt = null; db.userDao().saveProgress(p);
            // No UserPack exists and no catalog DB is opened: count all userdata as contracted.
            assertTrue(db.userDao().packs().isEmpty());
            assertArrayEquals(new long[]{0, 0, 0, 0, 0, 1}, success().getSummary().getBoxCounts());
            assertEquals(1, success().getSummary().getTotalLearned()); return null;
        });
    }
    @Test public void sameVocabularyUpsertMovesBoxWithoutIncreasingDistinctTotal() throws Exception {
        run(() -> {
            UserWordProgress p = progress("0123456789abcdef", 0); db.userDao().saveProgress(p);
            p.box = 4; db.userDao().saveProgress(p);
            assertEquals(1, db.userDao().distinctProgressCount());
            assertArrayEquals(new long[]{0, 0, 0, 0, 1, 0}, success().getSummary().getBoxCounts()); return null;
        });
    }
    @Test public void allQuizHistoryIsOrderedByTimeThenIdWithoutImplicitLimit() throws Exception {
        run(() -> {
            List<Long> ties = new ArrayList<>();
            for (int i = 0; i < 20; i++) { long id = quiz(i < 3 ? 500 : i, i % 5); if (i < 3) ties.add(id); }
            List<QuizAttempt> sql = db.userDao().completedQuizHistory();
            List<QuizHistoryItem> history = success().getQuizHistory();
            assertEquals(20, sql.size()); assertEquals(20, history.size());
            assertEquals((long) ties.get(2), sql.get(0).id);
            assertEquals((long) ties.get(1), sql.get(1).id);
            assertEquals((long) ties.get(0), sql.get(2).id);
            for (int i = 0; i < sql.size(); i++) {
                assertEquals(sql.get(i).id, history.get(i).getAttemptId());
                assertEquals(sql.get(i).scorePercent, history.get(i).getScorePercent());
                if (i > 0) assertTrue(sql.get(i - 1).completedAt >= sql.get(i).completedAt);
            }
            return null;
        });
    }
    @Test public void quizOnlyAndProgressOnlyAreNotEntirelyEmpty() throws Exception {
        run(() -> {
            quiz(100, 2); ProgressSnapshot onlyHistory = success();
            assertEquals(0, onlyHistory.getSummary().getTotalLearned()); assertFalse(onlyHistory.isEmpty());
            db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM QuizAttempt");
            db.userDao().saveProgress(progress("0123456789abcdef", 0));
            ProgressSnapshot onlyProgress = success(); assertTrue(onlyProgress.getQuizHistory().isEmpty());
            assertFalse(onlyProgress.isEmpty()); return null;
        });
    }
    @Test public void invalidPersistedBoxReturnsInvalidDataWithoutRepair() throws Exception {
        run(() -> {
            db.userDao().saveProgress(progress("0123456789abcdef", 0));
            db.getOpenHelper().getWritableDatabase().execSQL("UPDATE UserWordProgress SET box=6");
            ProgressReadResult result = read(); assertFalse(result.isSuccess());
            assertEquals(ProgressReadResult.Error.INVALID_DATA, result.getError());
            assertEquals(6, db.userDao().progress("0123456789abcdef").box); return null;
        });
    }
    @Test public void invalidStoredScoreIsNotSilentlyRecomputed() throws Exception {
        run(() -> {
            quiz(100, 2); db.getOpenHelper().getWritableDatabase().execSQL("UPDATE QuizAttempt SET scorePercent=99");
            ProgressReadResult result = read(); assertFalse(result.isSuccess());
            assertEquals(ProgressReadResult.Error.INVALID_DATA, result.getError());
            assertEquals(99, db.userDao().completedQuizHistory().get(0).scorePercent); return null;
        });
    }
    @Test public void closedDatabaseIsReadFailureNotEmptySuccess() throws Exception {
        run(() -> {
            var repository = ProgressProvider.create(db); db.close();
            ProgressReadResult result = repository.readProgress(); assertFalse(result.isSuccess());
            assertEquals(ProgressReadResult.Error.READ_FAILED, result.getError()); return null;
        });
    }
    @Test public void reopeningRetainsCountsAndHistory() throws Exception {
        run(() -> {
            db.userDao().saveProgress(progress("0123456789abcdef", 2)); long quizId = quiz(100, 3);
            db.close(); db = UserDatabase.open(context, name);
            ProgressSnapshot snapshot = success(); assertArrayEquals(new long[]{0, 0, 1, 0, 0, 0}, snapshot.getSummary().getBoxCounts());
            assertEquals(quizId, snapshot.getQuizHistory().get(0).getAttemptId()); return null;
        });
    }
    @Test public void returnedHistoryAndBoxCountsAreImmutableSnapshots() throws Exception {
        run(() -> {
            quiz(100, 4); db.userDao().saveProgress(progress("0123456789abcdef", 0));
            ProgressSnapshot snapshot = success();
            assertThrows(UnsupportedOperationException.class, () -> snapshot.getQuizHistory().clear());
            long[] boxes = snapshot.getSummary().getBoxCounts(); boxes[0] = 999;
            assertEquals(1, snapshot.getSummary().getBoxCount(0)); return null;
        });
    }
    @Test public void concurrentWriterCannotMixCountsAndHistoryFromDifferentStates() throws Exception {
        run(() -> {
            CountDownLatch betweenQueries = new CountDownLatch(1);
            CountDownLatch writerStarted = new CountDownLatch(1);
            AtomicBoolean armed = new AtomicBoolean(false);
            UserDatabase reader = Room.databaseBuilder(context, UserDatabase.class, name)
                    .setQueryCallback((sql, args) -> {
                        if (sql.contains("GROUP BY box") && armed.compareAndSet(true, false)) {
                            betweenQueries.countDown();
                            try { if (!writerStarted.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Writer did not start"); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                        }
                    }, Runnable::run).build();
            ExecutorService concurrent = Executors.newFixedThreadPool(2);
            try {
                // Initialize both handles before seeding, avoiding DATA-02 callback/setup lock contention.
                reader.getOpenHelper().getWritableDatabase();
                db.userDao().saveProgress(progress("0123456789abcdef", 0)); armed.set(true);
                Future<ProgressReadResult> reading = concurrent.submit(() -> new RoomProgressRepository(reader.userDao()).readProgress());
                Future<?> writing = concurrent.submit(() -> {
                    try {
                        if (!betweenQueries.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Reader did not reach grouped query");
                        writerStarted.countDown();
                        db.runInTransaction(() -> {
                            db.userDao().saveProgress(progress("0123456789abcdef", 5)); quiz(200, 4);
                        });
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                });
                ProgressReadResult before = reading.get(30, TimeUnit.SECONDS); writing.get(30, TimeUnit.SECONDS);
                assertTrue(before.isSuccess());
                assertArrayEquals(new long[]{1, 0, 0, 0, 0, 0}, before.getSnapshot().getSummary().getBoxCounts());
                assertTrue(before.getSnapshot().getQuizHistory().isEmpty());
                ProgressSnapshot after = success(); assertEquals(1, after.getQuizHistory().size());
                assertArrayEquals(new long[]{0, 0, 0, 0, 0, 1}, after.getSummary().getBoxCounts());
            } finally { concurrent.shutdownNow(); reader.close(); }
            return null;
        });
    }
    private static final String BANK = "5aecfbf5b9a23aee";
    private static final String BOOK = "90562a98e8686267";
    private static final Clock REVIEW_CLOCK = Clock.fixed(Instant.parse("2026-11-06T07:30:00Z"), ZoneOffset.UTC);
    private void withCatalog(java.util.function.Consumer<CatalogDatabase> test) {
        String catalogName = name + "-catalog";
        CatalogDatabase catalog = CatalogDatabase.open(context, catalogName);
        try {
            catalog.getOpenHelper().getReadableDatabase();
            test.accept(catalog);
        } finally { catalog.close(); context.deleteDatabase(catalogName); }
    }
    private RecordReviewUseCase reviewing(CatalogDatabase catalog) {
        return ReviewProvider.create(new RoomReviewRepository(catalog.catalogDao(), db),
                REVIEW_CLOCK, ZoneId.of("Asia/Bangkok"));
    }
    private void assertHealthyDb() {
        try (var c = db.getOpenHelper().getReadableDatabase().query("PRAGMA integrity_check")) {
            assertTrue(c.moveToFirst()); assertEquals("ok", c.getString(0)); assertFalse(c.moveToNext());
        }
        try (var c = db.getOpenHelper().getReadableDatabase().query("PRAGMA foreign_key_check")) {
            assertFalse(c.moveToFirst());
        }
    }
    private void assertSameSnapshot(ProgressSnapshot before, ProgressSnapshot after) {
        assertEquals(before.getSummary().getTotalLearned(), after.getSummary().getTotalLearned());
        assertArrayEquals(before.getSummary().getBoxCounts(), after.getSummary().getBoxCounts());
        assertEquals(before.getQuizHistory().size(), after.getQuizHistory().size());
        for (int i = 0; i < before.getQuizHistory().size(); i++) {
            QuizHistoryItem a = before.getQuizHistory().get(i), b = after.getQuizHistory().get(i);
            assertEquals(a.getAttemptId(), b.getAttemptId());
            assertEquals(a.getCompletedAtEpochMillis(), b.getCompletedAtEpochMillis());
            assertEquals(a.getQuestionCount(), b.getQuestionCount());
            assertEquals(a.getCorrectCount(), b.getCorrectCount());
            assertEquals(a.getScorePercent(), b.getScorePercent());
        }
    }
    @Test public void atomicReviewRefreshCountsCanonicalIdOnceAcrossSeveralPacks() throws Exception {
        run(() -> {
            withCatalog(catalog -> {
                long membershipCount = 0;
                for (var pack : catalog.catalogDao().packs())
                    for (var word : catalog.catalogDao().words(pack.id))
                        if (BANK.equals(word.id)) membershipCount++;
                assertTrue(membershipCount > 1); assertTrue(db.userDao().activePacks().isEmpty());
                RecordReviewUseCase review = reviewing(catalog);
                review.execute(BANK, Rating.REMEMBERED);
                ProgressSnapshot before = success();
                assertEquals(1, before.getSummary().getTotalLearned());
                assertArrayEquals(new long[]{0, 1, 0, 0, 0, 0}, before.getSummary().getBoxCounts());
                review.execute(BANK, Rating.REMEMBERED);
                ProgressSnapshot after = success();
                assertEquals(1, after.getSummary().getTotalLearned());
                assertArrayEquals(new long[]{0, 0, 1, 0, 0, 0}, after.getSummary().getBoxCounts());
                assertEquals(2, db.userDao().reviews().size());
                assertEquals(2, db.userDao().progress(BANK).reviewCount);
                assertEquals(1, before.getSummary().getBoxCount(1));
                assertHealthyDb();
            }); return null;
        });
    }
    @Test public void failedEventInsertDoesNotChangeSummaryOrHistory() throws Exception {
        reviewRollback(true, "INSERT", "ReviewEvent");
    }
    @Test public void failedFirstProgressInsertRollsBackEventAndKeepsSummary() throws Exception {
        reviewRollback(false, "INSERT", "UserWordProgress");
    }
    @Test public void failedProgressUpdateDoesNotMoveBoxOrChangeHistory() throws Exception {
        reviewRollback(true, "UPDATE", "UserWordProgress");
    }
    private void reviewRollback(boolean existing, String operation, String table) throws Exception {
        run(() -> {
            withCatalog(catalog -> {
                RecordReviewUseCase review = reviewing(catalog);
                if (existing) review.execute(BANK, Rating.HARD);
                quiz(100, 2);
                ProgressSnapshot before = success();
                int eventsBefore = db.userDao().reviews().size();
                var sql = db.getOpenHelper().getWritableDatabase();
                sql.execSQL("CREATE TRIGGER data07_review_fault BEFORE " + operation + " ON " + table
                        + " BEGIN SELECT RAISE(ABORT,'data07-forced-rollback'); END");
                ReviewWriteException failure = assertThrows(ReviewWriteException.class,
                        () -> review.execute(BANK, Rating.REMEMBERED));
                assertEquals(ReviewWriteException.Reason.STORAGE, failure.getReason());
                assertSameSnapshot(before, success());
                assertEquals(eventsBefore, db.userDao().reviews().size());
                if (existing) assertEquals(1, db.userDao().progress(BANK).reviewCount);
                else assertNull(db.userDao().progress(BANK));
                sql.execSQL("DROP TRIGGER data07_review_fault");
                review.execute(BANK, Rating.REMEMBERED);
                assertEquals(1, success().getSummary().getTotalLearned());
                assertArrayEquals(new long[]{0, 1, 0, 0, 0, 0}, success().getSummary().getBoxCounts());
                assertEquals(eventsBefore + 1, db.userDao().reviews().size());
                assertEquals(1, success().getQuizHistory().size());
                assertHealthyDb();
            }); return null;
        });
    }
    @Test public void atomicReviewAndQuizWriterCannotTearAggregationSnapshotAcrossThreeInterleavings() throws Exception {
        run(() -> {
            withCatalog(catalog -> {
                RecordReviewUseCase review = reviewing(catalog);
                for (int round = 0; round < 3; round++) {
                    var sql = db.getOpenHelper().getWritableDatabase();
                    db.runInTransaction(() -> {
                        sql.execSQL("DELETE FROM ReviewEvent"); sql.execSQL("DELETE FROM UserWordProgress");
                        sql.execSQL("DELETE FROM QuizAttempt");
                    });
                    CountDownLatch betweenQueries = new CountDownLatch(1), writerStarted = new CountDownLatch(1);
                    AtomicBoolean armed = new AtomicBoolean(false);
                    UserDatabase reader = Room.databaseBuilder(context, UserDatabase.class, name)
                            .setQueryCallback((query, args) -> {
                                if (query.contains("GROUP BY box") && armed.compareAndSet(true, false)) {
                                    betweenQueries.countDown();
                                    try { if (!writerStarted.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Writer did not start"); }
                                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                                }
                            }, Runnable::run).build();
                    ExecutorService parallel = Executors.newFixedThreadPool(2);
                    try {
                        reader.getOpenHelper().getWritableDatabase();
                        review.execute(BANK, Rating.HARD); quiz(100, 2);
                        armed.set(true);
                        Future<ProgressReadResult> reading = parallel.submit(() -> new RoomProgressRepository(reader.userDao()).readProgress());
                        Future<?> writing = parallel.submit(() -> {
                            try {
                                if (!betweenQueries.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Reader did not reach groups");
                                writerStarted.countDown();
                                db.runInTransaction(() -> {
                                    review.execute(BANK, Rating.REMEMBERED);
                                    review.execute(BOOK, Rating.REMEMBERED); quiz(200, 4);
                                });
                            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                        });
                        ProgressReadResult result = reading.get(30, TimeUnit.SECONDS); writing.get(30, TimeUnit.SECONDS);
                        assertTrue(result.isSuccess());
                        ProgressSnapshot old = result.getSnapshot();
                        assertEquals(1, old.getSummary().getTotalLearned());
                        assertArrayEquals(new long[]{1, 0, 0, 0, 0, 0}, old.getSummary().getBoxCounts());
                        assertEquals(1, old.getQuizHistory().size());
                        assertEquals(100, old.getQuizHistory().get(0).getCompletedAtEpochMillis());
                        ProgressSnapshot refreshed = success();
                        assertEquals(2, refreshed.getSummary().getTotalLearned());
                        assertArrayEquals(new long[]{0, 2, 0, 0, 0, 0}, refreshed.getSummary().getBoxCounts());
                        assertEquals(2, refreshed.getQuizHistory().size());
                        assertEquals(200, refreshed.getQuizHistory().get(0).getCompletedAtEpochMillis());
                        assertEquals(3, db.userDao().reviews().size()); assertHealthyDb();
                    } catch (Exception e) { throw new RuntimeException(e); }
                    finally { parallel.shutdownNow(); reader.close(); }
                }
            }); return null;
        });
    }

}
