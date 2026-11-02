package com.vocamind.app.feature.attribution;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.domain.catalog.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public class AttributionRenderingTest {
    @Test public void rendersLoadingEmptyErrorRetryAndMultipleLongSources() {
        FakeCatalogRepository repository = new FakeCatalogRepository();
        Queue<Runnable> worker = new ArrayDeque<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                        new FragmentManager.FragmentLifecycleCallbacks() {
                            @Override public void onFragmentCreated(@NonNull FragmentManager fm,
                                    @NonNull Fragment fragment, Bundle saved) {
                                if (!(fragment instanceof AttributionFragment)) return;
                                new ViewModelProvider(fragment, new ViewModelProvider.Factory() {
                                    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                                        return type.cast(new AttributionViewModel(new GetContentSources(repository),
                                                worker::add, Runnable::run, () -> { }));
                                    }
                                }).get(AttributionViewModel.class);
                            }
                        }, true);
                activity.findViewById(R.id.open_attribution).performClick();
            });
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.loading).getVisibility());
                worker.remove().run();
                assertEquals(activity.getString(R.string.attribution_empty),
                        ((TextView) activity.findViewById(R.id.message)).getText().toString());
                repository.failure = new CatalogReadException(new IllegalStateException());
                activity.findViewById(R.id.retry).performClick(); worker.remove().run();
                assertEquals(activity.getString(R.string.attribution_error),
                        ((TextView) activity.findViewById(R.id.message)).getText().toString());
                repository.failure = null;
                String longTitle = "Long local source ".repeat(40);
                String longUrl = "https://example.com/" + "path/".repeat(100);
                repository.sources = List.of(new CatalogSource("1", longTitle, longUrl,
                        "CC-BY-SA-4.0", longUrl, "2026-10-25"),
                        new CatalogSource("2", "Missing URLs", null, "Custom", "javascript:bad", null));
                activity.findViewById(R.id.retry).performClick(); worker.remove().run();
                LinearLayout rows = activity.findViewById(R.id.sources);
                assertEquals(2, rows.getChildCount());
                TextView title = rows.getChildAt(0).findViewById(R.id.title);
                TextView url = rows.getChildAt(0).findViewById(R.id.source_url);
                assertEquals(longTitle, title.getText().toString());
                assertTrue(url.getText().toString().endsWith(longUrl));
                assertNull(url.getEllipsize());
                assertTrue(url.getMaxLines() > 1);
                assertEquals(View.GONE, activity.findViewById(R.id.message).getVisibility());
                rows.getChildAt(1).findViewById(R.id.open_source).performClick();
                rows.getChildAt(1).findViewById(R.id.open_license).performClick();
                assertEquals(2, rows.getChildCount());
                assertTrue(((TextView) rows.getChildAt(1).findViewById(R.id.snapshot)).getText()
                        .toString().contains(activity.getString(R.string.attribution_missing)));
            });
        }
    }
}
