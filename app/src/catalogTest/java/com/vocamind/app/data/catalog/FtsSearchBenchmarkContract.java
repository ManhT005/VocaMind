package com.vocamind.app.data.catalog;

import android.content.Context;
import android.os.Build;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.domain.catalog.VocabularySearchQuery;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Profiling real prepack FTS queries; host results cannot certify reference-device latency. */
public abstract class FtsSearchBenchmarkContract {
    protected abstract InputStream benchmarkAsset() throws IOException;
    protected boolean enforceLatency() { return false; }
    protected abstract String runnerKind();
    /** Device wrapper can export the complete report through instrumentation before assertions. */
    protected void reportReady(JSONObject report) throws Exception { }
    private String datasetSha256() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = benchmarkAsset()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = stream.read(bytes)) != -1) digest.update(bytes, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    @Test public void profileFiveThousandWordPrepackAndWriteRawSamples() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "data05-benchmark-" + UUID.randomUUID() + ".db";
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CatalogDatabase db = Room.databaseBuilder(context, CatalogDatabase.class, name)
                .createFromInputStream(this::benchmarkAsset).build();
        try {
            worker.submit(() -> {
                try {
                    try (var count = db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(DISTINCT id) FROM VocabularyEntry")) {
                        assertTrue(count.moveToFirst()); assertEquals(5000, count.getInt(0));
                    }
                    JSONObject report = new JSONObject();
                    report.put("runner", runnerKind()); report.put("api", Build.VERSION.SDK_INT);
                    report.put("model", Build.MODEL); report.put("hardware", Build.HARDWARE);
                    report.put("datasetSha256", datasetSha256());
                    report.put("fingerprint", Build.FINGERPRINT);
                    report.put("warmupIterations", 20); report.put("sampleCount", 100);
                    report.put("datasetWords", 5000); report.put("referenceHardwareVerified", false);
                    report.put("latencyAssertionsEnabled", enforceLatency());
                    report.put("timingScope", "DAO MATCH query plus entity materialization; excludes asset opening, expression preparation, executor queue, domain mapping and UI");
                    JSONArray cases = new JSONArray();
                    String[] inputs = {"benchword04999", "nghĩa kiểm thử 04999", "benchword000", "notpresentbenchmark", "benchword"};
                    int[] counts = {1, 1, 100, 0, 5000};
                    for (int c = 0; c < inputs.length; c++) {
                        String expression = FtsMatchExpression.from(VocabularySearchQuery.parse(inputs[c]));
                        for (int warmup = 0; warmup < 20; warmup++) assertEquals(counts[c], db.catalogDao().searchVocabulary(expression).size());
                        long[] samples = new long[100]; JSONArray raw = new JSONArray();
                        for (int i = 0; i < samples.length; i++) {
                            long start = System.nanoTime(); var matches = db.catalogDao().searchVocabulary(expression);
                            samples[i] = System.nanoTime() - start;
                            assertEquals(counts[c], matches.size()); raw.put(samples[i]);
                        }
                        long[] sorted = samples.clone(); Arrays.sort(sorted);
                        double mean = Arrays.stream(samples).average().orElse(0.0) / 1_000_000.0;
                        JSONObject item = new JSONObject(); item.put("query", inputs[c]); item.put("matches", counts[c]);
                        item.put("samplesNs", raw); item.put("meanMs", mean); item.put("p50Ms", sorted[49] / 1_000_000.0);
                        item.put("p95Ms", sorted[94] / 1_000_000.0); item.put("maxMs", sorted[99] / 1_000_000.0);
                        item.put("observedAllBelow50Ms", sorted[99] < 50_000_000L);
                        item.put("observedMeanBelow15Ms", mean < 15); cases.put(item);
                    }
                    report.put("cases", cases);
                    File output = new File(context.getFilesDir(), "data05-fts-benchmark-api" + Build.VERSION.SDK_INT + ".json");
                    try (Writer writer = new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8)) {
                        writer.write(report.toString(2));
                    }
                    reportReady(report);
                    System.out.println("DATA05_BENCHMARK_JSON=" + report);
                    if (enforceLatency()) for (int i = 0; i < cases.length(); i++)
                        assertTrue("Query exceeded required 50ms: " + cases.getJSONObject(i).getString("query"),
                                cases.getJSONObject(i).getDouble("maxMs") < 50);
                    return null;
                } catch (Exception e) { throw new RuntimeException(e); }
            }).get(90, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); db.close(); context.deleteDatabase(name); }
    }
}
