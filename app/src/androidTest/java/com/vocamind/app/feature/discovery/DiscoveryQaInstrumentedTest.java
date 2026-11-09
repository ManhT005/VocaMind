package com.vocamind.app.feature.discovery;

import static org.junit.Assert.*;

import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.RoomVocabularySearchRepository;
import com.vocamind.app.domain.catalog.VocabularySearchQuery;
import org.junit.Test;
import org.junit.runner.RunWith;

/** QA catalog smoke on a separate .ui02validation.qa install; real Room/FTS throughout. */
@RunWith(AndroidJUnit4.class)
public class DiscoveryQaInstrumentedTest extends DiscoveryHarness {
    @Test public void qaPackVisibleWordAndEnglishVietnameseSearchOpenExactDetails() throws Exception {
        assertEquals("Requires -PcatalogQa=true -PdiscoveryQa=true","com.vocamind.app.ui02validation.qa",context.getPackageName());
        var pack=oracle.catalogDao().packs().get(0);
        var words=oracle.catalogDao().words(pack.id); var word=words.get(0);
        launch(); tap(R.id.open_packs);
        await("QA pack card",a -> a.findViewById(R.id.pack_list)!=null && ((android.widget.LinearLayout)a.findViewById(R.id.pack_list)).getChildCount()>0);
        onView(org.hamcrest.Matchers.allOf(withId(R.id.pack_title),withText(pack.title))).perform(scrollTo(),click());
        listCount(R.id.words,words.size());
        onView(org.hamcrest.Matchers.allOf(withId(R.id.word_title),
                withText(context.getString(R.string.pack_word_title,word.lemma,word.pos)),
                isDescendantOfA(withId(R.id.words)))).perform(click());
        await("QA detail",a -> text(a,R.id.detail_lemma,word.lemma) && visible(a,R.id.detail_save) && a.findViewById(R.id.detail_save).isEnabled());
        scenario.onActivity(a -> {
            assertEquals(word.id,((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController().getCurrentBackStackEntry().getArguments().getString("vocabularyId"));
            assertTrue(text(a,R.id.detail_pos,word.pos)); assertTrue(text(a,R.id.detail_meaning,word.meaningVi));
            assertTrue(text(a,R.id.detail_example_en,word.exampleEn)); assertTrue(text(a,R.id.detail_example_vi,word.exampleVi));
            assertTrue(text(a,R.id.detail_ipa,word.ipa==null?"Chưa có phiên âm":word.ipa));
        });
        tap(R.id.back); listCount(R.id.words,words.size()); tap(R.id.back);
        androidx.test.espresso.Espresso.pressBack();
        await("home",a -> visible(a,R.id.open_vocabulary)); tap(R.id.open_vocabulary);
        for(String q : java.util.List.of(word.lemma,word.meaningVi)) {
            var expected=new RoomVocabularySearchRepository(oracle.catalogDao()).search(VocabularySearchQuery.parse(q)).getWords();
            query(q); message(R.string.search_content); listCount(R.id.results,expected.size());
            scenario.onActivity(a -> {
                var adapter=(com.vocamind.app.feature.search.SearchAdapter)((androidx.recyclerview.widget.RecyclerView)a.findViewById(R.id.results)).getAdapter();
                assertEquals(expected.stream().map(w -> w.stableId).collect(java.util.stream.Collectors.toList()),adapter.getCurrentList().stream().map(r -> r.word.stableId).collect(java.util.stream.Collectors.toList()));
            });
            // QA client/customer share the Vietnamese meaning; click the visible lexical label,
            // then assert the opaque route ID rather than identifying a result by meaning alone.
            var first=expected.get(0);
            onView(org.hamcrest.Matchers.allOf(withId(R.id.word_title),withText(first.lemma),
                    isDescendantOfA(withId(R.id.results)))).perform(click());
            await("QA search detail",a -> text(a,R.id.detail_lemma,first.lemma) && visible(a,R.id.detail_save) && a.findViewById(R.id.detail_save).isEnabled());
            scenario.onActivity(a -> assertEquals(first.stableId,((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController().getCurrentBackStackEntry().getArguments().getString("vocabularyId")));
            tap(R.id.back); message(R.string.search_content);
        }
        assertTrue(user.userDao().packs().isEmpty()); assertTrue(user.userDao().allProgress().isEmpty());
    }
}
