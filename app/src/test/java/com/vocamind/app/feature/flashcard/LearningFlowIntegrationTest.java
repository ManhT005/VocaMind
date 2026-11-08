package com.vocamind.app.feature.flashcard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.repository.RoomPackRepository;
import com.vocamind.app.data.repository.RoomReviewRepository;
import com.vocamind.app.data.repository.RoomStudyRepository;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.data.user.entity.ReviewEvent;
import com.vocamind.app.domain.learning.LeitnerScheduler;
import com.vocamind.app.domain.learning.PlanToday;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.learning.RecordReviewUseCase;
import com.vocamind.app.domain.learning.WordProgress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

/**
 * LEARN-04: Today plan -> Flashcard session -> rating -> transaction -> relearn once -> finish,
 * over real catalog.db + userdata.db, a deterministic clock and the production use cases.
 * Rollback of a failed transaction is covered separately by AtomicReviewTest (DATA-06).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class LearningFlowIntegrationTest {
    private static final String W1 = "0000000000000001", W2 = "0000000000000002",
            W3 = "0000000000000003", W4 = "0000000000000004", OTHER = "00000000000000bb";
    private static final long DAY = 86_400_000L;
    private static final ZoneId ZONE = ZoneOffset.UTC;

    /** Test clock that only moves when the test says so. */
    private static final class TestClock extends Clock {
        long now = Instant.parse("2026-11-05T10:00:00Z").toEpochMilli();
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        void tick() { now += 1_000; }
        /** One day plus an hour, so words reviewed late yesterday are due this morning. */
        void nextDay() { now += DAY + 3_600_000L; }
    }

    private long monotonic;
    private final TestClock clock = new TestClock();
    private final Queue<Runnable> worker = new ArrayDeque<>();
    private final Queue<Runnable> main = new ArrayDeque<>();
    private Context context;
    private CatalogDatabase catalog;
    private UserDatabase user;
    private String catalogName, userName;
    private RoomPackRepository packs;
    private RoomStudyRepository study;
    private RoomReviewRepository reviews;

    @Before public void open() {
        context = ApplicationProvider.getApplicationContext();
        String token = UUID.randomUUID().toString();
        catalogName = "learn04-catalog-" + token + ".db";
        userName = "learn04-user-" + token + ".db";
        catalog = CatalogDatabase.open(context, catalogName);
        user = UserDatabase.open(context, userName);
        SupportSQLiteDatabase sql = catalog.getOpenHelper().getWritableDatabase();
        for (String t : new String[]{"PackWord", "VocabularyFts", "VocabularyEntry", "LearningPack", "ContentSource"}) {
            sql.execSQL("DELETE FROM " + t);
        }
        sql.execSQL("INSERT INTO ContentSource (id,title,url,licenseCode,licenseUrl,snapshotDate,attributionText) "
                + "VALUES ('src','Test','https://example.invalid','LicenseRef-Test','https://example.invalid/l','2026-11-01','Test')");
        sql.execSQL("INSERT INTO LearningPack (id,title,description,category,orderNo,version) VALUES ('A','A','d','t',1,'1')");
        sql.execSQL("INSERT INTO LearningPack (id,title,description,category,orderNo,version) VALUES ('B','B','d','t',2,'1')");
        int order = 1;
        for (String id : Arrays.asList(W1, W2, W3, W4, OTHER)) {
            sql.execSQL("INSERT INTO VocabularyEntry (id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                    + "VALUES (?,?,?,?,?,?,?,NULL,'src','1')", new Object[]{id, "w" + id, "w" + id, "noun", "nghia", "ex", "vd"});
        }
        for (String id : Arrays.asList(W1, W2, W3, W4)) {
            sql.execSQL("INSERT INTO PackWord VALUES ('A',?,?)", new Object[]{id, order++});
        }
        sql.execSQL("INSERT INTO PackWord VALUES ('B',?,1)", new Object[]{OTHER});
        packs = new RoomPackRepository(catalog.catalogDao(), user.userDao());
        study = new RoomStudyRepository(catalog.catalogDao(), user.userDao());
        reviews = new RoomReviewRepository(catalog.catalogDao(), user);
        assertTrue(io(() -> packs.activatePack("A", clock.millis())).isSuccess());
    }

    @After public void close() {
        catalog.close();
        user.close();
        context.deleteDatabase(catalogName);
        context.deleteDatabase(userName);
    }

    /** Production wiring with the clock under test; each rating ticks the clock first. */
    private FlashcardViewModel startSession() {
        PlanToday planner = new PlanToday(packs, study, clock, ZONE);
        RecordReviewUseCase useCase = new RecordReviewUseCase(reviews, new LeitnerScheduler(), clock, ZONE);
        FlashcardViewModel model = new FlashcardViewModel(planner::execute, worker::add, main::add, () -> { },
                (item, rating) -> {
                    clock.tick();
                    return useCase.execute(item.getVocabulary().stableId, rating);
                }, () -> monotonic);
        settle();
        return model;
    }

    /** Runs the queued worker task off the main thread (Room refuses main), then delivers to main. */
    private void settle() {
        Runnable task = worker.remove();
        Thread thread = new Thread(task, "learn04-worker");
        thread.start();
        try { thread.join(30_000); } catch (InterruptedException e) { throw new AssertionError(e); }
        assertFalse("worker timed out", thread.isAlive());
        main.remove().run();
    }

    private void review(FlashcardViewModel model, Rating rating) {
        model.flip();
        monotonic += 600;
        model.rate(model.state().getValue().presentationId, rating);
        settle();
    }

    /** Rates every other card REMEMBERED until the wanted word is on screen. */
    private void skipTo(FlashcardViewModel model, String wanted) {
        for (int guard = 0; guard < 10 && model.state().getValue().status == FlashcardViewModel.Status.READY
                && !id(model).equals(wanted); guard++) {
            review(model, Rating.REMEMBERED);
        }
        assertEquals(wanted, id(model));
    }

    private static String id(FlashcardViewModel model) { return model.state().getValue().card.getVocabulary().stableId; }

    private List<ReviewEvent> events() { return io(() -> user.userDao().reviews()); }

    private WordProgress progress(String id) {
        return io(() -> study.getProgress(id)).get().orElseThrow(AssertionError::new);
    }

    /** Room refuses queries on the main thread, which the LiveData-driven ViewModel needs. */
    private <T> T io(Callable<T> call) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { out.set(call.call()); } catch (Throwable e) { failure.set(e); }
        }, "learn04-io");
        thread.start();
        try { thread.join(30_000); } catch (InterruptedException e) { throw new AssertionError(e); }
        assertFalse("io timed out", thread.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
        return out.get();
    }

    @Test public void fullSessionDueOrderRatingsRelearnOnceAndFinish() throws Exception {
        FlashcardViewModel model = startSession();
        FlashcardViewModel.State plan = model.state().getValue();
        assertEquals(FlashcardViewModel.Status.READY, plan.status);
        assertEquals(4, plan.total); // only active pack A, in PackWord order, within the daily quota
        assertEquals(W1, id(model));
        assertTrue(events().isEmpty());

        review(model, Rating.REMEMBERED); // W1
        assertEquals(1, events().size());
        assertEquals(1, progress(W1).getBox());
        assertEquals(clock.millis() + DAY, progress(W1).getDueAtEpochMillis());

        assertEquals(W2, id(model));
        review(model, Rating.HARD); // W2: new word stays in box 0, due tomorrow
        assertEquals(2, events().size());
        assertEquals(0, progress(W2).getBox());
        assertEquals(0, progress(W2).getConsecutiveMistakes());
        assertEquals(clock.millis() + DAY, progress(W2).getDueAtEpochMillis());

        assertEquals(W3, id(model));
        review(model, Rating.FORGOT); // W3 is queued again after two other cards
        assertEquals(3, events().size());
        assertEquals(1, progress(W3).getConsecutiveMistakes());
        assertEquals(5, model.state().getValue().total);

        assertEquals(W4, id(model));
        review(model, Rating.REMEMBERED);
        assertEquals(W3, id(model)); // relearn copy, shown once
        long dueBeforeRelearn = progress(W3).getDueAtEpochMillis();
        review(model, Rating.FORGOT); // forgetting it again must not enqueue it a third time

        assertEquals(FlashcardViewModel.Status.FINISHED, model.state().getValue().status);
        List<ReviewEvent> all = events();
        assertEquals(5, all.size());
        assertEquals(Arrays.asList("REMEMBERED", "HARD", "FORGOT", "REMEMBERED", "FORGOT"),
                Arrays.asList(all.get(0).rating, all.get(1).rating, all.get(2).rating, all.get(3).rating, all.get(4).rating));
        for (int i = 1; i < all.size(); i++) assertTrue(all.get(i).reviewedAt > all.get(i - 1).reviewedAt);
        assertEquals(2, progress(W3).getConsecutiveMistakes());
        assertEquals(2, progress(W3).getReviewCount());
        assertEquals(clock.millis() + DAY, progress(W3).getDueAtEpochMillis()); // still due tomorrow
        assertTrue(dueBeforeRelearn <= progress(W3).getDueAtEpochMillis());

        // Same day again: nothing is due and every pack-A word is learned.
        model.retry();
        settle();
        assertEquals(FlashcardViewModel.Status.NOTHING_TO_STUDY, model.state().getValue().status);
        assertEquals(5, events().size());
    }

    @Test public void doubleTapWritesExactlyOneEventAndNextCardWorks() throws Exception {
        FlashcardViewModel model = startSession();
        model.flip();
        model.rate(model.state().getValue().presentationId, Rating.REMEMBERED);
        model.rate(model.state().getValue().presentationId, Rating.REMEMBERED);
        model.rate(model.state().getValue().presentationId, Rating.FORGOT);
        assertEquals(1, worker.size());
        settle();
        assertEquals(1, events().size());
        assertEquals(W2, id(model));
        review(model, Rating.REMEMBERED);
        assertEquals(2, events().size());
    }

    @Test public void ratingBeforeFlipWritesNothing() throws Exception {
        FlashcardViewModel model = startSession();
        model.rate(model.state().getValue().presentationId, Rating.REMEMBERED);
        assertTrue(worker.isEmpty());
        assertTrue(events().isEmpty());
    }

    @Test public void dueWordsComeBeforeNewWordsOnTheNextDay() throws Exception {
        FlashcardViewModel first = startSession();
        review(first, Rating.REMEMBERED); // W1 due tomorrow
        review(first, Rating.REMEMBERED); // W2 due tomorrow
        clock.nextDay();

        FlashcardViewModel second = startSession();
        assertEquals(W1, id(second)); // due, dueAt ASC
        review(second, Rating.REMEMBERED);
        assertEquals(W2, id(second));
        assertEquals(3, events().size());
        assertEquals(4, second.state().getValue().total); // 2 due + 2 new words of pack A
    }

    @Test public void repeatedForgotAcrossDaysTurnsLeechAndRememberedClearsIt() throws Exception {
        // Day 1: FORGOT, then FORGOT again on the relearn copy = 2 mistakes.
        FlashcardViewModel model = startSession();
        review(model, Rating.FORGOT); // W1
        skipTo(model, W1);
        review(model, Rating.FORGOT);
        assertEquals(2, progress(W1).getConsecutiveMistakes());
        assertFalse(progress(W1).isLeech());

        // Day 2: W1 is due again; two more FORGOTs reach the leech threshold (4).
        clock.nextDay();
        model = startSession();
        skipTo(model, W1);
        review(model, Rating.FORGOT);
        assertEquals(3, progress(W1).getConsecutiveMistakes());
        assertFalse(progress(W1).isLeech());
        skipTo(model, W1);
        review(model, Rating.FORGOT);
        assertEquals(4, progress(W1).getConsecutiveMistakes());
        assertTrue(progress(W1).isLeech());
        assertTrue(model.state().getValue().lastResult.becameLeech());

        // Day 3: a successful review clears the mistakes and the leech flag.
        clock.nextDay();
        model = startSession();
        skipTo(model, W1);
        review(model, Rating.HARD);
        assertEquals(0, progress(W1).getConsecutiveMistakes());
        assertFalse(progress(W1).isLeech());
    }

    @Test public void pausedPackWordsAreNeverPlanned() throws Exception {
        FlashcardViewModel model = startSession();
        assertEquals(4, model.state().getValue().total);
        assertTrue(io(() -> packs.pausePack("A")).isSuccess());
        model.retry();
        settle();
        assertEquals(FlashcardViewModel.Status.NO_ACTIVE_PACK, model.state().getValue().status);
    }
    @Test public void fastCommittedTripleTapDoesNotUpdateNextWordsProgress() {
        FlashcardViewModel model = startSession();
        model.flip();
        long first = model.state().getValue().presentationId;
        model.rate(first, Rating.REMEMBERED);
        settle();
        model.flip(); // expose the answer before the rest of the burst
        long second = model.state().getValue().presentationId;
        monotonic = 150;
        model.rate(second, Rating.FORGOT);
        monotonic = 300;
        model.rate(second, Rating.HARD);
        assertTrue(worker.isEmpty());
        assertEquals(1, events().size());
        assertEquals("REMEMBERED", events().get(0).rating);
        assertEquals(1, progress(W1).getReviewCount());
        assertEquals(1, progress(W1).getBox());
        assertFalse(io(() -> study.getProgress(W2)).get().isPresent());
        monotonic = 600;
        model.rate(first, Rating.FORGOT); // stale even outside the time window
        assertTrue(worker.isEmpty());
        model.rate(second, Rating.HARD);
        settle();
        assertEquals(2, events().size());
        assertEquals(1, progress(W2).getReviewCount());
        assertEquals(0, progress(W2).getBox());
        assertEquals(0, progress(W2).getConsecutiveMistakes());
    }

}
