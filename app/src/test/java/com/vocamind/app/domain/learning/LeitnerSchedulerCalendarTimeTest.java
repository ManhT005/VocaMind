package com.vocamind.app.domain.learning;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** DEC-12: calendar-day arithmetic preserves local time, subject to zone gap/overlap rules. */
public class LeitnerSchedulerCalendarTimeTest {
    private final LeitnerScheduler scheduler = new LeitnerScheduler();
    private ZonedDateTime next(String local, String zoneName, LeitnerState state, Rating rating) {
        ZoneId zone = ZoneId.of(zoneName);
        Clock clock = Clock.fixed(LocalDateTime.parse(local).atZone(zone).toInstant(), zone);
        ScheduleResult result = scheduler.schedule(state, rating, clock.millis(), zone);
        return Instant.ofEpochMilli(result.getDueAtEpochMillis()).atZone(zone);
    }
    @Test public void rememberedAt1430KeepsHourMinuteSecondsAndMillis() {
        assertEquals(LocalDateTime.parse("2026-11-06T14:30:12.345"),
                next("2026-11-05T14:30:12.345", "Asia/Bangkok", LeitnerState.newWord(), Rating.REMEMBERED).toLocalDateTime());
    }
    @Test public void hardAt2355KeepsLocalTimeOnNextDay() {
        assertEquals(LocalDateTime.parse("2026-11-06T23:55"),
                next("2026-11-05T23:55", "Asia/Bangkok", LeitnerState.newWord(), Rating.HARD).toLocalDateTime());
    }
    @Test public void forgotAt0005KeepsLocalTimeOnNextDay() {
        assertEquals(LocalDateTime.parse("2026-11-06T00:05"),
                next("2026-11-05T00:05", "Asia/Bangkok", new LeitnerState(4, 0, false), Rating.FORGOT).toLocalDateTime());
    }
    @Test public void berlinSpringForwardIs23HoursButSameLocalTime() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZonedDateTime start = LocalDateTime.parse("2026-03-28T14:30").atZone(berlin);
        ZonedDateTime due = next("2026-03-28T14:30", "Europe/Berlin", LeitnerState.newWord(), Rating.HARD);
        assertEquals(LocalDateTime.parse("2026-03-29T14:30"), due.toLocalDateTime());
        assertEquals(23, Duration.between(start, due).toHours());
    }
    @Test public void berlinFallBackIs25HoursButSameLocalTime() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZonedDateTime start = LocalDateTime.parse("2026-10-24T14:30").atZone(berlin);
        ZonedDateTime due = next("2026-10-24T14:30", "Europe/Berlin", LeitnerState.newWord(), Rating.HARD);
        assertEquals(LocalDateTime.parse("2026-10-25T14:30"), due.toLocalDateTime());
        assertEquals(25, Duration.between(start, due).toHours());
    }
    @Test public void boxFiveRememberedKeepsTimeAfter30CalendarDays() {
        assertEquals(LocalDateTime.parse("2026-12-05T14:30"),
                next("2026-11-05T14:30", "Asia/Bangkok", new LeitnerState(5, 0, false), Rating.REMEMBERED).toLocalDateTime());
    }
    @Test public void nonexistentSpringTimeMovesForwardByTheGap() {
        ZonedDateTime due = next("2026-03-28T02:30", "Europe/Berlin", LeitnerState.newWord(), Rating.HARD);
        assertEquals(LocalDateTime.parse("2026-03-29T03:30"), due.toLocalDateTime());
        assertEquals(ZoneOffset.ofHours(2), due.getOffset());
    }
    @Test public void overlappingFallTimeRetainsPreviousValidOffset() {
        ZonedDateTime due = next("2026-10-24T02:30", "Europe/Berlin", LeitnerState.newWord(), Rating.HARD);
        assertEquals(LocalDateTime.parse("2026-10-25T02:30"), due.toLocalDateTime());
        assertEquals(ZoneOffset.ofHours(2), due.getOffset());
    }
}
