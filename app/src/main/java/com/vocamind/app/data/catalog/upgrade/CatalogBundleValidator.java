package com.vocamind.app.data.catalog.upgrade;

import android.content.Context;
import com.vocamind.app.data.catalog.*;
import com.vocamind.app.data.repository.UserDataMapper;
import com.vocamind.app.domain.quiz.QuizVocabulary;
import org.json.*;
import java.io.File;
import java.util.*;

final class CatalogBundleValidator {
    private CatalogBundleValidator() { }
    static void verify(Context context, CatalogGeneration generation, File userdata) throws Exception {
        JSONObject descriptor = new JSONObject(new String(java.nio.file.Files.readAllBytes(
                new File(generation.directory, "generation.json").toPath()), java.nio.charset.StandardCharsets.UTF_8));
        JSONObject hashes = descriptor.getJSONObject("hashes");
        List<String> names = new ArrayList<>(); hashes.keys().forEachRemaining(names::add); Collections.sort(names);
        File bundleDirectory = new File(generation.directory, "bundle");
        String[] actual = bundleDirectory.list();
        if (actual == null || !new HashSet<>(Arrays.asList(actual)).equals(new HashSet<>(names)))
            throw new IllegalStateException("Unexpected bundle file set");
        StringBuilder identity = new StringBuilder();
        for (String name : names) {
            if (!CatalogStartupCoordinator.FILES.contains(name)) throw new IllegalStateException("Unknown bundle file");
            if (java.nio.file.Files.isSymbolicLink(generation.bundleFile(name).toPath())) throw new IllegalStateException("Symlink in bundle");
            if (!CatalogGenerationStore.hash(generation.bundleFile(name)).equals(hashes.getString(name)))
                throw new IllegalStateException("Catalog bundle checksum mismatch");
            identity.append(name).append(':').append(hashes.getString(name)).append('\n');
        }
        if (!names.containsAll(CatalogStartupCoordinator.REQUIRED)) throw new IllegalStateException("Incomplete bundle");
        if (!CatalogStartupCoordinator.digest(identity.toString()).equals(generation.id)) throw new IllegalStateException("Bundle ID mismatch");
        JSONObject metadata = new JSONObject(generation.text("catalog.metadata.json"));
        if (!hashes.getString("catalog.db").equals(metadata.getString("sha256")))
            throw new IllegalStateException("Catalog metadata checksum mismatch");
        if (names.contains("manifest.json")) {
            JSONObject manifest = new JSONObject(generation.text("manifest.json"));
            if (manifest.getInt("schemaVersion") != 1 || !"candidate-ready".equals(manifest.getString("status")))
                throw new IllegalStateException("Unsupported candidate manifest");
            JSONObject outputs = manifest.getJSONObject("outputHashes");
            Set<String> expected = new HashSet<>(CatalogStartupCoordinator.FILES); expected.remove("manifest.json");
            Set<String> declared = new HashSet<>(); outputs.keys().forEachRemaining(declared::add);
            if (!expected.equals(declared) || !names.containsAll(expected)) throw new IllegalStateException("Incomplete candidate manifest");
            for (Iterator<String> i = outputs.keys(); i.hasNext();) {
                String name = i.next();
                if (!hashes.has(name) || !hashes.getString(name).equals(outputs.getString(name)))
                    throw new IllegalStateException("Candidate manifest mismatch");
            }
            JSONObject gate = new JSONObject(generation.text("release-gate-report.json"));
            if (!"pass".equals(gate.getString("technicalStatus"))
                    || gate.getBoolean("releaseAuthorized") != manifest.getBoolean("releaseAuthorized"))
                throw new IllegalStateException("Candidate gate mismatch");
            if ((!manifest.getBoolean("releaseAuthorized") || "internal-qa".equals(metadata.optString("distributionScope")))
                    && !context.getPackageName().endsWith(".qa"))
                throw new IllegalStateException("Internal QA candidate is not authorized for this application profile");
        } else if (names.contains("release-gate-report.json") || !metadata.optString("kind").startsWith("synthetic-test-fixture")) {
            throw new IllegalStateException("Non-fixture bundle requires release manifest");
        }
        CatalogDatabase db = generation.openDatabase(context);
        try {
            var sql = db.getOpenHelper().getReadableDatabase(); // Forces Room identity/schema validation.
            try (var c = sql.query("PRAGMA integrity_check")) { c.moveToFirst(); if (!"ok".equals(c.getString(0))) throw new IllegalStateException("Corrupt catalog"); }
            try (var c = sql.query("PRAGMA foreign_key_check")) { if (c.moveToFirst()) throw new IllegalStateException("Broken catalog references"); }
            for (String query : Arrays.asList(
                    "SELECT count(*) FROM VocabularyEntry v LEFT JOIN VocabularyFts f ON f.rowid=v.rowid WHERE f.rowid IS NULL OR f.lemma!=v.lemma OR f.normalizedLemma!=v.normalizedLemma OR f.meaningVi!=v.meaningVi",
                    "SELECT count(*) FROM VocabularyFts f LEFT JOIN VocabularyEntry v ON f.rowid=v.rowid WHERE v.id IS NULL")) {
                try (var c = sql.query(query)) { c.moveToFirst(); if (c.getLong(0) != 0) throw new IllegalStateException("Invalid FTS mapping"); }
            }
            try (var c = sql.query("PRAGMA user_version")) {
                c.moveToFirst(); if (c.getInt(0) != metadata.getInt("schemaVersion")) throw new IllegalStateException("Metadata schema mismatch");
            }
            try (var c = sql.query("SELECT identity_hash FROM room_master_table WHERE id=42")) {
                if (!c.moveToFirst() || !metadata.getString("identityHash").equals(c.getString(0))) throw new IllegalStateException("Metadata identity mismatch");
            }
            try (var c = sql.query("SELECT COUNT(*) FROM VocabularyEntry")) {
                c.moveToFirst(); if (c.getInt(0) != metadata.getInt("uniqueVocabularyCount")) throw new IllegalStateException("Vocabulary count mismatch");
            }
            try (var c = sql.query("SELECT COUNT(*) FROM PackWord")) {
                c.moveToFirst(); if (c.getInt(0) != metadata.getInt("membershipCount")) throw new IllegalStateException("Membership count mismatch");
            }
            JSONObject packCounts = metadata.getJSONObject("packCounts");
            var packs = db.catalogDao().packs();
            if (packCounts.length() != packs.size()) throw new IllegalStateException("Pack count mismatch");
            for (var pack : packs) {
                if (db.catalogDao().wordCount(pack.id) != packCounts.getInt(pack.id)) throw new IllegalStateException("Pack membership mismatch");
            }
            var credits = generation.credits();
            List<com.vocamind.app.domain.catalog.CatalogSource> sources = new ArrayList<>();
            for (var source : db.catalogDao().sources()) sources.add(CatalogMapper.source(source));
            credits.verifySources(sources);
            var vocabulary = db.catalogDao().allVocabulary();
            List<QuizVocabulary> words = new ArrayList<>();
            Set<String> vocabularyIds = new HashSet<>(), packIds = new HashSet<>();
            for (var row : vocabulary) {
                words.add(UserDataMapper.quizVocabulary(CatalogMapper.vocabulary(row)));
                vocabularyIds.add(row.id);
            }
            for (var pack : packs) packIds.add(pack.id);
            generation.quizPolicy(words);
            CatalogReferenceChecker.check(userdata, vocabularyIds, packIds);
            // Probe each indexed column separately; selecting one scalar avoids loading matches.
            for (var row : vocabulary) {
                if (!row.id.matches("[0-9a-f]{16}")) throw new IllegalStateException("Invalid stable ID");
                if (!metadata.getString("contentVersion").equals(row.contentVersion)) throw new IllegalStateException("Content version mismatch");
                String[] columns = {"lemma", "normalizedLemma", "meaningVi"};
                String[] texts = {row.lemma, row.normalizedLemma, row.meaningVi};
                for (int i = 0; i < columns.length; i++) {
                    String expression = columns[i] + ": \"" + texts[i].replace("\"", "\"\"") + "\"";
                    try (var c = sql.query("SELECT 1 FROM VocabularyFts f JOIN VocabularyEntry v ON v.rowid=f.rowid "
                            + "WHERE VocabularyFts MATCH ? AND v.id=? LIMIT 1", new Object[]{expression, row.id})) {
                        if (!c.moveToFirst()) throw new IllegalStateException("FTS index does not resolve catalog entry");
                    }
                }
            }
        } finally { db.close(); }
    }
}
