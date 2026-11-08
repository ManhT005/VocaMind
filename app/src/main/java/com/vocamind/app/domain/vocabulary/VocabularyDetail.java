package com.vocamind.app.domain.vocabulary;

import com.vocamind.app.domain.catalog.CatalogVocabulary;

public final class VocabularyDetail {
    public final CatalogVocabulary word;
    public final boolean saved;
    public VocabularyDetail(CatalogVocabulary word, boolean saved) {
        this.word = word;
        this.saved = saved;
    }
}
