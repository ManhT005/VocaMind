package com.vocamind.app.feature.packs;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.vocamind.app.domain.common.RepoResult;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Loads a list off the main thread; exposes LOADING / CONTENT / EMPTY / ERROR. */
public class AsyncListViewModel<T> extends ViewModel {
    public enum Status { LOADING, CONTENT, EMPTY, ERROR }

    public static final class State<T> {
        public final Status status;
        public final List<T> items;

        private State(Status status, List<T> items) {
            this.status = status;
            this.items = List.copyOf(items);
        }
    }

    private final MutableLiveData<State<T>> state = new MutableLiveData<>();
    private final Supplier<RepoResult<List<T>>> query;
    private final Executor worker;
    private final Executor main;
    private final Runnable close;
    private boolean loading;
    private boolean cleared;

    protected AsyncListViewModel(Supplier<RepoResult<List<T>>> query, Executor worker, Executor main, Runnable close) {
        this.query = query;
        this.worker = worker;
        this.main = main;
        this.close = close;
        retry();
    }

    public LiveData<State<T>> state() { return state; }

    /** Main thread only; overlapping loads are ignored. */
    public void retry() {
        if (loading || cleared) return;
        loading = true;
        state.setValue(new State<>(Status.LOADING, List.of()));
        worker.execute(() -> {
            State<T> result;
            try {
                RepoResult<List<T>> r = query.get();
                result = !r.isSuccess() ? new State<>(Status.ERROR, List.of())
                        : new State<>(r.get().isEmpty() ? Status.EMPTY : Status.CONTENT, r.get());
            } catch (RuntimeException failure) {
                result = new State<>(Status.ERROR, List.of());
            }
            State<T> completed = result;
            main.execute(() -> {
                if (cleared) return;
                loading = false;
                state.setValue(completed);
            });
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        worker.execute(close); // serial worker: runs after any in-flight read
    }
}
