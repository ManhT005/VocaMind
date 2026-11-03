package com.vocamind.app.data.catalog;
import java.io.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class FtsSearchBenchmarkTest extends FtsSearchBenchmarkContract {
    @Override protected InputStream benchmarkAsset() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/benchmark/catalog-5000.db");
        if (stream == null) throw new FileNotFoundException("benchmark/catalog-5000.db");
        return stream;
    }
    @Override protected String runnerKind() { return "host-robolectric-native-sqlite"; }
}
