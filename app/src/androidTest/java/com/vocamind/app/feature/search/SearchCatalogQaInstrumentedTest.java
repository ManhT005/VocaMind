package com.vocamind.app.feature.search;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.TextView;
import android.view.View;
import androidx.appcompat.widget.SearchView;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.*;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.feature.vocabulary.VocabularyDetailFragment;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses installed app assets (including the current 150-word catalogQa bundle), no hardcoded pack. */
@RunWith(AndroidJUnit4.class)
public final class SearchCatalogQaInstrumentedTest {
    private void await(ActivityScenario<MainActivity> s, Predicate<MainActivity> condition) {
        long end = SystemClock.elapsedRealtime() + 20000;
        boolean[] ready = {false};
        while (SystemClock.elapsedRealtime()<end) {
            s.onActivity(a -> ready[0]=condition.test(a));
            if (ready[0]) return;
            SystemClock.sleep(20);
        }
        fail("QA search/detail did not settle");
    }
    private void screenshot(String name) throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync(); SystemClock.sleep(300);
        File directory=new File(instrumentation.getTargetContext().getFilesDir(),"search-vocab02-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try(var out=new FileOutputStream(new File(directory,name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        bitmap.recycle();
    }
    private boolean hasWord(MainActivity a, String id) {
        var list=(RecyclerView)a.findViewById(R.id.results);
        return list!=null && list.getVisibility()==View.VISIBLE
                && ((SearchAdapter)list.getAdapter()).getCurrentList().stream().anyMatch(r -> r.word.stableId.equals(id));
    }
    @Test public void currentCatalogSearchFullDetailExplicitSaveBackAndRecreate() throws Exception {
        var context=ApplicationProvider.getApplicationContext();
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CatalogVocabulary word;
        try {
            word=worker.submit(() -> {
                var db=CatalogDatabase.open(context,"catalog.db");
                try {
                    var dao=db.catalogDao();
                    var row=dao.words(dao.packs().get(0).id).get(0);
                    return CatalogMapper.vocabulary(row);
                } finally { db.close(); }
            }).get(15,TimeUnit.SECONDS);
        } finally { worker.shutdown(); }
        System.out.println("SEARCH_VOCAB02_QA stableId="+word.stableId+" lemma="+word.lemma);
        try(var s=ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(a -> a.findViewById(R.id.open_vocabulary).performClick());
            await(s,a -> a.findViewById(R.id.search)!=null);
            for (String query : java.util.List.of(word.lemma,word.lemma.toUpperCase(java.util.Locale.ROOT),
                    word.meaningVi,word.meaningVi.toUpperCase(java.util.Locale.ROOT),
                    java.text.Normalizer.normalize(word.meaningVi.toUpperCase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD))) {
                s.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery(query,false));
                await(s,a -> hasWord(a,word.stableId));
            }
            s.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery("notpresentsearchintegration",false));
            await(s,a -> ((TextView)a.findViewById(R.id.search_message)).getText().toString().equals(a.getString(R.string.search_no_match)));
            s.onActivity(a -> {
                SearchView search=a.findViewById(R.id.search);
                search.setQuery(word.lemma,false); search.setQuery("notpresentsearchintegration",false); search.setQuery(word.lemma,false);
                a.findViewById(R.id.reset).performClick();
            });
            SystemClock.sleep(900);
            s.onActivity(a -> {
                assertEquals(a.getString(R.string.search_no_input),((TextView)a.findViewById(R.id.search_message)).getText().toString());
                assertEquals(View.GONE,a.findViewById(R.id.results).getVisibility());
            });
            s.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery(word.lemma,false));
            await(s,a -> hasWord(a,word.stableId));
            screenshot("01-search");
            s.onActivity(a -> {
                var list=(RecyclerView)a.findViewById(R.id.results);
                var adapter=(SearchAdapter)list.getAdapter();
                int position=0;
                while(!adapter.getCurrentList().get(position).word.stableId.equals(word.stableId)) position++;
                var holder=adapter.onCreateViewHolder(list,0);
                adapter.onBindViewHolder(holder,position); holder.itemView.performClick();
            });
            await(s,a -> a.findViewById(R.id.detail_save)!=null && a.findViewById(R.id.detail_save).isEnabled());
            boolean[] initiallySaved={false};
            s.onActivity(a -> {
                var args=((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController().getCurrentBackStackEntry().getArguments();
                assertEquals(word.stableId,args.getString(VocabularyDetailFragment.ARG_VOCABULARY_ID));
                assertNull(args.getString(VocabularyDetailFragment.ARG_PACK_ID));
                assertEquals(word.lemma,((TextView)a.findViewById(R.id.detail_lemma)).getText().toString());
                assertEquals(word.meaningVi,((TextView)a.findViewById(R.id.detail_meaning)).getText().toString());
                assertEquals(word.exampleEn,((TextView)a.findViewById(R.id.detail_example_en)).getText().toString());
                assertEquals(word.exampleVi,((TextView)a.findViewById(R.id.detail_example_vi)).getText().toString());
                initiallySaved[0]=((TextView)a.findViewById(R.id.detail_save)).getText().toString().equals(a.getString(R.string.detail_unsave));
                a.findViewById(R.id.detail_save).performClick();
            });
            await(s,a -> a.findViewById(R.id.detail_save).isEnabled() && ((TextView)a.findViewById(R.id.detail_save)).getText().toString().equals(a.getString(initiallySaved[0]?R.string.detail_save:R.string.detail_unsave)));
            screenshot("02-full-detail-save");
            s.recreate();
            await(s,a -> a.findViewById(R.id.detail_save)!=null && a.findViewById(R.id.detail_save).isEnabled());
            s.onActivity(a -> {
                assertEquals(a.getString(initiallySaved[0]?R.string.detail_save:R.string.detail_unsave),((TextView)a.findViewById(R.id.detail_save)).getText().toString());
                a.findViewById(R.id.back).performClick();
            });
            await(s,a -> a.findViewById(R.id.search)!=null);
            s.onActivity(a -> {
                assertEquals(word.lemma,((SearchView)a.findViewById(R.id.search)).getQuery().toString());
                ((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController()
                        .navigate(R.id.vocabularyDetailFragment,VocabularyDetailFragment.searchArguments(word.stableId));
            });
            await(s,a -> a.findViewById(R.id.detail_save)!=null && a.findViewById(R.id.detail_save).isEnabled());
            s.onActivity(a -> a.findViewById(R.id.detail_save).performClick());
            await(s,a -> a.findViewById(R.id.detail_save).isEnabled() && ((TextView)a.findViewById(R.id.detail_save)).getText().toString().equals(a.getString(initiallySaved[0]?R.string.detail_unsave:R.string.detail_save)));
            screenshot("03-restored-saved-flag");
        }
    }
}
