package com.vocamind.app.domain.quiz;

import com.vocamind.app.domain.common.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GenerateQuizTest {
    private static final class Repository implements QuizRepository {
        RepoResult<List<QuizVocabulary>> next = RepoResult.success(Collections.emptyList());
        int reads;
        @Override public RepoResult<List<QuizVocabulary>> activeVocabulary() { reads++; return next; }
        @Override public RepoResult<Long> saveAttempt(QuizResult result, long time) {
            throw new AssertionError("generation must not save");
        }
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
