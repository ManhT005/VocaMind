package com.vocamind.app.data.catalog.upgrade;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Startup-only coordinator. No consumer or user writer may run during prepare/recovery. */
public final class CatalogStartupCoordinator {
    static final List<String> REQUIRED = Arrays.asList("catalog.db", "catalog.metadata.json", "content-credits.json", "quiz-policy.json");
    static final List<String> FILES = Arrays.asList("catalog.db", "catalog.metadata.json", "content-credits.json", "quiz-policy.json", "manifest.json", "release-gate-report.json");
    interface Source { InputStream open(String name) throws IOException; }
    enum Step { STAGED, VALIDATED, BEFORE_PUBLISH, AFTER_PUBLISH, REOPENED, COMMITTED }
    interface Faults { void at(Step step) throws IOException; }
    private static final Map<String, CatalogGeneration> PINNED = new HashMap<>();
    private final Context context;
    private final File userdata, legacy;
    private final CatalogGenerationStore store;
    private final Faults faults;

    CatalogStartupCoordinator(Context context, File root, File userdata, File legacy, Faults faults) throws IOException {
        this.context = context.getApplicationContext(); this.userdata = userdata; this.legacy = legacy;
        this.store = new CatalogGenerationStore(root); this.faults = faults;
    }
    /** Application composition only. A selected generation is pinned for this process lifetime. */
    public static synchronized CatalogGeneration bootstrap(Context context) {
        File root = new File(context.getNoBackupFilesDir(), "catalog-generations");
        String key = root.getAbsolutePath();
        CatalogGeneration pinned = PINNED.get(key);
        if (pinned != null && pinned.directory.exists()) return pinned;
        try {
            CatalogStartupCoordinator coordinator = new CatalogStartupCoordinator(context, root,
                    context.getDatabasePath("userdata.db"), context.getDatabasePath("catalog.db"), step -> {});
            CatalogGeneration selected = coordinator.prepare(name -> context.getAssets().open("database/" + name));
            PINNED.put(key, selected);
            return selected;
        } catch (Exception e) { throw failure(e); }
    }
    CatalogGeneration prepare(Source source) {
        synchronized (store.root.getAbsolutePath().intern()) {
            File stage = null;
            String previous = null;
            boolean published = false;
            try {
                CatalogGeneration current = recover(); previous = current == null ? null : current.id;
                if (current != null && sameBundle(source, current)) return current;
                stage = Files.createTempDirectory(store.root.toPath(), "stage-").toFile();
                File bundle = new File(stage, "bundle"); if (!bundle.mkdir()) throw new IOException("Cannot stage bundle");
                JSONObject hashes = new JSONObject(); StringBuilder identity = new StringBuilder();
                List<String> present = new ArrayList<>();
                for (String name : FILES) {
                    try (InputStream in = source.open(name)) {
                        File file = new File(bundle, name); CatalogGenerationStore.copy(in, file);
                        hashes.put(name, CatalogGenerationStore.hash(file)); present.add(name);
                    } catch (FileNotFoundException absent) { if (REQUIRED.contains(name)) throw absent; }
                }
                Collections.sort(present);
                for (String name : present) identity.append(name).append(':').append(hashes.getString(name)).append('\n');
                String id = digest(identity.toString());
                try (InputStream in = new ByteArrayInputStream(new JSONObject().put("hashes", hashes).toString().getBytes(StandardCharsets.UTF_8))) {
                    CatalogGenerationStore.copy(in, new File(stage, "generation.json"));
                }
                CatalogGenerationStore.syncDirectory(bundle); CatalogGenerationStore.syncDirectory(stage);
                faults.at(Step.STAGED);
                CatalogGeneration candidate = new CatalogGeneration(stage, id);
                CatalogBundleValidator.verify(context, candidate, userdata);
                CatalogGenerationStore.syncDirectory(new File(stage, "runtime"));
                CatalogGenerationStore.syncDirectory(stage);
                faults.at(Step.VALIDATED);
                if (current == null && legacy.exists()) verifyLegacyEquivalent(candidate);
                if (id.equals(previous)) return current;
                File target = store.generation(id).directory;
                if (target.exists()) {
                    try {
                        CatalogBundleValidator.verify(context, store.generation(id), userdata);
                    } catch (Exception invalidRetainedGeneration) {
                        // A previous failed publication may leave this digest's directory corrupt.
                        // The active generation is different and the fresh staged copy is verified.
                        // Preserve the rejected bytes, then admit a clean retry from packaged assets.
                        File rejected = new File(store.root, "rejected-" + UUID.randomUUID());
                        try {
                            Files.move(target.toPath(), rejected.toPath(), StandardCopyOption.ATOMIC_MOVE);
                            CatalogGenerationStore.syncDirectory(store.root);
                        } catch (Exception quarantineFailure) {
                            quarantineFailure.addSuppressed(invalidRetainedGeneration);
                            throw quarantineFailure;
                        }
                    }
                }
                if (!target.exists()) {
                    Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    CatalogGenerationStore.syncDirectory(store.root);
                }
                faults.at(Step.BEFORE_PUBLISH);
                store.write(id, previous, "PENDING"); published = true;
                faults.at(Step.AFTER_PUBLISH);
                CatalogGeneration selected = store.generation(id);
                CatalogBundleValidator.verify(context, selected, userdata);
                faults.at(Step.REOPENED);
                store.write(id, previous, "COMMITTED");
                faults.at(Step.COMMITTED);
                return selected;
            } catch (Exception e) {
                if (published && previous != null) {
                    try {
                        CatalogGeneration old = store.generation(previous);
                        CatalogBundleValidator.verify(context, old, userdata);
                        store.write(previous, null, "COMMITTED");
                    } catch (Exception rollback) { e.addSuppressed(rollback); }
                }
                throw failure(e);
            } finally {
                if (stage != null && stage.exists()) {
                    // Only our disposable staging tree; never an active generation/user database.
                    try (var paths = Files.walk(stage.toPath())) {
                        paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException ignored) { /* Retained orphan is never active. */ } });
                    } catch (IOException ignored) { /* Retry/recovery ignores unreferenced staging directories. */ }
                }
            }
        }
    }
    /** Compare trusted packaged bytes without copying a duplicate generation on each startup. */
    private boolean sameBundle(Source source, CatalogGeneration current) throws Exception {
        JSONObject hashes = new JSONObject(new String(Files.readAllBytes(
                new File(current.directory, "generation.json").toPath()), StandardCharsets.UTF_8)).getJSONObject("hashes");
        for (String name : FILES) {
            try (InputStream in = source.open(name)) {
                if (!hashes.has(name) || !hashes.getString(name).equals(CatalogGenerationStore.hash(in))) return false;
            } catch (FileNotFoundException absent) {
                if (REQUIRED.contains(name)) throw absent;
                if (hashes.has(name)) return false;
            }
        }
        return true;
    }
    CatalogGeneration recover() throws Exception {
        JSONObject state = store.read(); if (state == null) return null;
        String active = state.getString("active");
        String previous = state.isNull("previous") ? null : state.getString("previous");
        try {
            CatalogGeneration generation = store.generation(active);
            CatalogBundleValidator.verify(context, generation, userdata);
            if ("PENDING".equals(state.getString("phase"))) store.write(active, previous, "COMMITTED");
            return generation;
        } catch (Exception invalid) {
            if (previous == null) throw invalid;
            CatalogGeneration old = store.generation(previous);
            CatalogBundleValidator.verify(context, old, userdata);
            store.write(old.id, null, "COMMITTED"); return old;
        }
    }
    /** Used by the integration harness; production rollback is only admitted before consumers open. */
    CatalogGeneration rollback() {
        synchronized (store.root.getAbsolutePath().intern()) {
            try {
                JSONObject state = store.read();
                if (state == null || state.isNull("previous")) throw new IllegalStateException("No previous generation");
                CatalogGeneration previous = store.generation(state.getString("previous"));
                CatalogBundleValidator.verify(context, previous, userdata);
                store.write(previous.id, null, "COMMITTED"); return previous;
            } catch (Exception e) { throw failure(e); }
        }
    }
    private void verifyLegacyEquivalent(CatalogGeneration candidate) throws Exception {
        // A previous APK's companions cannot be guessed. Admit only schema-2 catalogs logically
        // identical to the trusted asset; keep the original legacy file untouched in every case.
        var current = candidate.openDatabase(context);
        try (android.database.sqlite.SQLiteDatabase old = android.database.sqlite.SQLiteDatabase.openDatabase(
                legacy.getAbsolutePath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                corrupted -> { throw new CatalogUpgradeException("Corrupt legacy catalog; file retained", null); })) {
            if (old.getVersion() != 2) throw new IllegalStateException("Legacy catalog schema needs explicit compatibility review");
            for (String table : Arrays.asList("ContentSource", "VocabularyEntry", "LearningPack", "PackWord")) {
                try (var a = old.rawQuery("SELECT * FROM " + table + " ORDER BY 1,2", null);
                     var b = current.getOpenHelper().getReadableDatabase().query("SELECT * FROM " + table + " ORDER BY 1,2")) {
                    if (!Arrays.equals(a.getColumnNames(), b.getColumnNames())) throw new IllegalStateException("Legacy schema differs");
                    while (a.moveToNext()) {
                        if (!b.moveToNext()) throw new IllegalStateException("Legacy content differs");
                        for (int i = 0; i < a.getColumnCount(); i++)
                            if (!Objects.equals(a.getString(i), b.getString(i))) throw new IllegalStateException("Legacy metadata cannot be matched to current APK");
                    }
                    if (b.moveToNext()) throw new IllegalStateException("Legacy content differs");
                }
            }
        } finally { current.close(); }
    }
    static String digest(String text) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(); for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b & 255)); return hex.toString();
    }
    private static CatalogUpgradeException failure(Exception e) {
        return e instanceof CatalogUpgradeException ? (CatalogUpgradeException) e
                : new CatalogUpgradeException("Catalog startup failed; data preserved, retry required", e);
    }
}
