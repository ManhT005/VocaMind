package com.vocamind.app.domain.learning;

import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoResult;
import java.util.List;
import java.util.Optional;

/**
 * Study queue over words of ACTIVE packs only. Blocking: call from a worker executor.
 * The use case computes quota and "now"; limit must be positive (quota 0 is handled by the caller).
 */
public interface StudyRepository {
    /** Due words (dueAt &lt;= now), ordered dueAt ASC then stableId ASC, at most limit. */
    RepoResult<List<StudyItem>> dueItems(long nowEpochMillis, int limit);

    /** Never-reviewed words in pack order, no duplicates across packs, at most limit. */
    RepoResult<List<CatalogVocabulary>> newWords(int limit);

    /** Daily new-word target and words first learned in [dayStart, nextDayStart). */
    RepoResult<DailyQuota> dailyQuota(long dayStartEpochMillis, long nextDayStartEpochMillis);

    RepoResult<Optional<WordProgress>> getProgress(String vocabularyId);
}
