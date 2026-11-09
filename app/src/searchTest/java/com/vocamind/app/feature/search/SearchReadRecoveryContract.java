package com.vocamind.app.feature.search;

import static org.junit.Assert.*;
import android.os.*;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.*;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.core.app.*;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.*;
import com.vocamind.app.domain.catalog.SearchVocabularyUseCase;
import com.vocamind.app.feature.vocabulary.VocabularyFragment;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.Test;

/** Closes only a UUID-named test DB, then retries against a reopened real Room/FTS repository.
 * The production SearchView is bound to the real use case; application catalog is never closed. */
public abstract class SearchReadRecoveryContract {
    protected void tick() { SystemClock.sleep(20); }
    private void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        boolean[] done = {false};
        while (System.nanoTime() < deadline) {
            scenario.onActivity(a -> done[0] = condition.test(a));
            if (done[0]) return;
            tick();
        }
        fail("Recovery UI did not settle");
    }
    @Test public void realRoomReadFailureShowsErrorAndRetryRecoversCurrentQuery() throws Exception {
        var context = ApplicationProvider.getApplicationContext();
        String name = "search-recovery-" + UUID.randomUUID() + ".db";
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicReference<CatalogDatabase> database = new AtomicReference<>();
        String[] queries = new String[2];
        AtomicReference<java.util.List<String>> expectedIds = new AtomicReference<>();
        worker.submit(() -> {
            database.set(CatalogDatabase.open(context, name));
            database.get().getOpenHelper().getReadableDatabase();
            var dao = database.get().catalogDao();
            var words = dao.words(dao.packs().get(0).id);
            var lemmas = words.stream().map(w -> w.lemma).distinct().collect(java.util.stream.Collectors.toList());
            assertTrue("Recovery fixture needs two distinct inputs", lemmas.size() >= 2);
            queries[0] = lemmas.get(0); queries[1] = lemmas.get(1);
            expectedIds.set(new RoomVocabularySearchRepository(dao)
                    .search(com.vocamind.app.domain.catalog.VocabularySearchQuery.parse(queries[1]))
                    .getWords().stream().map(w -> w.stableId).collect(java.util.stream.Collectors.toList()));
        }).get(15, TimeUnit.SECONDS);
        var useCase = new SearchVocabularyUseCase(query -> new RoomVocabularySearchRepository(database.get().catalogDao()).search(query), worker);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment fragment, @Nullable Bundle saved) {
                        if (!(fragment instanceof VocabularyFragment)) return;
                        Handler main = new Handler(Looper.getMainLooper());
                        var model = new SearchViewModel(new SavedStateHandle(), useCase::execute,
                                (delay, task) -> { main.postDelayed(task, delay); return () -> main.removeCallbacks(task); }, main::post);
                        fragment.getViewModelStore().put("androidx.lifecycle.ViewModelProvider.DefaultKey:" + SearchViewModel.class.getCanonicalName(), model);
                    }
                }, true);
                a.findViewById(R.id.open_vocabulary).performClick();
            });
            await(scenario, a -> a.findViewById(R.id.search) != null);
            scenario.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery(queries[0], false));
            await(scenario, a -> a.findViewById(R.id.results).getVisibility() == View.VISIBLE);
            worker.submit(() -> database.get().close()).get(15, TimeUnit.SECONDS);
            scenario.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery(queries[1], false));
            await(scenario, a -> a.findViewById(R.id.retry).getVisibility() == View.VISIBLE);
            scenario.onActivity(a -> {
                assertEquals(a.getString(R.string.search_error), ((TextView)a.findViewById(R.id.search_message)).getText().toString());
                assertEquals(View.GONE, a.findViewById(R.id.results).getVisibility());
            });
            worker.submit(() -> database.set(CatalogDatabase.open(context, name))).get(15, TimeUnit.SECONDS);
            scenario.onActivity(a -> a.findViewById(R.id.retry).performClick());
            await(scenario, a -> a.findViewById(R.id.results).getVisibility() == View.VISIBLE);
            scenario.onActivity(a -> {
                assertEquals(queries[1], ((SearchView)a.findViewById(R.id.search)).getQuery().toString());
                var adapter = (SearchAdapter)((androidx.recyclerview.widget.RecyclerView)a.findViewById(R.id.results)).getAdapter();
                assertEquals(expectedIds.get(), adapter.getCurrentList().stream().map(r -> r.word.stableId).collect(java.util.stream.Collectors.toList()));
            });
        } finally {
            worker.submit(() -> database.get().close()).get(15, TimeUnit.SECONDS);
            worker.shutdown(); context.deleteDatabase(name);
        }
    }
}
