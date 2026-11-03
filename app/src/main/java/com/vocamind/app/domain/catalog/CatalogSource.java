package com.vocamind.app.domain.catalog;

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
        this(id, title, url, licenseCode, licenseUrl, snapshotDate, "", List.of(), null);
    }

    public CatalogSource(String id, String title, String url, String licenseCode, String licenseUrl,
                         String snapshotDate, String attributionText) {
        this(id, title, url, licenseCode, licenseUrl, snapshotDate, attributionText, List.of(), null);
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
        this.authors = List.copyOf(authors);
        this.creditText = creditText;
    }
}
