package com.vocamind.app.feature.quiz;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class QuizScreenTest extends QuizScreenContract {
    public static class UnavailableQuizFragment extends QuizFragment {
        @Override protected androidx.lifecycle.ViewModelProvider.Factory viewModelFactory() {
            return new androidx.lifecycle.ViewModelProvider.Factory() {
                @Override public <T extends androidx.lifecycle.ViewModel> T create(Class<T> type) {
                    return type.cast(new QuizViewModel(new androidx.lifecycle.SavedStateHandle(),
                            () -> com.vocamind.app.domain.common.RepoResult.failure(com.vocamind.app.domain.common.RepoError.READ_FAILED),
                            (key, result, time) -> { throw new AssertionError("Error rendering must never write"); },
                            Runnable::run, Runnable::run, () -> 0L, () -> 0L, () -> {}));
                }
            };
        }
    }

    @org.junit.Test public void readFailureShowsAnActionableMessageWithoutInternalCodes() {
        try (var scenario = androidx.test.core.app.ActivityScenario.launch(com.vocamind.app.MainActivity.class)) {
            scenario.onActivity(activity -> {
                var host = (androidx.navigation.fragment.NavHostFragment) activity.getSupportFragmentManager()
                        .findFragmentById(com.vocamind.app.R.id.nav_host_fragment);
                host.getChildFragmentManager().setFragmentFactory(new androidx.fragment.app.FragmentFactory() {
                    @Override public androidx.fragment.app.Fragment instantiate(ClassLoader loader, String name) {
                        return name.equals(QuizFragment.class.getName()) ? new UnavailableQuizFragment() : super.instantiate(loader, name);
                    }
                });
                activity.findViewById(com.vocamind.app.R.id.open_quiz).performClick();
            });
            scenario.onActivity(activity -> {
                var status = (android.widget.TextView) activity.findViewById(com.vocamind.app.R.id.quiz_status);
                org.junit.Assert.assertEquals(activity.getString(com.vocamind.app.R.string.quiz_error), status.getText().toString());
                org.junit.Assert.assertTrue(activity.findViewById(com.vocamind.app.R.id.quiz_action).isEnabled());
                activity.findViewById(com.vocamind.app.R.id.quiz_action).performClick();
                org.junit.Assert.assertEquals(activity.getString(com.vocamind.app.R.string.quiz_error), status.getText().toString());
            });
        }
    }
}
