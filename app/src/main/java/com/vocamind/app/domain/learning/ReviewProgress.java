package com.vocamind.app.domain.learning;

import java.util.Objects;
import java.util.Optional;

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

    public static ReviewProgress afterReview(String id, Optional<ReviewProgress> expected,
                                            ScheduleResult result, long reviewedAt) {
        ReviewProgress old = expected.orElse(null);
        if (old != null && !old.vocabularyId.equals(id))
            throw new IllegalArgumentException("Review stableId mismatch");
        return new ReviewProgress(id, result.getBoxAfter(), result.getDueAtEpochMillis(),
                old == null || old.learnedAt == null ? reviewedAt : old.learnedAt,
                reviewedAt, result.getConsecutiveMistakes(), result.isLeech(),
                old == null ? 1 : Math.addExact(old.reviewCount, 1), old != null && old.isSaved);
    }

    public static ReviewProgress fromWordProgress(WordProgress p) {
        return new ReviewProgress(p.getVocabularyId(), p.getBox(), p.getDueAtEpochMillis(),
                p.getLearnedAtEpochMillis(), p.getLastReviewedAtEpochMillis(),
                p.getConsecutiveMistakes(), p.isLeech(), p.getReviewCount(), p.isSaved());
    }

    public WordProgress toWordProgress() {
        return new WordProgress(vocabularyId, box, dueAt, learnedAt, lastReviewedAt,
                consecutiveMistakes, isLeech, reviewCount, isSaved);
    }

    @Override public boolean equals(Object value) {
        if (!(value instanceof ReviewProgress)) return false;
        ReviewProgress p = (ReviewProgress) value;
        return vocabularyId.equals(p.vocabularyId) && box == p.box && dueAt == p.dueAt
                && Objects.equals(learnedAt, p.learnedAt) && Objects.equals(lastReviewedAt, p.lastReviewedAt)
                && consecutiveMistakes == p.consecutiveMistakes && isLeech == p.isLeech
                && reviewCount == p.reviewCount && isSaved == p.isSaved;
    }
    @Override public int hashCode() {
        return Objects.hash(vocabularyId, box, dueAt, learnedAt, lastReviewedAt,
                consecutiveMistakes, isLeech, reviewCount, isSaved);
    }
}
