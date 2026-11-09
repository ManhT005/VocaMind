package com.vocamind.app.feature.search;

import androidx.lifecycle.*;
import com.vocamind.app.domain.catalog.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/** Main-thread state machine. Cancellation only optimizes queued work; generation ensures correctness. */
public final class SearchViewModel extends ViewModel {
    public static final long DEBOUNCE_MS = 600; // DATA-05 handoff, not an SRS duration.
    public enum Status { NO_INPUT, LOADING, CONTENT, NO_MATCH, ERROR }
    public interface Scheduler { Runnable after(long delay, Runnable task); }
    public static final class State {
        public final Status status;
        public final String input;
        public final long generation;
        public final List<CatalogVocabulary> words;
        public final VocabularySearchResult.Error error;
        State(Status status, String input, long generation, List<CatalogVocabulary> words, VocabularySearchResult.Error error) {
            this.status = status; this.input = input; this.generation = generation;
            this.words = List.copyOf(words); this.error = error;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final SavedStateHandle saved;
    private final Function<String, CompletableFuture<VocabularySearchResult>> search;
    private final Scheduler scheduler;
    private final Executor main;
    private Runnable cancel = () -> {};
    private CompletableFuture<VocabularySearchResult> running;
    private long generation;
    private boolean disposed;
    public SearchViewModel(SavedStateHandle saved, Function<String, CompletableFuture<VocabularySearchResult>> search,
            Scheduler scheduler, Executor main) {
        this.saved = saved; this.search = search; this.scheduler = scheduler; this.main = main;
        input(saved.get("search.input") == null ? "" : saved.get("search.input"));
    }
    public LiveData<State> state() { return state; }
    public void input(String input) {
        if (disposed) return;
        String raw = input == null ? "" : input;
        long ticket = ++generation;
        cancel.run();
        if (running != null) running.cancel(false); // SQLite may still be running.
        saved.set("search.input", raw);
        try {
            if (VocabularySearchQuery.parse(raw).isEmpty()) { emit(Status.NO_INPUT, raw, ticket, List.of(), null); return; }
        } catch (IllegalArgumentException invalid) {
            emit(Status.ERROR, raw, ticket, List.of(), VocabularySearchResult.Error.INVALID_QUERY); return;
        }
        emit(Status.LOADING, raw, ticket, List.of(), null);
        long changed = System.nanoTime();
        cancel = scheduler.after(DEBOUNCE_MS, () -> {
            trace(ticket, "debounce_ms", System.nanoTime() - changed);
            submit(raw, ticket);
        });
    }
    public void reset() { input(""); }
    public void retry() {
        State s = state.getValue();
        if (s != null && s.status == Status.ERROR && s.error != VocabularySearchResult.Error.INVALID_QUERY) input(s.input);
    }
    private void submit(String raw, long ticket) {
        if (disposed || ticket != generation) return;
        try {
            long submitted = System.nanoTime();
            running = search.apply(raw);
            running.whenComplete((result, failure) -> main.execute(() -> {
                if (disposed || ticket != generation) return;
                trace(ticket, "submit_to_main_ms", System.nanoTime() - submitted);
                if (failure != null || result == null) emit(Status.ERROR, raw, ticket, List.of(), VocabularySearchResult.Error.READ_FAILED);
                else if (!result.isSuccess()) emit(Status.ERROR, raw, ticket, List.of(), result.getError());
                else emit(result.getWords().isEmpty() ? Status.NO_MATCH : Status.CONTENT, raw, ticket, result.getWords(), null);
            }));
        } catch (RuntimeException failure) {
            main.execute(() -> {
                if (!disposed && ticket == generation) emit(Status.ERROR, raw, ticket, List.of(), VocabularySearchResult.Error.EXECUTOR_UNAVAILABLE);
            });
        }
    }
    private static void trace(long generation, String phase, long nanos) {
        if (com.vocamind.app.BuildConfig.DEBUG)
            android.util.Log.d("SearchTiming", "generation=" + generation + " " + phase + "=" + nanos / 1_000_000.0);
    }
    private void emit(Status status, String raw, long ticket, List<CatalogVocabulary> words, VocabularySearchResult.Error error) {
        state.setValue(new State(status, raw, ticket, words, error));
    }
    public boolean canOpen(long ticket, String id) {
        State s = state.getValue();
        return !disposed && s != null && s.status == Status.CONTENT && ticket == generation
                && s.words.stream().anyMatch(w -> w.stableId.equals(id));
    }
    @Override protected void onCleared() {
        disposed = true; ++generation; cancel.run();
        if (running != null) running.cancel(false);
        // Borrowed application resources outlive every in-flight read.
    }
}
