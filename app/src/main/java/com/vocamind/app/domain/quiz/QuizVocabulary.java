package com.vocamind.app.domain.quiz;

import java.util.Objects;
import java.util.Locale;

/** Immutable projection of one vocabulary sense; upstream supplies active packs only. */
public final class QuizVocabulary {
    private final String stableId, lemma, pos, meaning, context;
    public QuizVocabulary(String stableId, String lemma, String pos, String meaning, String context) {
        if (stableId == null || !stableId.matches("[0-9a-fA-F]{16}"))
            throw new IllegalArgumentException("stableId must be 16 hex characters");
        this.stableId = stableId.toLowerCase(Locale.ROOT);
        this.lemma = Checks.text(lemma, "lemma");
        this.pos = Checks.text(pos, "pos");
        this.meaning = Checks.text(meaning, "meaning");
        if (context == null) throw new IllegalArgumentException("context (may be empty)");
        this.context = context;
    }
    public String getStableId() { return stableId; }
    public String getLemma() { return lemma; }
    public String getPos() { return pos; }
    public String getMeaning() { return meaning; }
    public String getContext() { return context; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof QuizVocabulary)) return false;
        QuizVocabulary v = (QuizVocabulary) other;
        return stableId.equals(v.stableId) && lemma.equals(v.lemma) && pos.equals(v.pos)
                && meaning.equals(v.meaning) && context.equals(v.context);
    }
    @Override public int hashCode() { return Objects.hash(stableId, lemma, pos, meaning, context); }
}
