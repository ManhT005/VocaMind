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

    public RoomQuizRepository(CatalogDao catalog, UserDao user) {
        this.catalog = catalog;
        this.user = user;
        this.scope = new ActiveCatalogScope(catalog, user);
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
