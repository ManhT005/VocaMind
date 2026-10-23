package com.vocamind.app.domain.quiz;

import java.util.*;

/** Snapshot. Invalid input throws IllegalArgumentException; insufficient input is a valid pool. */
public final class QuizPool {
    public enum Availability { READY, INSUFFICIENT_VOCABULARY, INSUFFICIENT_MEANINGS }
    private final List<QuizVocabulary> vocabulary;
    private final Map<String, String> meaningKeys;
    private final Availability availability;
    public QuizPool(Collection<QuizVocabulary> input, MeaningNormalizer normalizer) {
        if (input == null || normalizer == null) throw new IllegalArgumentException("input/normalizer");
        Map<String, QuizVocabulary> unique = new LinkedHashMap<>();
        Map<String, String> keys = new LinkedHashMap<>();
        for (QuizVocabulary v : input) {
            if (v == null) throw new IllegalArgumentException("null vocabulary");
            QuizVocabulary old = unique.get(v.getStableId());
            if (old != null && !old.equals(v)) throw new IllegalArgumentException("conflicting stableId: " + v.getStableId());
            if (old == null) {
                unique.put(v.getStableId(), v);
                keys.put(v.getStableId(), Checks.text(normalizer.normalize(v.getMeaning()), "meaningKey"));
            }
        }
        vocabulary = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        meaningKeys = Collections.unmodifiableMap(keys);
        availability = unique.size() < 4 ? Availability.INSUFFICIENT_VOCABULARY
                : new HashSet<>(keys.values()).size() < 4 ? Availability.INSUFFICIENT_MEANINGS : Availability.READY;
    }
    public List<QuizVocabulary> getVocabulary() { return vocabulary; }
    public String getMeaningKey(String stableId) {
        if (!meaningKeys.containsKey(stableId)) throw new IllegalArgumentException("unknown stableId");
        return meaningKeys.get(stableId);
    }
    public Availability getAvailability() { return availability; }
    public int getTargetQuestionCount() { return availability == Availability.READY ? Math.min(10, vocabulary.size()) : 0; }
}
