package com.vocamind.app.data.repository;

import androidx.annotation.WorkerThread;
import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.catalog.entity.VocabularyEntry;
import com.vocamind.app.data.user.UserDao;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.data.catalog.CatalogMapper;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.quiz.QuizRepository;
import com.vocamind.app.domain.quiz.QuizResult;
import com.vocamind.app.domain.quiz.QuizVocabulary;
import com.vocamind.app.domain.quiz.QuizSnapshot;
import com.vocamind.app.domain.quiz.QuizDistractorPolicy;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WorkerThread
public final class RoomQuizRepository implements QuizRepository {
    private final CatalogDao catalog;
    private final UserDao user;
    private final ActiveCatalogScope scope;
    private final Supplier<QuizDistractorPolicy> policy;

    public RoomQuizRepository(CatalogDao catalog, UserDao user, Supplier<QuizDistractorPolicy> policy) {
        if (policy == null) throw new IllegalArgumentException("policy loader required");
        this.catalog = catalog;
        this.user = user;
        this.scope = new ActiveCatalogScope(catalog, user);
        this.policy = policy;
    }

    @Override public RepoResult<QuizSnapshot> activeSnapshot() {
        return RepositoryGuard.read(() -> {
            QuizDistractorPolicy validated;
            try {
                validated = policy.get();
                if (validated == null) throw new IllegalArgumentException("policy required");
            } catch (IllegalArgumentException invalid) {
                throw new RepositoryGuard.InvalidDataException(invalid);
            }
            RepoResult<List<QuizVocabulary>> read = activeVocabulary();
            if (!read.isSuccess()) return RepoResult.failure(read.getError());
            return RepoResult.success(new QuizSnapshot(read.get(), validated));
        });
    }

    @Override public RepoResult<List<QuizVocabulary>> activeVocabulary() {
        return RepositoryGuard.read(() -> {
            List<String> ids = scope.eligibleIds();
            Map<String, VocabularyEntry> rows = new HashMap<>();
            for (VocabularyEntry v : catalog.vocabulariesByIds(ids)) rows.put(v.id, v);
            List<QuizVocabulary> out = new ArrayList<>();
            for (String id : ids) {
                VocabularyEntry v = rows.get(id);
                if (v == null) continue;
                CatalogVocabulary c = CatalogMapper.vocabulary(v);
                out.add(UserDataMapper.quizVocabulary(c));
            }
            return RepoResult.success(Collections.unmodifiableList(out));
        });
    }

    @Override public RepoResult<Long> saveAttempt(QuizResult result, long completedAt) {
        if (result == null || completedAt < 0) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.write(() -> RepoResult.success(user.insertQuiz(UserDataMapper.quizAttempt(result, completedAt))));
    }
}
