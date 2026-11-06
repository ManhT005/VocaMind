package com.vocamind.app.data.repository;

import androidx.annotation.WorkerThread;
import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.catalog.CatalogMapper;
import com.vocamind.app.data.catalog.entity.VocabularyEntry;
import com.vocamind.app.data.user.UserDao;
import com.vocamind.app.data.user.entity.UserWordProgress;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.learning.StudyRepository;
import com.vocamind.app.domain.learning.WordProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@WorkerThread
public final class RoomStudyRepository implements StudyRepository {
    private final CatalogDao catalog;
    private final UserDao user;
    private final ActiveCatalogScope scope;

    public RoomStudyRepository(CatalogDao catalog, UserDao user) {
        this.catalog = catalog;
        this.user = user;
        this.scope = new ActiveCatalogScope(catalog, user);
    }

    @Override public RepoResult<List<StudyItem>> dueItems(long now, int limit) {
        if (limit <= 0) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.read(() -> {
            List<String> eligible = scope.eligibleIds();
            if (eligible.isEmpty()) return RepoResult.success(Collections.<StudyItem>emptyList());
            List<UserWordProgress> due = user.dueProgress(eligible, now, limit);
            List<String> ids = new ArrayList<>();
            for (UserWordProgress p : due) ids.add(p.vocabularyId);
            Map<String, VocabularyEntry> words = new HashMap<>();
            for (VocabularyEntry v : catalog.vocabulariesByIds(ids)) words.put(v.id, v);
            List<StudyItem> out = new ArrayList<>();
            for (UserWordProgress p : due) {
                VocabularyEntry v = words.get(p.vocabularyId);
                if (v == null) continue; // eligible IDs come from the catalog; defensive only
                out.add(new StudyItem(CatalogMapper.vocabulary(v), UserDataMapper.progress(p)));
            }
            return RepoResult.success(Collections.unmodifiableList(out));
        });
    }

    @Override public RepoResult<List<CatalogVocabulary>> newWords(int limit) {
        if (limit <= 0) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.read(() -> {
            List<String> eligible = scope.eligibleIds();
            if (eligible.isEmpty()) return RepoResult.success(Collections.<CatalogVocabulary>emptyList());
            List<String> fresh = user.newVocabularyIds(eligible, limit);
            Map<String, VocabularyEntry> words = new HashMap<>();
            for (VocabularyEntry v : catalog.vocabulariesByIds(fresh)) words.put(v.id, v);
            List<CatalogVocabulary> out = new ArrayList<>();
            for (String id : fresh) {
                VocabularyEntry v = words.get(id);
                if (v != null) out.add(CatalogMapper.vocabulary(v));
            }
            return RepoResult.success(Collections.unmodifiableList(out));
        });
    }

    @Override public RepoResult<Optional<WordProgress>> getProgress(String vocabularyId) {
        if (vocabularyId == null || vocabularyId.trim().isEmpty()) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.read(() -> {
            UserWordProgress row = user.progress(vocabularyId);
            return RepoResult.success(Optional.ofNullable(row).map(UserDataMapper::progress));
        });
    }
}
