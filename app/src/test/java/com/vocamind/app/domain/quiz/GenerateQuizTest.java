package com.vocamind.app.domain.quiz;

import com.vocamind.app.domain.common.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GenerateQuizTest {
    private static final class Repository implements QuizRepository {
        RepoResult<List<QuizVocabulary>> next = RepoResult.success(Collections.emptyList());
        int reads;
        QuizDistractorPolicy policy = QuizDistractorPolicy.unrestricted();
        @Override public RepoResult<QuizSnapshot> activeSnapshot() {
            reads++;
            if (!next.isSuccess()) return RepoResult.failure(next.getError());
            return RepoResult.success(new QuizSnapshot(next.get(), policy));
        }
        @Override public RepoResult<List<QuizVocabulary>> activeVocabulary() {
            throw new AssertionError("generation must read vocabulary and policy together");
        }
        @Override public RepoResult<Long> saveAttempt(QuizResult result, long time) {
            throw new AssertionError("generation must not save");
        }
    }

    @Test public void defaultGeneratorUsesPolicyFromEveryFreshSnapshot() {
        Repository repository = new Repository();
        List<QuizVocabulary> words = new ArrayList<>();
        for (int i = 1; i <= 4; i++) words.add(new QuizVocabulary(String.format(Locale.ROOT, "%016x", i),
                "word" + i, "noun", "meaning" + i, ""));
        repository.next = RepoResult.success(words);
        GenerateQuiz generate = new GenerateQuiz(repository);
        assertEquals(QuizGenerationOutcome.Status.READY, generate.execute(new Random(60)).get().getStatus());
        repository.policy = new QuizDistractorPolicy(List.of(List.of(words.get(0).getStableId(), words.get(1).getStableId())));
        QuizGenerationOutcome rejected = generate.execute(new Random(60)).get();
        assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, rejected.getStatus());
        assertTrue(rejected.getQuestions().isEmpty());
        repository.policy = QuizDistractorPolicy.unrestricted();
        assertEquals(QuizGenerationOutcome.Status.READY, generate.execute(new Random(60)).get().getStatus());
        assertEquals(3, repository.reads);
    }
    @Test public void preservesRepositoryErrorsAndInvalidSnapshot() {
        Repository repository = new Repository();
        GenerateQuiz generate = new GenerateQuiz(repository);
        assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, generate.execute(null).get().getStatus());
        assertEquals(0, repository.reads);
        for (RepoError error : RepoError.values()) {
            repository.next = RepoResult.failure(error);
            assertEquals(error, generate.execute(new Random(1)).getError());
        }
        String id = "0000000000000001";
        repository.next = RepoResult.success(Arrays.asList(
                new QuizVocabulary(id,"word","noun","one",""),
                new QuizVocabulary(id,"word","noun","two","")));
        assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, generate.execute(new Random(1)).get().getStatus());
        repository.next = RepoResult.success(Collections.emptyList());
        assertEquals(QuizGenerationOutcome.Status.EMPTY, generate.execute(new Random(1)).get().getStatus());
        assertEquals(7, repository.reads);
    }
}
