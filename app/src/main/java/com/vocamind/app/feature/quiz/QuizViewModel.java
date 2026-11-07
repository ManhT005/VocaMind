package com.vocamind.app.feature.quiz;

import android.os.Bundle;
import androidx.lifecycle.*;
import com.vocamind.app.domain.quiz.*;
import com.vocamind.app.domain.common.*;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.*;

/** All event handlers and callbacks run on main; only blocking operations use worker. */
public final class QuizViewModel extends ViewModel {
    public enum Phase { LOADING, EMPTY, ERROR, ANSWERING, ANSWERED, SAVING, SAVE_ERROR, RESULT }
    public static final class State {
        public final Phase phase;
        public final QuizSession session;
        public final String error;
        State(Phase phase, QuizSession session, String error) {
            this.phase = phase; this.session = session; this.error = error;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final SavedStateHandle saved;
    private final Supplier<RepoResult<QuizGenerationOutcome>> generate;
    public interface Writer { RepoResult<Long> save(String key, QuizResult result, long time); }
    private final Writer writer;
    private final Executor worker, main;
    private final LongSupplier monotonic, wall;
    private final Runnable close;
    private QuizSession session;
    private String key;
    private long completedAt, lastAnswer;
    private boolean hasLastAnswer, cleared;
    private int generation;
    public QuizViewModel(SavedStateHandle saved, Supplier<RepoResult<QuizGenerationOutcome>> generate,
            Writer writer, Executor worker, Executor main, LongSupplier monotonic, LongSupplier wall, Runnable close) {
        this.saved=saved; this.generate=generate; this.writer=writer; this.worker=worker; this.main=main;
        this.monotonic=monotonic; this.wall=wall; this.close=close;
        Object restored = saved.get("quiz");
        if (restored == null) { start(); return; }
        try {
            if (!(restored instanceof Bundle)) throw new IllegalArgumentException("snapshot type");
            Bundle snapshot = (Bundle) restored;
            key = snapshot.getString("key");
            UUID.fromString(key);
            session = QuizSnapshotMapper.decode(snapshot.getBundle("session"));
            completedAt = snapshot.getLong("completedAt", -1);
            if (session.getState() == QuizSession.State.COMPLETED && completedAt < 0)
                throw new IllegalArgumentException("completion time");
            if (snapshot.containsKey("lastAnswerElapsed")) {
                lastAnswer = snapshot.getLong("lastAnswerElapsed", -1);
                if (lastAnswer < 0 || session.getAnswers().isEmpty())
                    throw new IllegalArgumentException("answer clock");
                hasLastAnswer = monotonic.getAsLong() >= lastAnswer;
                if (!hasLastAnswer) {
                    // Forget a pre-reset marker immediately, even if another restore
                    // occurs after the new clock has caught up with its old value.
                    Bundle normalized = new Bundle(snapshot);
                    normalized.remove("lastAnswerElapsed");
                    saved.set("quiz", normalized);
                }
            }
            if (session.getState() == QuizSession.State.COMPLETED) {
                save();
            } else publishQuestion();
        } catch (RuntimeException invalid) { session=null; publish(Phase.ERROR, "RESTORE_INVALID"); }
    }
    public LiveData<State> state() { return state; }
    /** An explicit abandoned attempt starts fresh on the next visit. Completed saves keep their key. */
    public void onOpened() {
        if (session != null && session.getState() == QuizSession.State.ABANDONED) start();
    }
    public void start() {
        State current = state.getValue();
        if (current != null && (current.phase == Phase.LOADING || current.phase == Phase.SAVING
                || current.phase == Phase.SAVE_ERROR || isInProgress())) return;
        int token = ++generation;
        session=null; key=null; hasLastAnswer=false; saved.remove("quiz");
        publish(Phase.LOADING, "");
        worker.execute(() -> {
            RepoResult<QuizGenerationOutcome> result;
            try { result=generate.get(); } catch (RuntimeException failure) { result=RepoResult.failure(RepoError.READ_FAILED); }
            final RepoResult<QuizGenerationOutcome> output=result;
            main.execute(() -> {
                if (cleared || token != generation) return;
                if (!output.isSuccess()) { publish(Phase.ERROR, output.getError().name()); return; }
                QuizGenerationOutcome outcome=output.get();
                if (outcome.getStatus() != QuizGenerationOutcome.Status.READY) {
                    publish(outcome.getStatus() == QuizGenerationOutcome.Status.EMPTY ? Phase.EMPTY : Phase.ERROR, outcome.getReason()); return;
                }
                session=new QuizSession(outcome.getQuestions()); key=UUID.randomUUID().toString(); completedAt=-1;
                persist(); publishQuestion();
            });
        });
    }
    public void answer(String questionId, String optionId) {
        if (!isInProgress()) return;
        long now=monotonic.getAsLong();
        if (hasLastAnswer) {
            // elapsedRealtime is shared across processes, but resets after reboot.
            // A marker ahead of the current clock cannot be used as a deadline.
            if (now < lastAnswer) hasLastAnswer = false;
            else if (now-lastAnswer < 600) return;
        }
        QuizSession.Transition transition=session.answer(questionId,optionId);
        if (transition.getOutcome() != QuizSession.Outcome.ACCEPTED) return;
        session=transition.getSession(); lastAnswer=now; hasLastAnswer=true;
        if (session.getState() == QuizSession.State.COMPLETED) {
            completedAt=wall.getAsLong(); persist(); save();
        } else { persist(); publishQuestion(); }
    }
    public void next() {
        if (!isInProgress()) return;
        session=session.next().getSession(); persist(); publishQuestion();
    }
    public boolean isInProgress() { return session != null && session.getState() == QuizSession.State.IN_PROGRESS; }
    public void abandon() {
        if (isInProgress()) { session=session.abandon().getSession(); saved.remove("quiz"); }
        generation++;
    }
    public void retrySave() {
        if (state.getValue().phase == Phase.SAVE_ERROR) save();
    }
    private void save() {
        publish(Phase.SAVING, "");
        final String completionKey=key;
        final QuizResult result=session.getResult().get();
        final long time=completedAt;
        worker.execute(() -> {
            RepoResult<Long> output;
            try { output=writer.save(completionKey,result,time); }
            catch (RuntimeException failure) { output=RepoResult.failure(RepoError.WRITE_FAILED); }
            final RepoResult<Long> done=output;
            main.execute(() -> {
                if (cleared || !completionKey.equals(key)) return;
                publish(done.isSuccess() ? Phase.RESULT : Phase.SAVE_ERROR, done.isSuccess() ? "" : done.getError().name());
            });
        });
    }
    private void persist() {
        Bundle out=new Bundle(); out.putString("key",key); out.putLong("completedAt",completedAt);
        if (hasLastAnswer) out.putLong("lastAnswerElapsed",lastAnswer);
        out.putBundle("session",QuizSnapshotMapper.encode(session)); saved.set("quiz",out);
    }
    private void publishQuestion() {
        publish(session.getAnswers().containsKey(session.getCurrentQuestion().getId()) ? Phase.ANSWERED : Phase.ANSWERING, "");
    }
    private void publish(Phase phase, String error) { state.setValue(new State(phase,session,error)); }
    @Override protected void onCleared() { cleared=true; generation++; worker.execute(close); }
}
