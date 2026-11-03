package com.vocamind.app.data.catalog;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.domain.catalog.*;
import org.junit.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Same real-asset contract runs on a device and on Robolectric native SQLite. */
public abstract class CatalogAssetContract {
    private Context context;
    private CatalogDatabase db;
    private CatalogRepository repository;
    private final String name = "catalog-data01-test.db";
    private ExecutorService worker;
    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(name);
        db = CatalogDatabase.open(context, name);
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
    private byte[] readAsset(String path) throws IOException {
        try (var input = context.getAssets().open(path);
             var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    @Test public void creditsEnrichExistingCopyAndBothRepositoryQueriesWithoutChangingDatabase() throws Exception {
        CatalogSource original = read(repository::getSources).get(0);
        assertFalse(original.attributionText.isEmpty());
        db.close();
        byte[] before = java.nio.file.Files.readAllBytes(context.getDatabasePath(name).toPath());
        db = CatalogDatabase.open(context, name);
        ContentCredits credits = ContentCredits.parse("""
                {"schemaVersion":1,"sources":[
                  {"sourceId":"fixture-authored-20261023","snapshotDate":"2026-10-23",
                   "authors":["Synthetic test author A","Synthetic test author B"],"creditText":"Test notice only"},
                  {"sourceId":"not-in-db","snapshotDate":"2026-10-25","authors":["Do not list"],"creditText":null}
                ]}
                """);
        repository = new RoomCatalogRepository(db.catalogDao(), () -> credits);
        List<CatalogSource> sources = read(repository::getSources);
        assertEquals(1, sources.size());
        assertEquals(List.of("Synthetic test author A", "Synthetic test author B"), sources.get(0).authors);
        String word = read(() -> repository.getWords("pack_toeic_basic")).get(0).stableId;
        assertEquals(sources.get(0).authors, read(() -> repository.getSourceForVocabulary(word)).get().authors);
        assertEquals("Test notice only", read(() -> repository.getSourceForVocabulary(word)).get().creditText);
        assertEquals("Test notice only", sources.get(0).creditText);
        for (CatalogSource source : List.of(sources.get(0),
                read(() -> repository.getSourceForVocabulary(word)).get())) {
            assertEquals(original.attributionText, source.attributionText);
            assertEquals(original.id, source.id);
            assertEquals(original.title, source.title);
            assertEquals(original.url, source.url);
            assertEquals(original.licenseCode, source.licenseCode);
            assertEquals(original.licenseUrl, source.licenseUrl);
            assertEquals(original.snapshotDate, source.snapshotDate);
            assertThrows(UnsupportedOperationException.class, source.authors::clear);
        }
        db.close();
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(context.getDatabasePath(name).toPath()));
    }

    @Test public void creditReadFailureIsAnErrorAndCanBeRetried() throws Exception {
        String word = read(() -> repository.getWords("pack_toeic_basic")).get(0).stableId;
        boolean[] broken = {true};
        repository = new RoomCatalogRepository(db.catalogDao(), () -> {
            if (broken[0]) throw new IllegalStateException("Simulated credit read error");
            return ContentCredits.empty();
        });
        read(() -> { assertThrows(CatalogReadException.class, repository::getSources); return null; });
        read(() -> { assertThrows(CatalogReadException.class,
                () -> repository.getSourceForVocabulary(word)); return null; });
        broken[0] = false;
        assertEquals(1, read(repository::getSources).size());
        assertTrue(read(repository::getSources).get(0).authors.isEmpty());
        assertTrue(read(() -> repository.getSourceForVocabulary(word)).isPresent());
    }

    @Test public void unmatchedCreditsPreserveBothRepositoryQueries() throws Exception {
        CatalogSource original = read(repository::getSources).get(0);
        String word = read(() -> repository.getWords("pack_toeic_basic")).get(0).stableId;
        ContentCredits credits = ContentCredits.parse("""
                {"schemaVersion":1,"sources":[
                  {"sourceId":"fixture-authored-20261023","snapshotDate":"different-date",
                   "authors":["Wrong snapshot"],"creditText":"Must not appear"},
                  {"sourceId":"different-source","snapshotDate":"2026-10-23",
                   "authors":["Wrong source"],"creditText":"Must not appear"}
                ]}
                """);
        repository = new RoomCatalogRepository(db.catalogDao(), () -> credits);
        for (CatalogSource source : List.of(read(repository::getSources).get(0),
                read(() -> repository.getSourceForVocabulary(word)).get())) {
            assertEquals(original.id, source.id);
            assertEquals(original.title, source.title);
            assertEquals(original.url, source.url);
            assertEquals(original.licenseCode, source.licenseCode);
            assertEquals(original.licenseUrl, source.licenseUrl);
            assertEquals(original.snapshotDate, source.snapshotDate);
            assertEquals(original.attributionText, source.attributionText);
            assertTrue(source.authors.isEmpty());
            assertNull(source.creditText);
            assertThrows(UnsupportedOperationException.class, () -> source.authors.add("Unexpected"));
        }
    }
    @Test public void opensAssetAndReadsFourPacks() throws Exception {
        List<CatalogPack> packs = read(repository::getPacks);
        assertEquals(4, packs.size());
        for (CatalogPack p : packs) assertEquals(4, p.wordCount);
        assertEquals("pack_toeic_basic", packs.get(0).id);
    }
    @Test public void wordsRespectMembershipOrderAndStableIds() throws Exception {
        List<CatalogVocabulary> words = read(() -> repository.getWords("pack_toeic_basic"));
        assertEquals(Arrays.asList("book", "book", "bank", "airport"),
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
        assertEquals("cuốn sách", v.meaningVi);
        assertFalse(v.exampleEn.isEmpty()); assertFalse(v.exampleVi.isEmpty());
        assertNull(v.ipa);
        assertEquals("1.0.0", v.contentVersion);
    }
    @Test public void sourceAttributionIsAvailableOffline() throws Exception {
        CatalogVocabulary v = read(() -> repository.getWords("pack_toeic_basic")).get(0);
        CatalogSource s = read(() -> repository.getSourceForVocabulary(v.stableId)).get();
        assertEquals(v.sourceId, s.id);
        assertEquals("FIXTURE-ONLY", s.licenseCode);
        assertEquals("Synthetic fixture authored for contract validation; not a verified source dataset.", s.attributionText);
        assertEquals(1, read(repository::getSources).size());
    }
    @Test public void ftsEnglishAndVietnameseReturnTextStableId() throws Exception {
        var en = read(() -> db.catalogDao().matchFixture("airport"));
        var vi = read(() -> db.catalogDao().matchFixture("\"sân bay\""));
        assertEquals(1, en.size()); assertEquals(1, vi.size());
        assertEquals(en.get(0).id, vi.get(0).id);
        assertEquals("airport", en.get(0).lemma);
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
        db = CatalogDatabase.open(context, name);
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
        db = CatalogDatabase.open(context, name);
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

    @Test public void canonicalFixtureSurvivesRoomAndRepository() throws Exception {
        byte[] canonical = readAsset("catalog.json");
        var json = new JSONObject(new String(canonical, StandardCharsets.UTF_8));
        var entries = json.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            var e = entries.getJSONObject(i);
            var word = read(() -> repository.getVocabulary(e.getString("id"))).get();
            assertEquals(e.getString("normalizedLemma"), word.normalizedLemma);
            assertEquals(e.getString("pos"), word.pos);
            assertEquals(e.getString("meaningVi"), word.meaningVi);
            assertEquals(e.getString("exampleEn"), word.exampleEn);
            assertEquals(e.getString("exampleVi"), word.exampleVi);
            assertNull(word.ipa);
            var source = read(() -> repository.getSourceForVocabulary(word.stableId)).get();
            assertEquals(json.getJSONArray("sources").getJSONObject(0).getString("attributionText"), source.attributionText);
        }
        var packs = read(repository::getPacks);
        for (int i = 0; i < packs.size(); i++) {
            assertEquals(json.getJSONArray("packs").getJSONObject(i).getString("id"), packs.get(i).id);
            String packId = packs.get(i).id;
            var words = read(() -> repository.getWords(packId));
            for (int j = 0; j < words.size(); j++) {
                assertEquals(entries.getJSONObject(j).getString("id"), words.get(j).stableId);
            }
        }
        var digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest(canonical)) hex.append(String.format("%02x", b & 255));
        {
            var metadata = new JSONObject(new String(readAsset("database/catalog.metadata.json"), StandardCharsets.UTF_8));
            assertEquals(hex.toString(), metadata.getString("canonicalSha256"));
            digest.reset();
            try (var asset = context.getAssets().open(CatalogDatabase.ASSET)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = asset.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            hex.setLength(0);
            for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
            assertEquals(hex.toString(), metadata.getString("sha256"));
        }
    }

    @Test public void databaseRejectsDuplicateOrdering() throws Exception {
        read(() -> {
            var sqlite = db.getOpenHelper().getWritableDatabase();
            assertThrows(SQLiteConstraintException.class, () -> sqlite.execSQL(
                    "UPDATE LearningPack SET orderNo=0 WHERE id='pack_toeic_office'"));
            assertThrows(SQLiteConstraintException.class, () -> sqlite.execSQL(
                    "UPDATE PackWord SET orderNo=0 WHERE packId='pack_toeic_basic' AND orderNo=1"));
            return null;
        });
    }
}
