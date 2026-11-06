package com.vocamind.app.data.catalog;

import androidx.annotation.WorkerThread;
import com.vocamind.app.domain.catalog.*;
import java.util.*;

@WorkerThread
public final class RoomVocabularySearchRepository implements VocabularySearchRepository {
    private final CatalogDao dao;
    public RoomVocabularySearchRepository(CatalogDao dao) { this.dao = Objects.requireNonNull(dao); }
    @Override public VocabularySearchResult search(VocabularySearchQuery query) {
        Objects.requireNonNull(query);
        if (query.isEmpty()) return VocabularySearchResult.success(query.getText(), Collections.emptyList());
        final List<com.vocamind.app.data.catalog.entity.VocabularyEntry> rows;
        try { rows = dao.searchVocabulary(FtsMatchExpression.from(query)); }
        catch (RuntimeException failure) {
            return VocabularySearchResult.failure(query.getText(), VocabularySearchResult.Error.READ_FAILED);
        }
        try {
            List<CatalogVocabulary> words = new ArrayList<>();
            for (var row : rows) words.add(CatalogMapper.vocabulary(row));
            return VocabularySearchResult.success(query.getText(), words);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return VocabularySearchResult.failure(query.getText(), VocabularySearchResult.Error.INVALID_DATA);
        }
    }
}
