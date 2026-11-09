package com.vocamind.app.data.catalog.upgrade;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class CatalogUpgradeTest extends CatalogUpgradeContract {
    @Override protected java.io.InputStream openUserSchema() {
        return getClass().getClassLoader().getResourceAsStream(LegacyUserdataFixture.SCHEMA);
    }
    @Override protected java.io.InputStream openCandidate(String name) throws java.io.IOException {
        var input=getClass().getClassLoader().getResourceAsStream("release-candidate/"+name);
        if(input==null)throw new java.io.FileNotFoundException(name);
        return input;
    }
}
