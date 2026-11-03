package com.vocamind.app.data.user;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.core.ProgressProvider;
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
}
