package com.vocamind.app.data.catalog;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.quiz.QuizVocabulary;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Run the generated non-fixture candidate through the real Room and domain readers. */
public abstract class ReleaseCatalogContract {
    private static final String DATABASE_NAME = "voc-data-06-candidate-test.db";

    private Context context;
    private CatalogDatabase database;
    private RoomCatalogRepository repository;
    private ExecutorService worker;
    private File candidate;
    private JSONObject canonical;

    protected abstract InputStream openCandidate(String name) throws Exception;

    private byte[] bytes(String name) throws Exception {
        try (InputStream input = openCandidate(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private String text(String name) throws Exception {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }

    private <T> T read(Callable<T> operation) throws Exception {
        return worker.submit(operation).get(30, TimeUnit.SECONDS);
    }

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
        candidate = File.createTempFile("voc-data-06-", ".db", context.getCacheDir());
        Files.write(candidate.toPath(), bytes("catalog.db"));

        database = Room.databaseBuilder(context, CatalogDatabase.class, DATABASE_NAME)
                .createFromFile(candidate)
                .build();
        ContentCredits credits = ContentCredits.parse(text("content-credits.json"));
        repository = new RoomCatalogRepository(database.catalogDao(), () -> credits);
        worker = Executors.newSingleThreadExecutor();
        canonical = new JSONObject(text("canonical-catalog.json"));
    }

    @After
    public void tearDown() {
        if (worker != null) worker.shutdownNow();
        if (database != null) database.close();
        if (context != null) context.deleteDatabase(DATABASE_NAME);
        if (candidate != null) candidate.delete();
    }

    @Test
    public void opensProductionCandidateAndPreservesAllAcceptedFieldsAndPackOrder() throws Exception {
        assertFalse(canonical.getBoolean("fixtureOnly"));
        JSONArray entries = canonical.getJSONArray("entries");
        assertEquals(150, entries.length());
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < entries.length(); i++) {
            JSONObject expected = entries.getJSONObject(i);
            CatalogVocabulary actual = read(() -> repository.getVocabulary(expected.getString("id"))).get();
            assertTrue(ids.add(actual.stableId));
            assertEquals(expected.getString("lemma"), actual.lemma);
            assertEquals(expected.getString("normalizedLemma"), actual.normalizedLemma);
            assertEquals(expected.getString("pos"), actual.pos);
            assertEquals(expected.getString("meaningVi"), actual.meaningVi);
            assertEquals(expected.getString("exampleEn"), actual.exampleEn);
            assertEquals(expected.getString("exampleVi"), actual.exampleVi);
            assertEquals(expected.getString("sourceId"), actual.sourceId);
            assertEquals(expected.getString("contentVersion"), actual.contentVersion);
            assertNull(actual.ipa);
        }

        var packs = read(repository::getPacks);
        assertEquals(4, packs.size());
        int[] quotas = {40, 40, 40, 30};
        JSONArray membership = canonical.getJSONArray("packWords");
        for (int i = 0; i < packs.size(); i++) {
            var pack = packs.get(i);
            assertEquals(canonical.getJSONArray("packs").getJSONObject(i).getString("id"), pack.id);
            assertEquals(quotas[i], pack.wordCount);
            List<CatalogVocabulary> words = read(() -> repository.getWords(pack.id));
            for (int j = 0; j < membership.length(); j++) {
                JSONObject edge = membership.getJSONObject(j);
                if (edge.getString("packId").equals(pack.id)) {
                    assertEquals(edge.getString("vocabularyId"), words.get(edge.getInt("orderNo")).stableId);
                }
            }
        }
    }

    @Test
    public void everyEnglishAndVietnamesePhraseFindsItsTextStableId() throws Exception {
        JSONArray entries = canonical.getJSONArray("entries");
        read(() -> {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                for (String column : List.of("lemma", "meaningVi")) {
                    String query = column + ": \"" + entry.getString(column).replace("\"", "\"\"") + "\"";
                    var matches = database.catalogDao().matchFixture(query);
                    assertTrue(entry.getString("id"), matches.stream()
                            .anyMatch(word -> word.id.equals(entry.optString("id"))));
                }
            }
            return null;
        });
    }

