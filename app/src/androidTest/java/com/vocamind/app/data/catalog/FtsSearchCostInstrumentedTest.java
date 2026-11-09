package com.vocamind.app.data.catalog;

import android.database.Cursor;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.data.catalog.entity.VocabularyEntry;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Diagnostic only: same full projection/order, no truncation or product DB mutation. */
@RunWith(AndroidJUnit4.class)
public class FtsSearchCostInstrumentedTest {
    @Test public void separateSqlAndHydrationCosts() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        String name = "search-cost-" + UUID.randomUUID() + ".db";
        var db = Room.databaseBuilder(context, CatalogDatabase.class, name)
                .createFromInputStream(() -> instrumentation.getContext().getAssets().open("benchmark/catalog-5000.db")).build();
        var worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                String sql = "SELECT v.* FROM VocabularyEntry v JOIN VocabularyFts f ON f.rowid=v.rowid "
                        + "WHERE VocabularyFts MATCH ? ORDER BY v.normalizedLemma COLLATE NOCASE, v.id";
                String expression = "\"benchword*\"";
                var report = new JSONObject();
                var cases = new JSONArray();
                // Alternate variants per round to reduce order/thermal bias. All samples retained.
                long[][] samples = new long[3][100];
                for (int round = -20; round < 100; round++) {
                    for (int offset = 0; offset < 3; offset++) {
                        int variant = Math.floorMod(round + offset, 3);
                        long start = System.nanoTime();
                        int count;
                        if (variant == 0) count = db.catalogDao().searchVocabulary(expression).size();
                        else try (Cursor cursor = db.query(sql, new Object[]{expression})) {
                            if (variant == 1) count = cursor.getCount();
                            else {
                                var rows = new ArrayList<VocabularyEntry>(cursor.getCount());
                                while (cursor.moveToNext()) {
                                    var v = new VocabularyEntry();
                                    v.id=cursor.getString(0); v.lemma=cursor.getString(1);
                                    v.normalizedLemma=cursor.getString(2); v.pos=cursor.getString(3);
                                    v.meaningVi=cursor.getString(4); v.exampleEn=cursor.getString(5);
                                    v.exampleVi=cursor.getString(6); v.ipa=cursor.getString(7);
                                    v.sourceId=cursor.getString(8); v.contentVersion=cursor.getString(9);
                                    rows.add(v);
                                }
                                count = rows.size();
                                assertEquals("benchword00000", rows.get(0).lemma);
                                assertEquals("benchword04999", rows.get(4999).lemma);
                            }
                        }
                        long elapsed = System.nanoTime()-start;
                        assertEquals(5000, count);
                        if (round >= 0) samples[variant][round]=elapsed;
                    }
                }
                String[] labels = {"production-dao-full-entities", "sql-full-cursor-count-no-java-hydration", "cursor-full-entities"};
                for (int v=0; v<3; v++) {
                    var row = new JSONObject(); var raw = new JSONArray();
                    for (long ns : samples[v]) raw.put(ns);
                    long[] sorted = samples[v].clone(); Arrays.sort(sorted);
                    row.put("variant", labels[v]); row.put("samplesNs", raw);
                    row.put("meanMs", Arrays.stream(samples[v]).average().orElseThrow()/1e6);
                    row.put("p95Ms", sorted[94]/1e6); row.put("maxMs", sorted[99]/1e6);
                    cases.put(row);
                }
                report.put("cases", cases); report.put("matches", 5000);
                report.put("scope", "Diagnostic interleaved full projection/order; cursor count includes SQLite evaluation/window fill, not Java string/entity hydration; scopes overlap, do not subtract as exact phase costs");
                try (var out = new FileWriter(new File(context.getFilesDir(), "search-cost.json"))) { out.write(report.toString(2)); }
                return null;
            }).get(180, TimeUnit.SECONDS);
        } finally { worker.shutdown(); worker.awaitTermination(10, TimeUnit.SECONDS); db.close(); context.deleteDatabase(name); }
    }
}
