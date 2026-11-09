package com.vocamind.app.data.catalog.upgrade;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class CatalogUpgradeInstrumentedTest extends CatalogUpgradeContract {
    @Override protected java.io.InputStream openUserSchema() throws java.io.IOException {
        return androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(LegacyUserdataFixture.SCHEMA);
    }
    @Override protected java.io.InputStream openCandidate(String name) throws java.io.IOException {
        return androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("release-candidate/"+name);
    }
}
