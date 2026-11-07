package com.vocamind.app.feature.quiz;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.*;
import androidx.lifecycle.*;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.quiz.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Deterministic UI data, real NavHost, actual Fragment/ViewModel lifecycle and Back dispatcher. */
public abstract class QuizScreenContract {
    static final AtomicInteger writes=new AtomicInteger();
    public static class FixtureQuizFragment extends QuizFragment {
        @Override protected ViewModelProvider.Factory viewModelFactory() {
            return new AbstractSavedStateViewModelFactory(requireActivity(),null) {
                @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key,
                        @NonNull Class<T> type, @NonNull SavedStateHandle saved) {
                    return type.cast(new QuizViewModel(saved,() -> RepoResult.success(QuizGenerationOutcome.ready(questions())),
                            (k,r,t) -> { writes.incrementAndGet(); return RepoResult.success(1L); },
                            Runnable::run,Runnable::run,new java.util.function.LongSupplier() {
                                long now; public long getAsLong() { now+=600; return now; }
                            },() -> 123L,() -> {}));
                }
            };
        }
    }
    static List<QuizQuestion> questions() {
        List<QuizQuestion> questions=new ArrayList<>();
        for (int i=0;i<4;i++) questions.add(new QuizQuestion(new QuizVocabulary(String.format(Locale.ROOT,"%016x",i),
                "word "+i,"noun","one","context "+i),Arrays.asList(new QuizOption("wrong","two","two"),
                new QuizOption("right","one","one"),new QuizOption("third","three","three"),new QuizOption("fourth","four","four")),
                "right",new UnicodeMeaningNormalizer()));
        return questions;
    }
    void open(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            NavHostFragment host=(NavHostFragment)activity.getSupportFragmentManager().getPrimaryNavigationFragment();
            if (host == null) {
                for (Fragment f : activity.getSupportFragmentManager().getFragments())
                    if (f instanceof NavHostFragment) host=(NavHostFragment)f;
            }
            assertNotNull(host);
            host.getChildFragmentManager().setFragmentFactory(new FragmentFactory() {
                @NonNull @Override public Fragment instantiate(@NonNull ClassLoader loader,@NonNull String name) {
                    return name.equals(QuizFragment.class.getName()) ? new FixtureQuizFragment() : super.instantiate(loader,name);
                }
            });
            activity.findViewById(R.id.open_quiz).performClick();
        });
    }
    @Test public void fullFlowKeepsFeedbackThroughRecreationAndSavesOnce() {
        writes.set(0);
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            open(scenario);
            scenario.onActivity(a -> {
                assertFalse(a.findViewById(R.id.quiz_next).isEnabled());
                assertEquals("noun\ncontext 0",((TextView)a.findViewById(R.id.quiz_context)).getText().toString());
                a.findViewById(R.id.quiz_option_1).performClick();
                assertFalse(a.findViewById(R.id.quiz_option_2).isEnabled());
                assertTrue(((TextView)a.findViewById(R.id.quiz_feedback)).getText().toString().contains("one"));
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.quiz_next).isEnabled());
                assertFalse(a.findViewById(R.id.quiz_option_1).isEnabled());
                a.findViewById(R.id.quiz_next).performClick();
            });
            for (int i=1;i<4;i++) {
                final int index=i;
                scenario.onActivity(a -> {
                    a.findViewById(R.id.quiz_option_2).performClick();
                    if (index<3) a.findViewById(R.id.quiz_next).performClick();
                });
            }
            scenario.onActivity(a -> {
                assertEquals(View.GONE,a.findViewById(R.id.quiz_next).getVisibility());
                assertTrue(((TextView)a.findViewById(R.id.quiz_status)).getText().toString().contains("75%"));
            });
            scenario.recreate(); assertEquals(1,writes.get());
            scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
            scenario.onActivity(a -> assertNotNull(a.findViewById(R.id.open_quiz)));
            open(scenario);
            scenario.onActivity(a -> assertTrue(((TextView)a.findViewById(R.id.quiz_status)).getText().toString().contains("75%")));
            assertEquals(1,writes.get());
        }
    }
    @Test public void toolbarAndSystemBackShareCancelAndConfirmExit() {
        writes.set(0);
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            open(scenario);
            scenario.onActivity(a -> a.findViewById(R.id.quiz_back).performClick());
            clickDialog(scenario,false);
            scenario.onActivity(a -> {
                assertNotNull(a.findViewById(R.id.quiz_option_1));
                a.getOnBackPressedDispatcher().onBackPressed();
            });
            clickDialog(scenario,true);
            scenario.onActivity(a -> assertNotNull(a.findViewById(R.id.open_quiz)));
            assertEquals(0,writes.get());
            open(scenario);
            scenario.onActivity(a -> assertTrue(a.findViewById(R.id.quiz_option_1).isEnabled()));
        }
    }
    private void clickDialog(ActivityScenario<MainActivity> scenario,boolean confirm) {
        scenario.onActivity(a -> {
            for (Fragment host : a.getSupportFragmentManager().getFragments()) {
                for (Fragment child : host.getChildFragmentManager().getFragments()) {
                    if (child instanceof QuizFragment) {
                        QuizFragment quiz=(QuizFragment)child;
                        assertNotNull(quiz.exitDialog);
                        quiz.exitDialog.getButton(confirm ? android.content.DialogInterface.BUTTON_POSITIVE
                                : android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
                        return;
                    }
                }
            }
            fail("Quiz fragment missing");
        });
    }
}
