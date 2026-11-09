package com.vocamind.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.domain.learning.PlanToday;
import com.vocamind.app.feature.today.TodayPlanViewModel;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Today counts from the Today Planner. Databases open lazily on the worker thread. */
public final class TodayPlanViewModelFactory implements ViewModelProvider.Factory {
    private final Context application;

    public TodayPlanViewModelFactory(Context context) { this.application = context.getApplicationContext(); }

    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.equals(TodayPlanViewModel.class)) {
            throw new IllegalArgumentException("Unsupported ViewModel: " + modelClass);
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        Lazy lazy = new Lazy(application);
        Runnable close = () -> {
            try { lazy.close(); }
            finally { worker.shutdown(); }
        };
        return modelClass.cast(new TodayPlanViewModel(() -> {
            RepositoryProvider repositories = lazy.get();
            return new PlanToday(repositories.packs(), repositories.study(), Clock.systemDefaultZone(),
                    ZoneId.systemDefault()).execute();
        }, worker, main::post, close));
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
