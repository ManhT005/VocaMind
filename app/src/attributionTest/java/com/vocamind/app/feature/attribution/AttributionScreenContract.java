package com.vocamind.app.feature.attribution;

import android.content.pm.ActivityInfo;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import org.junit.Test;
import static org.junit.Assert.*;

/** Shared on-device and Robolectric navigation/catalog integration checks. */
public abstract class AttributionScreenContract {
    @Test public void attributionAvailableBeforeLearningSurvivesRecreationAndBack() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.open_attribution).performClick());
            awaitSources(scenario);
            assertFixture(scenario);
            scenario.recreate();
            awaitSources(scenario);
            assertFixture(scenario);
            scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            awaitSources(scenario);
            assertFixture(scenario);
            scenario.onActivity(activity -> activity.findViewById(R.id.back).performClick());
            scenario.onActivity(activity -> assertNotNull(activity.findViewById(R.id.open_attribution)));
            scenario.onActivity(activity -> activity.findViewById(R.id.open_attribution).performClick());
            awaitSources(scenario);
            scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
            scenario.onActivity(activity -> assertNotNull(activity.findViewById(R.id.open_attribution)));
        }
    }

    private void assertFixture(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            LinearLayout rows = activity.findViewById(R.id.sources);
            assertEquals(1, rows.getChildCount());
            View row = rows.getChildAt(0);
            assertEquals("VocaMind synthetic DATA-01 test fixture; not release vocabulary",
                    ((TextView) row.findViewById(R.id.title)).getText().toString());
            assertTrue(((TextView) row.findViewById(R.id.source_url)).getText().toString()
                    .contains("https://github.com/ManhT005/VocaMind"));
            assertTrue(((TextView) row.findViewById(R.id.license)).getText().toString()
                    .contains("LicenseRef-Project-Test-Fixture"));
            assertTrue(((TextView) row.findViewById(R.id.license_url)).getText().toString()
                    .contains("tools/catalog/README.md"));
            assertTrue(((TextView) row.findViewById(R.id.snapshot)).getText().toString().contains("2026-10-25"));
            assertEquals(View.GONE, activity.findViewById(R.id.loading).getVisibility());
        });
    }

    private void awaitSources(ActivityScenario<MainActivity> scenario) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        boolean[] ready = {false};
        while (System.nanoTime() < deadline) {
            scenario.onActivity(activity -> {
                LinearLayout rows = activity.findViewById(R.id.sources);
                ready[0] = rows != null && rows.getChildCount() > 0;
            });
            if (ready[0]) return;
            Thread.sleep(30);
        }
        fail("Local catalog was not rendered within 15 seconds");
    }
}
