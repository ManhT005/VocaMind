package com.vocamind.app.core;

import android.content.Context;
import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.catalog.RoomCatalogRepository;
import com.vocamind.app.data.catalog.upgrade.CatalogGeneration;
import com.vocamind.app.data.catalog.upgrade.CatalogStartupCoordinator;
import com.vocamind.app.domain.catalog.CatalogRepository;
import com.vocamind.app.domain.quiz.QuizDistractorPolicy;
import com.vocamind.app.domain.quiz.QuizVocabulary;
import java.util.List;
import androidx.annotation.WorkerThread;

/** Application-scoped factory. Inject repository() into use cases, never the DAO. */
public final class CatalogProvider implements AutoCloseable {
    private final CatalogDatabase database;
    private final CatalogGeneration generation;
    private final CatalogRepository repository;
    @WorkerThread public CatalogProvider(Context context) {
        generation = CatalogStartupCoordinator.bootstrap(context);
        database = generation.openDatabase(context);
        repository = new RoomCatalogRepository(database.catalogDao(), generation::credits);
    }
    QuizDistractorPolicy quizPolicy(List<QuizVocabulary> words) {
        return generation.quizPolicy(words);
    }
    public CatalogRepository repository() { return repository; }
    /** Package-private: only RepositoryProvider may coordinate catalog and userdata. */
    CatalogDao catalogDao() { return database.catalogDao(); }
    @Override public void close() { database.close(); }
}
