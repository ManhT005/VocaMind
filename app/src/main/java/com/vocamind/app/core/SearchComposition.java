package com.vocamind.app.core;

import android.content.Context;
import com.vocamind.app.domain.catalog.*;
import java.util.concurrent.*;

/** Process owner: initializes on worker, reuses DATA-05 provider, never opens userdata.
 * Features do not close this catalog while SQLite may still be reading it. */
public final class SearchComposition {
    private static SearchComposition instance;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CompletableFuture<SearchProvider> provider;
    private CatalogProvider catalog;
    private SearchComposition(Context context) {
        Context app = context.getApplicationContext();
        provider = CompletableFuture.supplyAsync(() -> {
            catalog = new CatalogProvider(app);
            return catalog.searchProvider();
        }, worker);
    }
    public static synchronized SearchComposition get(Context context) {
        if (instance == null) instance = new SearchComposition(context);
        return instance;
    }
    public CompletableFuture<VocabularySearchResult> search(String raw) {
        CompletableFuture<VocabularySearchResult> result = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<VocabularySearchResult>> inner =
                new java.util.concurrent.atomic.AtomicReference<>();
        result.whenComplete((r, e) -> {
            CompletableFuture<?> task = inner.get();
            if (result.isCancelled() && task != null) task.cancel(false);
        });
        provider.whenComplete((p, error) -> {
            if (result.isCancelled()) return;
            if (error != null) { result.completeExceptionally(error); return; }
            CompletableFuture<VocabularySearchResult> task = p.search().execute(raw);
            inner.set(task);
            if (result.isCancelled()) task.cancel(false);
            task.whenComplete((r, e) -> {
                if (e != null) result.completeExceptionally(e); else result.complete(r);
            });
        });
        return result;
    }
}
