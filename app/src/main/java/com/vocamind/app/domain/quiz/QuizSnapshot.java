package com.vocamind.app.domain.quiz;

import java.util.*;

/** Active vocabulary and the policy of its full catalog, read together per generation. */
public final class QuizSnapshot {
    private final List<QuizVocabulary> vocabulary;
    private final QuizDistractorPolicy policy;

    public QuizSnapshot(List<QuizVocabulary> vocabulary, QuizDistractorPolicy policy) {
        if (vocabulary == null || policy == null) throw new IllegalArgumentException("snapshot/policy required");
        for (QuizVocabulary word : vocabulary)
            if (word == null) throw new IllegalArgumentException("null vocabulary");
        this.vocabulary = Collections.unmodifiableList(new ArrayList<>(vocabulary));
        this.policy = policy;
    }
    public List<QuizVocabulary> getVocabulary() { return vocabulary; }
    public QuizDistractorPolicy getPolicy() { return policy; }
}
