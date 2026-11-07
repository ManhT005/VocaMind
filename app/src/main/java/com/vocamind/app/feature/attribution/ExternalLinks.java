package com.vocamind.app.feature.attribution;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.vocamind.app.R;
import java.net.URI;
import java.net.URISyntaxException;

/** No network request or permission: only hand valid web URLs to an external app. */
public final class ExternalLinks {
    private ExternalLinks() { }

    public static boolean isWebUrl(String value) {
        if (value == null || value.codePoints().allMatch(Character::isWhitespace)) return false;
        try {
            URI uri = new URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().codePoints().allMatch(Character::isWhitespace)
                    && uri.getRawUserInfo() == null && uri.getPort() >= -1
                    && uri.getPort() <= 65535;
        } catch (URISyntaxException invalid) {
            return false;
        }
    }

    public static void open(Context context, String value) {
        if (!isWebUrl(value)) {
            Toast.makeText(context, R.string.attribution_invalid_link, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(value).normalizeScheme());
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            context.startActivity(intent);
        } catch (RuntimeException failure) {
            // Includes ActivityNotFoundException and SecurityException from the OS/handler.
            Toast.makeText(context, R.string.attribution_link_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
