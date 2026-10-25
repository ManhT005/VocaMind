package com.vocamind.app.data.catalog;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CatalogAssetInstrumentedTest extends CatalogAssetContract {
    @Override protected java.io.InputStream fixtureStream() throws java.io.IOException {
        return androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("fixtures/catalog.db");
    }
}
