package com.vocamind.app.data.catalog;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

import java.io.InputStream;
import java.util.Objects;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ReleaseCatalogTest extends ReleaseCatalogContract {
    @Override
    protected InputStream openCandidate(String name) {
        return Objects.requireNonNull(getClass().getClassLoader()
                .getResourceAsStream("release-candidate/" + name), name);
    }
}
