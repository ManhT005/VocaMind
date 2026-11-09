package com.vocamind.app.data.catalog;
import java.io.*;
import android.os.Bundle;
import org.json.JSONObject;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class FtsSearchBenchmarkInstrumentedTest extends FtsSearchBenchmarkContract {
    @Override protected InputStream benchmarkAsset() throws IOException {
        return InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("benchmark/catalog-5000.db");
    }
    @Override protected boolean enforceLatency() {
        return Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("data05EnforceLatency", "false"));
    }
    @Override protected void reportReady(JSONObject report) {
        if (Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("data09Report", "false"))) {
            Bundle values = new Bundle();
            values.putString("data09Report", report.toString());
            InstrumentationRegistry.getInstrumentation().sendStatus(2, values);
        }
    }
    @Override protected String runnerKind() { return "device-instrumentation-unqualified-until-inventory-verified"; }
}
