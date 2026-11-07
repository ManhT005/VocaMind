package com.vocamind.app.core;

import android.content.Context;
import com.vocamind.app.data.repository.RoomPackRepository;
import com.vocamind.app.data.repository.RoomQuizRepository;
import com.vocamind.app.data.repository.RoomReviewRepository;
import com.vocamind.app.data.repository.RoomStudyRepository;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.learning.ReviewRepository;
import com.vocamind.app.domain.learning.StudyRepository;
import com.vocamind.app.domain.pack.PackRepository;
import com.vocamind.app.domain.quiz.QuizRepository;
import com.vocamind.app.data.catalog.QuizPolicyAsset;
import com.vocamind.app.data.catalog.CatalogMapper;
import com.vocamind.app.data.repository.UserDataMapper;
import java.util.ArrayList;
import java.time.Clock;
import java.time.ZoneId;
import com.vocamind.app.domain.learning.RecordReviewUseCase;
import com.vocamind.app.domain.progress.ProgressRepository;
import com.vocamind.app.domain.progress.GetProgressUseCase;
import com.vocamind.app.domain.quiz.QuizVocabulary;
import com.vocamind.app.domain.vocabulary.VocabularyRepository;
import com.vocamind.app.data.repository.RoomVocabularyRepository;

/**
 * Composition root for DATA-04. Hands out domain contracts only; DAOs and Room stay private.
 * Every repository call is blocking: run it on a worker executor.
 */
public final class RepositoryProvider implements AutoCloseable {
    private final UserDatabase userDatabase;
    private final PackRepository packs;
    private final StudyRepository study;
    private final ReviewRepository reviews;
    private final QuizRepository quizzes;
    private final ProgressRepository progress;
    private final VocabularyRepository vocabulary;

    public RepositoryProvider(Context context, CatalogProvider catalog) {
        userDatabase = UserDatabase.open(context);
        var catalogDao = catalog.catalogDao();
        var userDao = userDatabase.userDao();
        packs = new RoomPackRepository(catalogDao, userDao);
        study = new RoomStudyRepository(catalogDao, userDao);
        reviews = new RoomReviewRepository(catalogDao, userDatabase);
        progress = ProgressProvider.create(userDatabase);
        quizzes = new RoomQuizRepository(catalogDao, userDao, () -> {
            var vocabulary = new ArrayList<QuizVocabulary>();
            for (var row : catalogDao.allVocabulary())
                vocabulary.add(UserDataMapper.quizVocabulary(CatalogMapper.vocabulary(row)));
            return QuizPolicyAsset.load(context.getApplicationContext().getAssets(), vocabulary);
        });
        vocabulary = new RoomVocabularyRepository(catalog.repository(), userDatabase);
    }

    public PackRepository packs() { return packs; }
    public StudyRepository study() { return study; }
    public ReviewRepository reviews() { return reviews; }
    /** Same writer/DB instance as reviews(); call the resulting use case on a worker. */
    public RecordReviewUseCase recordReview(Clock clock, ZoneId zone) {
        return ReviewProvider.create(reviews, clock, zone);
    }
    public QuizRepository quizzes() { return quizzes; }
    public ProgressRepository progress() { return progress; }
    /** Reads the same application-owned userdata DB as review/Quiz writes; run on a worker. */
    public GetProgressUseCase progressUseCase() { return progress::readProgress; }
    public VocabularyRepository vocabulary() { return vocabulary; }

    /** The catalog database is owned and closed by CatalogProvider. */
    @Override public void close() { userDatabase.close(); }
}
