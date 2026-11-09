package com.vocamind.app.domain.learning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.fake.FakePackRepository;
import com.vocamind.app.fake.FakeStudyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Test;

public class PlanTodayTest {
    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private final FakePackRepository packs = new FakePackRepository();
    private final FakeStudyRepository study = new FakeStudyRepository();
    private PlanToday planner;

    @Before public void setUp() {
        planner = new PlanToday(packs, study, Clock.fixed(NOW, ZONE), ZONE);
    }

    private static CatalogVocabulary word(String id) {
        return new CatalogVocabulary(id, id, id, "noun", "nghia " + id, "ex", "vd", null, "src", "v1");
    }

    private static StudyItem due(String id, long dueAt) {
        return new StudyItem(word(id), new WordProgress(id, 1, dueAt, 1L, 1L, 0, false, 1, false));
    }

    private void pack(String id, boolean active) {
        packs.states.add(new PackState(new CatalogPack(id, id, "d", "c", 1, "v1", 10), active, active ? 1L : null, 0));
    }

    @Test public void noActivePackAsksUserToChoose() {
        pack("p1", false);
        TodayPlan plan = planner.execute().get();
        assertEquals(TodayPlan.Status.NO_ACTIVE_PACK, plan.getStatus());
        assertEquals(0, study.calls);
        assertTrue(plan.queue().isEmpty());
    }

    @Test public void dueComesBeforeNewAndKeepsDueOrder() {
        pack("p1", true);
        study.due.add(due("a", 100));
        study.due.add(due("b", 200));
        study.fresh.add(word("n1"));
        study.fresh.add(word("n2"));
        TodayPlan plan = planner.execute().get();
        assertEquals(TodayPlan.Status.READY, plan.getStatus());
        assertEquals(2, plan.dueCount());
        assertEquals(2, plan.newCount());
        assertEquals("a", plan.queue().get(0).getVocabulary().stableId);
        assertEquals("b", plan.queue().get(1).getVocabulary().stableId);
        assertEquals("n1", plan.queue().get(2).getVocabulary().stableId);
        assertEquals("n2", plan.queue().get(3).getVocabulary().stableId);
    }

    @Test public void newWordsAreLimitedByRemainingDailyQuota() {
        pack("p1", true);
        study.dailyNewWords = 5;
        study.learnedToday = 3;
        for (int i = 0; i < 10; i++) study.fresh.add(word("n" + i));
        TodayPlan plan = planner.execute().get();
        assertEquals(2, plan.newCount());
        assertEquals("n0", plan.getNewWords().get(0).stableId);
    }

    @Test public void quotaAlreadyUsedStillShowsDueWords() {
        pack("p1", true);
        study.dailyNewWords = 5;
        study.learnedToday = 7; // learned more than the target: remaining must not go negative
        study.due.add(due("a", 100));
        study.fresh.add(word("n1"));
        TodayPlan plan = planner.execute().get();
        assertEquals(1, plan.dueCount());
        assertEquals(0, plan.newCount());
    }

    @Test public void duplicateStableIdAcrossTiersAndPacksAppearsOnce() {
        pack("p1", true);
        pack("p2", true);
        study.due.add(due("a", 100));
        study.due.add(due("a", 150));
        study.fresh.add(word("a"));
        study.fresh.add(word("n1"));
        study.fresh.add(word("n1"));
        TodayPlan plan = planner.execute().get();
        assertEquals(1, plan.dueCount());
        assertEquals(1, plan.newCount());
        assertEquals(2, plan.queue().size());
    }

    @Test public void notYetDueWordsAreExcluded() {
        pack("p1", true);
        study.due.add(due("later", NOW.toEpochMilli() + 1));
        TodayPlan plan = planner.execute().get();
        assertEquals(TodayPlan.Status.DONE, plan.getStatus());
    }

    @Test public void nothingLeftIsDoneState() {
        pack("p1", true);
        study.learnedToday = 5;
        study.fresh.add(word("n1"));
        TodayPlan plan = planner.execute().get();
        assertEquals(TodayPlan.Status.DONE, plan.getStatus());
        assertFalse(plan.queue().iterator().hasNext());
    }

    @Test public void repositoryFailureIsPropagated() {
        pack("p1", true);
        study.failure = RepoError.READ_FAILED;
        RepoResult<TodayPlan> result = planner.execute();
        assertFalse(result.isSuccess());
        assertEquals(RepoError.READ_FAILED, result.getError());
    }

    @Test public void packFailureIsPropagated() {
        packs.failure = RepoError.READ_FAILED;
        assertEquals(RepoError.READ_FAILED, planner.execute().getError());
    }
}
