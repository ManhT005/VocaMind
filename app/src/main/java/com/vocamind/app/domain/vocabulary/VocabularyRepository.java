package com.vocamind.app.domain.vocabulary;

import com.vocamind.app.domain.common.RepoResult;

/** Blocking local I/O. Pack routes validate membership; explicit catalog routes need only stableId. */
public interface VocabularyRepository {
    RepoResult<VocabularyDetail> getDetail(String packId, String stableId);
    default RepoResult<VocabularyDetail> getCatalogDetail(String stableId) {
        return RepoResult.failure(com.vocamind.app.domain.common.RepoError.INVALID_ARGUMENT);
    }
    /** Idempotent target command. First-save creates due-now progress with no learning timestamps.
     * Success confirms a committed flag, or verified missing progress for an unsave no-op.
     */
    RepoResult<Boolean> setSaved(String packId, String stableId, boolean saved);
    default RepoResult<Boolean> setCatalogSaved(String stableId, boolean saved) {
        return RepoResult.failure(com.vocamind.app.domain.common.RepoError.INVALID_ARGUMENT);
    }
}
