package com.vocamind.app.feature.packs;

import static org.junit.Assert.*;
import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.domain.pack.SetPackActive;
import com.vocamind.app.fake.FakePackRepository;
import androidx.lifecycle.ViewModelStore;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class PackActivationTest {
    private final FakePackRepository repo = new FakePackRepository();
    private final Queue<Runnable> worker = new ArrayDeque<>(), main = new ArrayDeque<>();
    private int clockCalls;
    private final SetPackActive action = new SetPackActive(repo, () -> { clockCalls++; return 0; });

    private void seed() {
        repo.states.add(new PackState(new CatalogPack("A", "A", "d", "c", 1, "1", 2), false, null, 1));
    }
    private PacksViewModel model() {
        return new PacksViewModel(repo::getPackStates, action::execute, worker::add, main::add, () -> {});
    }
    private void settle() { worker.remove().run(); main.remove().run(); }

    @Test public void invalidAndUnknownIdsDoNotReadClockOrWrite() {
        seed();
        for (boolean active : new boolean[]{true, false}) {
            for (String id : new String[]{null, "", "  "})
                assertEquals(RepoError.INVALID_ARGUMENT, action.execute(id, active).getError());
            assertEquals(RepoError.NOT_FOUND, action.execute("missing", active).getError());
        }
        assertEquals(0, clockCalls);
        assertNull(repo.states.get(0).getActivatedAtEpochMillis());
    }

    @Test public void explicitActionKeepsZeroFirstTimestampAndLearnedCount() {
        seed();
        assertFalse(action.execute("A", false).get().get(0).isActive());
        assertEquals(0, clockCalls);
        assertTrue(action.execute("A", true).get().get(0).isActive());
        action.execute("A", false); action.execute("A", false);
        PackState state = new SetPackActive(repo, () -> 900).execute("A", true).get().get(0);
        assertEquals(Long.valueOf(0), state.getActivatedAtEpochMillis());
        assertEquals(1, state.getLearnedCount());
    }

    @Test public void doubleTapRefreshAndOldReadCannotReplaceConfirmedWrite() {
        seed(); PacksViewModel model = model();
        worker.remove().run(); // old inactive snapshot waits on main
        model.setActive("A", true);
        assertTrue(model.state().getValue().pending);
        model.setActive("A", false); model.refresh(); model.retry();
        assertEquals(1, worker.size());
        worker.remove().run();
        Runnable old = main.remove(), confirmed = main.remove();
        confirmed.run(); old.run();
        assertTrue(model.state().getValue().items.get(0).isActive());
        assertEquals(1, clockCalls);
    }

    @Test public void readFailureAndWriteFailureStayErrorAndRetryReconciles() {
        seed(); PacksViewModel model = model(); settle();
        repo.failure = RepoError.WRITE_FAILED;
        model.setActive("A", true); settle();
        assertEquals(RepoError.WRITE_FAILED, model.state().getValue().error);
        repo.failure = RepoError.READ_FAILED;
        model.retry(); settle();
        assertEquals(AsyncListViewModel.Status.ERROR, model.state().getValue().status);
        repo.failure = null; model.retry(); settle();
        assertFalse(model.state().getValue().items.get(0).isActive());
        model.setActive("A", true); settle();
        assertTrue(model.state().getValue().items.get(0).isActive());
    }

    @Test public void savedWriteFollowedByReadFailureIsReconciledWithoutReplay() {
        seed();
        PacksViewModel model = new PacksViewModel(repo::getPackStates, (id, active) -> {
            action.execute(id, active);
            return RepoResult.failure(RepoError.READ_FAILED);
        }, worker::add, main::add, () -> {});
        settle(); model.setActive("A", true); settle();
        assertEquals(RepoError.READ_FAILED, model.state().getValue().error);
        assertTrue(repo.states.get(0).isActive());
        model.retry(); settle();
        assertTrue(model.state().getValue().items.get(0).isActive());
        assertEquals(1, clockCalls);
    }

    @Test public void initialLoadAndNewOwnerNeverActivateAndClearingWaitsForWrite() {
        seed(); PacksViewModel model = model(); settle();
        assertEquals(0, clockCalls);
        model.setActive("A", true);
        ViewModelStore store = new ViewModelStore(); store.put("packs", model); store.clear();
        worker.remove().run(); worker.remove().run(); main.remove().run();
        PacksViewModel reopened = model(); settle();
        assertTrue(reopened.state().getValue().items.get(0).isActive());
        assertEquals(1, clockCalls);
    }
}
