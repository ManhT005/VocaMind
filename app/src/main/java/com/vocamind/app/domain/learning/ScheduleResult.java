package com.vocamind.app.domain.learning;

/** Outcome of scheduling one review. Immutable. */
public final class ScheduleResult {
    private final Rating rating;
    private final int boxBefore;
    private final int boxAfter;
    private final long dueAtEpochMillis;
    private final int consecutiveMistakes;
    private final boolean leechBefore;
    private final boolean leech;

    ScheduleResult(Rating rating, int boxBefore, int boxAfter, long dueAtEpochMillis,
                   int consecutiveMistakes, boolean leechBefore, boolean leech) {
        this.rating = rating;
        this.boxBefore = boxBefore;
        this.boxAfter = boxAfter;
        this.dueAtEpochMillis = dueAtEpochMillis;
        this.consecutiveMistakes = consecutiveMistakes;
        this.leechBefore = leechBefore;
        this.leech = leech;
    }

    public Rating getRating() { return rating; }
    public int getBoxBefore() { return boxBefore; }
    public int getBoxAfter() { return boxAfter; }
    /** Start of the local calendar day when the word is due again, in epoch milliseconds. */
    public long getDueAtEpochMillis() { return dueAtEpochMillis; }
    public int getConsecutiveMistakes() { return consecutiveMistakes; }
    public boolean isLeech() { return leech; }
    /** True only for the review that turned the leech flag on. */
    public boolean becameLeech() { return leech && !leechBefore; }

    /** The state to use for the next review of this word. */
    public LeitnerState toState() {
        return new LeitnerState(boxAfter, consecutiveMistakes, leech);
    }
}
