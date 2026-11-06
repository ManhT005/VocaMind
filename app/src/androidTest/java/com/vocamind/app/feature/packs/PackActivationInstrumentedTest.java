package com.vocamind.app.feature.packs;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.core.CatalogProvider;
import com.vocamind.app.core.RepositoryProvider;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.learning.*;
import com.vocamind.app.domain.pack.PackState;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Run only on an isolated test install. Never clears or resets app databases. */
@RunWith(AndroidJUnit4.class)
public class PackActivationInstrumentedTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static NavController nav(MainActivity activity) {
        return ((NavHostFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment)).getNavController();
    }
    private static void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean[] ready = {false};
            scenario.onActivity(a -> ready[0] = condition.test(a));
            if (ready[0]) return;
            Thread.sleep(30);
        }
        fail("UI condition did not settle within 20 seconds");
    }
    private static boolean text(MainActivity a, int id, int string) {
        TextView view = a.findViewById(id);
        return view != null && view.getText().toString().equals(a.getString(string));
    }
    private static void drainAndAwait(ActivityScenario<MainActivity> scenario, java.util.Queue<Runnable> worker,
            Predicate<MainActivity> condition) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (System.nanoTime() < deadline) {
            Runnable task;
            while ((task = worker.poll()) != null) task.run();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            boolean[] ready = {false};
            scenario.onActivity(a -> ready[0] = condition.test(a));
            if (ready[0]) return;
            Thread.sleep(30);
        }
        fail("Controlled worker/UI did not settle");
    }
    private static void detail(ActivityScenario<MainActivity> scenario, String pack) throws Exception {
        Bundle args = new Bundle(); args.putString("packId", pack);
        scenario.onActivity(a -> nav(a).navigate(R.id.packDetailFragment, args));
        await(scenario, a -> a.findViewById(R.id.activation) != null && a.findViewById(R.id.activation).isEnabled());
    }
    private static void toggle(ActivityScenario<MainActivity> scenario, boolean active) throws Exception {
        scenario.onActivity(a -> {
            View button = a.findViewById(R.id.activation);
            button.performClick();
            assertFalse("pending must disable immediately", button.isEnabled());
            button.performClick(); // stale repeated callback must be rejected by mutation guard
        });
        await(scenario, a -> text(a, R.id.activation, active ? R.string.pack_pause : R.string.pack_activate)
                && a.findViewById(R.id.activation).isEnabled());
    }

    @Test public void offlineFirstLaunchCtaActivateVocabularyBackPauseAndConfirmedRotation() throws Exception {
        assertEquals("Enable airplane mode before first app launch", 1,
                Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
        try (CatalogProvider catalog = new CatalogProvider(context);
             RepositoryProvider repos = new RepositoryProvider(context, catalog);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(repos.packs().getPackStates().get().stream().noneMatch(PackState::isActive));
            String pack = repos.packs().getPackStates().get().get(0).getPack().id;
            List<StudyItem> expected = repos.packs().getPackWords(pack).get();
            scenario.onActivity(a -> a.findViewById(R.id.open_today).performClick());
            await(scenario, a -> a.findViewById(R.id.explore) != null
                    && a.findViewById(R.id.explore).getVisibility() == View.VISIBLE);
            scenario.onActivity(a -> a.findViewById(R.id.explore).performClick());
            await(scenario, a -> a.findViewById(R.id.pack_list) != null
                    && ((LinearLayout)a.findViewById(R.id.pack_list)).getChildCount() > 0);
            scenario.onActivity(a -> ((LinearLayout)a.findViewById(R.id.pack_list)).getChildAt(0).performClick());
            await(scenario, a -> text(a, R.id.activation, R.string.pack_activate) && a.findViewById(R.id.activation).isEnabled());
            UserDatabase user = UserDatabase.open(context);
            try { assertTrue(user.userDao().packs().isEmpty()); } finally { user.close(); }
            toggle(scenario, true);
            long first = repos.packs().getPackStates().get().get(0).getActivatedAtEpochMillis();
            await(scenario, a -> a.findViewById(R.id.words) != null
                    && ((RecyclerView)a.findViewById(R.id.words)).getAdapter().getItemCount() == expected.size());
            scenario.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.words);
                PackWordAdapter adapter = (PackWordAdapter)list.getAdapter();
                for (int i = 0; i < expected.size(); i++) {
                    assertEquals(expected.get(i).getVocabulary().stableId,
                            adapter.getCurrentList().get(i).getVocabulary().stableId);
                }
                PackWordAdapter.Holder holder = adapter.onCreateViewHolder(list, 0);
                adapter.onBindViewHolder(holder, 0);
                String ipa = expected.get(0).getVocabulary().ipa;
                assertEquals(ipa == null || ipa.trim().isEmpty() ? a.getString(R.string.word_ipa_missing) : ipa,
                        holder.binding.wordIpa.getText().toString());
                assertEquals(a.getString(R.string.word_unreviewed), holder.binding.wordStatus.getText().toString());
                holder.itemView.performClick();
            });
            String id = expected.get(0).getVocabulary().stableId;
            await(scenario, a -> a.findViewById(R.id.route_message) != null
                    && ((TextView)a.findViewById(R.id.route_message)).getText().toString().contains(id));
            scenario.onActivity(a -> assertEquals(id, nav(a).getCurrentBackStackEntry().getArguments().getString("vocabularyId")));
            scenario.onActivity(a -> a.findViewById(R.id.back).performClick());
            await(scenario, a -> text(a, R.id.activation, R.string.pack_pause) && a.findViewById(R.id.activation).isEnabled());
            scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            await(scenario, a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    && text(a, R.id.activation, R.string.pack_pause) && a.findViewById(R.id.activation).isEnabled());
            scenario.recreate();
            await(scenario, a -> text(a, R.id.activation, R.string.pack_pause) && a.findViewById(R.id.activation).isEnabled());
            toggle(scenario, false);
            assertEquals(Long.valueOf(first), repos.packs().getPackStates().get().get(0).getActivatedAtEpochMillis());
            scenario.onActivity(a -> a.findViewById(R.id.back).performClick());
            await(scenario, a -> a.findViewById(R.id.pack_list) != null
                    && ((LinearLayout)a.findViewById(R.id.pack_list)).getChildCount() > 0);
            scenario.onActivity(a -> assertFalse(((TextView)((LinearLayout)a.findViewById(R.id.pack_list))
                    .getChildAt(0).findViewById(R.id.pack_title)).getText().toString().contains(a.getString(R.string.pack_active))));
            scenario.onActivity(a -> nav(a).popBackStack());
            await(scenario, a -> a.findViewById(R.id.explore) != null && a.findViewById(R.id.explore).getVisibility() == View.VISIBLE);
        }
    }

    /** Invoked in separate runs before/after force-stop/reboot; never clears existing fixture. */
    @Test public void persistenceProbe() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        boolean target = Boolean.parseBoolean(args.getString("active", "true"));
        boolean verifyOnly = Boolean.parseBoolean(args.getString("verifyOnly", "false"));
        if (!verifyOnly) {
            com.vocamind.app.data.catalog.CatalogDatabase seedCatalog = com.vocamind.app.data.catalog.CatalogDatabase.open(context,"catalog.db");
            UserDatabase seedUser = UserDatabase.open(context);
            try {
                String pack = seedCatalog.catalogDao().packs().get(0).id;
                String word = seedCatalog.catalogDao().words(pack).get(0).id;
                if (seedUser.userDao().progress(word) == null) {
                    assertTrue(new com.vocamind.app.data.repository.RoomReviewRepository(seedCatalog.catalogDao(),seedUser)
                            .recordReview(word,new LeitnerScheduler().schedule(LeitnerState.newWord(),Rating.REMEMBERED,1000,ZoneOffset.UTC),1000).isSuccess());
                    assertEquals(1,seedUser.userDao().setSaved(word,true));
                }
            } finally { seedUser.close(); seedCatalog.close(); }
        }
        try (CatalogProvider catalog = new CatalogProvider(context);
             RepositoryProvider repos = new RepositoryProvider(context, catalog)) {
            String pack = repos.packs().getPackStates().get().get(0).getPack().id;
            String word = repos.packs().getPackWords(pack).get().get(0).getVocabulary().stableId;
            WordProgress before = repos.study().getProgress(word).get().get();
            Long activatedAt = repos.packs().getPackStates().get().get(0).getActivatedAtEpochMillis();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                detail(scenario, pack);
                boolean current = repos.packs().getPackStates().get().get(0).isActive();
                if (verifyOnly) assertEquals(target, current);
                else if (current != target) toggle(scenario, target);
                await(scenario, a -> text(a, R.id.activation, target ? R.string.pack_pause : R.string.pack_activate)
                        && a.findViewById(R.id.activation).isEnabled());
                scenario.recreate();
                await(scenario, a -> text(a, R.id.activation, target ? R.string.pack_pause : R.string.pack_activate)
                        && a.findViewById(R.id.activation).isEnabled());
            }
            WordProgress after = repos.study().getProgress(word).get().get();
            assertEquals(before.getDueAtEpochMillis(), after.getDueAtEpochMillis());
            assertEquals(before.getReviewCount(), after.getReviewCount());
            assertEquals(before.getBox(), after.getBox());
            assertTrue(after.isSaved());
            if (activatedAt != null) assertEquals(activatedAt, repos.packs().getPackStates().get().get(0).getActivatedAtEpochMillis());
        }
    }

    @Test public void rotationWhileWritePendingKeepsSingleMutationAndReconciles() throws Exception {
        java.util.Queue<Runnable> worker = new java.util.concurrent.ConcurrentLinkedQueue<>();
        int[] writes = {0};
        try (CatalogProvider catalog = new CatalogProvider(context);
             RepositoryProvider repos = new RepositoryProvider(context, catalog);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            String pack = repos.packs().getPackStates().get().get(0).getPack().id;
            boolean target = !repos.packs().getPackStates().get().get(0).isActive();
            scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            await(scenario, a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
            scenario.onActivity(a -> new androidx.lifecycle.ViewModelProvider(a, new androidx.lifecycle.ViewModelProvider.Factory() {
                @Override public <T extends androidx.lifecycle.ViewModel> T create(Class<T> type) {
                    return type.cast(new PacksViewModel(repos.packs()::getPackStates, (id, active) -> {
                        writes[0]++;
                        return new com.vocamind.app.domain.pack.SetPackActive(repos.packs(), () -> 1234).execute(id, active);
                    }, worker::add, task -> new android.os.Handler(android.os.Looper.getMainLooper()).post(task), () -> {}));
                }
            }).get(PacksViewModel.class));
            Bundle args = new Bundle(); args.putString("packId", pack);
            scenario.onActivity(a -> nav(a).navigate(R.id.packDetailFragment, args));
            // All injected DB work executes here, on the instrumentation worker, never the UI thread.
            await(scenario, a -> a.findViewById(R.id.activation) != null);
            scenario.onActivity(a -> {
                assertFalse(a.findViewById(R.id.activation).isEnabled());
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            await(scenario, a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            drainAndAwait(scenario, worker, a -> a.findViewById(R.id.activation) != null && a.findViewById(R.id.activation).isEnabled());
            scenario.onActivity(a -> {
                a.findViewById(R.id.activation).performClick();
                assertFalse(a.findViewById(R.id.activation).isEnabled());
                a.findViewById(R.id.activation).performClick();
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            });
            await(scenario, a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
            scenario.recreate();
            scenario.onActivity(a -> assertFalse(a.findViewById(R.id.activation).isEnabled()));
            drainAndAwait(scenario, worker, a -> text(a, R.id.activation, target ? R.string.pack_pause : R.string.pack_activate)
                    && a.findViewById(R.id.activation).isEnabled());
            assertEquals(1, writes[0]);
            assertEquals(target, repos.packs().getPackStates().get().get(0).isActive());
        }
    }

    @Test public void writeFailureAndSuccessfulWriteFailedRereadUseReadOnlyRetry() throws Exception {
        int[] writes = {0};
        try (CatalogProvider catalog = new CatalogProvider(context);
             RepositoryProvider repos = new RepositoryProvider(context, catalog);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                String pack = repos.packs().getPackStates().get().get(0).getPack().id;
                boolean before = repos.packs().getPackStates().get().get(0).isActive();
                scenario.onActivity(a -> new androidx.lifecycle.ViewModelProvider(a, new androidx.lifecycle.ViewModelProvider.Factory() {
                    @Override public <T extends androidx.lifecycle.ViewModel> T create(Class<T> type) {
                        return type.cast(new PacksViewModel(repos.packs()::getPackStates, (id, active) -> {
                            writes[0]++;
                            if (writes[0] == 1) return com.vocamind.app.domain.common.RepoResult.failure(
                                    com.vocamind.app.domain.common.RepoError.WRITE_FAILED);
                            assertTrue(new com.vocamind.app.domain.pack.SetPackActive(repos.packs(), () -> 5678)
                                    .execute(id, active).isSuccess());
                            return com.vocamind.app.domain.common.RepoResult.failure(
                                    com.vocamind.app.domain.common.RepoError.READ_FAILED);
                        }, worker, task -> new android.os.Handler(android.os.Looper.getMainLooper()).post(task), () -> {}));
                    }
                }).get(PacksViewModel.class));
                detail(scenario, pack);
                scenario.onActivity(a -> a.findViewById(R.id.activation).performClick());
                await(scenario, a -> text(a, R.id.activation, R.string.packs_retry));
                assertEquals(before, repos.packs().getPackStates().get().get(0).isActive());
                scenario.onActivity(a -> a.findViewById(R.id.activation).performClick());
                await(scenario, a -> text(a, R.id.activation, before ? R.string.pack_pause : R.string.pack_activate)
                        && a.findViewById(R.id.activation).isEnabled());
                assertEquals(1, writes[0]);
                scenario.onActivity(a -> a.findViewById(R.id.activation).performClick());
                await(scenario, a -> text(a, R.id.activation, R.string.packs_retry));
                assertEquals(!before, repos.packs().getPackStates().get().get(0).isActive());
                scenario.onActivity(a -> a.findViewById(R.id.activation).performClick());
                await(scenario, a -> text(a, R.id.activation, before ? R.string.pack_activate : R.string.pack_pause)
                        && a.findViewById(R.id.activation).isEnabled());
                assertEquals(2, writes[0]);
            } finally {
                scenario.close(); // ViewModel queues its final close before the injected worker shuts down.
                worker.shutdown();
                assertTrue(worker.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS));
            }
        }
    }

    /** Driver runs with finishActivities=false, then kills the background process externally. */
    @Test public void backgroundDestinationForProcessRestoration() throws Exception {
        String pack;
        try (CatalogProvider catalog = new CatalogProvider(context);
             RepositoryProvider repos = new RepositoryProvider(context, catalog)) {
            pack = repos.packs().getPackStates().get().get(0).getPack().id;
        }
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        detail(scenario, pack);
        scenario.onActivity(a -> assertTrue(a.moveTaskToBack(true)));
        // Intentionally retain the task for the external process-death driver, not a normal suite test.
    }

    @Test public void exclusiveSharedScopeAndProgressSurvivePauseInDedicatedFixture() {
        String token = java.util.UUID.randomUUID().toString();
        String cn = "pack02-scope-catalog-" + token, un = "pack02-scope-user-" + token;
        com.vocamind.app.data.catalog.CatalogDatabase catalog = com.vocamind.app.data.catalog.CatalogDatabase.open(context, cn);
        UserDatabase user = UserDatabase.open(context, un);
        try {
            androidx.sqlite.db.SupportSQLiteDatabase sql = catalog.getOpenHelper().getWritableDatabase();
            for (String table : new String[]{"PackWord", "VocabularyFts", "VocabularyEntry", "LearningPack", "ContentSource"})
                sql.execSQL("DELETE FROM " + table); // UUID-named test fixture only
            sql.execSQL("INSERT INTO ContentSource VALUES ('src','Test','https://example.invalid','LicenseRef-Test','https://example.invalid/l','2026-11-06','Test')");
            for (int i = 0; i < 2; i++) sql.execSQL("INSERT INTO LearningPack VALUES (?,?,?,?,?,?)",
                    new Object[]{i == 0 ? "A" : "B", "Test", "d", "test", i, "1"});
            String a = "0000000000000001", b = "0000000000000002", shared = "00000000000000aa";
            for (String id : new String[]{a,b,shared}) sql.execSQL("INSERT INTO VocabularyEntry "
                    + "(id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                    + "VALUES (?,?,?,'noun','m','e','v',NULL,'src','1')", new Object[]{id,id,id});
            sql.execSQL("INSERT INTO PackWord VALUES ('A',?,0),('A',?,1),('B',?,0),('B',?,1)",new Object[]{shared,a,shared,b});
            com.vocamind.app.data.repository.RoomPackRepository packs = new com.vocamind.app.data.repository.RoomPackRepository(catalog.catalogDao(), user.userDao());
            com.vocamind.app.data.repository.RoomStudyRepository study = new com.vocamind.app.data.repository.RoomStudyRepository(catalog.catalogDao(), user.userDao());
            com.vocamind.app.data.repository.RoomReviewRepository reviews = new com.vocamind.app.data.repository.RoomReviewRepository(catalog.catalogDao(),user);
            packs.activatePack("A",0); packs.activatePack("B",1); packs.pausePack("A");
            List<com.vocamind.app.domain.catalog.CatalogVocabulary> fresh = study.newWords(20).get();
            assertEquals(2,fresh.size()); assertEquals(shared,fresh.get(0).stableId); assertEquals(b,fresh.get(1).stableId);
            packs.activatePack("A",2);
            for (String id : new String[]{a,shared}) assertTrue(reviews.recordReview(id,
                    new LeitnerScheduler().schedule(LeitnerState.newWord(),Rating.REMEMBERED,1000,ZoneOffset.UTC),1000).isSuccess());
            user.userDao().setSaved(a,true);
            WordProgress before = study.getProgress(a).get().get();
            packs.pausePack("A");
            List<StudyItem> due = study.dueItems(Long.MAX_VALUE,20).get();
            assertEquals(1,due.size()); assertEquals(shared,due.get(0).getVocabulary().stableId);
            packs.activatePack("A",9999);
            assertEquals(Long.valueOf(0),packs.getPackStates().get().get(0).getActivatedAtEpochMillis());
            WordProgress after = study.getProgress(a).get().get();
            assertEquals(before.getDueAtEpochMillis(),after.getDueAtEpochMillis());
            assertEquals(before.getReviewCount(),after.getReviewCount()); assertTrue(after.isSaved());
            assertEquals(2,user.userDao().reviews().size());
            due=study.dueItems(Long.MAX_VALUE,20).get();
            assertEquals(2,due.size()); assertNotEquals(due.get(0).getVocabulary().stableId,due.get(1).getVocabulary().stableId);
            packs.pausePack("A"); packs.pausePack("B");
            assertTrue(study.newWords(20).get().isEmpty()); assertTrue(study.dueItems(Long.MAX_VALUE,20).get().isEmpty());
        } finally { catalog.close(); user.close(); context.deleteDatabase(cn); context.deleteDatabase(un); }
    }
}
