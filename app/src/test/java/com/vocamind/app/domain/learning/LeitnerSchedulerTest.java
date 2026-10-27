package com.vocamind.app.domain.learning;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.*;

public class LeitnerSchedulerTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long NOW = at(2026, 10, 26, 15, 30);
    private final LeitnerScheduler scheduler = new LeitnerScheduler();

    private static long at(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(ZONE).toInstant().toEpochMilli();
    }
    private static long startOfDay(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    @Test public void newWordStartsInBoxZero() {
        LeitnerState state = LeitnerState.newWord();
        assertEquals(0, state.getBox());
        assertEquals(0, state.getConsecutiveMistakes());
        assertFalse(state.isLeech());
    }

    @Test public void rememberedMovesUpOneBox() {
        ScheduleResult r = scheduler.schedule(new LeitnerState(2, 0, false), Rating.REMEMBERED, NOW, ZONE);
        assertEquals(2, r.getBoxBefore());
        assertEquals(3, r.getBoxAfter());
        assertEquals(startOfDay(2026, 11, 2), r.getDueAtEpochMillis());
        assertEquals(Rating.REMEMBERED, r.getRating());
    }

    @Test public void hardKeepsBox() {
        ScheduleResult r = scheduler.schedule(new LeitnerState(3, 0, false), Rating.HARD, NOW, ZONE);
        assertEquals(3, r.getBoxAfter());
        assertEquals(startOfDay(2026, 11, 2), r.getDueAtEpochMillis());
    }

    @Test public void forgotResetsToBoxZeroDueTomorrow() {
        ScheduleResult r = scheduler.schedule(new LeitnerState(4, 0, false), Rating.FORGOT, NOW, ZONE);
        assertEquals(0, r.getBoxAfter());
        assertEquals(1, r.getConsecutiveMistakes());
        assertEquals(startOfDay(2026, 10, 27), r.getDueAtEpochMillis());
    }

    @Test public void toStateCarriesResultForward() {
        ScheduleResult r = scheduler.schedule(new LeitnerState(1, 2, false), Rating.FORGOT, NOW, ZONE);
        LeitnerState next = r.toState();
        assertEquals(0, next.getBox());
        assertEquals(3, next.getConsecutiveMistakes());
        assertFalse(next.isLeech());
    }

    @Test public void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new LeitnerState(-1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new LeitnerState(6, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new LeitnerState(0, -1, false));
        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(null, Rating.HARD, NOW, ZONE));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(LeitnerState.newWord(), null, NOW, ZONE));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(LeitnerState.newWord(), Rating.HARD, NOW, null));
    }
}