    @Test
    public void candidatePolicyBindsToRoomProjectionAndExcludesAllThreePairsBothWays() throws Exception {
        List<QuizVocabulary> projection = new ArrayList<>();
        JSONArray entries = canonical.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            String id = entries.getJSONObject(i).getString("id");
            var word = read(() -> repository.getVocabulary(id)).get();
            projection.add(new QuizVocabulary(word.stableId, word.lemma, word.pos, word.meaningVi, word.exampleEn));
        }

        String policyText = text("quiz-policy.json");
        var policy = QuizPolicyAsset.parse(policyText, projection);
        JSONObject companion = new JSONObject(policyText);
        JSONArray pairs = companion.getJSONArray("forbiddenDistractorPairs");
        assertEquals(3, pairs.length());
        for (int i = 0; i < pairs.length(); i++) {
            String first = pairs.getJSONArray(i).getString(0);
            String second = pairs.getJSONArray(i).getString(1);
            assertFalse(policy.allows(first, second));
            assertFalse(policy.allows(second, first));
        }

        companion.put("projectionSha256", "0000000000000000000000000000000000000000000000000000000000000000");
        assertThrows(IllegalArgumentException.class,
                () -> QuizPolicyAsset.parse(companion.toString(), projection));
        assertThrows(IllegalArgumentException.class,
                () -> QuizPolicyAsset.parse(policyText, projection.subList(1, projection.size())));
    }

    @Test
    public void offlineSourceCreditsKeepProjectAcceptanceAndMissingAuthorsExplicit() throws Exception {
        var sources = read(repository::getSources);
        assertEquals(1, sources.size());
        var source = sources.get(0);
        assertEquals("PROJECT-USE-OWNER-ACCEPTED", source.licenseCode);
        assertEquals("vocamind-ai-editorial-v1", source.id);
        assertTrue(source.authors.isEmpty());
        assertEquals(source.attributionText, source.creditText);
        assertTrue(source.attributionText.contains("not an external license grant"));
        String id = canonical.getJSONArray("entries").getJSONObject(0).getString("id");
        var vocabularySource = read(() -> repository.getSourceForVocabulary(id)).get();
        assertEquals(source.creditText, vocabularySource.creditText);
        assertEquals(source.snapshotDate, vocabularySource.snapshotDate);
    }

    @Test
    public void sqliteIntegrityCountsAndBundleChecksumsMatch() throws Exception {
        JSONObject metadata = new JSONObject(text("catalog.metadata.json"));
        read(() -> {
            var sqlite = database.getOpenHelper().getReadableDatabase();
            try (var cursor = sqlite.query("PRAGMA integrity_check")) {
                assertTrue(cursor.moveToFirst());
                assertEquals("ok", cursor.getString(0));
            }
            try (var cursor = sqlite.query("PRAGMA foreign_key_check")) {
                assertFalse(cursor.moveToFirst());
            }
            try (var cursor = sqlite.query(
                    "SELECT COUNT(DISTINCT id), (SELECT COUNT(*) FROM PackWord) FROM VocabularyEntry")) {
                assertTrue(cursor.moveToFirst());
                assertEquals(150, cursor.getInt(0));
                assertEquals(150, cursor.getInt(1));
            }
            try (var cursor = sqlite.query(
                    "SELECT COUNT(*) FROM VocabularyFts f JOIN VocabularyEntry v ON v.rowid=f.rowid "
                            + "WHERE f.lemma=v.lemma AND f.meaningVi=v.meaningVi AND typeof(v.id)='text'")) {
                assertTrue(cursor.moveToFirst());
                assertEquals(150, cursor.getInt(0));
            }
            try (var cursor = sqlite.query("SELECT identity_hash FROM room_master_table WHERE id=42")) {
                assertTrue(cursor.moveToFirst());
                assertEquals(metadata.getString("identityHash"), cursor.getString(0));
            }
            return null;
        });

        JSONObject hashes = new JSONObject(text("manifest.json")).getJSONObject("outputHashes");
        for (String name : List.of("catalog.db", "catalog.metadata.json", "quiz-policy.json",
                "content-credits.json", "release-gate-report.json")) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes(name));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            assertEquals(name, hashes.getString(name), hex.toString());
        }
        assertFalse(new JSONObject(text("release-gate-report.json")).getBoolean("releaseAuthorized"));
    }
}
