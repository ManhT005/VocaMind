package com.vocamind.app.domain.learning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Pure Leitner scheduler: box 0..5, due dates on local calendar days, leech after
 * {@link #LEECH_THRESHOLD} FORGOT ratings in a row.
 *
 * <p>Intervals follow the common 1-3-7-14-30 day Leitner sequence; box 0 (FORGOT) is due
 * tomorrow. See docs/plans/learn-01-leitner-scheduler.md.
 */
public final class LeitnerScheduler {
    public static final int MIN_BOX = 0;
    public static final int MAX_BOX = 5;
    public static final int LEECH_THRESHOLD = 4;

    /** Days until the next review, indexed by the box after the rating. */
    private static final int[] INTERVAL_DAYS = {1, 1, 3, 7, 14, 30};

    /** Days until the next review for a word that is now in {@code box}. */
    public static int intervalDays(int box) {
        if (box < MIN_BOX || box > MAX_BOX) throw new IllegalArgumentException("box must be 0..5");
        return INTERVAL_DAYS[box];
    }

    public ScheduleResult schedule(LeitnerState state, Rating rating, long nowEpochMillis, ZoneId zone) {
        if (state == null) throw new IllegalArgumentException("state");
        if (rating == null) throw new IllegalArgumentException("rating");
        if (zone == null) throw new IllegalArgumentException("zone");

        int boxBefore = state.getBox();
        int boxAfter;
        int mistakes;
        boolean leech;
        switch (rating) {
            case REMEMBERED:
                boxAfter = Math.min(boxBefore + 1, MAX_BOX);
                mistakes = 0;
                leech = false;
                break;
            case HARD:
                boxAfter = boxBefore;
                mistakes = 0;
                leech = false;
                break;
            case FORGOT:
                boxAfter = MIN_BOX;
                mistakes = saturatingIncrement(state.getConsecutiveMistakes());
                leech = state.isLeech() || mistakes >= LEECH_THRESHOLD;
                break;
            default:
                throw new IllegalArgumentException("rating");
        }

        LocalDate today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate();
        long dueAt = today.plusDays(intervalDays(boxAfter))
                .atStartOfDay(zone).toInstant().toEpochMilli();
        return new ScheduleResult(rating, boxBefore, boxAfter, dueAt, mistakes, state.isLeech(), leech);
    }

    private static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }
}
