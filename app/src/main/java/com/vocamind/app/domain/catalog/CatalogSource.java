package com.vocamind.app.domain.catalog;

/** Immutable catalog snapshot. ipa may be null on CatalogVocabulary. */
public final class CatalogSource {
    public final String id;
    public final String title;
    public final String url;
    public final String licenseCode;
    public final String licenseUrl;
    public final String snapshotDate;

    public CatalogSource(String id, String title, String url, String licenseCode, String licenseUrl, String snapshotDate) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.licenseCode = licenseCode;
        this.licenseUrl = licenseUrl;
        this.snapshotDate = snapshotDate;
    }
}
