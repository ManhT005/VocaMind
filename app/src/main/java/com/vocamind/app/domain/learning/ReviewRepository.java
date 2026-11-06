package com.vocamind.app.domain.learning;

import com.vocamind.app.domain.common.RepoResult;

/** Blocking write contract: call from a worker executor. */
public interface ReviewRepository {
    /**
     * Atomically stores the ReviewEvent and the updated progress of one word.
     * The word must exist in the catalog (NOT_FOUND otherwise) and result.getBoxBefore() must equal
     * the stored box (0 for a word without progress), else INVALID_ARGUMENT. On any failure
     * neither row changes. Returns the stored progress.
     */
    RepoResult<WordProgress> recordReview(String vocabularyId, ScheduleResult result, long reviewedAtEpochMillis);
}
