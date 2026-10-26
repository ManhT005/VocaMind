package com.vocamind.app.domain.learning;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

/** Box limits, local calendar days, DST and invalid input. */
public class LeitnerSchedulerBoundaryTest {
    private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private final LeitnerScheduler scheduler = new LeitnerScheduler();

    private static long at(LocalDateTime time, ZoneId zone) {
        return time.atZone(zone).toInstant().toEpochMilli();
    }
    private static ZonedDateTime due(ScheduleResult r, ZoneId zone) {
        return Instant.ofEpochMilli(r.getDueAtEpochMillis()).atZone(zone);
    }

    @Test public void lastMillisecondOfDayAndMidnightBelongToDifferentDays() {
        long beforeMidnight = at(LocalDateTime.of(2026, 10, 26, 23, 59, 59, 999_000_000), HCM);
        long midnight = at(LocalDateTime.of(2026, 10, 27, 0, 0), HCM);
        ScheduleResult late = scheduler.schedule(LeitnerState.newWord(), Rating.FORGOT, beforeMidnight, HCM);
        ScheduleResult early = scheduler.schedule(LeitnerState.newWord(), Rating.FORGOT, midnight, HCM);
        assertEquals(LocalDate.of(2026, 10, 27), due(late, HCM).toLocalDate());
        assertEquals(LocalDate.of(2026, 10, 28), due(early, HCM).toLocalDate());
    }

    @Test public void dueAtIsAlwaysStartOfLocalDay() {
        for (int box = 0; box <= 5; box++) {
            ScheduleResult r = scheduler.schedule(new LeitnerState(box, 0, false), Rating.HARD,
                    at(LocalDateTime.of(2026, 10, 26, 13, 37), HCM), HCM);
            assertEquals(LocalTime.MIDNIGHT, due(r, HCM).toLocalTime());
        }
    }

    @Test public void sameInstantUsesTheZoneCalendarDay() {
        // 2026-10-26 20:00 UTC is already 2026-10-27 03:00 in Ho Chi Minh City.
        long now = at(LocalDateTime.of(2026, 10, 26, 20, 0), ZoneId.of("UTC"));
        ScheduleResult utc = scheduler.schedule(LeitnerState.newWord(), Rating.FORGOT, now, ZoneId.of("UTC"));
        ScheduleResult hcm = scheduler.schedule(LeitnerState.newWord(), Rating.FORGOT, now, HCM);
        assertEquals(LocalDate.of(2026, 10, 27), due(utc, ZoneId.of("UTC")).toLocalDate());
        assertEquals(LocalDate.of(2026, 10, 28), due(hcm, HCM).toLocalDate());
    }

    @Test public void springForwardKeepsCalendarDays() {
        // DST starts 2026-03-08 in New York; that day has 23 hours.
        long now = at(LocalDateTime.of(2026, 3, 7, 22, 0), NEW_YORK);
        ScheduleResult r = scheduler.schedule(new LeitnerState(1, 0, false), Rating.REMEMBERED, now, NEW_YORK);
        assertEquals(LocalDate.of(2026, 3, 9), due(r, NEW_YORK).toLocalDate());
        assertEquals(LocalTime.MIDNIGHT, due(r, NEW_YORK).toLocalTime());
    }

    @Test public void fallBackKeepsCalendarDays() {
        // DST ends 2026-11-01 in New York; that day has 25 hours.
        long now = at(LocalDateTime.of(2026, 10, 31, 23, 30), NEW_YORK);
        ScheduleResult r = scheduler.schedule(LeitnerState.newWord(), Rating.FORGOT, now, NEW_YORK);
        assertEquals(LocalDate.of(2026, 11, 1), due(r, NEW_YORK).toLocalDate());
        assertEquals(LocalTime.MIDNIGHT, due(r, NEW_YORK).toLocalTime());
    }

    @Test public void wordDueTodayIsDueAtAnyTimeThatDay() {
        long reviewedAt = at(LocalDateTime.of(2026, 10, 26, 21, 0), HCM);
        ScheduleResult r = scheduler.schedule(LeitnerState.newWord(), Rating.FORGOT, reviewedAt, HCM);
        long nextMorning = at(LocalDateTime.of(2026, 10, 27, 6, 0), HCM);
        assertTrue(r.getDueAtEpochMillis() <= nextMorning);
        assertTrue(r.getDueAtEpochMillis() > reviewedAt);
    }

    @Test public void mistakeCounterDoesNotOverflow() {
        ScheduleResult r = scheduler.schedule(new LeitnerState(0, Integer.MAX_VALUE, true), Rating.FORGOT,
                at(LocalDateTime.of(2026, 10, 26, 9, 0), HCM), HCM);
        assertEquals(Integer.MAX_VALUE, r.getConsecutiveMistakes());
        assertTrue(r.isLeech());
    }

    @Test public void intervalTableMatchesPlanAndRejectsInvalidBox() {
        int[] expected = {1, 1, 2, 4, 7, 14};
        for (int box = 0; box <= 5; box++) assertEquals(expected[box], LeitnerScheduler.intervalDays(box));
        assertThrows(IllegalArgumentException.class, () -> LeitnerScheduler.intervalDays(-1));
        assertThrows(IllegalArgumentException.class, () -> LeitnerScheduler.intervalDays(6));
    }
}
