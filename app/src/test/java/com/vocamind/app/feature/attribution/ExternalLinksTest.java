package com.vocamind.app.feature.attribution;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import com.vocamind.app.R;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public class ExternalLinksTest {
    @Test public void normalizesSchemeForBrowserFilterWithoutChangingCaseSensitiveComponents() {
        Context base = ApplicationProvider.getApplicationContext();
        Intent[] launched = {null};
        Context capture = new ContextWrapper(base) {
            @Override public void startActivity(Intent intent) { launched[0] = intent; }
        };
        for (String address : new String[]{
                "HTTPS://example.com/License?Key=AbC#Terms",
                "hTtPs://example.com/License?Key=AbC#Terms",
                "HTTP://example.com/License?Key=AbC#Terms",
                "hTtP://example.com/License?Key=AbC#Terms",
                "https://example.com/License?Key=AbC#Terms"}) {
            launched[0] = null;
            ExternalLinks.open(capture, address);
            assertNotNull(address, launched[0]);
            String scheme = address.substring(0, address.indexOf(':')).toLowerCase(java.util.Locale.ROOT);
            IntentFilter browser = new IntentFilter(Intent.ACTION_VIEW);
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            browser.addDataScheme(scheme);
            // Android scheme matching is case-sensitive. This fails on the old dispatch code.
            assertTrue(address, browser.match(base.getContentResolver(), launched[0], false, "ATTR-R1") >= 0);
            assertEquals(scheme, launched[0].getScheme());
            assertEquals("/License", launched[0].getData().getEncodedPath());
            assertEquals("Key=AbC", launched[0].getData().getEncodedQuery());
            assertEquals("Terms", launched[0].getData().getEncodedFragment());
        }
    }

    @Test public void onlyValidWebAddressesAreAccepted() {
        for (String value : new String[]{null, "", " ", "https://", "https://bad host/", "https:///path",
                "javascript:alert(1)", "file:///sdcard/a", "intent://example", "mailto:a@b.com",
                "https://user:password@example.com", "https://example.com:99999"}) {
            assertFalse(String.valueOf(value), ExternalLinks.isWebUrl(value));
        }
        assertTrue(ExternalLinks.isWebUrl("https://example.com/path?q=1#license"));
        assertTrue(ExternalLinks.isWebUrl("HTTP://example.com"));
    }

    @Test public void handsBrowserIntentToOsAndReportsMissingHandlerOrSecurityFailure() {
        Context base = ApplicationProvider.getApplicationContext();
        Intent[] launched = {null};
        Context success = new ContextWrapper(base) {
            @Override public void startActivity(Intent intent) { launched[0] = intent; }
        };
        ExternalLinks.open(success, "https://example.com/license");
        assertEquals(Intent.ACTION_VIEW, launched[0].getAction());
        assertTrue(launched[0].hasCategory(Intent.CATEGORY_BROWSABLE));
        assertEquals("https://example.com/license", launched[0].getDataString());
        for (RuntimeException failure : new RuntimeException[]{new ActivityNotFoundException(), new SecurityException()}) {
            ExternalLinks.open(new ContextWrapper(base) {
                @Override public void startActivity(Intent intent) { throw failure; }
            }, "https://example.com");
            assertEquals(base.getString(R.string.attribution_link_failed), ShadowToast.getTextOfLatestToast());
        }
        launched[0] = null;
        ExternalLinks.open(success, "javascript:alert(1)");
        assertNull(launched[0]);
        assertEquals(base.getString(R.string.attribution_invalid_link), ShadowToast.getTextOfLatestToast());
    }
}
