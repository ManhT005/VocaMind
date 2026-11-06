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
