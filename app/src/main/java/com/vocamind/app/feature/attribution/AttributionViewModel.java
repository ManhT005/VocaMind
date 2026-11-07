package com.vocamind.app.feature.attribution;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.vocamind.app.domain.catalog.CatalogSource;
import com.vocamind.app.domain.catalog.GetContentSources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.Executor;

public final class AttributionViewModel extends ViewModel {
    public enum Status { LOADING, CONTENT, EMPTY, ERROR }

    public static final class State {
        public final Status status;
        public final List<CatalogSource> sources;

        private State(Status status, List<CatalogSource> sources) {
            this.status = status;
            List<CatalogSource> copy = new ArrayList<>(sources);
            copy.forEach(Objects::requireNonNull);
            this.sources = Collections.unmodifiableList(copy);
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final GetContentSources getSources;
    private final Executor worker;
    private final Executor main;
    private final Runnable close;
    private boolean loading;
    private boolean cleared;

    public AttributionViewModel(GetContentSources getSources, Executor worker,
                                Executor main, Runnable close) {
        this.getSources = getSources;
        this.worker = worker;
        this.main = main;
        this.close = close;
        retry();
    }

    public LiveData<State> state() { return state; }

    /** Called on the main thread; duplicate taps cannot enqueue overlapping reads. */
    public void retry() {
        if (loading || cleared) return;
        loading = true;
        state.setValue(new State(Status.LOADING, Collections.emptyList()));
        worker.execute(() -> {
            State result;
            try {
                List<CatalogSource> sources = getSources.execute();
                result = new State(sources.isEmpty() ? Status.EMPTY : Status.CONTENT, sources);
            } catch (RuntimeException failure) {
                result = new State(Status.ERROR, Collections.emptyList());
            }
            State completed = result;
            main.execute(() -> {
                if (cleared) return;
                loading = false;
                state.setValue(completed);
            });
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        // Production uses one serial worker: close runs after any in-flight read.
        worker.execute(close);
    }
}
