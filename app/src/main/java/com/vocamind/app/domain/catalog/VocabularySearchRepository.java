package com.vocamind.app.domain.catalog;

/** Blocking read-only search, called by SearchVocabularyUseCase on its worker executor.
 * Return all matches as domain vocabulary/stableIds; never expose Room/FTS rowid. */
@FunctionalInterface
public interface VocabularySearchRepository {
    VocabularySearchResult search(VocabularySearchQuery query);
}
