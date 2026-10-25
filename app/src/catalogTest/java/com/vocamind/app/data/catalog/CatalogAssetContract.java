package com.vocamind.app.data.catalog;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.domain.catalog.*;
import org.junit.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Fixed fixture contract; independent of the replaceable production catalog asset. */
public abstract class CatalogAssetContract {
    private Context context;
    private CatalogDatabase db;
    private CatalogRepository repository;
    private final String name = "catalog-data01-test.db";
    private ExecutorService worker;
    protected abstract java.io.InputStream fixtureStream() throws java.io.IOException;
    private CatalogDatabase openFixture() {
        return androidx.room.Room.databaseBuilder(context, CatalogDatabase.class, name)
                .createFromInputStream(this::fixtureStream).build();
    }
    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(name);
        db = openFixture();
        repository = new RoomCatalogRepository(db.catalogDao());
        worker = Executors.newSingleThreadExecutor();
    }
    @After public void tearDown() {
        if (worker != null) worker.shutdownNow();
        if (db != null) db.close();
        context.deleteDatabase(name);
    }
    private <T> T read(Callable<T> call) throws Exception {
        return worker.submit(call).get(30, TimeUnit.SECONDS);
    }
    @Test public void opensAssetAndReadsFourPacks() throws Exception {
        List<CatalogPack> packs = read(repository::getPacks);
        assertEquals(4, packs.size());
        for (CatalogPack p : packs) assertEquals(4, p.wordCount);
        assertEquals("pack_toeic_basic", packs.get(0).id);
    }
    @Test public void wordsRespectMembershipOrderAndStableIds() throws Exception {
        List<CatalogVocabulary> words = read(() -> repository.getWords("pack_toeic_basic"));
        assertEquals(Arrays.asList("contract", "office", "book", "study"),
                Arrays.asList(words.get(0).lemma, words.get(1).lemma, words.get(2).lemma, words.get(3).lemma));
        Set<String> ids = new HashSet<>();
        for (CatalogVocabulary v : words) {
            assertTrue(v.stableId.matches("[0-9a-f]{16}"));
            assertTrue(ids.add(v.stableId));
            assertEquals(v.lemma, read(() -> repository.getVocabulary(v.stableId)).get().lemma);
        }
    }
    @Test public void missingIdsReturnEmpty() throws Exception {
        assertFalse(read(() -> repository.getVocabulary("missing")).isPresent());
        assertFalse(read(() -> repository.getPack("missing")).isPresent());
        assertTrue(read(() -> repository.getWords("missing")).isEmpty());
        assertFalse(read(() -> repository.getSourceForVocabulary("missing")).isPresent());
    }
    @Test public void examplesAndNullableIpaSurviveMapping() throws Exception {
        CatalogVocabulary v = read(() -> repository.getWords("pack_toeic_basic")).get(0);
        assertEquals("hợp đồng", v.meaningVi);
        assertFalse(v.exampleEn.isEmpty()); assertFalse(v.exampleVi.isEmpty());
        assertNull(v.ipa);
        assertEquals("1.0.0", v.contentVersion);
    }
    @Test public void sourceAttributionIsAvailableOffline() throws Exception {
        CatalogVocabulary v = read(() -> repository.getWords("pack_toeic_basic")).get(0);
        CatalogSource s = read(() -> repository.getSourceForVocabulary(v.stableId)).get();
        assertEquals(v.sourceId, s.id);
        assertEquals("LicenseRef-Project-Test-Fixture", s.licenseCode);
        assertEquals(1, read(repository::getSources).size());
    }
    @Test public void ftsEnglishAndVietnameseReturnTextStableId() throws Exception {
        var en = read(() -> db.catalogDao().matchFixture("contract"));
        var vi = read(() -> db.catalogDao().matchFixture("\"hợp đồng\""));
        assertEquals(1, en.size()); assertEquals(1, vi.size());
        assertEquals(en.get(0).id, vi.get(0).id);
        assertEquals("contract", en.get(0).lemma);
        assertTrue(en.get(0).id.matches("[0-9a-f]{16}"));
    }
    @Test public void everyFtsRowMapsToCorrectEntry() throws Exception {
        read(() -> {
            try (var c = db.getOpenHelper().getReadableDatabase().query(
                    "SELECT COUNT(*) FROM VocabularyFts f JOIN VocabularyEntry v ON f.rowid=v.rowid WHERE f.lemma=v.lemma AND f.meaningVi=v.meaningVi")) {
                assertTrue(c.moveToFirst()); assertEquals(4, c.getInt(0));
            }
            return null;
        });
    }
    @Test public void databaseIsConsistent() throws Exception {
        read(() -> {
            var sqlite = db.getOpenHelper().getReadableDatabase();
            try (var c = sqlite.query("PRAGMA integrity_check")) {
                assertTrue(c.moveToFirst()); assertEquals("ok", c.getString(0));
            }
            try (var c = sqlite.query("PRAGMA foreign_key_check")) { assertFalse(c.moveToFirst()); }
            return null;
        });
    }
    @Test public void reopeningExistingCopyWorks() throws Exception {
        read(repository::getPacks);
        db.close();
        db = openFixture();
        repository = new RoomCatalogRepository(db.catalogDao());
        assertEquals(4, read(repository::getPacks).size());
    }
    @Test public void invalidIdentityHashFailsWithoutDeletingCopy() throws Exception {
        read(() -> {
            db.getOpenHelper().getWritableDatabase().execSQL(
                    "UPDATE room_master_table SET identity_hash='invalid-test-hash' WHERE id=42");
            return null;
        });
        db.close();
        db = openFixture();
        repository = new RoomCatalogRepository(db.catalogDao());
        read(() -> {
            CatalogReadException failure = assertThrows(CatalogReadException.class, repository::getPacks);
            assertTrue(failure.getCause() instanceof IllegalStateException);
            return null;
        });
        assertTrue(context.getDatabasePath(name).isFile());
    }
    @Test public void domainListsAreImmutable() throws Exception {
        List<CatalogPack> packs = read(repository::getPacks);
        assertThrows(UnsupportedOperationException.class, packs::clear);
    }
}
