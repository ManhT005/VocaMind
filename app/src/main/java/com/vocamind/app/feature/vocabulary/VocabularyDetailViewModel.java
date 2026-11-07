package com.vocamind.app.feature.vocabulary;

import androidx.lifecycle.*;
import com.vocamind.app.domain.common.*;
import com.vocamind.app.domain.vocabulary.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Immutable route per Fragment. Retry reconciles persisted state, never replays a command. */
public final class VocabularyDetailViewModel extends ViewModel {
    public static final class State {
        public final VocabularyDetail detail;
        public final boolean loading, pending;
        public final RepoError error;
        public State(VocabularyDetail detail, boolean loading, boolean pending, RepoError error) {
            this.detail = detail; this.loading = loading; this.pending = pending; this.error = error;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final Supplier<RepoResult<VocabularyDetail>> read;
    private final java.util.function.Function<Boolean, RepoResult<Boolean>> write;
    private final Executor worker, main;
    private final Runnable close;
    private long generation;
    private boolean cleared;
    public VocabularyDetailViewModel(Supplier<RepoResult<VocabularyDetail>> read,
            java.util.function.Function<Boolean, RepoResult<Boolean>> write,
            Executor worker, Executor main, Runnable close) {
        this.read = read; this.write = write; this.worker = worker; this.main = main; this.close = close;
        refresh();
    }
    public LiveData<State> state() { return state; }
    public void refresh() {
        if (cleared || (state.getValue() != null && state.getValue().pending)) return;
        long ticket = ++generation;
        state.setValue(new State(null, true, false, null));
        worker.execute(() -> {
            RepoResult<VocabularyDetail> result;
            try { result = read.get(); } catch (RuntimeException e) { result = RepoResult.failure(RepoError.READ_FAILED); }
            var completed = result;
            main.execute(() -> {
                if (cleared || ticket != generation) return;
                state.setValue(completed.isSuccess() ? new State(completed.get(), false, false, null)
                        : new State(null, false, false, completed.getError()));
            });
        });
    }
    public void toggleSaved() {
        State previous = state.getValue();
        if (cleared || previous == null || previous.loading || previous.pending || previous.error != null || previous.detail == null) return;
        boolean target = !previous.detail.saved;
        long ticket = ++generation;
        state.setValue(new State(previous.detail, false, true, null));
        worker.execute(() -> {
            RepoResult<Boolean> result;
            try { result = write.apply(target); } catch (RuntimeException e) { result = RepoResult.failure(RepoError.WRITE_FAILED); }
            var completed = result;
            main.execute(() -> {
                if (cleared || ticket != generation) return;
                state.setValue(completed.isSuccess()
                        ? new State(new VocabularyDetail(previous.detail.word, completed.get()), false, false, null)
                        : new State(previous.detail, false, false, completed.getError()));
            });
        });
    }
    @Override protected void onCleared() { cleared = true; ++generation; worker.execute(close); }
}
