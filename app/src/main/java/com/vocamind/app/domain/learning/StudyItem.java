package com.vocamind.app.domain.learning;

import com.vocamind.app.domain.catalog.CatalogVocabulary;

/** A catalog word joined with its progress by stableId. Progress is null for a new word. */
public final class StudyItem {
    private final CatalogVocabulary vocabulary;
    private final WordProgress progress;

    public StudyItem(CatalogVocabulary vocabulary, WordProgress progress) {
        if (vocabulary == null) throw new IllegalArgumentException("vocabulary");
        if (progress != null && !progress.getVocabularyId().equals(vocabulary.stableId)) {
            throw new IllegalArgumentException("progress belongs to another word");
        }
        this.vocabulary = vocabulary;
        this.progress = progress;
    }

    public CatalogVocabulary getVocabulary() { return vocabulary; }
    /** Null for a word never reviewed. */
    public WordProgress getProgress() { return progress; }
    public LeitnerState toState() { return progress == null ? LeitnerState.newWord() : progress.toState(); }
}
