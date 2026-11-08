package com.vocamind.app.feature.packs;

import static org.junit.Assert.*;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.R;
import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.domain.pack.SetPackActive;
import com.vocamind.app.fake.FakePackRepository;
import com.vocamind.app.feature.today.TodayFragment;
import com.vocamind.app.feature.today.TodayPlanViewModel;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Host rendering evidence, not a substitute for device navigation/offline/reboot smoke. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public class PackActivationRenderingTest {
    private final Queue<Runnable> worker = new ArrayDeque<>(), main = new ArrayDeque<>();
    private final FakePackRepository repo = new FakePackRepository();
    private int writes;
    private final PacksViewModel packs = new PacksViewModel(repo::getPackStates, (id, active) -> {
        writes++;
        return new SetPackActive(repo, () -> 0).execute(id, active);
    }, worker::add, main::add, () -> {});

    private void settle() {
        while (!worker.isEmpty() || !main.isEmpty()) {
            if (!worker.isEmpty()) worker.remove().run();
            if (!main.isEmpty()) main.remove().run();
        }
    }

    private AppCompatActivity show(Fragment fragment) {
        org.robolectric.android.controller.ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class);
        AppCompatActivity activity = controller.get();
        activity.setTheme(R.style.Theme_VocaMind);
        controller.setup();
        activity.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment)
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        new ViewModelProvider(activity, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) {
                if (type == PacksViewModel.class) return type.cast(packs);
                if (type == TodayPlanViewModel.class) {
                    return type.cast(new TodayPlanViewModel(() -> RepoResult.failure(RepoError.READ_FAILED),
                            worker::add, main::add, () -> {}));
                }
                return type.cast(new PackWordsViewModel(() -> RepoResult.success(List.of()),
                        worker::add, main::add, () -> {}));
            }
        }).get(PacksViewModel.class);
        if (fragment instanceof TodayFragment) {
            new ViewModelProvider(activity, new ViewModelProvider.Factory() {
                @Override public <T extends ViewModel> T create(Class<T> type) {
                    return type.cast(new TodayPlanViewModel(() -> RepoResult.failure(RepoError.READ_FAILED),
                            worker::add, main::add, () -> {}));
                }
            }).get(TodayPlanViewModel.class);
        }
        if (fragment instanceof PackDetailFragment) {
            new ViewModelProvider(fragment, new ViewModelProvider.Factory() {
                @Override public <T extends ViewModel> T create(Class<T> type) {
                    return type.cast(new PackWordsViewModel(() -> RepoResult.success(List.of()),
                            worker::add, main::add, () -> {}));
                }
            }).get(PackWordsViewModel.class);
        }
        activity.getSupportFragmentManager().beginTransaction()
                .setMaxLifecycle(fragment, Lifecycle.State.RESUMED).commitNow();
        return activity;
    }

    @Test public void todayShowsCtaOnlyAfterSuccessfulReadAndNeverWrites() {
        TodayFragment fragment = new TodayFragment();
        AppCompatActivity activity = show(fragment);
        View view = fragment.requireView();
        assertEquals(View.GONE, view.findViewById(R.id.explore).getVisibility());
        settle();
        assertEquals(View.VISIBLE, view.findViewById(R.id.explore).getVisibility());
        assertEquals(activity.getString(R.string.today_choose_pack), ((TextView)view.findViewById(R.id.message)).getText());
        repo.failure = RepoError.READ_FAILED; packs.refresh(); settle();
        assertEquals(View.GONE, view.findViewById(R.id.explore).getVisibility());
        assertEquals(View.VISIBLE, view.findViewById(R.id.retry).getVisibility());
        repo.failure = null;
        repo.states.add(new PackState(new CatalogPack("A", "A", "d", "c", 1, "1", 0), true, 0L, 0));
        view.findViewById(R.id.retry).performClick(); settle();
        assertEquals(View.GONE, view.findViewById(R.id.explore).getVisibility());
        assertEquals(0, writes);
        activity.finish();
    }

    @Test public void detailDisablesWhilePendingThenShowsPersistedActiveAndPaused() {
        repo.states.add(new PackState(new CatalogPack("A", "A", "d", "c", 1, "1", 0), false, null, 0));
        PackDetailFragment fragment = new PackDetailFragment();
        Bundle args = new Bundle(); args.putString(PacksFragment.ARG_PACK_ID, "A"); fragment.setArguments(args);
        AppCompatActivity activity = show(fragment); settle();
        TextView button = fragment.requireView().findViewById(R.id.activation);
        assertEquals(activity.getString(R.string.pack_activate), button.getText());
        button.performClick();
        assertFalse(button.isEnabled());
        button.performClick();
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
        activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();
        button = fragment.requireView().findViewById(R.id.activation);
        assertFalse(button.isEnabled());
        settle();
        assertEquals(1, writes);
        assertEquals(activity.getString(R.string.pack_pause), button.getText());
        button.performClick(); settle();
        assertFalse(repo.states.get(0).isActive());
        assertEquals(activity.getString(R.string.pack_activate), button.getText());
        activity.finish();
    }
}
