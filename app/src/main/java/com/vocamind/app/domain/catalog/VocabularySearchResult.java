package com.vocamind.app.domain.catalog;

import java.util.*;

/** Immutable domain search response. Empty success is different from a failed read. */
public final class VocabularySearchResult {
    public enum Error { INVALID_QUERY, READ_FAILED, INVALID_DATA, EXECUTOR_UNAVAILABLE }
    private final String query;
    private final List<CatalogVocabulary> words;
    private final Error error;
    private VocabularySearchResult(String query, List<CatalogVocabulary> words, Error error) {
        this.query = query; this.words = words; this.error = error;
    }
    public static VocabularySearchResult success(String query, List<CatalogVocabulary> words) {
        Objects.requireNonNull(query); Objects.requireNonNull(words);
        Set<String> ids = new HashSet<>();
        for (CatalogVocabulary word : words) {
            if (word == null || word.stableId == null || !word.stableId.matches("[0-9a-f]{16}")
                    || !ids.add(word.stableId)) throw new IllegalArgumentException("Invalid search vocabulary IDs");
        }
        return new VocabularySearchResult(query, Collections.unmodifiableList(new ArrayList<>(words)), null);
    }
    public static VocabularySearchResult failure(String query, Error error) {
        return new VocabularySearchResult(Objects.requireNonNull(query), null, Objects.requireNonNull(error));
    }
    public String getQuery() { return query; }
    public boolean isSuccess() { return error == null; }
    public List<CatalogVocabulary> getWords() {
        if (!isSuccess()) throw new IllegalStateException("Failure has no result list");
        return words;
    }
    public Error getError() {
        if (isSuccess()) throw new IllegalStateException("Success has no error");
        return error;
    }
}
