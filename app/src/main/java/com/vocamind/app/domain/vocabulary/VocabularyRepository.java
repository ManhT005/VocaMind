package com.vocamind.app.domain.vocabulary;

import com.vocamind.app.domain.common.RepoResult;

/** Blocking local I/O. IDs are opaque TEXT; pack membership is required for this route. */
public interface VocabularyRepository {
    RepoResult<VocabularyDetail> getDetail(String packId, String stableId);
    /** Idempotent target command. Success confirms a committed flag, never UPDATE 0. */
    RepoResult<Boolean> setSaved(String packId, String stableId, boolean saved);
}
