package com.vocamind.app.feature.search;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.*;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.*;
import androidx.lifecycle.*;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.domain.catalog.*;
import com.vocamind.app.feature.vocabulary.VocabularyFragment;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;

/** LOCAL ONLY: compiled through qa.init.gradle, never added to production sources.
 * Real-catalog tests and injected-callback UI tests are intentionally separate. */
@RunWith(AndroidJUnit4.class)
public class SmallPhoneQaTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition) throws Exception {
        long until = SystemClock.elapsedRealtime() + 20000;
        boolean[] okay = {false};
        do {
            scenario.onActivity(a -> okay[0] = condition.test(a));
            if (okay[0]) return;
            SystemClock.sleep(20); // Instrumentation thread, not UI/main.
        } while (SystemClock.elapsedRealtime() < until);
        fail("QA condition did not settle");
    }
    private void open(ActivityScenario<MainActivity> scenario) throws Exception {
        scenario.onActivity(a -> a.findViewById(R.id.open_vocabulary).performClick());
        await(scenario, a -> a.findViewById(R.id.search) != null);
    }
    private void input(ActivityScenario<MainActivity> scenario, String raw) {
        scenario.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).setQuery(raw, false));
    }
    private String message(MainActivity a) { return ((TextView)a.findViewById(R.id.search_message)).getText().toString(); }
    private void content(ActivityScenario<MainActivity> scenario, String lemma, int count) throws Exception {
        await(scenario, a -> {
            RecyclerView r = a.findViewById(R.id.results);
            if (r == null || r.getVisibility() != View.VISIBLE || r.getAdapter().getItemCount() != count) return false;
            SearchAdapter adapter = (SearchAdapter)r.getAdapter();
            return adapter.getCurrentList().get(0).word.lemma.equals(lemma);
        });
    }
    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(300); // Allow a presented frame, rather than capturing the previous screen.
        File dir = new File(instrumentation.getTargetContext().getFilesDir(), "small-phone-qa");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (OutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }
    @Test public void realCatalogEnglishVietnameseCaseUnicodeAndStates() throws Exception {
        try (var s = ActivityScenario.launch(MainActivity.class)) {
            open(s);
            s.onActivity(a -> assertEquals(a.getString(R.string.search_no_input), message(a)));
            screenshot("01-no-input");
            for (String query : List.of("bank", "BANK", "ngân hàng", "NGÂN HÀNG", Normalizer.normalize("NGÂN HÀNG", Normalizer.Form.NFD))) {
                input(s, query); content(s, "bank", 1);
                s.onActivity(a -> {
                    var adapter = (SearchAdapter)((RecyclerView)a.findViewById(R.id.results)).getAdapter();
                    assertEquals("5aecfbf5b9a23aee", adapter.getCurrentList().get(0).word.stableId);
                    assertEquals("ngân hàng", adapter.getCurrentList().get(0).word.meaningVi);
                    assertEquals(query, ((SearchView)a.findViewById(R.id.search)).getQuery().toString());
                });
            }
            screenshot("02-unicode-content");
            input(s, "notpresentvocabulary");
            await(s, a -> message(a).equals(a.getString(R.string.search_no_match)));
            screenshot("03-no-match");
            input(s, "x".repeat(257));
            s.onActivity(a -> {
                assertEquals(a.getString(R.string.search_invalid), message(a));
                assertEquals(View.GONE, a.findViewById(R.id.results).getVisibility());
                assertEquals(View.GONE, a.findViewById(R.id.retry).getVisibility());
            });
            screenshot("04-invalid-query-error");
            for (String empty : List.of("", "  ", "! *")) {
                input(s, empty);
                s.onActivity(a -> assertEquals(a.getString(R.string.search_no_input), message(a)));
            }
        }
    }
    @Test public void realCatalogRapidTypingDebounceClearAtoBtoA() throws Exception {
        try (var s = ActivityScenario.launch(MainActivity.class)) {
            open(s);
            for (int round=0; round<5; round++) {
                // Entire sequence in one main dispatch: no debounce runnable can execute in between.
                s.onActivity(a -> {
                    SearchView search = a.findViewById(R.id.search);
                    for (String raw : List.of("b", "ba", "bank", "book")) search.setQuery(raw, false);
                    assertEquals(a.getString(R.string.search_loading), message(a));
                    assertEquals(View.GONE, a.findViewById(R.id.results).getVisibility());
                });
                content(s, "book", 2);
                for (String raw : List.of("bank", "book", "bank")) { input(s, raw); content(s, raw, raw.equals("bank") ? 1 : 2); }
                s.onActivity(a -> {
                    ((SearchView)a.findViewById(R.id.search)).setQuery("book", false);
                    a.findViewById(R.id.reset).performClick();
                });
                SystemClock.sleep(900);
                s.onActivity(a -> {
                    assertEquals(a.getString(R.string.search_no_input), message(a));
                    assertEquals(View.GONE, a.findViewById(R.id.results).getVisibility());
                });
            }
            screenshot("05-rapid-clear-settled");
        }
    }
    @Test public void realCatalogNavigationKeyboardBackAndRecreation() throws Exception {
        try (var s = ActivityScenario.launch(MainActivity.class)) {
            open(s); input(s, "bank"); content(s, "bank", 1);
            s.onActivity(a -> {
                SearchView search = a.findViewById(R.id.search);
                EditText text = search.findViewById(androidx.appcompat.R.id.search_src_text);
                text.requestFocus();
                ((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
            });
            await(s, a -> a.getWindow().getDecorView().getRootWindowInsets() != null
                    && a.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()));
            screenshot("06-keyboard-bank");
            s.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.results);
                SearchAdapter adapter = (SearchAdapter)list.getAdapter();
                SearchAdapter.Holder holder = adapter.onCreateViewHolder(list, 0);
                adapter.onBindViewHolder(holder, 0); holder.itemView.performClick();
            });
            await(s, a -> a.findViewById(R.id.route_message) != null && ((TextView)a.findViewById(R.id.route_message)).getText().toString().contains("5aecfbf5b9a23aee"));
            s.onActivity(a -> {
                var nav = ((NavHostFragment)a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment)).getNavController();
                assertNull(nav.getCurrentBackStackEntry().getArguments().getString("packId"));
            });
            screenshot("07-detail-preview");
            s.onActivity(a -> a.findViewById(R.id.back).performClick());
            content(s, "bank", 1);
            s.recreate(); content(s, "bank", 1);
            s.onActivity(a -> assertEquals("bank", ((SearchView)a.findViewById(R.id.search)).getQuery().toString()));
            screenshot("08-recreated-query");
        }
    }
    static final class Uncancellable extends CompletableFuture<VocabularySearchResult> {
        @Override public boolean cancel(boolean interrupt) { return false; }
    }
    private final List<Uncancellable> requests = new ArrayList<>();
    private SearchViewModel injected;
    private void inject(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(a -> a.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle state) {
                if (!(f instanceof VocabularyFragment)) return;
                Handler main = new Handler(Looper.getMainLooper());
                injected = new SearchViewModel(new SavedStateHandle(), raw -> {
                    Uncancellable request = new Uncancellable(); requests.add(request); return request;
                }, (delay, task) -> { main.postDelayed(task, delay); return () -> main.removeCallbacks(task); }, main::post);
                f.getViewModelStore().put("androidx.lifecycle.ViewModelProvider.DefaultKey:" + SearchViewModel.class.getCanonicalName(), injected);
            }
        }, true));
    }
    private void request(ActivityScenario<MainActivity> s, String raw, int count) throws Exception {
        input(s, raw); await(s, a -> requests.size() == count);
    }
    private CatalogVocabulary word(String id, String lemma) {
        return new CatalogVocabulary(id, lemma, lemma, "n", "nghĩa kiểm thử", "e", "v", null, "s", "1");
    }
    private VocabularySearchResult result(String raw, String lemma) {
        return VocabularySearchResult.success(raw, List.of(word("ffffffffffffffff", lemma)));
    }
    @Test public void injectedInFlightClearOutOfOrderAtoBtoAErrorRetryUi() throws Exception {
        try (var s = ActivityScenario.launch(MainActivity.class)) {
            inject(s); open(s);
            request(s, "A", 1); request(s, "B", 2); request(s, "A", 3);
            requests.get(2).complete(result("a", "new A")); content(s, "new A", 1);
            requests.get(0).complete(result("a", "stale A"));
            requests.get(1).complete(VocabularySearchResult.failure("b", VocabularySearchResult.Error.READ_FAILED));
            instrumentation.waitForIdleSync(); content(s, "new A", 1);
            request(s, "clear-running", 4);
            s.onActivity(a -> a.findViewById(R.id.reset).performClick());
            requests.get(3).complete(result("clear running", "stale"));
            instrumentation.waitForIdleSync();
            s.onActivity(a -> assertEquals(a.getString(R.string.search_no_input), message(a)));
            request(s, "retry", 5);
            requests.get(4).complete(VocabularySearchResult.failure("retry", VocabularySearchResult.Error.READ_FAILED));
            await(s, a -> a.findViewById(R.id.retry).getVisibility() == View.VISIBLE);
            screenshot("09-injected-read-error");
            s.onActivity(a -> {
                assertEquals(a.getString(R.string.search_error), message(a));
                a.findViewById(R.id.retry).performClick();
            });
            await(s, a -> requests.size()==6);
            requests.get(5).complete(result("retry", "recovered")); content(s, "recovered", 1);
            screenshot("10-injected-retry-recovered");
        }
    }
    @Test public void injectedLongMeaningListScrollOnSmallScreen() throws Exception {
        try (var s = ActivityScenario.launch(MainActivity.class)) {
            inject(s); open(s); request(s, "long", 1);
            List<CatalogVocabulary> words = new ArrayList<>();
            for (int i=0; i<80; i++) words.add(new CatalogVocabulary(String.format(Locale.ROOT, "%016x", i), "long-"+i,
                    "long", "n", "Nghĩa tiếng Việt rất dài để kiểm tra xuống dòng và cuộn trên màn hình nhỏ. ".repeat(5), "e", "v", null, "s", "1"));
            requests.get(0).complete(VocabularySearchResult.success("long", words)); content(s, "long-0", 80);
            s.onActivity(a -> ((SearchView)a.findViewById(R.id.search)).clearFocus());
            screenshot("11-long-list-top");
            s.onActivity(a -> {
                RecyclerView r = a.findViewById(R.id.results);
                assertTrue(r.getHeight()>0); assertTrue(r.canScrollVertically(1)); r.scrollToPosition(79);
            });
            await(s, a -> ((RecyclerView)a.findViewById(R.id.results)).findViewHolderForAdapterPosition(79) != null);
            screenshot("12-long-list-bottom");
        }
    }
}
