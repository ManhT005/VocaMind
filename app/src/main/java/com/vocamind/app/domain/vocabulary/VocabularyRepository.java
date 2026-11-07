package com.vocamind.app.domain.vocabulary;

import com.vocamind.app.domain.common.RepoResult;

/** Blocking local I/O. IDs are opaque TEXT; pack membership is required for this route. */
public interface VocabularyRepository {
    RepoResult<VocabularyDetail> getDetail(String packId, String stableId);
    /** Idempotent target command. First-save creates due-now progress with no learning timestamps.
     * Success confirms a committed flag, or verified missing progress for an unsave no-op.
     */
    RepoResult<Boolean> setSaved(String packId, String stableId, boolean saved);
}
