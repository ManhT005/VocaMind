package com.vocamind.app.feature.discovery;

import static org.junit.Assert.*;

import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import android.database.Cursor;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.entity.VocabularyEntry;
import com.vocamind.app.data.catalog.upgrade.CatalogStartupCoordinator;
import com.vocamind.app.data.user.entity.UserWordProgress;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real UI -> production ViewModels/use cases/repositories -> Room/FTS; no injected dependencies. */
@RunWith(AndroidJUnit4.class)
public class DiscoveryJourneyInstrumentedTest extends DiscoveryHarness {
    private String pack() { return oracle.catalogDao().packs().get(0).id; }
    private List<VocabularyEntry> words() { return oracle.catalogDao().words(pack()); }
    private void openPacks() throws Exception {
        tap(R.id.open_packs);
        await("packs", a -> a.findViewById(R.id.pack_list) != null
                && ((android.widget.LinearLayout)a.findViewById(R.id.pack_list)).getChildCount() == oraclePackCount);
    }
    private int oraclePackCount;
    private void openPack() throws Exception {
        oraclePackCount = oracle.catalogDao().packs().size();
        openPacks();
        String title = oracle.catalogDao().packs().get(0).title;
        // An active title has a suffix, so match the known title prefix on a visible card.
        onView(org.hamcrest.Matchers.allOf(withId(R.id.pack_title), withText(org.hamcrest.Matchers.startsWith(title))))
                .perform(scrollTo(), click());
        await("activation ready", a -> visible(a,R.id.activation) && a.findViewById(R.id.activation).isEnabled());
        listCount(R.id.words, words().size());
        scenario.onActivity(a -> assertEquals(packId, ((NavHostFragment)a.getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment)).getNavController().getCurrentBackStackEntry().getArguments().getString("packId")));
    }
    private String packId;
    private void start() { packId = pack(); launch(); }
    private void active(boolean active) throws Exception {
        await("pack " + active, a -> text(a,R.id.activation,a.getString(active ? R.string.pack_pause : R.string.pack_activate))
                && a.findViewById(R.id.activation).isEnabled());
        assertEquals(active, user.userDao().pack(pack()).isActive);
    }
    private void detail(VocabularyEntry word, String expectedPack) throws Exception {
        await("detail " + word.id, a -> text(a,R.id.detail_lemma,word.lemma) && visible(a,R.id.detail_save)
                && a.findViewById(R.id.detail_save).isEnabled());
        scenario.onActivity(a -> {
            var args = ((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment))
                    .getNavController().getCurrentBackStackEntry().getArguments();
            assertEquals(word.id,args.getString("vocabularyId"));
            assertEquals(expectedPack,args.getString("packId"));
            assertTrue(text(a,R.id.detail_pos,word.pos));
            assertTrue(text(a,R.id.detail_meaning,word.meaningVi));
            assertTrue(text(a,R.id.detail_example_en,word.exampleEn));
            assertTrue(text(a,R.id.detail_example_vi,word.exampleVi));
            assertTrue(text(a,R.id.detail_ipa,word.ipa == null || word.ipa.trim().isEmpty() ? "Chưa có phiên âm" : word.ipa));
        });
        onView(withId(R.id.detail_example_en)).perform(scrollTo()).check(matches(withText(word.exampleEn)));
        onView(withId(R.id.detail_example_vi)).perform(scrollTo()).check(matches(withText(word.exampleVi)));
    }
    private String catalogSnapshot() {
        // Logical snapshot of the actual selected runtime DB, including FTS/memberships/sources.
        var db = CatalogStartupCoordinator.bootstrap(context).openDatabase(context);
        try { return logicalSnapshot(db); } finally { db.close(); }
    }
    private String logicalSnapshot(com.vocamind.app.data.catalog.CatalogDatabase db) {
            StringBuilder s = new StringBuilder();
            for (String table : List.of("VocabularyEntry","LearningPack","PackWord","ContentSource","VocabularyFts")) {
                try (Cursor c = db.getOpenHelper().getReadableDatabase().query("SELECT * FROM " + table + " ORDER BY rowid")) {
                    s.append(table);
                    while(c.moveToNext()) for(int i=0;i<c.getColumnCount();i++) s.append(c.getString(i)).append('\u0000');
                }
            }
            return s.toString();
    }
    private void preserved(UserWordProgress p, boolean saved) {
        assertNotNull(p); assertEquals(saved,p.isSaved); assertEquals(3,p.box); assertEquals(321000L,p.dueAt);
        assertEquals(Long.valueOf(12000),p.learnedAt); assertEquals(Long.valueOf(22000),p.lastReviewedAt);
        assertEquals(7,p.reviewCount); assertEquals(2,p.consecutiveMistakes); assertFalse(p.isLeech);
    }
    private void seedProgress(String id) {
        UserWordProgress p = new UserWordProgress(); p.vocabularyId=id; p.box=3; p.dueAt=321000;
        p.learnedAt=12000L; p.lastReviewedAt=22000L; p.reviewCount=7; p.consecutiveMistakes=2; p.isSaved=true;
        user.userDao().saveProgress(p);
    }
    @Test public void offlinePackOrderVisibleItemsDetailsBothIpaAndNoWrites() throws Exception {
        // Use the separate oracle for the baseline: do not prewarm the app's catalog startup.
        // In the independent driver run pm clear guarantees the first actual UI opens the prepack cold.
        String before = logicalSnapshot(oracle); start(); openPack();
        List<VocabularyEntry> expected=words();
        // Every attached row is asserted in predefined order, including duplicate lemma/different sense.
        for(int i=0;i<expected.size();i++) {
            var word=expected.get(i); row(R.id.words,i,word.meaningVi,true); detail(word,pack());
            tap(R.id.back); listCount(R.id.words,expected.size());
        }
        assertTrue("fixture must include IPA",expected.stream().anyMatch(w -> w.ipa!=null));
        assertTrue("fixture must include null IPA",expected.stream().anyMatch(w -> w.ipa==null));
        assertTrue(user.userDao().packs().isEmpty()); assertTrue(user.userDao().allProgress().isEmpty());
        assertEquals(before,catalogSnapshot());
    }
    @Test public void activateCloseLaunchPauseCloseLaunchPreservesLearningAndSaved() throws Exception {
        String id=words().get(0).id; seedProgress(id); String before=catalogSnapshot();
        start(); openPack(); tap(R.id.activation); active(true);
        long activatedAt=user.userDao().pack(pack()).activatedAt;
        reopen(); openPack(); active(true); tap(R.id.activation); active(false);
        reopen(); openPack(); active(false);
        assertEquals(activatedAt,user.userDao().pack(pack()).activatedAt);
        preserved(user.userDao().progress(id),true); assertEquals(before,catalogSnapshot());
    }
    @Test public void allInactiveTodayCtaOpensDiscoveryWithoutActivation() throws Exception {
        start(); tap(R.id.open_today);
        await("no pack CTA", a -> visible(a,R.id.explore)); tap(R.id.explore);
        await("discovery from Today", a -> a.findViewById(R.id.pack_list)!=null
                && ((android.widget.LinearLayout)a.findViewById(R.id.pack_list)).getChildCount()>0);
        assertTrue(user.userDao().packs().isEmpty());
        assertTrue(user.userDao().allProgress().isEmpty());
    }
    @Test public void saveBackReopenCloseLaunchUnsavePreservesCatalogAndLearning() throws Exception {
        var word=words().get(0); seedProgress(word.id);
        // Explicit, known unsaved starting state while retaining existing review history.
        user.userDao().setSaved(word.id,false); String before=catalogSnapshot(); start(); openPack();
        row(R.id.words,0,word.meaningVi,true); detail(word,pack());
        await("unsaved", a -> text(a,R.id.detail_save,a.getString(R.string.detail_save)));
        tap(R.id.detail_save);
        await("saved", a -> text(a,R.id.detail_save,a.getString(R.string.detail_unsave)) && a.findViewById(R.id.detail_save).isEnabled());
        preserved(user.userDao().progress(word.id),true);
        tap(R.id.back); listCount(R.id.words,words().size()); row(R.id.words,0,word.meaningVi,true); detail(word,pack());
        scenario.onActivity(a -> assertTrue(text(a,R.id.detail_save,a.getString(R.string.detail_unsave))));
        reopen(); openPack(); row(R.id.words,0,word.meaningVi,true); detail(word,pack());
        scenario.onActivity(a -> assertTrue(text(a,R.id.detail_save,a.getString(R.string.detail_unsave))));
        tap(R.id.detail_save);
        await("unsaved persisted", a -> text(a,R.id.detail_save,a.getString(R.string.detail_save)) && a.findViewById(R.id.detail_save).isEnabled());
        preserved(user.userDao().progress(word.id),false); assertEquals(before,catalogSnapshot());
    }
    @Test public void offlineEnglishVietnameseRealFtsVisibleResultDetailResetNoMatch() throws Exception {
        String before=catalogSnapshot(); start(); tap(R.id.open_vocabulary); message(R.string.search_no_input);
        var word=oracle.catalogDao().vocabulary("5aecfbf5b9a23aee");
        for(String q : List.of("BANK","NGÂN HÀNG")) {
            query(q); message(R.string.search_content); listCount(R.id.results,1);
            row(R.id.results,0,word.meaningVi,true); detail(word,null);
            tap(R.id.back); message(R.string.search_content);
        }
        scenario.recreate(); message(R.string.search_content);
        query("zzzznonmatchingui02"); message(R.string.search_no_match); assertHidden(R.id.results); assertHidden(R.id.retry);
        query("   "); message(R.string.search_no_input); assertHidden(R.id.results); assertHidden(R.id.loading);
        query("book"); message(R.string.search_content); listCount(R.id.results,2);
        // Same lemma, distinct sense: real click must route the verb stableId.
        var verb=oracle.catalogDao().vocabulary("d17314a48504e2ac");
        row(R.id.results,1,verb.meaningVi,true); detail(verb,null); tap(R.id.back); message(R.string.search_content);
        tap(R.id.reset); message(R.string.search_no_input); assertHidden(R.id.results); assertHidden(R.id.loading);
        assertTrue(user.userDao().allProgress().isEmpty()); assertTrue(user.userDao().packs().isEmpty());
        assertEquals(before,catalogSnapshot());
    }
    /** Opt-in expected failure: driver requires failure + all artifacts; default suite skips it. */
    @Test public void failureArtifactProbe() throws Exception {
        org.junit.Assume.assumeTrue("Only external harness verification enables this probe",
                "true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("ui02FailureProbe")));
        start(); tap(R.id.open_vocabulary); message(R.string.search_no_input);
        fail("UI02_EXPECTED_ARTIFACT_PROBE");
    }
}
