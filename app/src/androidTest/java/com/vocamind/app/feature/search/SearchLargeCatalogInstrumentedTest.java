package com.vocamind.app.feature.search;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.View;
import androidx.annotation.*;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.*;
import androidx.lifecycle.SavedStateHandle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.*;
import com.vocamind.app.domain.catalog.SearchVocabularyUseCase;
import com.vocamind.app.feature.vocabulary.VocabularyFragment;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual DATA-05 5000-word FTS fixture to use case/ViewModel/RecyclerView.
 * UUID test catalog only; the installed 150-word QA catalog is not replaced. */
@RunWith(AndroidJUnit4.class)
public final class SearchLargeCatalogInstrumentedTest {
    private void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition) {
        long end=SystemClock.elapsedRealtime()+30000;
        boolean[] ready={false};
        while(SystemClock.elapsedRealtime()<end) {
            scenario.onActivity(a -> ready[0]=condition.test(a));
            if(ready[0]) return;
            SystemClock.sleep(20);
        }
        fail("Large catalog UI did not settle");
    }
    private void screenshot(String name) throws Exception {
        var i=InstrumentationRegistry.getInstrumentation(); i.waitForIdleSync(); SystemClock.sleep(300);
        File dir=new File(i.getTargetContext().getFilesDir(),"search-vocab02-qa");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap bitmap=i.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try(var out=new FileOutputStream(new File(dir,name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        bitmap.recycle();
    }
    @Test public void fiveThousandRealMatchesRenderAndScrollWithoutTruncation() throws Exception {
        var context=ApplicationProvider.getApplicationContext();
        var assets=InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        String name="search-large-"+UUID.randomUUID()+".db";
        var db=Room.databaseBuilder(context,CatalogDatabase.class,name)
                .createFromInputStream(() -> assets.open("benchmark/catalog-5000.db")).build();
        ExecutorService worker=Executors.newSingleThreadExecutor();
        var useCase=new SearchVocabularyUseCase(new RoomVocabularySearchRepository(db.catalogDao()),worker);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override public void onFragmentCreated(@NonNull FragmentManager fm,@NonNull Fragment f,@Nullable Bundle saved) {
                        if(!(f instanceof VocabularyFragment)) return;
                        Handler main=new Handler(Looper.getMainLooper());
                        var model=new SearchViewModel(new SavedStateHandle(),useCase::execute,
                                (delay,task) -> { main.postDelayed(task,delay); return () -> main.removeCallbacks(task); },main::post);
                        f.getViewModelStore().put("androidx.lifecycle.ViewModelProvider.DefaultKey:"+SearchViewModel.class.getCanonicalName(),model);
                    }
                },true);
                a.findViewById(R.id.open_vocabulary).performClick();
            });
            await(scenario,a -> a.findViewById(R.id.search)!=null);
            scenario.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery("benchword",false));
            await(scenario,a -> {
                RecyclerView r=a.findViewById(R.id.results);
                return r.getVisibility()==View.VISIBLE && r.getAdapter().getItemCount()==5000;
            });
            screenshot("04-5000-results");
            scenario.onActivity(a -> {
                RecyclerView r=a.findViewById(R.id.results);
                assertTrue(r.canScrollVertically(1)); r.scrollToPosition(4999);
            });
            await(scenario,a -> ((RecyclerView)a.findViewById(R.id.results)).findViewHolderForAdapterPosition(4999)!=null);
            scenario.onActivity(a -> {
                var adapter=(SearchAdapter)((RecyclerView)a.findViewById(R.id.results)).getAdapter();
                assertEquals("benchword04999",adapter.getCurrentList().get(4999).word.lemma);
                assertEquals(5000,adapter.getCurrentList().stream().map(r -> r.word.stableId).distinct().count());
            });
            screenshot("05-5000-last-row");
            scenario.onActivity(a -> a.findViewById(R.id.reset).performClick());
            SystemClock.sleep(900);
            scenario.onActivity(a -> assertEquals(View.GONE,a.findViewById(R.id.results).getVisibility()));
        } finally {
            worker.submit(db::close).get(15,TimeUnit.SECONDS); worker.shutdown(); context.deleteDatabase(name);
        }
    }
}
