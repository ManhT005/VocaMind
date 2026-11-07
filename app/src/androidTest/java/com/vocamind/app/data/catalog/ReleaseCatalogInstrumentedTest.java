package com.vocamind.app.data.catalog;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.RunWith;

import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class ReleaseCatalogInstrumentedTest extends ReleaseCatalogContract {
    @Override
    protected InputStream openCandidate(String name) throws Exception {
        return InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("release-candidate/" + name);
    }
}
