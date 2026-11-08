package com.vocamind.app.feature.flashcard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.learning.TodayPlan;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * UI state of one flashcard session: which card is shown and whether it is flipped.
 * It never computes scheduling. Persisting a rating is the job of the {@link RatingListener}
 * (FLASH-02); without one the card is simply advanced.
 * The ViewModel survives rotation, so card, face and position are kept.
 */
public final class FlashcardViewModel extends ViewModel {
    public enum Status { LOADING, READY, FINISHED, NO_ACTIVE_PACK, NOTHING_TO_STUDY, ERROR }

    /** Called on the main thread when the learner rates the flipped card. */
    public interface RatingListener { void onRated(StudyItem item, Rating rating); }

    public static final class State {
        public final Status status;
        public final StudyItem card;
        public final boolean flipped;
        /** 1-based position of the current card; 0 when there is no card. */
        public final int position;
        public final int total;

        State(Status status, StudyItem card, boolean flipped, int position, int total) {
            this.status = status;
            this.card = card;
            this.flipped = flipped;
            this.position = position;
            this.total = total;
        }

        /** Rating buttons are usable only while a card is shown with its answer. */
        public boolean ratingEnabled() { return status == Status.READY && flipped; }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final Supplier<RepoResult<TodayPlan>> loader;
    private final Executor worker;
    private final Executor main;
    private final Runnable close;
    private final RatingListener listener;
    private List<StudyItem> queue = Collections.emptyList();
    private int index;
    private boolean flipped;
    private boolean loading;
    private boolean cleared;

    public FlashcardViewModel(Supplier<RepoResult<TodayPlan>> loader, Executor worker, Executor main,
                              Runnable close, RatingListener listener) {
        this.loader = loader;
        this.worker = worker;
        this.main = main;
        this.close = close;
        this.listener = listener;
        retry();
    }

    public LiveData<State> state() { return state; }

    /** Main thread only. Loads (or reloads) the session queue; overlapping loads are ignored. */
    public void retry() {
        if (loading || cleared) return;
        loading = true;
        state.setValue(new State(Status.LOADING, null, false, 0, 0));
        worker.execute(() -> {
            State result;
            List<StudyItem> items = Collections.emptyList();
            try {
                RepoResult<TodayPlan> plan = loader.get();
                if (!plan.isSuccess()) {
                    result = new State(Status.ERROR, null, false, 0, 0);
                } else if (plan.get().getStatus() == TodayPlan.Status.NO_ACTIVE_PACK) {
                    result = new State(Status.NO_ACTIVE_PACK, null, false, 0, 0);
                } else if (plan.get().queue().isEmpty()) {
                    result = new State(Status.NOTHING_TO_STUDY, null, false, 0, 0);
                } else {
                    items = plan.get().queue();
                    result = null;
                }
            } catch (Throwable failure) {
                result = new State(Status.ERROR, null, false, 0, 0);
            }
            State terminal = result;
            List<StudyItem> loaded = items;
            main.execute(() -> {
                loading = false;
                if (cleared) return;
                if (terminal != null) { state.setValue(terminal); return; }
                queue = loaded;
                index = 0;
                flipped = false;
                publish();
            });
        });
    }

    /** Shows the answer side. No effect when there is no card or it is already flipped. */
    public void flip() {
        if (!isShowingCard() || flipped) return;
        flipped = true;
        publish();
    }

    /** Ignored before the card is flipped, so ratings can never skip the answer. */
    public void rate(Rating rating) {
        if (rating == null || !isShowingCard() || !flipped) return;
        StudyItem item = queue.get(index);
        if (listener != null) listener.onRated(item, rating);
        index++;
        flipped = false;
        publish();
    }

    private boolean isShowingCard() {
        State current = state.getValue();
        return !cleared && current != null && current.status == Status.READY;
    }

    private void publish() {
        if (index >= queue.size()) {
            state.setValue(new State(Status.FINISHED, null, false, 0, queue.size()));
        } else {
            state.setValue(new State(Status.READY, queue.get(index), flipped, index + 1, queue.size()));
        }
    }

    @Override protected void onCleared() {
        cleared = true;
        worker.execute(close); // serial worker: runs after any in-flight read
    }
}
