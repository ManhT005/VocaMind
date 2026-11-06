package com.vocamind.app.domain.learning;

import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import java.util.Optional;

/** Blocking worker contract. One catalog-validated, optimistic persistence path. */
public interface ReviewRepository {
    /** Unknown catalog ID is NOT_FOUND, not an empty progress snapshot. */
    Optional<ReviewProgress> getProgress(String vocabularyId);

    /** Compare the entire expected snapshot and commit event + progress atomically.
     * CONFLICT requires a reload and a new user decision; never retry a rating automatically. */
    void recordReview(Optional<ReviewProgress> expected, ReviewProgress updated,
                      Rating rating, long reviewedAt);

    /**
     * DATA-04 compatibility adapter through the same compare-and-commit primitive.
     * Captures its snapshot on entry; rejects mismatched box and replayed timestamps.
     * Callers needing protection from changes since snapshot capture must use the explicit
     * snapshot API (or RecordReviewUseCase), rather than reuse a precomputed result.
     */
    @Deprecated
    default RepoResult<WordProgress> recordReview(String vocabularyId, ScheduleResult result, long reviewedAt) {
        try {
            ReviewProgress.requireId(vocabularyId);
            if (result == null || reviewedAt < 0) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
            Optional<ReviewProgress> expected = getProgress(vocabularyId);
            if (expected.map(p -> p.box).orElse(0) != result.getBoxBefore())
                return RepoResult.failure(RepoError.INVALID_ARGUMENT);
            if (expected.isPresent() && expected.get().lastReviewedAt != null
                    && reviewedAt <= expected.get().lastReviewedAt)
                return RepoResult.failure(RepoError.CONFLICT);
            ReviewProgress updated = ReviewProgress.afterReview(vocabularyId, expected, result, reviewedAt);
            recordReview(expected, updated, result.getRating(), reviewedAt);
            return RepoResult.success(updated.toWordProgress());
        } catch (ReviewWriteException failure) {
            switch (failure.getReason()) {
                case NOT_FOUND: return RepoResult.failure(RepoError.NOT_FOUND);
                case READ_FAILED: return RepoResult.failure(RepoError.READ_FAILED);
                case INVALID_DATA: return RepoResult.failure(RepoError.INVALID_DATA);
                case CONFLICT: return RepoResult.failure(RepoError.CONFLICT);
                case STORAGE: return RepoResult.failure(RepoError.WRITE_FAILED);
                default: throw new AssertionError(failure.getReason());
            }
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        }
    }
}
