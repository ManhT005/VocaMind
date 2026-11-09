package com.vocamind.app.data.catalog.upgrade;

import android.content.Context;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.core.CatalogProvider;
import com.vocamind.app.core.RepositoryProvider;
import com.vocamind.app.data.user.UserDatabase;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Each phase runs in a new instrumentation process against the same installed app data. */
@RunWith(AndroidJUnit4.class)
public class CatalogRebootInstrumentedTest {
    private static final String NAME = UserDatabase.NAME;
    private static final String WORD = "90562a98e8686267";
    private static final String PACK = "pack_toeic_basic";
    private static final String DESCRIPTION_B = "DATA-08 generation B";

    @Test public void runPersistentPhase() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        String phase = InstrumentationRegistry.getArguments().getString("data08Phase");
        assumeTrue("Requires the isolated DATA-08 runner", phase != null);
        Context context = instrumentation.getTargetContext().getApplicationContext();
        assertEquals("com.vocamind.app.data08validation", context.getPackageName());
        File root = new File(context.getNoBackupFilesDir(), "catalog-generations");
        File baseline = new File(context.getNoBackupFilesDir(), "data08-test-baseline.json");
        JSONObject evidence = "seed".equals(phase) ? new JSONObject() : new JSONObject(
                new String(Files.readAllBytes(baseline.toPath()), StandardCharsets.UTF_8));
        CatalogGenerationStore store = new CatalogGenerationStore(root);

        switch (phase) {
            case "seed":
                assertFalse(baseline.exists());
                LegacyUserdataFixture.create(context, NAME,
                        instrumentation.getContext().getAssets().open(LegacyUserdataFixture.SCHEMA), WORD);
                evidence.put("legacy", LegacyUserdataFixture.snapshot(context, NAME, 1, true));
                try (var bundled = context.getAssets().open("database/catalog.db")) {
                    CatalogGenerationStore.copy(bundled, context.getDatabasePath("catalog.db"));
                }
                evidence.put("generationA", openCatalog(context, false, false));
                assertEquals(evidence.getString("legacy"), LegacyUserdataFixture.snapshot(context, NAME, 1, true));
                write(baseline, evidence);
                break;
            case "upgrade":
                assertEquals(evidence.getString("legacy"), LegacyUserdataFixture.snapshot(context, NAME, 1, true));
                String upgraded = openCatalog(context, true, true);
                assertNotEquals(evidence.getString("generationA"), upgraded);
                assertEquals(evidence.getString("generationA"), store.read().getString("previous"));
                assertEquals(evidence.getString("legacy"), LegacyUserdataFixture.snapshot(context, NAME, 2, true));
                LegacyUserdataFixture.verifyCompletionKey(context, NAME);
                evidence.put("generationB", upgraded);
                evidence.put("migrated", LegacyUserdataFixture.snapshot(context, NAME, 2, false));
                write(baseline, evidence);
                break;
            case "verify-upgrade":
                assertEquals(evidence.getString("generationB"), openCatalog(context, true, true));
                assertEquals(evidence.getString("migrated"), LegacyUserdataFixture.snapshot(context, NAME, 2, false));
                break;
            case "rollback":
                assertEquals(evidence.getString("generationB"), store.read().getString("active"));
                var coordinator = new CatalogStartupCoordinator(context, root, context.getDatabasePath(NAME),
                        context.getDatabasePath("catalog.db"), step -> { });
                assertEquals(evidence.getString("generationA"), coordinator.rollback().id);
                assertEquals(evidence.getString("migrated"), LegacyUserdataFixture.snapshot(context, NAME, 2, false));
                break;
            case "verify-rollback":
                assertEquals(evidence.getString("generationA"), openCatalog(context, false, true));
                assertEquals(evidence.getString("migrated"), LegacyUserdataFixture.snapshot(context, NAME, 2, false));
                break;
            default:
                fail("Unknown DATA-08 phase: " + phase);
        }
        JSONObject state = store.read();
        assertEquals("COMMITTED", state.getString("phase"));
        String snapshot = evidence.getString(evidence.has("migrated") ? "migrated" : "legacy");
        Bundle report = new Bundle();
        report.putString("stream", "\nDATA08_PASS " + phase + " " + state.getString("active") + " "
                + CatalogStartupCoordinator.digest(snapshot) + "\n");
        instrumentation.sendStatus(2, report);
    }

    private static String openCatalog(Context context, boolean upgraded, boolean openUserdata) throws Exception {
        try (CatalogProvider catalog = new CatalogProvider(context)) {
            if (openUserdata) {
                try (RepositoryProvider repositories = new RepositoryProvider(context, catalog)) {
                    assertTrue(repositories.progress().readProgress().isSuccess());
                }
            }
            CatalogGeneration generation = CatalogStartupCoordinator.bootstrap(context);
            var database = generation.openDatabase(context);
            try {
                assertEquals(upgraded, DESCRIPTION_B.equals(database.catalogDao().pack(PACK).description));
            } finally {
                database.close();
            }
            return generation.id;
        }
    }

    private static void write(File file, JSONObject document) throws Exception {
        try (var input = new java.io.ByteArrayInputStream(document.toString().getBytes(StandardCharsets.UTF_8))) {
            CatalogGenerationStore.copy(input, file);
        }
    }
}
