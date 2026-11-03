package com.vocamind.app.domain.learning;

import java.util.Optional;

/** Blocking worker-thread contract, no Android/Room types. IDs must already be valid catalog words. */
public interface ReviewRepository {
    Optional<ReviewProgress> getProgress(String vocabularyId);
    /** Check expected state and write one event + updated progress atomically.
     * Throw CONFLICT on a stale snapshot; throw STORAGE on DB failure. No automatic retry. */
    void recordReview(Optional<ReviewProgress> expected, ReviewProgress updated,
                      Rating rating, long reviewedAt);
}
