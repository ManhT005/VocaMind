package com.vocamind.app.feature.packs;

import static org.junit.Assert.*;

import androidx.lifecycle.ViewModelStore;
import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.pack.GetPackWords;
import com.vocamind.app.domain.pack.GetPacks;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.fake.FakePackRepository;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class PacksViewModelTest {
    private final Queue<Runnable> worker = new ArrayDeque<>();
    private final Queue<Runnable> main = new ArrayDeque<>();

    private void settle() { worker.remove().run(); main.remove().run(); }

    private static PackState pack(String id, int order, int words, int learned) {
        return new PackState(new CatalogPack(id, "Pack " + id, "desc", "cat", order, "1", words), false, null, learned);
    }

    @Test public void packListLoadsOffMainThenShowsContentEmptyAndError() {
        FakePackRepository repo = new FakePackRepository();
        PacksViewModel model = new PacksViewModel(() -> new GetPacks(repo).execute(), worker::add, main::add, () -> { });
        assertEquals(AsyncListViewModel.Status.LOADING, model.state().getValue().status);
        assertEquals(0, repo.calls);
        settle();
        assertEquals(AsyncListViewModel.Status.EMPTY, model.state().getValue().status);

        repo.states.add(pack("A", 1, 10, 3));
        repo.states.add(pack("B", 2, 8, 0));
        model.retry(); settle();
        assertEquals(AsyncListViewModel.Status.CONTENT, model.state().getValue().status);
        assertEquals(2, model.state().getValue().items.size());
        assertEquals(3, model.state().getValue().items.get(0).getLearnedCount());

        repo.failure = RepoError.READ_FAILED;
        model.retry(); settle();
        assertEquals(AsyncListViewModel.Status.ERROR, model.state().getValue().status);
        assertTrue("failure is never shown as empty", model.state().getValue().items.isEmpty());
    }

    @Test public void overlappingRetriesAreIgnoredAndThrownErrorsBecomeError() {
        FakePackRepository repo = new FakePackRepository();
        PacksViewModel model = new PacksViewModel(() -> { throw new IllegalStateException(); },
                worker::add, main::add, () -> { });
        model.retry();
        assertEquals(1, worker.size());
        settle();
        assertEquals(AsyncListViewModel.Status.ERROR, model.state().getValue().status);
    }

    @Test public void thrownErrorStillLeavesLoadingAndBecomesError() {
        PacksViewModel model = new PacksViewModel(() -> { throw new AssertionError("storage Error"); },
                worker::add, main::add, () -> { });
        assertEquals(AsyncListViewModel.Status.LOADING, model.state().getValue().status);
        settle();
        assertEquals(AsyncListViewModel.Status.ERROR, model.state().getValue().status);
        assertEquals(RepoError.READ_FAILED, model.state().getValue().error);
    }

    @Test public void wordsLoadByPackIdInCatalogOrderAndUnknownPackIsError() {
        FakePackRepository repo = new FakePackRepository();
        CatalogVocabulary v = new CatalogVocabulary("0000000000000001", "apple", "apple", "noun", "quả táo", "e", "v", null, "s", "1");
        repo.words.put("A", List.of(new StudyItem(v, null)));
        PackWordsViewModel ok = new PackWordsViewModel(() -> new GetPackWords(repo).execute("A"), worker::add, main::add, () -> { });
        settle();
        assertEquals(AsyncListViewModel.Status.CONTENT, ok.state().getValue().status);
        assertEquals("apple", ok.state().getValue().items.get(0).getVocabulary().lemma);
        PackWordsViewModel missing = new PackWordsViewModel(() -> new GetPackWords(repo).execute("zzz"), worker::add, main::add, () -> { });
        settle();
        assertEquals(AsyncListViewModel.Status.ERROR, missing.state().getValue().status);
    }

    @Test public void clearingClosesAfterReadAndIgnoresLateDelivery() {
        boolean[] closed = {false};
        PacksViewModel model = new PacksViewModel(() -> new GetPacks(new FakePackRepository()).execute(),
                worker::add, main::add, () -> closed[0] = true);
        ViewModelStore store = new ViewModelStore();
        store.put("packs", model);
        store.clear();
        assertFalse(closed[0]);
        worker.remove().run(); worker.remove().run(); main.remove().run();
        assertTrue(closed[0]);
        assertEquals(AsyncListViewModel.Status.LOADING, model.state().getValue().status);
    }
}
