package com.vocamind.app.domain.learning;

/** Immutable per-word learning progress, keyed by catalog stable ID. */
public final class WordProgress {
    private final String vocabularyId;
    private final int box;
    private final long dueAtEpochMillis;
    private final Long learnedAtEpochMillis;
    private final Long lastReviewedAtEpochMillis;
    private final int consecutiveMistakes;
    private final boolean leech;
    private final int reviewCount;
    private final boolean saved;

    public WordProgress(String vocabularyId, int box, long dueAtEpochMillis, Long learnedAtEpochMillis,
                        Long lastReviewedAtEpochMillis, int consecutiveMistakes, boolean leech,
                        int reviewCount, boolean saved) {
        if (vocabularyId == null || vocabularyId.trim().isEmpty()) throw new IllegalArgumentException("vocabularyId");
        if (box < LeitnerScheduler.MIN_BOX || box > LeitnerScheduler.MAX_BOX) throw new IllegalArgumentException("box must be 0..5");
        if (consecutiveMistakes < 0 || reviewCount < 0) throw new IllegalArgumentException("counters must be >= 0");
        this.vocabularyId = vocabularyId;
        this.box = box;
        this.dueAtEpochMillis = dueAtEpochMillis;
        this.learnedAtEpochMillis = learnedAtEpochMillis;
        this.lastReviewedAtEpochMillis = lastReviewedAtEpochMillis;
        this.consecutiveMistakes = consecutiveMistakes;
        this.leech = leech;
        this.reviewCount = reviewCount;
        this.saved = saved;
    }

    public String getVocabularyId() { return vocabularyId; }
    public int getBox() { return box; }
    public long getDueAtEpochMillis() { return dueAtEpochMillis; }
    /** Null until the first review. */
    public Long getLearnedAtEpochMillis() { return learnedAtEpochMillis; }
    public Long getLastReviewedAtEpochMillis() { return lastReviewedAtEpochMillis; }
    public int getConsecutiveMistakes() { return consecutiveMistakes; }
    public boolean isLeech() { return leech; }
    public int getReviewCount() { return reviewCount; }
    public boolean isSaved() { return saved; }

    /** Input for {@link LeitnerScheduler#schedule}. */
    public LeitnerState toState() { return new LeitnerState(box, consecutiveMistakes, leech); }
}
