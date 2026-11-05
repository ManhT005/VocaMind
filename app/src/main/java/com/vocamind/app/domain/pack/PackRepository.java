package com.vocamind.app.domain.pack;

import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.StudyItem;
import java.util.List;

/** Merges catalog packs with user state by packId. Blocking: call from a worker executor. */
public interface PackRepository {
    /** All catalog packs in catalog order, with activation and learned count. */
    RepoResult<List<PackState>> getPackStates();

    /** NOT_FOUND if the catalog has no such pack. Keeps the first activation time on reactivation. */
    RepoResult<PackState> activatePack(String packId, long nowEpochMillis);

    /** Pauses an active pack. Returns true if state changed; progress is kept. */
    RepoResult<Boolean> pausePack(String packId);

    /**
     * Words of one pack in PackWord.orderNo order, each joined to its progress by stableId
     * (progress is null for a never-reviewed word). NOT_FOUND if the catalog has no such pack.
     */
    RepoResult<List<StudyItem>> getPackWords(String packId);
}
