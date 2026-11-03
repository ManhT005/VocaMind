package com.vocamind.app.data.user;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.core.ReviewProvider;
import com.vocamind.app.data.user.entity.*;
import com.vocamind.app.domain.learning.*;
import org.junit.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** File-backed SQLite integration; fault injection uses DB triggers, not production hooks. */
public abstract class AtomicReviewContract {
    private static final String ID = "0123456789abcdef";
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"), ZONE);
    private Context context;
    private UserDatabase db;
    private ExecutorService worker;
    private String name;
    @Before public void open() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        name = "data06-" + UUID.randomUUID() + ".db";
        worker = Executors.newSingleThreadExecutor();
        run(() -> { db = UserDatabase.open(context, name); return null; });
    }
    @After public void close() throws Exception {
        try { if (db != null) run(() -> { db.close(); return null; }); }
        finally { if (worker != null) worker.shutdownNow(); context.deleteDatabase(name); }
    }
    private <T> T run(Callable<T> call) throws Exception {
        try { return worker.submit(call).get(60, TimeUnit.SECONDS); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw new AssertionError(e.getCause());
        }
    }
    private UserWordProgress seed(String id) {
        UserWordProgress p = new UserWordProgress(); p.vocabularyId = id;
        p.box = 3; p.dueAt = 1234; p.learnedAt = 111L; p.lastReviewedAt = 222L;
        p.consecutiveMistakes = 3; p.isLeech = false; p.reviewCount = 7; p.isSaved = true;
        db.userDao().saveProgress(p); return p;
    }
    private RecordReviewUseCase useCase() { return ReviewProvider.create(db, CLOCK, ZONE); }
    private void fault(String name, String operation, String table) {
        db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER " + name + " BEFORE "
                + operation + " ON " + table + " BEGIN SELECT RAISE(ABORT, 'data06-forced-failure'); END");
    }
    private String snapshot() {
        StringBuilder s = new StringBuilder();
        for (String table : Arrays.asList("ReviewEvent", "UserWordProgress")) {
            try (var c = db.getOpenHelper().getReadableDatabase().query("SELECT * FROM " + table + " ORDER BY 1")) {
                while (c.moveToNext()) for (int i = 0; i < c.getColumnCount(); i++)
                    s.append(table).append(':').append(c.getColumnName(i)).append('=')
                            .append(c.isNull(i) ? "<null>" : c.getString(i)).append(';');
            }
        }
        return s.toString();
    }
    @Test public void firstReviewWritesOneMatchingEventAndProgress() throws Exception {
        run(() -> {
            ScheduleResult result = useCase().execute(ID, Rating.REMEMBERED);
            UserWordProgress p = db.userDao().progress(ID); ReviewEvent e = db.userDao().reviews().get(0);
            assertEquals(1, db.userDao().reviews().size()); assertTrue(e.id > 0);
            assertEquals(ID, e.vocabularyId); assertEquals("REMEMBERED", e.rating);
            assertEquals(0, e.boxBefore); assertEquals(1, e.boxAfter); assertEquals(1, p.reviewCount);
            assertEquals(result.getDueAtEpochMillis(), p.dueAt); assertEquals(p.dueAt, e.nextDueAt);
            assertEquals(CLOCK.millis(), e.reviewedAt);
            assertEquals(Long.valueOf(CLOCK.millis()), p.learnedAt);
            assertEquals(p.learnedAt, p.lastReviewedAt); return null;
        });
    }
    @Test public void existingWordAllRatingsPersistSchedulerOutcome() throws Exception {
        run(() -> {
            for (Rating rating : Rating.values()) {
                String id = String.format(Locale.ROOT, "%016x", rating.ordinal() + 100);
                seed(id); ScheduleResult result = useCase().execute(id, rating);
                UserWordProgress p = db.userDao().progress(id);
                assertEquals(result.getBoxAfter(), p.box); assertEquals(result.getDueAtEpochMillis(), p.dueAt);
                assertEquals(result.getConsecutiveMistakes(), p.consecutiveMistakes);
                assertEquals(result.isLeech(), p.isLeech); assertTrue(p.isSaved);
                assertEquals(8, p.reviewCount); assertEquals(Long.valueOf(111), p.learnedAt);
                assertEquals(Long.valueOf(CLOCK.millis()), p.lastReviewedAt);
            }
            assertEquals(3, db.userDao().reviews().size()); return null;
        });
    }
    @Test public void eventInsertFailureLeavesExistingProgressUnchanged() throws Exception {
        run(() -> {
            seed(ID); String before = snapshot(); fault("fail_event", "INSERT", "ReviewEvent");
            ReviewWriteException e = assertThrows(ReviewWriteException.class, () -> useCase().execute(ID, Rating.FORGOT));
            assertEquals(ReviewWriteException.Reason.STORAGE, e.getReason()); assertNotNull(e.getCause());
            assertEquals(before, snapshot()); return null;
        });
    }
    @Test public void newProgressInsertFailureRollsBackInsertedEvent() throws Exception {
        run(() -> {
            fault("fail_new_progress", "INSERT", "UserWordProgress");
            assertThrows(ReviewWriteException.class, () -> useCase().execute(ID, Rating.HARD));
            assertNull(db.userDao().progress(ID)); assertTrue(db.userDao().reviews().isEmpty()); return null;
        });
    }
    @Test public void existingProgressUpdateFailureRollsBackInsertedEvent() throws Exception {
        run(() -> {
            seed(ID); String before = snapshot(); fault("fail_update", "UPDATE", "UserWordProgress");
            assertThrows(ReviewWriteException.class, () -> useCase().execute(ID, Rating.REMEMBERED));
            assertEquals(before, snapshot()); return null;
        });
    }
    @Test public void explicitRetryAfterFaultAndReopenPersistsExactlyOneReview() throws Exception {
        run(() -> {
            seed(ID); fault("temporary_fault", "UPDATE", "UserWordProgress");
            assertThrows(ReviewWriteException.class, () -> useCase().execute(ID, Rating.FORGOT));
            db.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER temporary_fault");
            useCase().execute(ID, Rating.FORGOT); String committed = snapshot();
            db.close(); db = UserDatabase.open(context, name);
            assertEquals(committed, snapshot()); assertEquals(1, db.userDao().reviews().size());
            assertEquals(8, db.userDao().progress(ID).reviewCount); return null;
        });
    }
    @Test public void savedFlagChangedAfterReadCausesConflictNotOverwrite() throws Exception {
        run(() -> {
            seed(ID); ReviewRepository repository = new RoomReviewRepository(db.userDao());
            Optional<ReviewProgress> stale = repository.getProgress(ID);
            db.userDao().setSaved(ID, false); String before = snapshot();
            ReviewProgress next = new ReviewProgress(ID, 4, 999, 111L, CLOCK.millis(), 0, false, 8, true);
            ReviewWriteException e = assertThrows(ReviewWriteException.class,
                    () -> repository.recordReview(stale, next, Rating.REMEMBERED, CLOCK.millis()));
            assertEquals(ReviewWriteException.Reason.CONFLICT, e.getReason());
            assertEquals(before, snapshot()); return null;
        });
    }
    @Test public void staleEmptySnapshotCannotOverwriteFirstReview() throws Exception {
        run(() -> {
            ReviewRepository first = new RoomReviewRepository(db.userDao());
            Optional<ReviewProgress> empty = first.getProgress(ID);
            useCase().execute(ID, Rating.HARD); String committed = snapshot();
            ReviewProgress next = new ReviewProgress(ID, 1, 999, CLOCK.millis(), CLOCK.millis(), 0, false, 1, false);
            ReviewWriteException e = assertThrows(ReviewWriteException.class,
                    () -> first.recordReview(empty, next, Rating.REMEMBERED, CLOCK.millis()));
            assertEquals(ReviewWriteException.Reason.CONFLICT, e.getReason());
            assertEquals(committed, snapshot()); return null;
        });
    }
    @Test public void twoDatabaseInstancesCannotCommitSameSnapshotTwice() throws Exception {
        run(() -> {
            UserDatabase other = UserDatabase.open(context, name);
            seed(ID);
            ExecutorService parallel = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Callable<ReviewWriteException.Reason>> tasks = new ArrayList<>();
                for (UserDatabase database : Arrays.asList(db, other)) {
                    ReviewRepository real = new RoomReviewRepository(database.userDao());
                    ReviewRepository coordinated = new ReviewRepository() {
                        public Optional<ReviewProgress> getProgress(String id) {
                            Optional<ReviewProgress> snapshot = real.getProgress(id);
                            try { barrier.await(15, TimeUnit.SECONDS); }
                            catch (Exception e) { throw new RuntimeException(e); }
                            return snapshot;
                        }
                        public void recordReview(Optional<ReviewProgress> expected, ReviewProgress next, Rating rating, long now) {
                            real.recordReview(expected, next, rating, now);
                        }
                    };
                    tasks.add(() -> {
                        try { new RecordReviewUseCase(coordinated, new LeitnerScheduler(), CLOCK, ZONE).execute(ID, Rating.HARD); return null; }
                        catch (ReviewWriteException e) { return e.getReason(); }
                    });
                }
                int successes = 0, conflicts = 0;
                for (Future<ReviewWriteException.Reason> f : parallel.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                    ReviewWriteException.Reason reason = f.get();
                    if (reason == null) successes++;
                    else if (reason == ReviewWriteException.Reason.CONFLICT) conflicts++;
                    else fail("Expected conflict, got " + reason);
                }
                assertEquals(1, successes); assertEquals(1, conflicts);
                assertEquals(1, db.userDao().reviews().size()); assertEquals(8, db.userDao().progress(ID).reviewCount);
            } finally { parallel.shutdownNow(); other.close(); }
            return null;
        });
    }
    @Test public void closedDatabaseReadReportsStorageFailure() throws Exception {
        run(() -> {
            RecordReviewUseCase useCase = useCase(); db.close();
            ReviewWriteException e = assertThrows(ReviewWriteException.class, () -> useCase.execute(ID, Rating.HARD));
            assertEquals(ReviewWriteException.Reason.STORAGE, e.getReason()); return null;
        });
    }
}
