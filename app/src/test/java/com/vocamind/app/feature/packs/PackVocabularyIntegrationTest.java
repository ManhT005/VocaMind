package com.vocamind.app.feature.packs;

import static org.junit.Assert.*;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Real composition-root regression: keep both feature flows through one NavHost/Activity. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = {26,35})
public class PackVocabularyIntegrationTest {
    private MainActivity activity;
    private NavHostFragment host;
    private void await(BooleanSupplier condition) throws Exception {
        for (int n=0; n<400; n++) {
            activity.getSupportFragmentManager().executePendingTransactions();
            host.getChildFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Integrated navigation did not settle");
    }
    private boolean button(int label) {
        TextView button = activity.findViewById(R.id.activation);
        return button != null && button.isEnabled() && button.getText().toString().equals(activity.getString(label));
    }
    private boolean packsReady() {
        LinearLayout list = activity.findViewById(R.id.pack_list);
        return list != null && list.getChildCount()>0;
    }
    @Test public void explicitActivationWordRouteBackListAndTodaySharePersistedState() throws Exception {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
        host = (NavHostFragment)activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController nav = host.getNavController();
        try {
            nav.navigate(R.id.todayFragment);
            await(() -> activity.findViewById(R.id.explore) != null && activity.findViewById(R.id.explore).getVisibility()==View.VISIBLE);
            activity.findViewById(R.id.explore).performClick(); await(this::packsReady);
            ((LinearLayout)activity.findViewById(R.id.pack_list)).getChildAt(0).performClick();
            await(() -> button(R.string.pack_activate));
            activity.findViewById(R.id.activation).performClick();
            assertFalse(activity.findViewById(R.id.activation).isEnabled());
            await(() -> button(R.string.pack_pause));
            await(() -> activity.findViewById(R.id.words) != null && ((RecyclerView)activity.findViewById(R.id.words)).getAdapter().getItemCount()>0);
            RecyclerView list = activity.findViewById(R.id.words);
            PackWordAdapter adapter = (PackWordAdapter)list.getAdapter();
            String id = adapter.getCurrentList().get(0).getVocabulary().stableId;
            PackWordAdapter.Holder row = adapter.onCreateViewHolder(list,0);
            adapter.onBindViewHolder(row,0); row.itemView.performClick();
            await(() -> nav.getCurrentDestination().getId()==R.id.vocabularyDetailFragment);
            assertEquals(id,nav.getCurrentBackStackEntry().getArguments().getString("vocabularyId"));
            nav.popBackStack(); await(() -> button(R.string.pack_pause));
            nav.popBackStack(); await(this::packsReady);
            TextView title = ((LinearLayout)activity.findViewById(R.id.pack_list)).getChildAt(0).findViewById(R.id.pack_title);
            assertTrue(title.getText().toString().contains(activity.getString(R.string.pack_active)));
            nav.popBackStack();
            await(() -> activity.findViewById(R.id.explore)!=null && activity.findViewById(R.id.loading).getVisibility()==View.GONE);
            assertEquals(View.GONE,activity.findViewById(R.id.explore).getVisibility());
            nav.navigate(R.id.packsFragment); await(this::packsReady);
            ((LinearLayout)activity.findViewById(R.id.pack_list)).getChildAt(0).performClick();
            await(() -> button(R.string.pack_pause)); activity.findViewById(R.id.activation).performClick();
            await(() -> button(R.string.pack_activate));
            nav.popBackStack(); await(this::packsReady); nav.popBackStack();
            await(() -> activity.findViewById(R.id.explore)!=null && activity.findViewById(R.id.explore).getVisibility()==View.VISIBLE);
        } finally { controller.pause().stop().destroy(); }
    }
}
