package com.vocamind.app.data.catalog;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class CatalogAssetTest extends CatalogAssetContract {
    @Override protected java.io.InputStream fixtureStream() throws java.io.IOException {
        java.io.InputStream stream = getClass().getResourceAsStream("/fixtures/catalog.db");
        if (stream == null) throw new java.io.FileNotFoundException("fixtures/catalog.db");
        return stream;
    }
}
