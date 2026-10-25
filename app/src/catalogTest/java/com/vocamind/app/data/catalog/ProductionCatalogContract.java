package com.vocamind.app.data.catalog;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.domain.catalog.*;
import org.junit.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Checks the shipped asset through createFromAsset, without fixture-specific counts/content.
 * These are DATA-01 structural checks, not the full VOC-DATA-06 content/release gate. */
public abstract class ProductionCatalogContract {
    private Context context;
    private CatalogDatabase db;
    private CatalogRepository repository;
    private ExecutorService worker;
    private final String name = "catalog-production-contract-test.db";
    @Before public void open() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(name);
        db = CatalogDatabase.open(context, name);
        repository = new RoomCatalogRepository(db.catalogDao());
        worker = Executors.newSingleThreadExecutor();
    }
    @After public void close() {
        if (worker != null) worker.shutdownNow();
        if (db != null) db.close();
        context.deleteDatabase(name);
    }
    private <T> T read(Callable<T> operation) throws Exception {
        return worker.submit(operation).get(30, TimeUnit.SECONDS);
    }
    @Test public void assetOpensAndMapsAllPackWordsAndSources() throws Exception {
        List<CatalogPack> packs = read(repository::getPacks);
        assertTrue("DATA-01 requires at least four packs", packs.size() >= 4);
        Set<String> packIds = new HashSet<>();
        int memberships = 0;
        for (CatalogPack pack : packs) {
            assertTrue(packIds.add(pack.id));
            List<CatalogVocabulary> words = read(() -> repository.getWords(pack.id));
            assertEquals(pack.wordCount, words.size());
            Set<String> wordIds = new HashSet<>();
            for (CatalogVocabulary word : words) {
                memberships++;
                assertTrue(wordIds.add(word.stableId));
                assertTrue(word.stableId.matches("[0-9a-f]{16}"));
                assertFalse(word.meaningVi.trim().isEmpty());
                assertFalse(word.exampleEn.trim().isEmpty());
                assertFalse(word.exampleVi.trim().isEmpty());
                assertEquals(word.stableId, read(() -> repository.getVocabulary(word.stableId)).get().stableId);
                assertEquals(word.sourceId, read(() -> repository.getSourceForVocabulary(word.stableId)).get().id);
            }
        }
        assertTrue("Catalog needs queryable fixture/content", memberships > 0);
        assertFalse(read(repository::getSources).isEmpty());
    }
    @Test public void integrityAndEveryFtsMappingAreValid() throws Exception {
        read(() -> {
            var sql = db.getOpenHelper().getReadableDatabase();
            try (var c = sql.query("PRAGMA integrity_check")) {
                assertTrue(c.moveToFirst()); assertEquals("ok", c.getString(0));
            }
            try (var c = sql.query("PRAGMA foreign_key_check")) { assertFalse(c.moveToFirst()); }
            for (String query : Arrays.asList(
                    "SELECT COUNT(*) FROM VocabularyFts f LEFT JOIN VocabularyEntry v ON f.rowid=v.rowid WHERE v.id IS NULL",
                    "SELECT COUNT(*) FROM VocabularyEntry v LEFT JOIN VocabularyFts f ON f.rowid=v.rowid WHERE f.rowid IS NULL",
                    "SELECT COUNT(*) FROM VocabularyEntry v JOIN VocabularyFts f ON f.rowid=v.rowid WHERE f.lemma != v.lemma OR f.normalizedLemma != v.normalizedLemma OR f.meaningVi != v.meaningVi")) {
                try (var c = sql.query(query)) { assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); }
            }
            return null;
        });
    }
    @Test public void matchResolvesAnActualAssetWordToItsStableId() throws Exception {
        CatalogVocabulary sample = null;
        for (CatalogPack pack : read(repository::getPacks)) {
            List<CatalogVocabulary> words = read(() -> repository.getWords(pack.id));
            if (!words.isEmpty()) { sample = words.get(0); break; }
        }
        assertNotNull(sample);
        final CatalogVocabulary selected = sample;
        String expression = "\"" + selected.normalizedLemma.replace("\"", "\"\"") + "\"";
        assertTrue(read(() -> db.catalogDao().matchFixture(expression)).stream()
                .anyMatch(v -> selected.stableId.equals(v.id)));
    }
    @Test public void existingAssetCopyReopensWithSamePackIds() throws Exception {
        List<String> before = new ArrayList<>();
        for (CatalogPack pack : read(repository::getPacks)) before.add(pack.id);
        db.close();
        db = CatalogDatabase.open(context, name);
        repository = new RoomCatalogRepository(db.catalogDao());
        List<String> after = new ArrayList<>();
        for (CatalogPack pack : read(repository::getPacks)) after.add(pack.id);
        assertEquals(before, after);
    }
}
