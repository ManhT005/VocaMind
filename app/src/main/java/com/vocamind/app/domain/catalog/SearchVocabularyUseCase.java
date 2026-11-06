package com.vocamind.app.domain.catalog;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.*;

/** May be invoked from UI: database work is submitted to the injected background executor.
 * Debounce and request-generation/stale-result guards belong to SEARCH-01's ViewModel. */
public final class SearchVocabularyUseCase {
    private final VocabularySearchRepository repository;
    private final Executor worker;
    public SearchVocabularyUseCase(VocabularySearchRepository repository, Executor worker) {
        this.repository = Objects.requireNonNull(repository); this.worker = Objects.requireNonNull(worker);
    }
    public CompletableFuture<VocabularySearchResult> execute(String input) {
        final VocabularySearchQuery query;
        try { query = VocabularySearchQuery.parse(input); }
        catch (IllegalArgumentException invalid) {
            return CompletableFuture.completedFuture(VocabularySearchResult.failure(input == null ? "" : input,
                    VocabularySearchResult.Error.INVALID_QUERY));
        }
        if (query.isEmpty()) return CompletableFuture.completedFuture(
                VocabularySearchResult.success(query.getText(), Collections.emptyList()));
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return Objects.requireNonNull(repository.search(query)); }
                catch (RuntimeException failure) {
                    return VocabularySearchResult.failure(query.getText(), VocabularySearchResult.Error.READ_FAILED);
                }
            }, worker);
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(VocabularySearchResult.failure(query.getText(),
                    VocabularySearchResult.Error.EXECUTOR_UNAVAILABLE));
        }
    }
}
