package com.vocamind.app.feature.today;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.TodayPlan;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Counts for the Today screen: due words, new words and whether anything is left to study. */
public final class TodayPlanViewModel extends ViewModel {
    public enum Status { LOADING, READY, DONE, NO_ACTIVE_PACK, ERROR }

    public static final class State {
        public final Status status;
        public final int dueCount;
        public final int newCount;

        State(Status status, int dueCount, int newCount) {
            this.status = status;
            this.dueCount = dueCount;
            this.newCount = newCount;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final Supplier<RepoResult<TodayPlan>> loader;
    private final Executor worker;
    private final Executor main;
    private final Runnable close;
    private boolean loading;
    private boolean cleared;

    public TodayPlanViewModel(Supplier<RepoResult<TodayPlan>> loader, Executor worker, Executor main, Runnable close) {
        this.loader = loader;
        this.worker = worker;
        this.main = main;
        this.close = close;
        retry();
    }

    public LiveData<State> state() { return state; }

    /** Main thread only. Shows LOADING first; overlapping loads are ignored. */
    public void retry() { load(true); }

    /** Main thread only. Reloads in place so the counts are fresh when returning from a session. */
    public void refresh() { load(false); }

    private void load(boolean showLoading) {
        if (loading || cleared) return;
        loading = true;
        if (showLoading || state.getValue() == null) state.setValue(new State(Status.LOADING, 0, 0));
        worker.execute(() -> {
            State result;
            try {
                RepoResult<TodayPlan> plan = loader.get();
                if (!plan.isSuccess()) {
                    result = new State(Status.ERROR, 0, 0);
                } else {
                    TodayPlan p = plan.get();
                    Status status = p.getStatus() == TodayPlan.Status.NO_ACTIVE_PACK ? Status.NO_ACTIVE_PACK
                            : p.getStatus() == TodayPlan.Status.DONE ? Status.DONE : Status.READY;
                    result = new State(status, p.dueCount(), p.newCount());
                }
            } catch (Throwable failure) {
                result = new State(Status.ERROR, 0, 0);
            }
            State completed = result;
            main.execute(() -> {
                loading = false;
                if (!cleared) state.setValue(completed);
            });
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        worker.execute(close); // serial worker: runs after any in-flight read
    }
}
