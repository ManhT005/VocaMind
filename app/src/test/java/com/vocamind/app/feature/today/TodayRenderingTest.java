package com.vocamind.app.feature.today;

import static org.junit.Assert.assertEquals;

import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.R;
import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.learning.PlanToday;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.learning.WordProgress;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.fake.FakePackRepository;
import com.vocamind.app.fake.FakeStudyRepository;
import com.vocamind.app.feature.packs.PacksViewModel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Today screen states driven by pack state + Today plan (fakes, no database). */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class TodayRenderingTest {
    private final Queue<Runnable> worker = new ArrayDeque<>(), main = new ArrayDeque<>();
    private final FakePackRepository packs = new FakePackRepository();
    private final FakeStudyRepository study = new FakeStudyRepository();
    private final PacksViewModel packModel = new PacksViewModel(packs::getPackStates, worker::add, main::add, () -> {});
    private final TodayPlanViewModel planModel = new TodayPlanViewModel(
            () -> new PlanToday(packs, study, Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC),
                    ZoneOffset.UTC).execute(), worker::add, main::add, () -> {});

    private void settle() {
        while (!worker.isEmpty() || !main.isEmpty()) {
            if (!worker.isEmpty()) worker.remove().run();
            if (!main.isEmpty()) main.remove().run();
        }
    }

    private static CatalogVocabulary word(String id) {
        return new CatalogVocabulary(id, id, id, "noun", "m", "e", "v", null, "s", "1");
    }

    private void activePack() {
        packs.states.add(new PackState(new CatalogPack("A", "A", "d", "c", 1, "1", 10), true, 0L, 0));
    }

    private View show(TodayFragment fragment) {
        org.robolectric.android.controller.ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class);
        AppCompatActivity a = controller.get();
        a.setTheme(R.style.Theme_VocaMind);
        controller.setup();
        a.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment)
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        new ViewModelProvider(a, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) {
                return type.cast(type == PacksViewModel.class ? packModel : planModel);
            }
        }).get(PacksViewModel.class);
        new ViewModelProvider(a, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(planModel); }
        }).get(TodayPlanViewModel.class);
        a.getSupportFragmentManager().beginTransaction().setMaxLifecycle(fragment, Lifecycle.State.RESUMED).commitNow();
        settle();
        return fragment.requireView();
    }

    private static int vis(View root, int id) { return root.findViewById(id).getVisibility(); }

    @Test public void dueAndNewCountsAndStartAreShownWhenSomethingIsLeft() {
        activePack();
        study.due.add(new StudyItem(word("a"), new WordProgress("a", 1, 1L, 1L, 1L, 0, false, 1, false)));
        study.fresh.add(word("n1"));
        study.fresh.add(word("n2"));
        View view = show(new TodayFragment());
        assertEquals("Cần ôn: 1 từ · Từ mới: 2 từ", ((TextView) view.findViewById(R.id.summary)).getText().toString());
        assertEquals(View.VISIBLE, vis(view, R.id.start));
        assertEquals(View.GONE, vis(view, R.id.explore));
        assertEquals(View.GONE, vis(view, R.id.done_quiz));
        assertEquals(View.GONE, vis(view, R.id.retry));
    }

    @Test public void doneStateOffersQuizAndExploreInsteadOfStart() {
        activePack();
        study.learnedToday = 5; // quota used, nothing due
        study.fresh.add(word("n1"));
        View view = show(new TodayFragment());
        assertEquals(View.GONE, vis(view, R.id.start));
        assertEquals(View.VISIBLE, vis(view, R.id.done_quiz));
        assertEquals(View.VISIBLE, vis(view, R.id.done_explore));
        assertEquals(View.VISIBLE, vis(view, R.id.message));
    }

    @Test public void noActivePackShowsChoosePackCtaOnly() {
        View view = show(new TodayFragment());
        assertEquals(View.VISIBLE, vis(view, R.id.explore));
        assertEquals(View.GONE, vis(view, R.id.start));
        assertEquals(View.GONE, vis(view, R.id.done_quiz));
    }

    @Test public void planFailureShowsRetryAndNoStart() {
        activePack();
        study.failure = RepoError.READ_FAILED;
        View view = show(new TodayFragment());
        assertEquals(View.VISIBLE, vis(view, R.id.retry));
        assertEquals(View.GONE, vis(view, R.id.start));
    }
}
