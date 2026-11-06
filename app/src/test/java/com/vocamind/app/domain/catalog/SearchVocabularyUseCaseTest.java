package com.vocamind.app.domain.catalog;

import org.junit.Test;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class SearchVocabularyUseCaseTest {
    @Test public void normalizationPreservesVietnameseAndTreatsOperatorsAsWords() {
        var q = VocabularySearchQuery.parse(Normalizer.normalize(" NGÂN\u00a0HÀNG ", Normalizer.Form.NFD));
        assertEquals(Arrays.asList("ngân", "hàng"), q.getTokens());
        assertEquals("bank or airport", VocabularySearchQuery.parse("bank OR \"airport\"").getText());
        assertThrows(UnsupportedOperationException.class, () -> q.getTokens().clear());
    }
    @Test public void emptyAndPunctuationDoNotScheduleOrRead() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        SearchVocabularyUseCase useCase = new SearchVocabularyUseCase(q -> { reads.incrementAndGet(); return null; },
                task -> { throw new AssertionError("Blank input must not schedule work"); });
        for (String raw : Arrays.asList(null, "", " \u00a0 ", "\"*():-")) {
            var result = useCase.execute(raw).get(); assertTrue(result.isSuccess()); assertTrue(result.getWords().isEmpty());
        }
        assertEquals(0, reads.get());
    }
    @Test public void databaseRunsOnWorkerAndCallingThreadDoesNotBlock() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Thread> executionThread = new AtomicReference<>(); Thread caller = Thread.currentThread();
        try {
            SearchVocabularyUseCase useCase = new SearchVocabularyUseCase(q -> {
                executionThread.set(Thread.currentThread()); entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return VocabularySearchResult.success(q.getText(), Collections.emptyList());
            }, worker);
            var future = useCase.execute("bank"); assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertFalse(future.isDone()); assertNotSame(caller, executionThread.get());
            release.countDown(); assertTrue(future.get(10, TimeUnit.SECONDS).isSuccess());
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test public void invalidInputAndRejectedExecutorAreExplicitErrors() throws Exception {
        SearchVocabularyUseCase useCase = new SearchVocabularyUseCase(q -> { throw new AssertionError("No read"); },
                r -> { throw new RejectedExecutionException(); });
        assertEquals(VocabularySearchResult.Error.INVALID_QUERY, useCase.execute("x".repeat(257)).get().getError());
        assertEquals(VocabularySearchResult.Error.INVALID_QUERY, useCase.execute("x ".repeat(33)).get().getError());
        assertEquals(VocabularySearchResult.Error.EXECUTOR_UNAVAILABLE, useCase.execute("bank").get().getError());
    }
    @Test public void repositoryFailuresAreNotEmptySuccess() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var result = new SearchVocabularyUseCase(q -> { throw new IllegalStateException("DB closed"); }, worker)
                    .execute("bank").get(10, TimeUnit.SECONDS);
            assertFalse(result.isSuccess()); assertEquals(VocabularySearchResult.Error.READ_FAILED, result.getError());
            assertThrows(IllegalStateException.class, result::getWords);
        } finally { worker.shutdownNow(); }
    }
    @Test public void resultSnapshotsAreImmutableAndRejectDuplicateStableIds() {
        CatalogVocabulary word = new CatalogVocabulary("0123456789abcdef", "bank", "bank", "noun",
                "ngân hàng", "Example", "Ví dụ", null, "test", "1.0.0");
        List<CatalogVocabulary> input = new ArrayList<>(Collections.singletonList(word));
        var result = VocabularySearchResult.success("bank", input); input.clear();
        assertEquals(1, result.getWords().size());
        assertThrows(UnsupportedOperationException.class, () -> result.getWords().clear());
        assertThrows(IllegalArgumentException.class, () -> VocabularySearchResult.success("bank", Arrays.asList(word, word)));
    }
}
