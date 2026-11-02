package com.vocamind.app.domain.catalog;

/** Immutable catalog snapshot. ipa may be null on CatalogVocabulary. */
public final class CatalogVocabulary {
    public final String stableId;
    public final String lemma;
    public final String normalizedLemma;
    public final String pos;
    public final String meaningVi;
    public final String exampleEn;
    public final String exampleVi;
    public final String ipa;
    public final String sourceId;
    public final String contentVersion;

    public CatalogVocabulary(String stableId, String lemma, String normalizedLemma, String pos, String meaningVi, String exampleEn, String exampleVi, String ipa, String sourceId, String contentVersion) {
        this.stableId = stableId;
        this.lemma = lemma;
        this.normalizedLemma = normalizedLemma;
        this.pos = pos;
        this.meaningVi = meaningVi;
        this.exampleEn = exampleEn;
        this.exampleVi = exampleVi;
        this.ipa = ipa;
        this.sourceId = sourceId;
        this.contentVersion = contentVersion;
    }
}
