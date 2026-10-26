package com.vocamind.app.domain.learning;

/** Scheduling state of one word before a review. Immutable. */
public final class LeitnerState {
    private final int box;
    private final int consecutiveMistakes;
    private final boolean leech;

    public LeitnerState(int box, int consecutiveMistakes, boolean leech) {
        if (box < LeitnerScheduler.MIN_BOX || box > LeitnerScheduler.MAX_BOX) {
            throw new IllegalArgumentException("box must be 0..5");
        }
        if (consecutiveMistakes < 0) {
            throw new IllegalArgumentException("consecutiveMistakes must be >= 0");
        }
        this.box = box;
        this.consecutiveMistakes = consecutiveMistakes;
        this.leech = leech;
    }

    /** State of a word that has never been reviewed. */
    public static LeitnerState newWord() {
        return new LeitnerState(LeitnerScheduler.MIN_BOX, 0, false);
    }

    public int getBox() { return box; }
    public int getConsecutiveMistakes() { return consecutiveMistakes; }
    public boolean isLeech() { return leech; }
}
