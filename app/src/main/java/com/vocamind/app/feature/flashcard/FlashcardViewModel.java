package com.vocamind.app.feature.flashcard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.learning.ScheduleResult;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.learning.TodayPlan;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * UI state of one flashcard session: which card is shown and whether it is flipped.
 * It never computes scheduling: a rating is committed by the {@link ReviewWriter} (the
 * review use case) on the worker, and the card only advances after that commit succeeded.
 * While a write is in flight ratings are disabled; a failed write keeps the same card and
 * face so the learner decides again (never retried automatically).
 * The ViewModel survives rotation, so card, face and position are kept.
 */
public final class FlashcardViewModel extends ViewModel {
    public enum Status { LOADING, READY, FINISHED, NO_ACTIVE_PACK, NOTHING_TO_STUDY, ERROR }

    /** Commits one review and returns its schedule; runs on the worker and throws on failure. */
    public interface ReviewWriter { ScheduleResult write(StudyItem item, Rating rating); }

    public static final class State {
        public final Status status;
        public final StudyItem card;
        public final boolean flipped;
        /** 1-based position of the current card; 0 when there is no card. */
        public final int position;
        public final int total;
        /** A review is being committed; ratings are locked. */
        public final boolean saving;
        /** The last rating could not be saved; the same card is shown again. */
        public final boolean saveFailed;
        /** Outcome of the most recent committed review, for the result/leech hint. Null at start. */
        public final ScheduleResult lastResult;

        State(Status status, StudyItem card, boolean flipped, int position, int total,
              boolean saving, boolean saveFailed, ScheduleResult lastResult) {
            this.status = status;
            this.card = card;
            this.flipped = flipped;
            this.position = position;
            this.total = total;
            this.saving = saving;
            this.saveFailed = saveFailed;
            this.lastResult = lastResult;
        }

        State(Status status, StudyItem card, boolean flipped, int position, int total) {
            this(status, card, flipped, position, total, false, false, null);
        }

        /** Rating buttons are usable only while a card is shown with its answer and nothing is saving. */
        public boolean ratingEnabled() { return status == Status.READY && flipped && !saving; }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final Supplier<RepoResult<TodayPlan>> loader;
    private final Executor worker;
    private final Executor main;
    private final Runnable close;
    private final ReviewWriter writer;
    private ScheduleResult lastResult;
    private boolean saving;
    private boolean saveFailed;
    private List<StudyItem> queue = Collections.emptyList();
    private int index;
    private boolean flipped;
    private boolean loading;
    private boolean cleared;

    public FlashcardViewModel(Supplier<RepoResult<TodayPlan>> loader, Executor worker, Executor main,
                              Runnable close, ReviewWriter writer) {
        this.loader = loader;
        this.worker = worker;
        this.main = main;
        this.close = close;
        this.writer = writer;
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
                saving = false;
                saveFailed = false;
                lastResult = null;
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

    /**
     * Ignored before the card is flipped and while another rating is being saved, so one card
     * receives at most one rating. Main thread only.
     */
    public void rate(Rating rating) {
        if (rating == null || !isShowingCard() || !flipped || saving) return;
        StudyItem item = queue.get(index);
        saving = true;
        saveFailed = false;
        publish();
        worker.execute(() -> {
            ScheduleResult result = null;
            try {
                result = writer.write(item, rating);
            } catch (Throwable failure) {
                result = null;
            }
            ScheduleResult committed = result;
            main.execute(() -> {
                saving = false;
                if (cleared) return;
                if (committed == null) {
                    saveFailed = true;
                } else {
                    lastResult = committed;
                    index++;
                    flipped = false;
                }
                publish();
            });
        });
    }

    private boolean isShowingCard() {
        State current = state.getValue();
        return !cleared && current != null && current.status == Status.READY;
    }

    private void publish() {
        if (index >= queue.size()) {
            state.setValue(new State(Status.FINISHED, null, false, 0, queue.size(), false, false, lastResult));
        } else {
            state.setValue(new State(Status.READY, queue.get(index), flipped, index + 1, queue.size(),
                    saving, saveFailed, lastResult));
        }
    }

    @Override protected void onCleared() {
        cleared = true;
        worker.execute(close); // serial worker: runs after any in-flight read
    }
}
