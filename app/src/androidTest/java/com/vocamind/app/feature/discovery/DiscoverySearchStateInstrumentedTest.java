package com.vocamind.app.feature.discovery;

import static org.junit.Assert.*;
import android.os.*;
import androidx.annotation.*;
import androidx.fragment.app.*;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.CatalogMapper;
import com.vocamind.app.domain.catalog.*;
import com.vocamind.app.feature.search.SearchViewModel;
import com.vocamind.app.feature.vocabulary.VocabularyFragment;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Controlled-state rendering tests. Real SearchView + VM contract/debounce, controlled future boundary;
 * explicitly NOT Room/FTS integration evidence. No state/LiveData/view result injection. */
@RunWith(AndroidJUnit4.class)
public class DiscoverySearchStateInstrumentedTest extends DiscoveryHarness {
    private static final class Request {
        final String query;
        // Simulate SQLite work that cannot be cancelled, to test late success AND late failure.
        final CompletableFuture<VocabularySearchResult> result = new CompletableFuture<>() {
            @Override public boolean cancel(boolean interrupt) { return false; }
        };
        Request(String query) { this.query=query; }
    }
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private void openControlled() throws Exception {
        launch();
        scenario.onActivity(a -> a.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle state) {
                if (!(f instanceof VocabularyFragment)) return;
                Handler main = new Handler(Looper.getMainLooper());
                SavedStateHandle saved = new SavedStateHandle();
                if(state!=null && state.containsKey("ui02.input")) saved.set("search.input",state.getString("ui02.input"));
                SearchViewModel model = new SearchViewModel(saved, q -> {
                    Request request=new Request(q); requests.add(request); return request.result;
                }, (delay,task) -> { main.postDelayed(task,delay); return () -> main.removeCallbacks(task); }, main::post);
                f.getViewModelStore().put("androidx.lifecycle.ViewModelProvider.DefaultKey:"+SearchViewModel.class.getCanonicalName(),model);
            }
            @Override public void onFragmentSaveInstanceState(@NonNull FragmentManager fm,@NonNull Fragment f,@NonNull Bundle out) {
                if(f instanceof VocabularyFragment && f.getView()!=null) {
                    var search=(androidx.appcompat.widget.SearchView)f.requireView().findViewById(R.id.search);
                    out.putString("ui02.input", search.getQuery().toString());
                }
            }
        },true));
        tap(R.id.open_vocabulary); message(R.string.search_no_input);
    }
    private Request request(int count, String query) throws Exception {
        await("submitted request " + count + ": " + query, a -> requests.size()>=count);
        assertEquals(count,requests.size()); Request r=requests.get(count-1); assertEquals(query,r.query); return r;
    }
    private void loading() throws Exception {
        message(R.string.search_loading); assertHidden(R.id.results); assertHidden(R.id.retry);
        scenario.onActivity(a -> assertTrue("loading spinner visible",visible(a,R.id.loading)));
    }
    private void complete(Request r, String id) {
        r.result.complete(VocabularySearchResult.success(r.query,id==null ? List.of() : List.of(CatalogMapper.vocabulary(oracle.catalogDao().vocabulary(id)))));
    }
    private void failRead(Request r) { r.result.complete(VocabularySearchResult.failure(r.query,VocabularySearchResult.Error.READ_FAILED)); }
    private void settledMain() { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    @Test public void loadingContentNoMatchAndAtoBtoARejectsOldSuccessAndError() throws Exception {
        openControlled(); query("bank"); loading(); Request first=request(1,"bank");
        query("book"); loading(); Request second=request(2,"book");
        query("bank"); loading(); Request third=request(3,"bank");
        complete(first,"d17314a48504e2ac"); failRead(second); settledMain(); loading();
        complete(third,"5aecfbf5b9a23aee"); message(R.string.search_content); listCount(R.id.results,1);
        row(R.id.results,0,"ngân hàng",false);
        query("book"); Request abandoned=request(4,"book");
        query("bank"); Request newest=request(5,"bank"); complete(newest,"5aecfbf5b9a23aee");
        message(R.string.search_content); failRead(abandoned); settledMain(); message(R.string.search_content);
        listCount(R.id.results,1); row(R.id.results,0,"ngân hàng",false); assertHidden(R.id.retry);
        query("nomatch"); loading(); complete(request(6,"nomatch"),null); message(R.string.search_no_match); assertHidden(R.id.results);
    }
    @Test public void retryCurrentQueryErrorEditInvalidAndResetAreDistinct() throws Exception {
        openControlled(); query("bank"); failRead(request(1,"bank")); message(R.string.search_error);
        assertHidden(R.id.results); tap(R.id.retry); loading(); Request retry=request(2,"bank");
        complete(retry,"5aecfbf5b9a23aee"); message(R.string.search_content);
        query("book"); failRead(request(3,"book")); message(R.string.search_error);
        query("bank"); loading(); complete(request(4,"bank"),null); message(R.string.search_no_match);
        query("x".repeat(1000)); message(R.string.search_invalid); assertHidden(R.id.retry); assertHidden(R.id.results);
        tap(R.id.reset); message(R.string.search_no_input); assertEquals(4,requests.size()); assertHidden(R.id.loading);
        query("bank"); failRead(request(5,"bank")); message(R.string.search_error);
        tap(R.id.reset); message(R.string.search_no_input); assertHidden(R.id.retry); assertHidden(R.id.results);
        assertEquals(5,requests.size());
    }
    @Test public void resetRunningRejectsLateCompletionAndLateError() throws Exception {
        openControlled(); query("bank"); Request first=request(1,"bank");
        tap(R.id.reset); message(R.string.search_no_input);
        complete(first,"5aecfbf5b9a23aee"); settledMain(); message(R.string.search_no_input); assertHidden(R.id.results);
        query("book"); Request second=request(2,"book"); tap(R.id.reset); message(R.string.search_no_input);
        failRead(second); settledMain(); message(R.string.search_no_input); assertHidden(R.id.retry); assertHidden(R.id.loading);
    }
    @Test public void whitespaceAndResetPendingNeverSubmitFts() throws Exception {
        openControlled(); query("   "); message(R.string.search_no_input); assertEquals(0,requests.size());
        // Execute typing + visible Reset in the same main-loop turn, before the real 600ms timer fires.
        onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.reset)).perform(new androidx.test.espresso.ViewAction() {
            @Override public org.hamcrest.Matcher<android.view.View> getConstraints() { return androidx.test.espresso.matcher.ViewMatchers.isDisplayed(); }
            @Override public String getDescription() { return "Type pending query then tap visible Reset before debounce"; }
            @Override public void perform(androidx.test.espresso.UiController ui,android.view.View reset) {
                android.view.View root=reset.getRootView();
                android.widget.EditText input=root.findViewById(androidx.appcompat.R.id.search_src_text);
                androidx.test.espresso.action.ViewActions.replaceText("bank").perform(ui,input);
                androidx.test.espresso.action.ViewActions.click().perform(ui,reset);
            }
        });
        message(R.string.search_no_input);
        // A subsequent query's submission is an observable deadline beyond cancelled debounce.
        query("book"); complete(request(1,"book"),null); message(R.string.search_no_match);
        assertEquals(1,requests.size());
    }
    @Test public void backWhileRunningAndRecreateKeepsCurrentQueryAndDropsAbandonedRead() throws Exception {
        openControlled(); query("bank"); Request old=request(1,"bank");
        androidx.test.espresso.Espresso.pressBack();
        await("home after back",a -> visible(a,R.id.open_vocabulary));
        failRead(old); settledMain(); tap(R.id.open_vocabulary); message(R.string.search_no_input);
        query("book"); Request current=request(2,"book"); scenario.recreate(); loading();
        complete(current,"d17314a48504e2ac"); message(R.string.search_content); listCount(R.id.results,1);
        row(R.id.results,0,"đặt trước",false);
    }
}
