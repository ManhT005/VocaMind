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
                (item, rating) -> useCase.execute(item.getVocabulary().stableId, rating));
        settle();
        return model;
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
        model.rate(Rating.REMEMBERED);
        assertTrue(worker.isEmpty());
        assertTrue(reviews.calls.isEmpty());
        assertEquals(1, state(model).position);
    }

    @Test public void rememberedCommitsBoxOneAndAdvancesAfterTheWrite() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        model.rate(Rating.REMEMBERED);
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
        model.rate(Rating.FORGOT);
        settle();
        assertEquals(0, reviews.calls.get(0).box);
        assertEquals(1, reviews.calls.get(0).consecutiveMistakes);
        model.flip();
        model.rate(Rating.HARD);
        settle();
        assertEquals(2, reviews.calls.size());
        assertEquals(0, reviews.calls.get(1).box);
        assertEquals(0, reviews.calls.get(1).consecutiveMistakes);
    }

    @Test public void tapsWhileSavingDoNotWriteTwice() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        model.rate(Rating.FORGOT);
        model.rate(Rating.FORGOT);
        model.rate(Rating.REMEMBERED);
        assertEquals(1, worker.size());
        settle();
        assertEquals(1, reviews.calls.size());
        assertEquals(B, state(model).card.getVocabulary().stableId);
    }

    @Test public void retryWhileSavingIsIgnoredSoLateCommitCannotAdvanceARebuiltQueue() {
        twoNewWords();
        FlashcardViewModel model = model();
        model.flip();
        model.rate(Rating.REMEMBERED);
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
        model.rate(Rating.REMEMBERED);
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
        model.rate(Rating.REMEMBERED);
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
        model.rate(Rating.HARD);
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
        model.rate(Rating.FORGOT); // A forgotten: queue becomes B, C, A
        settle();
        assertEquals(4, state(model).total);
        String[] expected = {B, "cccccccccccccccc", A};
        for (String id : expected) {
            assertEquals(id, state(model).card.getVocabulary().stableId);
            model.flip();
            model.rate(Rating.REMEMBERED);
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
            model.rate(Rating.FORGOT);
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
        model.rate(Rating.FORGOT);
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
                model.rate(Rating.FORGOT);
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
        model.rate(Rating.REMEMBERED);
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
}
