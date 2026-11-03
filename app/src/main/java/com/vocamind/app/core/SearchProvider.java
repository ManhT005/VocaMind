package com.vocamind.app.core;

import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.catalog.RoomVocabularySearchRepository;
import com.vocamind.app.domain.catalog.SearchVocabularyUseCase;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns only its worker, not the application-owned catalog. Reuse per owner, not per keystroke. */
public final class SearchProvider implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "vocamind-search"));
    private final SearchVocabularyUseCase search;
    public SearchProvider(CatalogDatabase database) {
        search = new SearchVocabularyUseCase(new RoomVocabularySearchRepository(
                Objects.requireNonNull(database).catalogDao()), worker);
    }
    public SearchVocabularyUseCase search() { return search; }
    /** Gracefully finish queued reads; app database must outlive in-flight reads.
     * New requests return EXECUTOR_UNAVAILABLE. Owner must discard results after disposal. */
    @Override public void close() { worker.shutdown(); }
}
