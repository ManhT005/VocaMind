package com.vocamind.app.feature.packs;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.common.RepoError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Loads a list off the main thread; exposes LOADING / CONTENT / EMPTY / ERROR. */
public class AsyncListViewModel<T> extends ViewModel {
    public enum Status { LOADING, CONTENT, EMPTY, ERROR }

    public static final class State<T> {
        public final Status status;
        public final List<T> items;
        public final boolean pending;
        public final RepoError error;

        private State(Status status, List<T> items) {
            this(status, items, false, null);
        }

        private State(Status status, List<T> items, boolean pending, RepoError error) {
            this.pending = pending;
            this.error = error;
            this.status = status;
            List<T> copy = new ArrayList<>(items);
            copy.forEach(Objects::requireNonNull);
            this.items = Collections.unmodifiableList(copy);
        }
    }

    private final MutableLiveData<State<T>> state = new MutableLiveData<>();
    private final Supplier<RepoResult<List<T>>> query;
    private final Executor worker;
    private final Executor main;
    private final Runnable close;
    private boolean loading;
    private boolean cleared;
    private boolean writing;
    private long generation;

    protected AsyncListViewModel(Supplier<RepoResult<List<T>>> query, Executor worker, Executor main, Runnable close) {
        this.query = query;
        this.worker = worker;
        this.main = main;
        this.close = close;
        retry();
    }

    public LiveData<State<T>> state() { return state; }

    /** Main thread only; ordinary overlapping retries are ignored. */
    public void retry() {
        if (loading || writing || cleared) return;
        load(query, false);
    }

    /** Lifecycle refresh supersedes old reads, including results waiting on the main queue. */
    public void refresh() {
        if (writing || cleared) return; // write already includes a fresh query
        load(query, false);
    }

    protected final void mutate(Supplier<RepoResult<List<T>>> operation) {
        if (writing || cleared) return;
        load(operation, true);
    }

    private void load(Supplier<RepoResult<List<T>>> operation, boolean write) {
        long ticket = ++generation;
        writing = write;
        loading = true;
        List<T> previous = state.getValue() == null ? Collections.emptyList() : state.getValue().items;
        state.setValue(new State<>(Status.LOADING, previous, write, null));
        worker.execute(() -> {
            RepoResult<List<T>> result;
            // Throwable, not RuntimeException: an Error from storage must still reach the main
            // thread, otherwise the screen would stay on LOADING forever.
            try { result = operation.get(); }
            catch (Throwable failure) {
                result = RepoResult.failure(write ? RepoError.WRITE_FAILED : RepoError.READ_FAILED);
            }
            RepoResult<List<T>> completed = result;
            main.execute(() -> {
                if (cleared || ticket != generation) return;
                loading = false;
                writing = false;
                if (!completed.isSuccess()) {
                    state.setValue(new State<>(Status.ERROR, Collections.emptyList(), false, completed.getError()));
                } else {
                    List<T> items = completed.get();
                    state.setValue(new State<>(items.isEmpty() ? Status.EMPTY : Status.CONTENT, items));
                }
            });
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        worker.execute(close); // serial worker: runs after any in-flight read
    }
}
