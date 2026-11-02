package com.vocamind.app.feature.attribution;

import androidx.lifecycle.ViewModelStore;
import com.vocamind.app.domain.catalog.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class AttributionViewModelTest {
    @Test public void loadsOffCallerAndDistinguishesEmptyFailureAndSuccessfulRetry() {
        FakeCatalogRepository repository = new FakeCatalogRepository();
        Queue<Runnable> worker = new ArrayDeque<>();
        Queue<Runnable> main = new ArrayDeque<>();
        AttributionViewModel model = new AttributionViewModel(new GetContentSources(repository),
                worker::add, main::add, () -> { });
        assertEquals(AttributionViewModel.Status.LOADING, model.state().getValue().status);
        assertEquals(0, repository.calls);
        model.retry();
        assertEquals(1, worker.size());
        worker.remove().run();
        assertEquals(AttributionViewModel.Status.LOADING, model.state().getValue().status);
        main.remove().run();
        assertEquals(AttributionViewModel.Status.EMPTY, model.state().getValue().status);
        repository.failure = new CatalogReadException(new IllegalStateException());
        model.retry(); worker.remove().run(); main.remove().run();
        assertEquals(AttributionViewModel.Status.ERROR, model.state().getValue().status);
        repository.failure = null;
        repository.sources = List.of(new CatalogSource("1", "Local source", null, "CC0", null, "2026-10-25"));
        model.retry(); worker.remove().run(); main.remove().run();
        assertEquals(AttributionViewModel.Status.CONTENT, model.state().getValue().status);
        assertEquals(repository.sources, model.state().getValue().sources);
        assertThrows(UnsupportedOperationException.class, () -> model.state().getValue().sources.clear());
    }

    @Test public void clearingClosesAfterReadAndIgnoresLateDelivery() {
        Queue<Runnable> worker = new ArrayDeque<>();
        Queue<Runnable> main = new ArrayDeque<>();
        boolean[] closed = {false};
        AttributionViewModel model = new AttributionViewModel(new GetContentSources(new FakeCatalogRepository()),
                worker::add, main::add, () -> closed[0] = true);
        ViewModelStore store = new ViewModelStore();
        store.put("attribution", model);
        store.clear();
        assertFalse(closed[0]);
        worker.remove().run(); worker.remove().run(); main.remove().run();
        assertTrue(closed[0]);
        assertEquals(AttributionViewModel.Status.LOADING, model.state().getValue().status);
        model.retry();
        assertTrue(worker.isEmpty());
    }
}
