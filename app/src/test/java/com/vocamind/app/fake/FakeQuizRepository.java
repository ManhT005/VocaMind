package com.vocamind.app.fake;

import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.quiz.QuizRepository;
import com.vocamind.app.domain.quiz.QuizResult;
import com.vocamind.app.domain.quiz.QuizVocabulary;
import com.vocamind.app.domain.quiz.QuizSnapshot;
import com.vocamind.app.domain.quiz.QuizDistractorPolicy;
import java.util.ArrayList;
import java.util.List;

public final class FakeQuizRepository implements QuizRepository {
    private final java.util.Map<String, String> payloads = new java.util.HashMap<>();
    private final java.util.Map<String, Long> attemptIds = new java.util.HashMap<>();
    @Override public RepoResult<Long> saveAttemptOnce(String key, QuizResult result, long completedAt) {
        if (failure != null) return RepoResult.failure(failure);
        if (key == null || key.trim().isEmpty() || result == null || completedAt < 0)
            return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        String payload = result.getQuestionCount() + ":" + result.getCorrectCount() + ":" + completedAt;
        if (payloads.containsKey(key)) return payload.equals(payloads.get(key))
                ? RepoResult.success(attemptIds.get(key)) : RepoResult.failure(RepoError.CONFLICT);
        RepoResult<Long> inserted = saveAttempt(result, completedAt);
        payloads.put(key, payload); attemptIds.put(key, inserted.get());
        return inserted;
    }
    @Override public RepoResult<QuizSnapshot> activeSnapshot() {
        if (failure != null) return RepoResult.failure(failure);
        return RepoResult.success(new QuizSnapshot(vocabulary, QuizDistractorPolicy.unrestricted()));
    }
    public final List<QuizVocabulary> vocabulary = new ArrayList<>();
    public final List<QuizResult> saved = new ArrayList<>();
    public RepoError failure;

    @Override public RepoResult<List<QuizVocabulary>> activeVocabulary() {
        if (failure != null) return RepoResult.failure(failure);
        return RepoResult.success(new ArrayList<>(vocabulary));
    }

    @Override public RepoResult<Long> saveAttempt(QuizResult result, long completedAt) {
        if (failure != null) return RepoResult.failure(failure);
        saved.add(result);
        return RepoResult.success((long) saved.size());
    }
}
