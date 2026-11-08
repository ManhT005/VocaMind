package com.vocamind.app.feature.search;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.*;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.feature.vocabulary.VocabularyDetailFragment;
import java.io.File;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.Test;

/** Real app -> VM -> DATA-05 -> prepack FTS, compiled for host and device.
 * Device execution still requires a connected device and separately verified airplane mode. */
public abstract class SearchScreenContract {
    protected void tick() throws Exception { Thread.sleep(20); } // Test runner only, never onActivity/main.
    private void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        boolean[] ready = {false};
        while (System.nanoTime() < deadline) {
            scenario.onActivity(a -> ready[0] = condition.test(a));
            if (ready[0]) return;
            tick();
        }
        fail("Search/navigation did not settle");
    }
    private void query(ActivityScenario<MainActivity> scenario, String query, int count) throws Exception {
        scenario.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery(query, false));
        await(scenario, a -> {
            RecyclerView list = a.findViewById(R.id.results);
            return list != null && list.getVisibility() == View.VISIBLE && list.getAdapter().getItemCount() == count;
        });
    }
    @Test public void catalogSearchEnglishVietnameseDetailBackRecreateClearAndNoWrites() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // Schema bootstrap is distinct from search/navigation business writes.
        ExecutorService bootstrap = Executors.newSingleThreadExecutor();
        try { bootstrap.submit(() -> { var db = com.vocamind.app.data.user.UserDatabase.open(context); db.close(); }).get(15, TimeUnit.SECONDS); }
        finally { bootstrap.shutdown(); }
        File userdata = context.getDatabasePath("userdata.db");
        boolean existed = userdata.exists();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.findViewById(R.id.open_vocabulary).performClick());
            query(scenario, "BANK", 1);
            String before = snapshot(context);
            query(scenario, Normalizer.normalize("NGÂN HÀNG", Normalizer.Form.NFD), 1);
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.results);
                SearchAdapter adapter = (SearchAdapter)list.getAdapter();
                assertEquals("5aecfbf5b9a23aee", adapter.getCurrentList().get(0).word.stableId);
                assertEquals("ngân hàng", adapter.getCurrentList().get(0).word.meaningVi);
                SearchAdapter.Holder holder = adapter.onCreateViewHolder(list, 0);
                adapter.onBindViewHolder(holder, 0); holder.itemView.performClick();
            });
            await(scenario, a -> a.findViewById(R.id.detail_lemma) != null
                    && ((TextView)a.findViewById(R.id.detail_lemma)).getText().toString().equals("bank")
                    && a.findViewById(R.id.detail_save).isEnabled());
            scenario.onActivity(a -> {
                var nav = ((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController();
                assertNull(nav.getCurrentBackStackEntry().getArguments().getString("packId"));
                assertEquals("5aecfbf5b9a23aee", nav.getCurrentBackStackEntry().getArguments().getString("vocabularyId"));
                assertEquals("ngân hàng", ((TextView)a.findViewById(R.id.detail_meaning)).getText().toString());
                assertFalse(((TextView)a.findViewById(R.id.detail_example_en)).getText().toString().trim().isEmpty());
                assertFalse(((TextView)a.findViewById(R.id.detail_example_vi)).getText().toString().trim().isEmpty());
                a.findViewById(R.id.back).performClick();
            });
            await(scenario, a -> a.findViewById(R.id.search) != null);
            scenario.recreate();
            await(scenario, a -> a.findViewById(R.id.results) != null && a.findViewById(R.id.results).getVisibility() == View.VISIBLE);
            scenario.onActivity(a -> assertEquals(Normalizer.normalize("NGÂN HÀNG", Normalizer.Form.NFD),
                    ((SearchView)a.findViewById(R.id.search)).getQuery().toString()));
            query(scenario, "book", 2); // All packs inactive: no activation needed.
            scenario.onActivity(a -> a.findViewById(R.id.reset).performClick());
            scenario.onActivity(a -> {
                assertEquals("", ((SearchView)a.findViewById(R.id.search)).getQuery().toString());
                assertEquals(View.GONE, a.findViewById(R.id.results).getVisibility());
                assertEquals(a.getString(R.string.search_no_input), ((TextView)a.findViewById(R.id.search_message)).getText().toString());
            });
            assertEquals(before, snapshot(context));
            assertEquals(existed, userdata.exists());
        }
    }
    @Test public void catalogDetailSaveRequiresExplicitClickAndPersistsAcrossBack() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.findViewById(R.id.open_vocabulary).performClick());
            query(scenario, "bank", 1);
            scenario.onActivity(a -> {
                var nav = ((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController();
                nav.navigate(R.id.vocabularyDetailFragment, VocabularyDetailFragment.searchArguments("5aecfbf5b9a23aee"));
            });
            await(scenario, a -> a.findViewById(R.id.detail_save) != null && a.findViewById(R.id.detail_save).isEnabled());
            boolean[] initiallySaved = {false};
            scenario.onActivity(a -> {
                initiallySaved[0] = ((TextView)a.findViewById(R.id.detail_save)).getText().toString().equals(a.getString(R.string.detail_unsave));
                a.findViewById(R.id.detail_save).performClick();
            });
            await(scenario, a -> a.findViewById(R.id.detail_save).isEnabled()
                    && ((TextView)a.findViewById(R.id.detail_save)).getText().toString().equals(a.getString(initiallySaved[0] ? R.string.detail_save : R.string.detail_unsave)));
            scenario.onActivity(a -> a.findViewById(R.id.back).performClick());
            query(scenario, "bank", 1);
            scenario.onActivity(a -> ((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment))
                    .getNavController().navigate(R.id.vocabularyDetailFragment, VocabularyDetailFragment.searchArguments("5aecfbf5b9a23aee")));
            await(scenario, a -> a.findViewById(R.id.detail_save) != null && a.findViewById(R.id.detail_save).isEnabled());
            scenario.onActivity(a -> {
                assertEquals(a.getString(initiallySaved[0] ? R.string.detail_save : R.string.detail_unsave),
                        ((TextView)a.findViewById(R.id.detail_save)).getText().toString());
                a.findViewById(R.id.detail_save).performClick(); // Restore the original saved flag.
            });
            await(scenario, a -> a.findViewById(R.id.detail_save).isEnabled()
                    && ((TextView)a.findViewById(R.id.detail_save)).getText().toString().equals(a.getString(initiallySaved[0] ? R.string.detail_unsave : R.string.detail_save)));
        }
    }
    private String snapshot(Context context) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            return worker.submit(() -> {
                StringBuilder result = new StringBuilder();
                for (String name : List.of("catalog.db", "userdata.db")) {
                    File file = context.getDatabasePath(name);
                    if (!file.exists()) { result.append(name).append(":absent"); continue; }
                    try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.toString(), null, SQLiteDatabase.OPEN_READONLY);
                         var tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null)) {
                        while (tables.moveToNext()) {
                            String table = tables.getString(0); result.append(name).append(table);
                            try (var rows = db.rawQuery("SELECT * FROM \"" + table.replace("\"", "\"\"") + "\" ORDER BY rowid", null)) {
                                while (rows.moveToNext()) for (int c=0; c<rows.getColumnCount(); c++) {
                                    if (rows.getType(c) == android.database.Cursor.FIELD_TYPE_BLOB)
                                        result.append(Arrays.toString(rows.getBlob(c)));
                                    else result.append(rows.getString(c));
                                    result.append('\u0000');
                                }
                            }
                        }
                    }
                }
                return result.toString();
            }).get(15, TimeUnit.SECONDS); // Test harness only; no production main-thread waits.
        } finally { worker.shutdown(); }
    }
    @Test public void opaqueNavigationArgumentsAndReboundHolder() {
        assertTrue(VocabularyDetailFragment.validArguments(VocabularyDetailFragment.searchArguments("ffffffffffffffff")));
        assertFalse(VocabularyDetailFragment.validArguments(VocabularyDetailFragment.searchArguments("unknown")));
        assertTrue(VocabularyDetailFragment.validArguments(VocabularyDetailFragment.arguments("paused-pack", "ffffffffffffffff")));
        Context c = new android.view.ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VocaMind);
        List<String> clicks = new ArrayList<>();
        SearchAdapter adapter = new SearchAdapter((g, id) -> clicks.add(id));
        var a = new SearchAdapter.Row(new com.vocamind.app.domain.catalog.CatalogVocabulary("ffffffffffffffff", "same", "same", "n", "nghĩa rất dài ".repeat(100), "e", "v", null, "s", "1"), 1);
        var b = new SearchAdapter.Row(new com.vocamind.app.domain.catalog.CatalogVocabulary("0000000000000001", "same", "same", "n", "nghĩa khác", "e", "v", null, "s", "1"), 1);
        adapter.submitList(List.of(a, b));
        var holder = adapter.onCreateViewHolder(new android.widget.FrameLayout(c), 0);
        adapter.onBindViewHolder(holder, 1); holder.itemView.performClick();
        adapter.onBindViewHolder(holder, 0); holder.itemView.performClick();
        assertEquals(List.of("0000000000000001", "ffffffffffffffff"), clicks);
        assertFalse(SearchAdapter.DIFF.areItemsTheSame(a,b));
        assertTrue(SearchAdapter.DIFF.areItemsTheSame(a, new SearchAdapter.Row(a.word, 2)));
        assertFalse(SearchAdapter.DIFF.areContentsTheSame(a, new SearchAdapter.Row(a.word, 2)));
    }
}
