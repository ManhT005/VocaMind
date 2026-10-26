package com.vocamind.app.domain.learning;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.*;

/**
 * TC-SCHED-01..10. Expected values follow the rules in
 * docs/plans/learn-01-leitner-scheduler.md (assumed until checked against the SRS).
 */
public class LeitnerSchedulerSrsTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long NOW = LocalDateTime.of(2026, 10, 26, 9, 0).atZone(ZONE).toInstant().toEpochMilli();
    private final LeitnerScheduler scheduler = new LeitnerScheduler();

    private ScheduleResult rate(int box, int mistakes, boolean leech, Rating rating) {
        return scheduler.schedule(new LeitnerState(box, mistakes, leech), rating, NOW, ZONE);
    }
    private static long dueInDays(int days) {
        return LocalDate.of(2026, 10, 26).plusDays(days).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    @Test public void tcSched01_newWordRememberedGoesToBox1DueTomorrow() {
        ScheduleResult r = scheduler.schedule(LeitnerState.newWord(), Rating.REMEMBERED, NOW, ZONE);
        assertEquals(0, r.getBoxBefore());
        assertEquals(1, r.getBoxAfter());
        assertEquals(dueInDays(1), r.getDueAtEpochMillis());
    }

    @Test public void tcSched02_rememberedStepsThroughEveryBoxWithItsInterval() {
        int[] expectedDays = {1, 2, 4, 7, 14};
        for (int box = 0; box < 5; box++) {
            ScheduleResult r = rate(box, 0, false, Rating.REMEMBERED);
            assertEquals(box + 1, r.getBoxAfter());
            assertEquals(dueInDays(expectedDays[box]), r.getDueAtEpochMillis());
        }
    }

    @Test public void tcSched03_rememberedAtBox5StaysAtBox5() {
        ScheduleResult r = rate(5, 0, false, Rating.REMEMBERED);
        assertEquals(5, r.getBoxAfter());
        assertEquals(dueInDays(14), r.getDueAtEpochMillis());
    }

    @Test public void tcSched04_hardKeepsBoxAndUsesSameBoxInterval() {
        int[] expectedDays = {1, 1, 2, 4, 7, 14};
        for (int box = 0; box <= 5; box++) {
            ScheduleResult r = rate(box, 0, false, Rating.HARD);
            assertEquals(box, r.getBoxAfter());
            assertEquals(dueInDays(expectedDays[box]), r.getDueAtEpochMillis());
        }
    }

    @Test public void tcSched05_hardAtBox0StaysAtBox0DueTomorrow() {
        ScheduleResult r = rate(0, 0, false, Rating.HARD);
        assertEquals(0, r.getBoxAfter());
        assertEquals(dueInDays(1), r.getDueAtEpochMillis());
    }

    @Test public void tcSched06_forgotFromAnyBoxResetsToBox0DueTomorrow() {
        for (int box = 0; box <= 5; box++) {
            ScheduleResult r = rate(box, 0, false, Rating.FORGOT);
            assertEquals(box, r.getBoxBefore());
            assertEquals(0, r.getBoxAfter());
            assertEquals(dueInDays(1), r.getDueAtEpochMillis());
        }
    }

    @Test public void tcSched07_threeForgotInARowIsNotLeech() {
        ScheduleResult r = rate(0, 2, false, Rating.FORGOT);
        assertEquals(3, r.getConsecutiveMistakes());
        assertFalse(r.isLeech());
        assertFalse(r.becameLeech());
    }

    @Test public void tcSched08_fourthForgotInARowBecomesLeech() {
        ScheduleResult r = rate(0, 3, false, Rating.FORGOT);
        assertEquals(4, r.getConsecutiveMistakes());
        assertTrue(r.isLeech());
        assertTrue(r.becameLeech());
        ScheduleResult again = scheduler.schedule(r.toState(), Rating.FORGOT, NOW, ZONE);
        assertEquals(5, again.getConsecutiveMistakes());
        assertTrue(again.isLeech());
        assertFalse(again.becameLeech());
    }

    @Test public void tcSched09_hardOrRememberedResetsMistakesAndClearsLeech() {
        for (Rating rating : new Rating[]{Rating.HARD, Rating.REMEMBERED}) {
            ScheduleResult r = rate(0, 4, true, rating);
            assertEquals(0, r.getConsecutiveMistakes());
            assertFalse(r.isLeech());
            assertFalse(r.becameLeech());
        }
    }

    @Test public void tcSched10_resultIsDeterministicForSameInput() {
        ScheduleResult a = rate(3, 1, false, Rating.REMEMBERED);
        ScheduleResult b = rate(3, 1, false, Rating.REMEMBERED);
        assertEquals(a.getBoxAfter(), b.getBoxAfter());
        assertEquals(a.getDueAtEpochMillis(), b.getDueAtEpochMillis());
        assertEquals(a.getConsecutiveMistakes(), b.getConsecutiveMistakes());
        assertEquals(a.isLeech(), b.isLeech());
    }
}
