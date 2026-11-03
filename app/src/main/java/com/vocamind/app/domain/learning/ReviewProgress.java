package com.vocamind.app.domain.learning;

/** Immutable persisted review snapshot. Nullable timestamps represent no learning history. */
public final class ReviewProgress {
    public final String vocabularyId;
    public final int box;
    public final long dueAt;
    public final Long learnedAt;
    public final Long lastReviewedAt;
    public final int consecutiveMistakes;
    public final boolean isLeech;
    public final int reviewCount;
    public final boolean isSaved;

    public ReviewProgress(String vocabularyId, int box, long dueAt, Long learnedAt,
                          Long lastReviewedAt, int consecutiveMistakes, boolean isLeech,
                          int reviewCount, boolean isSaved) {
        requireId(vocabularyId);
        if (box < 0 || box > 5 || consecutiveMistakes < 0 || reviewCount < 0)
            throw new IllegalArgumentException("Invalid progress state");
        this.vocabularyId = vocabularyId; this.box = box; this.dueAt = dueAt;
        this.learnedAt = learnedAt; this.lastReviewedAt = lastReviewedAt;
        this.consecutiveMistakes = consecutiveMistakes; this.isLeech = isLeech;
        this.reviewCount = reviewCount; this.isSaved = isSaved;
    }
    public static void requireId(String id) {
        if (id == null || !id.matches("[0-9a-f]{16}"))
            throw new IllegalArgumentException("Expected canonical stableId (16 lowercase hex)");
    }
    public LeitnerState schedulingState() { return new LeitnerState(box, consecutiveMistakes, isLeech); }
}
