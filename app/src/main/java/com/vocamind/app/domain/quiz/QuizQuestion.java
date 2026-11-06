package com.vocamind.app.domain.quiz;

import java.util.*;

public final class QuizQuestion {
    private final QuizVocabulary vocabulary;
    private final List<QuizOption> options;
    private final String correctOptionId;
    /** Uses the pool's normalizer to validate display meanings and the target answer.
     * Prefer QuizPool.createQuestion when constructing questions from a pool. */
    public QuizQuestion(QuizVocabulary vocabulary, Collection<QuizOption> options,
                        String correctOptionId, MeaningNormalizer normalizer) {
        if (normalizer == null) throw new IllegalArgumentException("normalizer required");
        if (vocabulary == null || options == null || options.size() != 4) throw new IllegalArgumentException("vocabulary/four options required");
        List<QuizOption> copy = new ArrayList<>(options);
        String expectedKey = Checks.text(normalizer.normalize(vocabulary.getMeaning()), "meaningKey");
        Set<String> ids = new HashSet<>(), keys = new HashSet<>();
        QuizOption correct = null;
        for (QuizOption option : copy) {
            if (option == null || !ids.add(option.getId()) || !keys.add(option.getMeaningKey()))
                throw new IllegalArgumentException("distinct option IDs and meanings required");
            String actualKey = Checks.text(normalizer.normalize(option.getMeaning()), "meaningKey");
            if (!actualKey.equals(option.getMeaningKey()))
                throw new IllegalArgumentException("option meaningKey does not match normalized meaning");
            if (option.getId().equals(correctOptionId)) correct = option;
        }
        if (correct == null) throw new IllegalArgumentException("missing correct option");
        if (!expectedKey.equals(correct.getMeaningKey()))
            throw new IllegalArgumentException("correct option meaning does not match vocabulary");
        this.vocabulary = vocabulary;
        this.options = Collections.unmodifiableList(copy);
        this.correctOptionId = correctOptionId;
    }
    public String getId() { return vocabulary.getStableId(); }
    public QuizVocabulary getVocabulary() { return vocabulary; }
    public List<QuizOption> getOptions() { return options; }
    public String getCorrectOptionId() { return correctOptionId; }
    public boolean hasOption(String id) {
        for (QuizOption o : options) if (o.getId().equals(id)) return true;
        return false;
    }
}
