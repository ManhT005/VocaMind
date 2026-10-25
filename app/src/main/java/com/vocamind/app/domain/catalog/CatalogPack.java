package com.vocamind.app.domain.catalog;

/** Immutable catalog snapshot. ipa may be null on CatalogVocabulary. */
public final class CatalogPack {
    public final String id;
    public final String title;
    public final String description;
    public final String category;
    public final int orderNo;
    public final String version;
    public final int wordCount;

    public CatalogPack(String id, String title, String description, String category, int orderNo, String version, int wordCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.orderNo = orderNo;
        this.version = version;
        this.wordCount = wordCount;
    }
}
