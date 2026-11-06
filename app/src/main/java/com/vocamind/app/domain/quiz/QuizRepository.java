package com.vocamind.app.domain.quiz;

import com.vocamind.app.domain.common.RepoResult;
import java.util.List;

/** Blocking contract: call from a worker executor. */
public interface QuizRepository {
    /** Fresh active vocabulary with validated catalog policy. Missing/invalid policy is INVALID_DATA. */
    RepoResult<QuizSnapshot> activeSnapshot();

    /** Distinct words of ACTIVE packs in pack order; INVALID_DATA if a stable ID is not 16 hex. */
    RepoResult<List<QuizVocabulary>> activeVocabulary();

    /** Persists one completed attempt and returns its ID. Abandoned sessions are never saved. */
    RepoResult<Long> saveAttempt(QuizResult result, long completedAtEpochMillis);
}
