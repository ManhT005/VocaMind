package com.vocamind.app.domain.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.List;

/** Immutable catalog source snapshot with optional companion credits. */
public final class CatalogSource {
    public final String id;
    public final String title;
    public final String url;
    public final String licenseCode;
    public final String licenseUrl;
    public final String snapshotDate;
    public final String attributionText;
    public final List<String> authors;
    public final String creditText;

    public CatalogSource(String id, String title, String url, String licenseCode, String licenseUrl, String snapshotDate) {
        this(id, title, url, licenseCode, licenseUrl, snapshotDate, "", Collections.emptyList(), null);
    }

    public CatalogSource(String id, String title, String url, String licenseCode, String licenseUrl,
                         String snapshotDate, String attributionText) {
        this(id, title, url, licenseCode, licenseUrl, snapshotDate, attributionText, Collections.emptyList(), null);
    }

    public CatalogSource(String id, String title, String url, String licenseCode, String licenseUrl,
                         String snapshotDate, List<String> authors, String creditText) {
        this(id, title, url, licenseCode, licenseUrl, snapshotDate, "", authors, creditText);
    }

    public CatalogSource(String id, String title, String url, String licenseCode, String licenseUrl,
                         String snapshotDate, String attributionText, List<String> authors, String creditText) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.licenseCode = licenseCode;
        this.licenseUrl = licenseUrl;
        this.snapshotDate = snapshotDate;
        this.attributionText = attributionText;
        List<String> copy = new ArrayList<>(authors);
        copy.forEach(Objects::requireNonNull);
        this.authors = Collections.unmodifiableList(copy);
        this.creditText = creditText;
    }
}
