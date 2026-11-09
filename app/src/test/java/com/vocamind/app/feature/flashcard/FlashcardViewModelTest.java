package com.vocamind.app.feature.flashcard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.learning.LeitnerScheduler;
import com.vocamind.app.domain.learning.PlanToday;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.learning.RecordReviewUseCase;
import com.vocamind.app.domain.learning.ReviewProgress;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.fake.FakePackRepository;
import com.vocamind.app.fake.FakeReviewRepository;
import com.vocamind.app.fake.FakeStudyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class FlashcardViewModelTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);
    private long now;
    private final Queue<Runnable> worker = new ArrayDeque<>();
    private final Queue<Runnable> main = new ArrayDeque<>();
    private final FakePackRepository packs = new FakePackRepository();
    private final FakeStudyRepository study = new FakeStudyRepository();
    private final FakeReviewRepository reviews = new FakeReviewRepository();
    private final RecordReviewUseCase useCase =
            new RecordReviewUseCase(reviews, new LeitnerScheduler(), CLOCK, ZoneOffset.UTC);

    private static final String A = "aaaaaaaaaaaaaaaa", B = "bbbbbbbbbbbbbbbb";

    private static CatalogVocabulary word(String id) {
        return new CatalogVocabulary(id, id, id, "noun", "nghia " + id, "ex " + id, "vd " + id, null, "src", "v1");
    }

    private void activePack() {
        packs.states.add(new PackState(new CatalogPack("p", "p", "d", "c", 1, "v1", 10), true, 1L, 0));
    }

    private void settle() {
        worker.remove().run();
        main.remove().run();
    }

    /** Production wiring: the review use case commits through the repository. */
    private FlashcardViewModel model() {
        PlanToday planner = new PlanToday(packs, study, CLOCK, ZoneOffset.UTC);
        FlashcardViewModel model = new FlashcardViewModel(planner::execute, worker::add, main::add, () -> { },
                (item, rating) -> useCase.execute(item.getVocabulary().stableId, rating), () -> now);
        settle();
        return model;
    }

    /** Ordinary user decisions; precise timing scenarios call model.rate directly. */
    private void rate(FlashcardViewModel model, Rating rating) {
        now += 600;
        model.rate(state(model).presentationId, rating);
    }

    private static FlashcardViewModel.State state(FlashcardViewModel model) { return model.state().getValue(); }

    private void twoNewWords() {
        activePack();
        study.fresh.add(word(A));
        study.fresh.add(word(B));
    }

    @Test public void frontShowsOnlyTheWordAndRatingIsDisabled() {
        twoNewWords();
        FlashcardViewModel.State s = state(model());
        assertEquals(FlashcardViewModel.Status.READY, s.status);
        assertFalse(s.flipped);
        assertFalse(s.ratingEnabled());
        assertEquals(1, s.position);
        assertEquals(2, s.total);
        assertEquals(A, s.card.getVocabulary().stableId);
    }

    @Test public void ratingBeforeFlipWritesNothing() {
        twoNewWords();
        FlashcardViewModel model = model();
        rate(model, Rating.REMEMBERED);
        assertTrue(worker.isEmpty());
        assertTrue(reviews.calls.isEmpty());
        assertEquals(1, state(model).position);
    }

    @Test public void rememberedCommitsBoxOneAndAdvancesAfterTheWrite() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        rate(model, Rating.REMEMBERED);
        // Write in flight: still on card A, ratings locked.
        assertTrue(state(model).saving);
        assertFalse(state(model).ratingEnabled());
        assertEquals(A, state(model).card.getVocabulary().stableId);
        settle();
        FlashcardViewModel.State next = state(model);
        assertEquals(B, next.card.getVocabulary().stableId);
        assertFalse(next.flipped);
        assertFalse(next.saving);
        assertEquals(1, reviews.calls.size());
        ReviewProgress saved = reviews.calls.get(0);
        assertEquals(A, saved.vocabularyId);
        assertEquals(1, saved.box);
        assertEquals(0, saved.consecutiveMistakes);
        assertEquals(1, next.lastResult.getBoxAfter());
    }

    @Test public void forgotAndHardEachCommitOneReview() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        rate(model, Rating.FORGOT);
        settle();
        assertEquals(0, reviews.calls.get(0).box);
        assertEquals(1, reviews.calls.get(0).consecutiveMistakes);
        model.flip();
        rate(model, Rating.HARD);
        settle();
        assertEquals(2, reviews.calls.size());
        assertEquals(0, reviews.calls.get(1).box);
        assertEquals(0, reviews.calls.get(1).consecutiveMistakes);
    }

    @Test public void tapsWhileSavingDoNotWriteTwice() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        rate(model, Rating.FORGOT);
        rate(model, Rating.FORGOT);
        rate(model, Rating.REMEMBERED);
        assertEquals(1, worker.size());
        settle();
        assertEquals(1, reviews.calls.size());
        assertEquals(B, state(model).card.getVocabulary().stableId);
    }

    @Test public void retryWhileSavingIsIgnoredSoLateCommitCannotAdvanceARebuiltQueue() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        rate(model, Rating.REMEMBERED);
        model.retry(); // must not start a reload while the write is in flight
        assertEquals(1, worker.size());
        settle();
        assertEquals(1, reviews.calls.size());
        assertEquals(B, state(model).card.getVocabulary().stableId);
        assertEquals(2, state(model).total);
    }

    @Test public void failedWriteKeepsCardAndFaceAndWritesNothing() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        reviews.failure = RepoError.WRITE_FAILED;
        rate(model, Rating.REMEMBERED);
        settle();
        FlashcardViewModel.State s = state(model);
        assertTrue(s.saveFailed);
        assertFalse(s.saving);
        assertTrue(s.flipped);
        assertTrue(s.ratingEnabled());
        assertEquals(A, s.card.getVocabulary().stableId);
        assertTrue(reviews.calls.isEmpty());

        // The learner decides again; nothing was retried automatically.
        reviews.failure = null;
        rate(model, Rating.REMEMBERED);
        settle();
        assertEquals(1, reviews.calls.size());
        assertFalse(state(model).saveFailed);
        assertEquals(B, state(model).card.getVocabulary().stableId);
    }

    @Test public void conflictIsReportedAsFailureNotCommitted() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        reviews.failure = RepoError.CONFLICT;
        rate(model, Rating.HARD);
        settle();
        assertTrue(state(model).saveFailed);
        assertEquals(1, state(model).position);
    }

    @Test public void forgotRelearnsOnceAfterTwoOtherCardsThenFinishes() {
        activePack();
        study.fresh.add(word(A));
        study.fresh.add(word(B));
        study.fresh.add(word("cccccccccccccccc"));
        FlashcardViewModel model = model();
        model.flip();
        rate(model, Rating.FORGOT); // A forgotten: queue becomes B, C, A
        settle();
        assertEquals(4, state(model).total);
        String[] expected = {B, "cccccccccccccccc", A};
        for (String id : expected) {
            assertEquals(id, state(model).card.getVocabulary().stableId);
            model.flip();
            rate(model, Rating.REMEMBERED);
            settle();
        }
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
        assertEquals(4, reviews.calls.size()); // A twice, B, C
        assertEquals(1, reviews.calls.get(3).box); // relearn remembered: due tomorrow, not lost
    }

    @Test public void repeatedForgotDoesNotLoopTheSession() {
        activePack();
        study.fresh.add(word(A));
        FlashcardViewModel model = model();
        int shown = 0;
        while (state(model).status == FlashcardViewModel.Status.READY) {
            assertTrue("loop guard", ++shown < 10);
            model.flip();
            rate(model, Rating.FORGOT);
            settle();
        }
        assertEquals(2, shown);
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
    }

    @Test public void failedSaveDoesNotQueueRelearn() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        reviews.failure = RepoError.WRITE_FAILED;
        rate(model, Rating.FORGOT);
        settle();
        assertEquals(2, state(model).total);
        assertEquals(A, state(model).card.getVocabulary().stableId);
    }

    @Test public void fourthForgotMarksLeechAndHintExposesIt() {
        activePack();
        study.fresh.add(word(A));
        FlashcardViewModel model = model();
        // Each session: FORGOT, then FORGOT again on the relearn copy (which ends the session).
        for (int session = 0; session < 2; session++) {
            for (int card = 0; card < 2; card++) {
                model.flip();
                rate(model, Rating.FORGOT);
                settle();
            }
            assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
            if (session == 0) { model.retry(); settle(); } // replan: the fake still offers A
        }
        assertEquals(4, reviews.calls.size());
        assertEquals(4, reviews.calls.get(3).consecutiveMistakes);
        assertTrue(reviews.calls.get(3).isLeech);
        assertTrue(state(model).lastResult.becameLeech());
    }

    @Test public void lastRatingFinishesSessionAndKeepsResult() {
        activePack();
        study.fresh.add(word(A));
        FlashcardViewModel model = model();
        model.flip();
        rate(model, Rating.REMEMBERED);
        settle();
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
        assertNull(state(model).card);
        assertEquals(1, state(model).lastResult.getBoxAfter());
        model.flip();
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
    }

    @Test public void noActivePackAndNothingToStudyAreDistinct() {
        assertEquals(FlashcardViewModel.Status.NO_ACTIVE_PACK, state(model()).status);
        packs.states.clear();
        activePack();
        study.learnedToday = 5;
        assertEquals(FlashcardViewModel.Status.NOTHING_TO_STUDY, state(model()).status);
    }

    @Test public void loadFailureShowsErrorAndRetryRecovers() {
        activePack();
        study.fresh.add(word(A));
        study.failure = RepoError.READ_FAILED;
        FlashcardViewModel model = model();
        assertEquals(FlashcardViewModel.Status.ERROR, state(model).status);
        study.failure = null;
        model.retry();
        settle();
        assertEquals(FlashcardViewModel.Status.READY, state(model).status);
    }

    @Test public void stateSurvivesRecreationBecauseViewModelIsRetained() {
        activePack();
        study.fresh.add(word(A));
        FlashcardViewModel model = model();
        model.flip();
        FlashcardViewModel.State after = model.state().getValue();
        assertTrue(after.flipped);
        assertEquals(A, after.card.getVocabulary().stableId);
        assertEquals(Optional.empty(), Optional.ofNullable(after.lastResult));
    }
    @Test public void fastCommitTripleTapAndExactBoundaryUseFirstAcceptedTimestamp() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        long first = state(model).presentationId;
        // The very first tap at monotonic time zero must be accepted.
        model.rate(first, Rating.REMEMBERED);
        assertTrue(state(model).saving);
        assertFalse(state(model).ratingEnabled());
        settle(); // commit completes before the second tap
        long second = state(model).presentationId;
        assertTrue(second > first);
        model.flip();
        now = 150;
        model.rate(second, Rating.FORGOT);
        now = 300;
        model.rate(second, Rating.HARD);
        now = 599;
        model.rate(second, Rating.REMEMBERED);
        assertTrue(worker.isEmpty());
        assertEquals(1, reviews.calls.size());
        assertEquals(B, state(model).card.getVocabulary().stableId);
        now = 600;
        model.rate(second, Rating.HARD);
        settle();
        assertEquals(2, reviews.calls.size());
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
    }

    @Test public void slowCommitStaysLockedEvenAfterDebounceExpires() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        long first = state(model).presentationId;
        model.rate(first, Rating.HARD);
        now = 1000;
        model.rate(first, Rating.REMEMBERED);
        assertEquals(1, worker.size());
        assertTrue(state(model).saving);
        settle();
        assertEquals(1, reviews.calls.size());
        assertEquals(0, reviews.calls.get(0).box); // HARD, not REMEMBERED
    }

    @Test public void staleOccurrenceCannotRateNextCardOrReloadedSession() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        long first = state(model).presentationId;
        model.rate(first, Rating.REMEMBERED);
        settle();
        model.flip();
        now = 600;
        model.rate(first, Rating.FORGOT);
        assertTrue(worker.isEmpty());
        long second = state(model).presentationId;
        model.retry();
        settle();
        model.flip();
        assertTrue(state(model).presentationId > second);
        model.rate(second, Rating.HARD);
        assertTrue(worker.isEmpty());
        model.rate(state(model).presentationId, Rating.HARD);
        settle();
        assertEquals(2, reviews.calls.size());
    }

    @Test public void invalidInputDoesNotStartOrExtendDebounce() {
        twoNewWords();
        FlashcardViewModel model = model();
        long first = state(model).presentationId;
        model.rate(first, Rating.HARD); // front
        model.flip();
        model.rate(first, null);
        model.rate(first + 1, Rating.FORGOT);
        model.rate(first, Rating.REMEMBERED); // same timestamp, still accepted
        settle();
        model.flip();
        now = 599;
        model.rate(first, Rating.HARD); // stale input cannot extend the window
        now = 600;
        model.rate(state(model).presentationId, Rating.HARD);
        settle();
        assertEquals(2, reviews.calls.size());
    }

    @Test public void failedAttemptKeepsOccurrenceAndDebouncesExplicitRetry() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        long first = state(model).presentationId;
        reviews.failure = RepoError.WRITE_FAILED;
        model.rate(first, Rating.FORGOT);
        settle();
        assertEquals(first, state(model).presentationId);
        assertTrue(state(model).saveFailed);
        assertTrue(state(model).flipped);
        assertEquals(2, state(model).total); // no relearn on failure
        reviews.failure = null;
        now = 150;
        model.rate(first, Rating.FORGOT);
        now = 599;
        model.rate(first, Rating.REMEMBERED);
        assertTrue(worker.isEmpty());
        assertTrue(reviews.calls.isEmpty());
        now = 600;
        model.rate(first, Rating.REMEMBERED);
        settle();
        assertEquals(1, reviews.calls.size());
        assertEquals(B, state(model).card.getVocabulary().stableId);
    }

    @Test public void relearnHasNewOccurrenceAndAcceptsSameWordAgain() {
        activePack();
        study.fresh.add(word(A));
        FlashcardViewModel model = model();
        model.flip();
        long first = state(model).presentationId;
        model.rate(first, Rating.FORGOT);
        settle();
        model.flip();
        long relearn = state(model).presentationId;
        assertTrue(relearn > first);
        assertEquals(A, state(model).card.getVocabulary().stableId);
        now = 600;
        model.rate(first, Rating.FORGOT);
        assertTrue(worker.isEmpty());
        model.rate(relearn, Rating.FORGOT);
        settle();
        assertEquals(2, reviews.calls.size());
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
        now = 1200;
        model.rate(relearn, Rating.REMEMBERED);
        assertTrue(worker.isEmpty());
    }

}
