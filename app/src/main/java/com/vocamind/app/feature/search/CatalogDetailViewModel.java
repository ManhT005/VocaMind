package com.vocamind.app.feature.search;

import androidx.lifecycle.*;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

/** Minimal read-only VOCAB-02 receiver. Full detail/save remains with VOCAB-02. */
public final class CatalogDetailViewModel extends ViewModel {
    public enum Status { LOADING, FOUND, NOT_FOUND, ERROR }
    public static final class State {
        public final Status status;
        public final CatalogVocabulary word;
        State(Status status, CatalogVocabulary word) { this.status = status; this.word = word; }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private boolean disposed;
    public CatalogDetailViewModel(String id,
            Function<String, CompletableFuture<Optional<CatalogVocabulary>>> lookup, Executor main) {
        state.setValue(new State(Status.LOADING, null));
        if (id == null || !id.matches("[0-9a-f]{16}")) {
            state.setValue(new State(Status.NOT_FOUND, null)); return;
        }
        try {
            lookup.apply(id).whenComplete((word, error) -> main.execute(() -> {
                if (!disposed) state.setValue(error != null || word == null ? new State(Status.ERROR, null)
                        : word.isPresent() ? new State(Status.FOUND, word.get()) : new State(Status.NOT_FOUND, null));
            }));
        } catch (RuntimeException failure) { state.setValue(new State(Status.ERROR, null)); }
    }
    public LiveData<State> state() { return state; }
    @Override protected void onCleared() { disposed = true; }
}
