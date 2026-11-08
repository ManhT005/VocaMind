package com.vocamind.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.domain.learning.PlanToday;
import com.vocamind.app.feature.flashcard.FlashcardViewModel;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Builds the flashcard session from the Today Planner. Databases open lazily on the worker. */
public final class FlashcardViewModelFactory implements ViewModelProvider.Factory {
    private final Context application;

    public FlashcardViewModelFactory(Context context) { this.application = context.getApplicationContext(); }

    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.equals(FlashcardViewModel.class)) {
            throw new IllegalArgumentException("Unsupported ViewModel: " + modelClass);
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        Lazy lazy = new Lazy(application);
        Runnable close = () -> {
            try { lazy.close(); }
            finally { worker.shutdown(); }
        };
        Clock clock = Clock.systemDefaultZone();
        ZoneId zone = ZoneId.systemDefault();
        return modelClass.cast(new FlashcardViewModel(() -> {
            RepositoryProvider repositories = lazy.get();
            return new PlanToday(repositories.packs(), repositories.study(), clock, zone).execute();
        }, worker, main::post, close,
                // Commits ReviewEvent + progress in one transaction (DATA-06) via the review use case.
                (item, rating) -> lazy.get().recordReview(clock, zone).execute(item.getVocabulary().stableId, rating),
                SystemClock::elapsedRealtime));
    }

    /** Touched only from the serial worker (get and close), so no locking is needed. */
    private static final class Lazy {
        private final Context context;
        private CatalogProvider catalog;
        private RepositoryProvider repositories;

        Lazy(Context context) { this.context = context; }

        RepositoryProvider get() {
            if (repositories == null) {
                catalog = new CatalogProvider(context);
                repositories = new RepositoryProvider(context, catalog);
            }
            return repositories;
        }

        void close() {
            try { if (repositories != null) repositories.close(); }
            finally { if (catalog != null) catalog.close(); }
        }
    }
}
