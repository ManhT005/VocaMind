package com.vocamind.app.domain.learning;

/** The user's new-word target for one calendar day and how many words were first learned in it. */
public final class DailyQuota {
    private final int dailyNewWords;
    private final int learnedToday;

    public DailyQuota(int dailyNewWords, int learnedToday) {
        if (dailyNewWords <= 0) throw new IllegalArgumentException("dailyNewWords must be > 0");
        if (learnedToday < 0) throw new IllegalArgumentException("learnedToday must be >= 0");
        this.dailyNewWords = dailyNewWords;
        this.learnedToday = learnedToday;
    }

    public int getDailyNewWords() { return dailyNewWords; }
    public int getLearnedToday() { return learnedToday; }
    /** New words still allowed today; never negative. */
    public int remaining() { return Math.max(0, dailyNewWords - learnedToday); }
}
