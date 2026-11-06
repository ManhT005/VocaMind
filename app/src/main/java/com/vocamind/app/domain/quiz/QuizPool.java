package com.vocamind.app.domain.quiz;

import java.util.*;

/** Snapshot. Invalid input throws IllegalArgumentException; insufficient input is a valid pool. */
public final class QuizPool {
    public enum Availability { READY, INSUFFICIENT_VOCABULARY, INSUFFICIENT_MEANINGS }
    private final List<QuizVocabulary> vocabulary;
    private final Map<String, String> meaningKeys;
    private final Availability availability;
    private final MeaningNormalizer normalizer;
    private final QuizDistractorPolicy policy;
    public QuizPool(Collection<QuizVocabulary> input, MeaningNormalizer normalizer) {
        this(input, normalizer, QuizDistractorPolicy.unrestricted());
    }
    public QuizPool(Collection<QuizVocabulary> input, MeaningNormalizer normalizer, QuizDistractorPolicy policy) {
        if (input == null || normalizer == null) throw new IllegalArgumentException("input/normalizer");
        if (policy == null) throw new IllegalArgumentException("policy required");
        this.policy = policy;
        this.normalizer = normalizer;
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
    /** Constructs a question from this snapshot using exactly the pool's normalizer. */
    public QuizQuestion createQuestion(String stableId, Collection<QuizOption> options, String correctOptionId) {
        if (options == null) throw new IllegalArgumentException("options required");
        for (QuizOption option : options) {
            if (option == null) throw new IllegalArgumentException("option required");
            if (!option.getId().equals(correctOptionId) && !policy.allows(stableId, option.getId()))
                throw new IllegalArgumentException("forbidden distractor");
        }
        for (QuizVocabulary v : vocabulary)
            if (v.getStableId().equals(stableId))
                return new QuizQuestion(v, options, correctOptionId, normalizer);
        throw new IllegalArgumentException("unknown stableId");
    }
    public List<QuizVocabulary> getVocabulary() { return vocabulary; }
    public QuizDistractorPolicy getPolicy() { return policy; }
    public String getMeaningKey(String stableId) {
        if (!meaningKeys.containsKey(stableId)) throw new IllegalArgumentException("unknown stableId");
        return meaningKeys.get(stableId);
    }
    public Availability getAvailability() { return availability; }
    public int getTargetQuestionCount() { return availability == Availability.READY ? Math.min(10, vocabulary.size()) : 0; }
}
