package com.vocamind.app.feature.packs;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.R;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.learning.*;
import com.vocamind.app.feature.vocabulary.VocabularyDetailFragment;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import android.os.Looper;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public class VocabularyListTest {
    private StudyItem item(String id, String ipa, WordProgress progress) {
        return new StudyItem(new CatalogVocabulary(id, "same lemma", "same", "noun", "nghĩa dài", "e", "v", ipa, "s", "1"), progress);
    }
    @Test public void recycledHolderReplacesIpaStatusAndClickTargetAfterRefresh() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VocaMind);
        List<String> clicked = new ArrayList<>();
        PackWordAdapter adapter = new PackWordAdapter(clicked::add);
        PackWordAdapter.Holder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
        StudyItem a = item("ffffffffffffffff", "/a/", new WordProgress("ffffffffffffffff", 0, 0, 1L, 1L, 0, false, 1, false));
        StudyItem b = item("0000000000000001", null, new WordProgress("0000000000000001", 5, 0, null, null, 0, false, 0, true));
        adapter.submitList(List.of(a, b));
        adapter.onBindViewHolder(holder, 0);
        assertEquals("/a/", holder.binding.wordIpa.getText().toString());
        assertEquals("Học lại", holder.binding.wordStatus.getText().toString());
        adapter.onBindViewHolder(holder, 1);
        assertEquals("Chưa có phiên âm", holder.binding.wordIpa.getText().toString());
        assertEquals("Chưa có lượt ôn", holder.binding.wordStatus.getText().toString());
        holder.itemView.performClick();
        assertEquals(List.of("0000000000000001"), clicked);
        assertFalse(PackWordAdapter.DIFF.areItemsTheSame(a, b));
        assertTrue(PackWordAdapter.DIFF.areItemsTheSame(a, item("ffffffffffffffff", null, null)));
        for (int box = 1; box <= 5; box++) {
            PackWordAdapter next = new PackWordAdapter(clicked::add);
            next.submitList(List.of(item("id", " ", new WordProgress("id", box, 0, 1L, 1L, 0, false, 1, false))));
            next.onBindViewHolder(holder, 0);
            assertEquals("Cấp ôn " + box, holder.binding.wordStatus.getText().toString());
            assertEquals("Chưa có phiên âm", holder.binding.wordIpa.getText().toString());
        }
        PackWordAdapter refreshed = new PackWordAdapter(clicked::add);
        refreshed.submitList(List.of(b, a));
        refreshed.onBindViewHolder(holder, 0);
        holder.itemView.performClick();
        assertEquals("0000000000000001", clicked.get(1));
    }
    @Test public void realGraphDispatchReceiveBackAndRecreation() throws Exception {
        org.robolectric.android.controller.ActivityController<com.vocamind.app.MainActivity> controller =
                org.robolectric.Robolectric.buildActivity(com.vocamind.app.MainActivity.class).setup();
        com.vocamind.app.MainActivity activity = controller.get();
        androidx.navigation.fragment.NavHostFragment host = (androidx.navigation.fragment.NavHostFragment)
                activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        androidx.navigation.NavController nav = host.getNavController();
        // Use a catalog pack read from the real local asset on the worker.
        java.util.concurrent.atomic.AtomicReference<String> pack = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<com.vocamind.app.domain.catalog.CatalogVocabulary> expectedWord = new java.util.concurrent.atomic.AtomicReference<>();
        final android.content.Context dbContext = activity.getApplicationContext();
        Thread reader = new Thread(() -> {
            com.vocamind.app.data.catalog.CatalogDatabase db = com.vocamind.app.data.catalog.CatalogDatabase.open(dbContext, "nav-fixture.db");
            try {
                pack.set(db.catalogDao().packs().get(0).id);
                var word = db.catalogDao().words(pack.get()).get(0);
                expectedWord.set(com.vocamind.app.data.catalog.CatalogMapper.vocabulary(word));
                var user = com.vocamind.app.data.user.UserDatabase.open(dbContext);
                try {
                    var progress = new com.vocamind.app.data.user.entity.UserWordProgress();
                    progress.vocabularyId = word.id; progress.box = 3; progress.dueAt = 12345;
                    progress.learnedAt = 100L; progress.lastReviewedAt = 200L; progress.reviewCount = 4;
                    user.userDao().saveProgress(progress);
                } finally { user.close(); }
            } finally { db.close(); }
        });
        reader.start(); reader.join();
        android.os.Bundle args = new android.os.Bundle();
        args.putString("packId", pack.get()); args.putString("packTitle", "Test pack");
        nav.navigate(R.id.packDetailFragment, args);
        activity.getSupportFragmentManager().executePendingTransactions();
        host.getChildFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.words);
        for (int n=0; n<200 && list.getAdapter().getItemCount()==0; n++) {
            Thread.sleep(20); Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertTrue(list.getAdapter().getItemCount()>0);
        PackWordAdapter adapter = (PackWordAdapter) list.getAdapter();
        PackWordAdapter.Holder holder = adapter.onCreateViewHolder(list, 0);
        adapter.onBindViewHolder(holder, 0);
        String expected = adapter.getCurrentList().get(0).getVocabulary().stableId;
        holder.itemView.performClick();
        activity.getSupportFragmentManager().executePendingTransactions();
        host.getChildFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(R.id.vocabularyDetailFragment, nav.getCurrentDestination().getId());
        assertEquals(expected, nav.getCurrentBackStackEntry().getArguments().getString("vocabularyId"));
        assertEquals(pack.get(), nav.getCurrentBackStackEntry().getArguments().getString("packId"));
        android.widget.TextView received = activity.findViewById(R.id.route_message);
        for (int n=0; n<200 && !received.getText().toString().equals(activity.getString(R.string.detail_ready)); n++) {
            Thread.sleep(20); Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(activity.getString(R.string.detail_ready), received.getText().toString());
        assertEquals(expectedWord.get().lemma, ((android.widget.TextView)activity.findViewById(R.id.detail_lemma)).getText().toString());
        activity.findViewById(R.id.detail_save).performClick();
        assertFalse(activity.findViewById(R.id.detail_save).isEnabled());
        // Rotate while the mutation is pending: the retained ViewModel must not replay it.
        controller.recreate();
        activity = controller.get();
        host = (androidx.navigation.fragment.NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        nav = host.getNavController();
        assertEquals(expected, nav.getCurrentBackStackEntry().getArguments().getString("vocabularyId"));
        for (int n=0; n<200 && !((android.widget.TextView)activity.findViewById(R.id.detail_save)).getText().toString().equals(activity.getString(R.string.detail_unsave)); n++) {
            Thread.sleep(20); Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(activity.getString(R.string.detail_unsave), ((android.widget.TextView)activity.findViewById(R.id.detail_save)).getText().toString());
        java.util.concurrent.atomic.AtomicReference<Boolean> persistedSaved = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> verificationFailure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread verifySaved = new Thread(() -> {
            var user = com.vocamind.app.data.user.UserDatabase.open(dbContext);
            try {
                var progress = user.userDao().progress(expected);
                persistedSaved.set(progress.isSaved);
                assertEquals(3, progress.box); assertEquals(12345L, progress.dueAt); assertEquals(4, progress.reviewCount);
            } catch (Throwable failure) { verificationFailure.set(failure); }
            finally { user.close(); }
        });
        verifySaved.start(); verifySaved.join();
        if (verificationFailure.get() != null) throw new AssertionError(verificationFailure.get());
        assertEquals(Boolean.TRUE, persistedSaved.get());
        assertTrue(nav.popBackStack());
        activity.getSupportFragmentManager().executePendingTransactions();
        host.getChildFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(R.id.packDetailFragment, nav.getCurrentDestination().getId());
        assertEquals(pack.get(), nav.getCurrentBackStackEntry().getArguments().getString("packId"));
        nav.navigate(R.id.vocabularyDetailFragment, VocabularyDetailFragment.arguments(pack.get(), expected));
        host.getChildFragmentManager().executePendingTransactions();
        for (int n=0; n<200 && !((android.widget.TextView)activity.findViewById(R.id.detail_save)).getText().toString().equals(activity.getString(R.string.detail_unsave)); n++) {
            Thread.sleep(20); Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(activity.getString(R.string.detail_unsave), ((android.widget.TextView)activity.findViewById(R.id.detail_save)).getText().toString());
        activity.findViewById(R.id.detail_save).performClick();
        for (int n=0; n<200 && !((android.widget.TextView)activity.findViewById(R.id.detail_save)).getText().toString().equals(activity.getString(R.string.detail_save)); n++) {
            Thread.sleep(20); Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(activity.getString(R.string.detail_save), ((android.widget.TextView)activity.findViewById(R.id.detail_save)).getText().toString());
        assertTrue(nav.popBackStack()); host.getChildFragmentManager().executePendingTransactions();
        nav.navigate(R.id.vocabularyDetailFragment, VocabularyDetailFragment.arguments(pack.get(), "unknown-id"));
        host.getChildFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        android.widget.TextView invalid = activity.findViewById(R.id.route_message);
        for (int n=0; n<200 && !invalid.getText().toString().contains("Thiếu hoặc sai"); n++) {
            Thread.sleep(20); Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertTrue(invalid.getText().toString().contains("Thiếu hoặc sai"));
        controller.pause().stop().destroy();
    }
    @Test public void routePreservesOpaqueTextAndRejectsMissingArguments() {
        android.os.Bundle args = VocabularyDetailFragment.arguments("B", "ffffffffffffffff");
        assertTrue(VocabularyDetailFragment.validArguments(args));
        assertEquals("ffffffffffffffff", args.getString("vocabularyId"));
        assertEquals("B", args.getString("packId"));
        assertFalse(VocabularyDetailFragment.validArguments(null));
        assertFalse(VocabularyDetailFragment.validArguments(VocabularyDetailFragment.arguments("A", " ")));
    }
    @Test public void refreshDiscardsLateReadAndLoadsDurableStateAgain() {
        Queue<Runnable> worker = new ArrayDeque<>(), main = new ArrayDeque<>();
        int[] calls = {0};
        PackWordsViewModel model = new PackWordsViewModel(() -> {
            calls[0]++;
            return com.vocamind.app.domain.common.RepoResult.success(List.of(item("id"+calls[0], null, null)));
        }, worker::add, main::add, () -> {});
        worker.remove().run();
        model.refresh();
        main.remove().run();
        assertEquals(AsyncListViewModel.Status.LOADING, model.state().getValue().status);
        worker.remove().run(); main.remove().run();
        assertEquals("id2", model.state().getValue().items.get(0).getVocabulary().stableId);
    }
}
