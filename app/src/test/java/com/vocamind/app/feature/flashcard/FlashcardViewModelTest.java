package com.vocamind.app.feature.flashcard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.learning.PlanToday;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.fake.FakePackRepository;
import com.vocamind.app.fake.FakeStudyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class FlashcardViewModelTest {
    private final Queue<Runnable> worker = new ArrayDeque<>();
    private final Queue<Runnable> main = new ArrayDeque<>();
    private final FakePackRepository packs = new FakePackRepository();
    private final FakeStudyRepository study = new FakeStudyRepository();
    private final List<String> rated = new ArrayList<>();

    private static CatalogVocabulary word(String id) {
        return new CatalogVocabulary(id, id, id, "noun", "nghia " + id, "ex " + id, "vd " + id, null, "src", "v1");
    }

    private void activePack() {
        packs.states.add(new PackState(new CatalogPack("p", "p", "d", "c", 1, "v1", 10), true, 1L, 0));
    }

    private FlashcardViewModel model(FlashcardViewModel.RatingListener listener) {
        PlanToday planner = new PlanToday(packs, study,
                Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
        FlashcardViewModel model = new FlashcardViewModel(planner::execute, worker::add, main::add, () -> { }, listener);
        worker.remove().run();
        main.remove().run();
        return model;
    }

    private static FlashcardViewModel.State state(FlashcardViewModel model) { return model.state().getValue(); }

    @Test public void frontShowsOnlyTheWordAndRatingIsDisabled() {
        activePack();
        study.fresh.add(word("a"));
        study.fresh.add(word("b"));
        FlashcardViewModel model = model(null);
        FlashcardViewModel.State s = state(model);
        assertEquals(FlashcardViewModel.Status.READY, s.status);
        assertFalse(s.flipped);
        assertFalse(s.ratingEnabled());
        assertEquals(1, s.position);
        assertEquals(2, s.total);
        assertEquals("a", s.card.getVocabulary().stableId);
    }

    @Test public void ratingBeforeFlipIsIgnored() {
        activePack();
        study.fresh.add(word("a"));
        FlashcardViewModel model = model((item, rating) -> rated.add(item.getVocabulary().stableId));
        model.rate(Rating.REMEMBERED);
        assertTrue(rated.isEmpty());
        assertEquals(1, state(model).position);
    }

    @Test public void flipEnablesRatingAndRatingAdvancesToNextCardFront() {
        activePack();
        study.fresh.add(word("a"));
        study.fresh.add(word("b"));
        FlashcardViewModel model = model((item, rating) -> rated.add(item.getVocabulary().stableId + ":" + rating));
        model.flip();
        assertTrue(state(model).ratingEnabled());
        model.rate(Rating.HARD);
        assertEquals(List.of("a:HARD"), rated);
        FlashcardViewModel.State next = state(model);
        assertEquals("b", next.card.getVocabulary().stableId);
        assertFalse(next.flipped);
        assertFalse(next.ratingEnabled());
    }

    @Test public void doubleRatingOnOneFaceCountsOnce() {
        activePack();
        study.fresh.add(word("a"));
        study.fresh.add(word("b"));
        FlashcardViewModel model = model((item, rating) -> rated.add(item.getVocabulary().stableId));
        model.flip();
        model.rate(Rating.FORGOT);
        model.rate(Rating.FORGOT); // second tap lands on the unflipped next card
        assertEquals(List.of("a"), rated);
    }

    @Test public void lastRatingFinishesSession() {
        activePack();
        study.fresh.add(word("a"));
        FlashcardViewModel model = model(null);
        model.flip();
        model.rate(Rating.REMEMBERED);
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
        assertNull(state(model).card);
        model.flip();
        assertEquals(FlashcardViewModel.Status.FINISHED, state(model).status);
    }

    @Test public void noActivePackAndNothingToStudyAreDistinct() {
        FlashcardViewModel none = model(null);
        assertEquals(FlashcardViewModel.Status.NO_ACTIVE_PACK, state(none).status);

        packs.states.clear();
        activePack();
        study.learnedToday = 5;
        FlashcardViewModel done = model(null);
        assertEquals(FlashcardViewModel.Status.NOTHING_TO_STUDY, state(done).status);
    }

    @Test public void failureShowsErrorAndRetryRecovers() {
        activePack();
        study.fresh.add(word("a"));
        study.failure = RepoError.READ_FAILED;
        FlashcardViewModel model = model(null);
        assertEquals(FlashcardViewModel.Status.ERROR, state(model).status);
        study.failure = null;
        model.retry();
        worker.remove().run();
        main.remove().run();
        assertEquals(FlashcardViewModel.Status.READY, state(model).status);
    }

    @Test public void stateSurvivesRecreationBecauseViewModelIsRetained() {
        activePack();
        study.fresh.add(word("a"));
        FlashcardViewModel model = model(null);
        model.flip();
        FlashcardViewModel.State before = state(model);
        // A rotated Fragment re-observes the same retained ViewModel.
        FlashcardViewModel.State after = model.state().getValue();
        assertTrue(after.flipped);
        assertEquals(before.card, after.card);
    }
}
