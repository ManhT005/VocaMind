package com.vocamind.app.feature.search;

import static org.junit.Assert.*;
import androidx.lifecycle.*;
import com.vocamind.app.domain.catalog.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public class SearchViewModelTest {
    // Simulate SQLite work which ignores cancellation, rather than testing CompletableFuture cancellation itself.
    static final class Uncancellable extends CompletableFuture<VocabularySearchResult> {
        @Override public boolean cancel(boolean interrupt) { return false; }
    }
    static final class Clock implements SearchViewModel.Scheduler {
        long now;
        final List<Job> jobs = new ArrayList<>();
        static final class Job { long at; Runnable run; boolean cancelled; }
        @Override public Runnable after(long delay, Runnable task) {
            Job j = new Job(); j.at = now + delay; j.run = task; jobs.add(j); return () -> j.cancelled = true;
        }
        void advance(long millis) {
            now += millis;
            for (Job j : new ArrayList<>(jobs)) if (!j.cancelled && j.at <= now) {
                jobs.remove(j); j.run.run();
            }
        }
    }
    Clock clock;
    Queue<Runnable> main;
    List<String> inputs;
    List<Uncancellable> requests;
    SavedStateHandle saved;
    SearchViewModel model;
    static CatalogVocabulary word(String id, String lemma) {
        return new CatalogVocabulary(id, lemma, lemma, "noun", "nghĩa Việt", "example", "ví dụ", null, "s", "1");
    }
    static VocabularySearchResult content(String input, String lemma) {
        return VocabularySearchResult.success(input, List.of(word("ffffffffffffffff", lemma)));
    }
    @Before public void setup() {
        clock = new Clock(); main = new ArrayDeque<>(); inputs = new ArrayList<>(); requests = new ArrayList<>(); saved = new SavedStateHandle();
        model = new SearchViewModel(saved, raw -> {
            inputs.add(raw); Uncancellable f = new Uncancellable(); requests.add(f); return f;
        }, clock, main::add);
    }
    void drain() { while (!main.isEmpty()) main.remove().run(); }
    SearchViewModel.State state() { return model.state().getValue(); }
    void request(String raw) { model.input(raw); clock.advance(600); }
    @Test public void debounce600AndImmediateInvalidation() {
        model.input("bank"); clock.advance(599); assertTrue(inputs.isEmpty());
        model.input("book"); clock.advance(599); assertTrue(inputs.isEmpty()); clock.advance(1);
        assertEquals(List.of("book"), inputs);
        requests.get(0).complete(content("book", "book")); drain();
        assertEquals(SearchViewModel.Status.CONTENT, state().status);
        long old = state().generation;
        model.input("bank"); assertTrue(state().words.isEmpty()); assertFalse(model.canOpen(old, "ffffffffffffffff"));
    }
    @Test public void outOfOrderContentAndOldErrorCannotStopNewLoading() {
        request("bank"); request("book");
        requests.get(0).complete(VocabularySearchResult.failure("bank", VocabularySearchResult.Error.READ_FAILED)); drain();
        assertEquals(SearchViewModel.Status.LOADING, state().status);
        requests.get(1).complete(content("book", "new")); drain();
        assertEquals("new", state().words.get(0).lemma);
        request("bank"); request("book");
        requests.get(3).complete(content("book", "latest")); drain();
        requests.get(2).complete(content("bank", "stale")); drain();
        assertEquals("latest", state().words.get(0).lemma);
    }
    @Test public void aToBToAUsesGenerationNotQueryEquality() {
        request("A"); request("B"); request("A");
        requests.get(2).complete(content("a", "new A")); drain();
        requests.get(0).complete(content("a", "old A")); requests.get(1).completeExceptionally(new IllegalStateException()); drain();
        assertEquals("new A", state().words.get(0).lemma); assertEquals("A", state().input);
    }
    @Test public void clearWhitespaceTokenlessInvalidateRunningAndPending() {
        for (String empty : List.of("", "   ", "! * \"")) {
            request("book"); Uncancellable old = requests.get(requests.size()-1);
            model.input(empty); assertEquals(SearchViewModel.Status.NO_INPUT, state().status);
            old.complete(content("book", "stale")); drain(); assertEquals(SearchViewModel.Status.NO_INPUT, state().status);
        }
        model.input("bank"); model.reset(); clock.advance(600); assertEquals(3, inputs.size());
    }
    @Test public void clearRejectsQueuedCompletionAndError() {
        request("A"); requests.get(0).complete(content("a", "old")); model.reset(); drain();
        assertEquals(SearchViewModel.Status.NO_INPUT, state().status);
        request("B"); model.reset(); requests.get(1).completeExceptionally(new RuntimeException()); drain();
        assertEquals(SearchViewModel.Status.NO_INPUT, state().status);
    }
    @Test public void errorsAreDistinctFromNoMatchRetryUsesCurrentInput() {
        request("book"); requests.get(0).complete(VocabularySearchResult.success("book", List.of())); drain();
        assertEquals(SearchViewModel.Status.NO_MATCH, state().status);
        for (VocabularySearchResult.Error error : VocabularySearchResult.Error.values()) {
            request("new"); requests.get(requests.size()-1).complete(VocabularySearchResult.failure("new", error)); drain();
            assertEquals(SearchViewModel.Status.ERROR, state().status); assertEquals(error, state().error);
        }
        model.retry(); clock.advance(600); assertEquals("new", inputs.get(inputs.size()-1));
        requests.get(requests.size()-1).complete(content("new", "recovered")); drain();
        assertEquals(SearchViewModel.Status.CONTENT, state().status);
    }
    @Test public void invalidInputDoesNotSubmitOrBecomeNoMatch() {
        request("book"); model.input("x".repeat(257));
        requests.get(0).complete(content("book", "old")); drain(); clock.advance(600);
        assertEquals(VocabularySearchResult.Error.INVALID_QUERY, state().error); assertEquals(1, inputs.size());
        model.retry(); clock.advance(600); assertEquals(1, inputs.size());
    }
    @Test public void immediateExceptionalAndRejectedFuturesUseMainDelivery() {
        for (boolean exceptional : List.of(false, true)) {
            SearchViewModel vm = new SearchViewModel(new SavedStateHandle(), q -> exceptional
                    ? CompletableFuture.failedFuture(new RuntimeException()) : CompletableFuture.completedFuture(content("a", "ok")), clock, main::add);
            vm.input("A"); clock.advance(600); assertEquals(SearchViewModel.Status.LOADING, vm.state().getValue().status);
            drain(); assertEquals(exceptional ? SearchViewModel.Status.ERROR : SearchViewModel.Status.CONTENT, vm.state().getValue().status);
        }
        SearchViewModel rejected = new SearchViewModel(new SavedStateHandle(), q -> { throw new RejectedExecutionException(); }, clock, main::add);
        rejected.input("A"); clock.advance(600); drain();
        assertEquals(VocabularySearchResult.Error.EXECUTOR_UNAVAILABLE, rejected.state().getValue().error);
    }
    @Test public void disposedOwnerIgnoresLateCompletionAndCancelsDebounce() {
        request("A"); ViewModelStore store = new ViewModelStore(); store.put("search", model); store.clear();
        requests.get(0).complete(content("a", "late")); drain(); assertEquals(SearchViewModel.Status.LOADING, state().status);
        model.input("B"); clock.advance(600); assertEquals(1, inputs.size());
    }
    @Test public void savedRawQueryRestoresAndNavigationUsesOpaqueId() {
        request("  BOOK "); requests.get(0).complete(content("book", "book")); drain();
        assertEquals("  BOOK ", state().input); assertTrue(model.canOpen(state().generation, "ffffffffffffffff"));
        assertFalse(model.canOpen(state().generation, "0"));
        SearchViewModel restored = new SearchViewModel(new SavedStateHandle(Map.of("search.input", saved.get("search.input"))),
                q -> CompletableFuture.completedFuture(content("book", "book")), clock, main::add);
        assertEquals("  BOOK ", restored.state().getValue().input);
        clock.advance(600); drain(); assertEquals(SearchViewModel.Status.CONTENT, restored.state().getValue().status);
    }
    @Test public void detailMissingUnknownFailureAndDisposedAreSafe() {
        CatalogDetailViewModel bad = new CatalogDetailViewModel("missing", q -> { fail("invalid ID queried"); return null; }, main::add);
        assertEquals(CatalogDetailViewModel.Status.NOT_FOUND, bad.state().getValue().status);
        CatalogDetailViewModel unknown = new CatalogDetailViewModel("ffffffffffffffff", q -> CompletableFuture.completedFuture(Optional.empty()), main::add);
        drain(); assertEquals(CatalogDetailViewModel.Status.NOT_FOUND, unknown.state().getValue().status);
        CatalogDetailViewModel failed = new CatalogDetailViewModel("ffffffffffffffff", q -> CompletableFuture.failedFuture(new RuntimeException()), main::add);
        drain(); assertEquals(CatalogDetailViewModel.Status.ERROR, failed.state().getValue().status);
        CompletableFuture<Optional<CatalogVocabulary>> pending = new CompletableFuture<>();
        CatalogDetailViewModel disposed = new CatalogDetailViewModel("ffffffffffffffff", q -> pending, main::add);
        ViewModelStore store = new ViewModelStore(); store.put("detail", disposed); store.clear();
        pending.complete(Optional.of(word("ffffffffffffffff", "a"))); drain();
        assertEquals(CatalogDetailViewModel.Status.LOADING, disposed.state().getValue().status);
    }
}
