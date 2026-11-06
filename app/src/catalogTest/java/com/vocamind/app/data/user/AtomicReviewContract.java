package com.vocamind.app.data.user;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.core.ReviewProvider;
import com.vocamind.app.data.user.entity.*;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.repository.RoomReviewRepository;
import com.vocamind.app.domain.common.*;
import com.vocamind.app.domain.learning.*;
import org.junit.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** File-backed SQLite integration; fault injection uses DB triggers, not production hooks. */
public abstract class AtomicReviewContract {
    private static final String ID = "5aecfbf5b9a23aee";
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"), ZONE);
    private Context context;
    private UserDatabase db;
    private CatalogDatabase catalog;
    private ExecutorService worker;
    private String name;
    @Before public void open() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        name = "data06-" + UUID.randomUUID() + ".db";
        worker = Executors.newSingleThreadExecutor();
        run(() -> {
            catalog = CatalogDatabase.open(context, name + "-catalog");
            catalog.getOpenHelper().getReadableDatabase();
            db = UserDatabase.open(context, name); return null;
        });
    }
    @After public void close() throws Exception {
        try { if (db != null) run(() -> { db.close(); catalog.close(); return null; }); }
        finally { if (worker != null) worker.shutdownNow(); context.deleteDatabase(name); context.deleteDatabase(name + "-catalog"); }
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
    private RecordReviewUseCase useCase() { return ReviewProvider.create(new RoomReviewRepository(catalog.catalogDao(), db), CLOCK, ZONE); }
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
                String id = new String[]{"90562a98e8686267", "d17314a48504e2ac", ID}[rating.ordinal()];
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
            seed(ID); ReviewRepository repository = new RoomReviewRepository(catalog.catalogDao(), db);
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
            ReviewRepository first = new RoomReviewRepository(catalog.catalogDao(), db);
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
        race(true, false);
    }
    @Test public void legacyAndUseCaseCannotCommitSameSnapshotTwice() throws Exception {
        race(true, true);
    }
    @Test public void legacyAndUseCaseCannotBothInsertFirstReview() throws Exception {
        race(false, true);
    }
    private void race(boolean existing, boolean legacySecond) throws Exception {
        run(() -> {
            UserDatabase other = UserDatabase.open(context, name);
            if (existing) seed(ID);
            ExecutorService parallel = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Callable<ReviewWriteException.Reason>> tasks = new ArrayList<>();
                for (UserDatabase database : Arrays.asList(db, other)) {
                    ReviewRepository real = new RoomReviewRepository(catalog.catalogDao(), database);
                    ReviewRepository coordinated = new ReviewRepository() {
                        public Optional<ReviewProgress> getProgress(String id) {
                            Optional<ReviewProgress> value = real.getProgress(id);
                            try { barrier.await(15, TimeUnit.SECONDS); }
                            catch (Exception e) { throw new RuntimeException(e); }
                            return value;
                        }
                        public void recordReview(Optional<ReviewProgress> expected, ReviewProgress next, Rating rating, long now) {
                            real.recordReview(expected, next, rating, now);
                        }
                    };
                    boolean legacy = legacySecond && database == other;
                    tasks.add(() -> {
                        try {
                            if (legacy) {
                                var state = existing ? new LeitnerState(3, 3, false) : LeitnerState.newWord();
                                var result = new LeitnerScheduler().schedule(state, Rating.HARD, CLOCK.millis(), ZONE);
                                var committed = coordinated.recordReview(ID, result, CLOCK.millis());
                                if (committed.isSuccess()) return null;
                                assertEquals(RepoError.CONFLICT, committed.getError());
                                return ReviewWriteException.Reason.CONFLICT;
                            }
                            new RecordReviewUseCase(coordinated, new LeitnerScheduler(), CLOCK, ZONE)
                                    .execute(ID, Rating.HARD);
                            return null;
                        } catch (ReviewWriteException e) { return e.getReason(); }
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
                assertEquals(1, db.userDao().reviews().size());
                assertEquals(existing ? 8 : 1, db.userDao().progress(ID).reviewCount);
                assertEquals(1, db.userDao().allProgress().size());
            } finally { parallel.shutdownNow(); other.close(); }
            return null;
        });
    }
    @Test public void bothApisUseOneWriterAndLegacyHardReplayIsRejected() throws Exception {
        run(() -> {
            ReviewRepository repository = new RoomReviewRepository(catalog.catalogDao(), db);
            var result = new LeitnerScheduler().schedule(LeitnerState.newWord(), Rating.HARD, CLOCK.millis(), ZONE);
            assertTrue(repository.recordReview(ID, result, CLOCK.millis()).isSuccess());
            ReviewProvider.create(repository, CLOCK, ZONE).execute(ID, Rating.HARD);
            assertEquals(2, db.userDao().reviews().size());
            assertEquals(2, db.userDao().progress(ID).reviewCount);
            assertEquals(RepoError.CONFLICT, repository.recordReview(ID, result, CLOCK.millis()).getError());
            assertEquals(2, db.userDao().reviews().size());
            assertEquals(1, db.userDao().allProgress().size());
            assertTrue(db.userDao().quizzes().isEmpty());
            return null;
        });
    }
    @Test public void unknownCatalogWordCannotCreateOrphanThroughEitherApi() throws Exception {
        run(() -> {
            String unknown = "ffffffffffffffff";
            ReviewRepository repository = new RoomReviewRepository(catalog.catalogDao(), db);
            assertEquals(ReviewWriteException.Reason.NOT_FOUND,
                    assertThrows(ReviewWriteException.class, () -> useCase().execute(unknown, Rating.HARD)).getReason());
            var result = new LeitnerScheduler().schedule(LeitnerState.newWord(), Rating.HARD, CLOCK.millis(), ZONE);
            assertEquals(RepoError.NOT_FOUND, repository.recordReview(unknown, result, CLOCK.millis()).getError());
            ReviewProgress next = ReviewProgress.afterReview(unknown, Optional.empty(), result, CLOCK.millis());
            assertEquals(ReviewWriteException.Reason.NOT_FOUND,
                    assertThrows(ReviewWriteException.class,
                            () -> repository.recordReview(Optional.empty(), next, Rating.HARD, CLOCK.millis())).getReason());
            assertTrue(db.userDao().allProgress().isEmpty()); assertTrue(db.userDao().reviews().isEmpty());
            return null;
        });
    }
    @Test public void catalogReadFailureIsTypedReadFailedWithoutWriting() throws Exception {
        run(() -> {
            catalog.close();
            assertEquals(ReviewWriteException.Reason.READ_FAILED,
                    assertThrows(ReviewWriteException.class, () -> useCase().execute(ID, Rating.HARD)).getReason());
            var result = new LeitnerScheduler().schedule(LeitnerState.newWord(), Rating.HARD, CLOCK.millis(), ZONE);
            var repository = new RoomReviewRepository(catalog.catalogDao(), db);
            assertEquals(RepoError.READ_FAILED, repository.recordReview(ID, result, CLOCK.millis()).getError());
            assertTrue(db.userDao().reviews().isEmpty()); assertTrue(db.userDao().allProgress().isEmpty());
            return null;
        });
    }
    @Test public void legacyEventFailureRollsBack() throws Exception { legacyFailure(true, "INSERT", "ReviewEvent"); }
    @Test public void legacyNewProgressFailureRollsBackEvent() throws Exception { legacyFailure(false, "INSERT", "UserWordProgress"); }
    @Test public void legacyProgressUpdateFailureRollsBackEvent() throws Exception { legacyFailure(true, "UPDATE", "UserWordProgress"); }
    private void legacyFailure(boolean existing, String operation, String table) throws Exception {
        run(() -> {
            if (existing) seed(ID);
            String before = snapshot(); fault("legacy_fault", operation, table);
            var state = existing ? new LeitnerState(3, 3, false) : LeitnerState.newWord();
            var result = new LeitnerScheduler().schedule(state, Rating.HARD, CLOCK.millis(), ZONE);
            assertEquals(RepoError.WRITE_FAILED,
                    new RoomReviewRepository(catalog.catalogDao(), db).recordReview(ID, result, CLOCK.millis()).getError());
            assertEquals(before, snapshot());
            assertEquals(existing ? 1 : 0, db.userDao().allProgress().size());
            assertTrue(db.userDao().quizzes().isEmpty());
            return null;
        });
    }
    @Test public void malformedProgressIsTypedInvalidDataWithNoWrite() throws Exception {
        run(() -> {
            seed(ID); db.getOpenHelper().getWritableDatabase().execSQL("UPDATE UserWordProgress SET box=9 WHERE vocabularyId=?", new Object[]{ID});
            String before = snapshot();
            assertEquals(ReviewWriteException.Reason.INVALID_DATA,
                    assertThrows(ReviewWriteException.class, () -> useCase().execute(ID, Rating.HARD)).getReason());
            assertEquals(before, snapshot()); return null;
        });
    }
    @Test public void persistedDueTimeSurvivesZoneChangeAndReopen() throws Exception {
        run(() -> {
            ScheduleResult committed = useCase().execute(ID, Rating.HARD);
            long dueAt = committed.getDueAtEpochMillis();
            assertEquals(LocalDateTime.parse("2026-11-04T16:30"),
                    Instant.ofEpochMilli(dueAt).atZone(ZONE).toLocalDateTime());
            db.close(); db = UserDatabase.open(context, name);
            ReviewRepository repository = new RoomReviewRepository(catalog.catalogDao(), db);
            ReviewProvider.create(repository, CLOCK, ZoneId.of("America/New_York"))
                    .execute("90562a98e8686267", Rating.HARD);
            assertEquals(dueAt, db.userDao().progress(ID).dueAt);
            assertEquals(dueAt, db.userDao().reviews().get(0).nextDueAt);
            assertEquals(dueAt, repository.getProgress(ID).get().dueAt);
            return null;
        });
    }
    @Test public void mismatchedIdsAndTamperedHistoryCannotWrite() throws Exception {
        run(() -> {
            seed(ID); String before = snapshot();
            ReviewRepository repository = new RoomReviewRepository(catalog.catalogDao(), db);
            Optional<ReviewProgress> expected = repository.getProgress(ID);
            ReviewProgress wrongId = new ReviewProgress("90562a98e8686267", 3, 999, 111L,
                    CLOCK.millis(), 0, false, 8, true);
            assertThrows(IllegalArgumentException.class,
                    () -> repository.recordReview(expected, wrongId, Rating.HARD, CLOCK.millis()));
            ReviewProgress wrongCount = new ReviewProgress(ID, 3, 999, 111L,
                    CLOCK.millis(), 0, false, 9, true);
            assertThrows(IllegalArgumentException.class,
                    () -> repository.recordReview(expected, wrongCount, Rating.HARD, CLOCK.millis()));
            ReviewEvent event = new ReviewEvent(); event.vocabularyId = "90562a98e8686267";
            event.rating = "HARD"; event.boxBefore = 3; event.boxAfter = 3; event.nextDueAt = 1234;
            UserWordProgress row = db.userDao().progress(ID);
            assertThrows(IllegalArgumentException.class, () -> db.userDao().recordReviewIfUnchanged(row, event, row));
            assertEquals(before, snapshot()); return null;
        });
    }
    @Test public void closedDatabaseReadReportsReadFailure() throws Exception {
        run(() -> {
            RecordReviewUseCase useCase = useCase(); db.close();
            ReviewWriteException e = assertThrows(ReviewWriteException.class, () -> useCase.execute(ID, Rating.HARD));
            assertEquals(ReviewWriteException.Reason.READ_FAILED, e.getReason()); return null;
        });
    }
}
