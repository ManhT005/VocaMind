package com.vocamind.app.domain.quiz;

import java.util.*;

public final class QuizQuestion {
    private final QuizVocabulary vocabulary;
    private final List<QuizOption> options;
    private final String correctOptionId;
    public QuizQuestion(QuizVocabulary vocabulary, Collection<QuizOption> options, String correctOptionId) {
        if (vocabulary == null || options == null || options.size() != 4) throw new IllegalArgumentException("vocabulary/four options required");
        List<QuizOption> copy = new ArrayList<>(options);
        Set<String> ids = new HashSet<>(), keys = new HashSet<>();
        for (QuizOption option : copy) {
            if (option == null || !ids.add(option.getId()) || !keys.add(option.getMeaningKey()))
                throw new IllegalArgumentException("distinct option IDs and meanings required");
        }
        if (!ids.contains(correctOptionId)) throw new IllegalArgumentException("missing correct option");
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
