package com.vocamind.app.domain.quiz;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizDistractorPolicyTest {
    private String id(int value) { return String.format(Locale.ROOT, "%016x", value); }
    private QuizVocabulary word(int value, String meaning) {
        return new QuizVocabulary(id(value), "word" + value, "noun", meaning, "");
    }

    @Test public void policyCopiesInputAndAppliesBothDirections() {
        List<String> pair = new ArrayList<>(List.of(id(1), id(2)));
        List<List<String>> pairs = new ArrayList<>(List.of(pair));
        QuizDistractorPolicy policy = new QuizDistractorPolicy(pairs);
        pair.clear(); pairs.clear();
        assertFalse(policy.allows(id(1), id(2)));
        assertFalse(policy.allows(id(2), id(1)));
        assertFalse(policy.allows(id(1), id(1)));
        assertTrue(policy.allows(id(1), id(3)));
        assertThrows(IllegalArgumentException.class, () -> new QuizDistractorPolicy(List.of(List.of(id(1), id(1)))));
        assertThrows(IllegalArgumentException.class, () -> new QuizDistractorPolicy(List.of(List.of("bad", id(1)))));
    }

    @Test public void mixedBucketKeepsOnlySafeRepresentativesAndPoolRejectsForbiddenOptions() {
        List<QuizVocabulary> words = List.of(word(1, "a"), word(2, "b"), word(3, "b"),
                word(4, "d"), word(5, "e"), word(6, "f"));
        QuizDistractorPolicy policy = new QuizDistractorPolicy(List.of(List.of(id(1), id(2))));
        QuizPool pool = new QuizPool(words, new UnicodeMeaningNormalizer(), policy);
        int safeRepresentatives = 0;
        for (int seed = 0; seed < 100; seed++) {
            QuizGenerationOutcome result = new DefaultQuizGenerator().generate(pool, new Random(seed));
            assertEquals(QuizGenerationOutcome.Status.READY, result.getStatus());
            assertEquals(6, result.getQuestions().size());
            for (QuizQuestion q : result.getQuestions()) for (QuizOption option : q.getOptions()) {
                if (!option.getId().equals(q.getId())) assertTrue(policy.allows(q.getId(), option.getId()));
                if (q.getId().equals(id(1)) && option.getId().equals(id(3))) safeRepresentatives++;
            }
        }
        assertTrue(safeRepresentatives > 0);
        List<QuizOption> invalid = List.of(new QuizOption(id(1), "a", "a"), new QuizOption(id(2), "b", "b"),
                new QuizOption(id(4), "d", "d"), new QuizOption(id(5), "e", "e"));
        assertThrows(IllegalArgumentException.class, () -> pool.createQuestion(id(1), invalid, id(1)));
    }

    @Test public void snapshotCopiesVocabularyAndRequiresExplicitPolicy() {
        List<QuizVocabulary> words = new ArrayList<>(List.of(word(1, "a")));
        QuizSnapshot snapshot = new QuizSnapshot(words, QuizDistractorPolicy.unrestricted());
        words.clear();
        assertEquals(1, snapshot.getVocabulary().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getVocabulary().clear());
        assertThrows(IllegalArgumentException.class, () -> new QuizSnapshot(List.of(), null));
    }
}
