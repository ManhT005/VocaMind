package com.vocamind.app.data.catalog;

import com.vocamind.app.data.catalog.entity.*;
import com.vocamind.app.domain.catalog.*;

public final class CatalogMapper {
    private CatalogMapper() { }
    public static CatalogSource source(ContentSource s) {
        return new CatalogSource(s.id, s.title, s.url, s.licenseCode, s.licenseUrl, s.snapshotDate);
    }
    public static CatalogVocabulary vocabulary(VocabularyEntry v) {
        return new CatalogVocabulary(v.id, v.lemma, v.normalizedLemma, v.pos,
                v.meaningVi, v.exampleEn, v.exampleVi, v.ipa, v.sourceId, v.contentVersion);
    }
    public static CatalogPack pack(LearningPack p, int count) {
        return new CatalogPack(p.id, p.title, p.description, p.category, p.orderNo, p.version, count);
    }
}
