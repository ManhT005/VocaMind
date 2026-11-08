package com.vocamind.app.fake;

import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.DailyQuota;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.learning.StudyRepository;
import com.vocamind.app.domain.learning.WordProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory StudyRepository for ViewModel/domain tests. Set {@code failure} to simulate errors. */
public final class FakeStudyRepository implements StudyRepository {
    public final List<StudyItem> due = new ArrayList<>();
    public final List<CatalogVocabulary> fresh = new ArrayList<>();
    public final List<WordProgress> progress = new ArrayList<>();
    public RepoError failure;
    public int dailyNewWords = 5;
    public int learnedToday;
    public int calls;

    @Override public RepoResult<List<StudyItem>> dueItems(long now, int limit) {
        calls++;
        if (failure != null) return RepoResult.failure(failure);
        if (limit <= 0) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        List<StudyItem> out = new ArrayList<>();
        for (StudyItem item : due) {
            if (out.size() == limit) break;
            if (item.getProgress() != null && item.getProgress().getDueAtEpochMillis() <= now) out.add(item);
        }
        return RepoResult.success(out);
    }

    @Override public RepoResult<List<CatalogVocabulary>> newWords(int limit) {
        calls++;
        if (failure != null) return RepoResult.failure(failure);
        if (limit <= 0) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepoResult.success(new ArrayList<>(fresh.subList(0, Math.min(limit, fresh.size()))));
    }

    @Override public RepoResult<DailyQuota> dailyQuota(long dayStart, long nextDayStart) {
        calls++;
        if (failure != null) return RepoResult.failure(failure);
        return RepoResult.success(new DailyQuota(dailyNewWords, learnedToday));
    }

    @Override public RepoResult<Optional<WordProgress>> getProgress(String id) {
        calls++;
        if (failure != null) return RepoResult.failure(failure);
        for (WordProgress p : progress) if (p.getVocabularyId().equals(id)) return RepoResult.success(Optional.of(p));
        return RepoResult.success(Optional.empty());
    }
}
