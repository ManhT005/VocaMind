package com.vocamind.app.feature.quiz;

import android.os.Bundle;
import androidx.lifecycle.SavedStateHandle;
import com.vocamind.app.domain.common.*;
import com.vocamind.app.domain.quiz.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.Executor;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class QuizViewModelTest {
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable r) { tasks.add(r); }
        void run() { tasks.remove().run(); }
    }
    static List<QuizQuestion> questions(int n) {
        List<QuizQuestion> out=new ArrayList<>();
        for (int i=0;i<n;i++) out.add(new QuizQuestion(new QuizVocabulary(String.format(Locale.ROOT,"%016x",i),
                "word", "noun", "one", "sense"), Arrays.asList(new QuizOption("a","one","one"),
                new QuizOption("b","two","two"),new QuizOption("c","three","three"),new QuizOption("d","four","four")),
                "a",new UnicodeMeaningNormalizer()));
        return out;
    }
    Queue worker;
    SavedStateHandle handle;
    long now;
    int reads, writes;
    boolean failSave;
    String key;
    long timestamp;
    QuizViewModel model;
    @Before public void setup() { worker=new Queue(); handle=new SavedStateHandle(); now=1000; }
    QuizViewModel create(SavedStateHandle saved, int n) {
        return new QuizViewModel(saved,() -> { reads++; return RepoResult.success(QuizGenerationOutcome.ready(questions(n))); },
                (k,r,t) -> { writes++; if (key != null) { assertEquals(key,k); assertEquals(timestamp,t); }
                    key=k; timestamp=t;
                    return failSave ? RepoResult.failure(RepoError.WRITE_FAILED) : RepoResult.success(9L); },
                worker,Runnable::run,() -> now,() -> 456L,() -> {});
    }
    QuizViewModel.Phase phase() { return model.state().getValue().phase; }
    QuizSession session() { return model.state().getValue().session; }
    void answer(String option) { model.answer(session().getCurrentQuestion().getId(),option); }
    void complete(int n, int correct) {
        for (int i=0;i<n;i++) { now+=600; answer(i<correct ? "a" : "b"); if (i<n-1) model.next(); }
    }
    @Test public void locksAnswersAndDebouncesAcrossQuestions() {
        model=create(handle,4); model.start(); assertEquals(1,worker.tasks.size()); worker.run();
        model.next(); assertEquals(0,session().getPosition());
        model.answer("old","a"); model.answer(session().getCurrentQuestion().getId(),"unknown");
        answer("b"); assertEquals(QuizViewModel.Phase.ANSWERED,phase()); answer("a");
        assertEquals("b",session().getAnswers().get(session().getCurrentQuestion().getId()));
        model.next(); model.next(); assertEquals(1,session().getPosition());
        now+=599; answer("a"); assertEquals(QuizViewModel.Phase.ANSWERING,phase());
        now++; answer("a"); assertEquals(QuizViewModel.Phase.ANSWERED,phase());
        assertEquals(0,writes);
    }
    @Test public void completesOnLastAnswerAndRetriesSamePayload() {
        model=create(handle,7); worker.run(); failSave=true; complete(7,2);
        assertEquals(QuizViewModel.Phase.SAVING,phase()); assertEquals(29,session().getResult().get().getScorePercent());
        model.start(); model.next(); answer("a"); assertEquals(1,worker.tasks.size()); worker.run();
        assertEquals(QuizViewModel.Phase.SAVE_ERROR,phase());
        model.start(); assertEquals(0,worker.tasks.size()); failSave=false;
        model.retrySave(); model.retrySave(); assertEquals(1,worker.tasks.size()); worker.run();
        assertEquals(QuizViewModel.Phase.RESULT,phase()); assertEquals(2,writes); assertEquals(456,timestamp);
    }
    @Test public void snapshotKeepsOrderFeedbackAndCompletedKey() {
        model=create(handle,6); worker.run(); answer("b"); model.next();
        SavedStateHandle restored=new SavedStateHandle(Collections.singletonMap("quiz",handle.get("quiz")));
        model=create(restored,6); assertEquals(1,reads); assertEquals(1,session().getPosition());
        assertEquals(1,session().getAnswers().size());
        for (int i=1;i<6;i++) { now+=600; answer(i==1 ? "a" : "b"); if (i<5) model.next(); }
        assertEquals(17,session().getResult().get().getScorePercent()); worker.run();
        SavedStateHandle completed=new SavedStateHandle(Collections.singletonMap("quiz",restored.get("quiz")));
        model=create(completed,6); assertEquals(QuizViewModel.Phase.SAVING,phase()); worker.run();
        assertEquals(2,writes); assertEquals(1,reads);
    }
    @Test public void abandonDoesNotSaveAndCompletedCannotAbandon() {
        model=create(handle,4); worker.run(); answer("a"); model.abandon();
        assertFalse(model.isInProgress()); assertNull(handle.get("quiz")); assertEquals(0,writes);
        model=create(new SavedStateHandle(),4); worker.run(); complete(4,4); model.abandon(); worker.run();
        assertEquals(QuizViewModel.Phase.RESULT,phase()); assertEquals(100,session().getResult().get().getScorePercent());
    }
    @Test public void invalidSnapshotShowsErrorWithoutGeneratingOrSaving() {
        Bundle broken=new Bundle(); broken.putString("key","bad");
        model=create(new SavedStateHandle(Collections.singletonMap("quiz",broken)),4);
        assertEquals(QuizViewModel.Phase.ERROR,phase()); assertEquals(0,reads); assertEquals(0,writes);
        assertEquals("RESTORE_INVALID",model.state().getValue().error);
        model.start(); worker.run(); assertEquals(QuizViewModel.Phase.ANSWERING,phase());
    }
    @Test public void repositoryErrorAndInvalidInputAreNotEmpty() {
        for (boolean invalid : new boolean[]{false,true}) {
            model=new QuizViewModel(new SavedStateHandle(),() -> invalid
                    ? RepoResult.success(QuizGenerationOutcome.invalidInput("policy")) : RepoResult.failure(RepoError.READ_FAILED),
                    (k,r,t) -> { fail("Must not save"); return null; },worker,Runnable::run,() -> now,() -> 1L,() -> {});
            worker.run(); assertEquals(QuizViewModel.Phase.ERROR,phase());
        }
    }
    @Test public void emptyPoolIsNotAnErrorAndRetryReadsAgain() {
        model=new QuizViewModel(handle,() -> { reads++; return RepoResult.success(QuizGenerationOutcome.empty(
                new QuizPool(Collections.emptyList(),new UnicodeMeaningNormalizer()))); },
                (k,r,t) -> { fail("Must not save"); return null; },worker,Runnable::run,() -> now,() -> 1L,() -> {});
        worker.run(); assertEquals(QuizViewModel.Phase.EMPTY,phase()); model.start(); worker.run();
        assertEquals(2,reads); assertEquals(QuizViewModel.Phase.EMPTY,phase());
    }
    @Test public void clearingDuringGenerationDropsLateCallback() {
        Queue main=new Queue();
        model=new QuizViewModel(handle,() -> RepoResult.success(QuizGenerationOutcome.ready(questions(4))),
                (k,r,t) -> { fail("Must not save"); return null; },worker,main,() -> now,() -> 1L,() -> {});
        androidx.lifecycle.ViewModelStore store=new androidx.lifecycle.ViewModelStore(); store.put("quiz",model);
        worker.run(); store.clear(); main.run();
        assertEquals(QuizViewModel.Phase.LOADING,phase()); assertNull(handle.get("quiz")); worker.run();
    }
    @Test public void closingScreenDuringSaveDoesNotAbandonCompletedPayload() {
        model=create(handle,4); worker.run(); complete(4,0);
        model.onOpened(); assertEquals(1,worker.tasks.size());
        worker.run(); assertEquals(QuizViewModel.Phase.RESULT,phase());
        assertEquals(0,session().getResult().get().getScorePercent()); assertEquals(1,writes);
    }
}
