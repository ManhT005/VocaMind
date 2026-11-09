package com.vocamind.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.domain.catalog.GetContentSources;
import com.vocamind.app.feature.attribution.AttributionViewModel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Composition root owns Room and executors; feature code sees only the use case. */
public final class AttributionViewModelFactory implements ViewModelProvider.Factory {
    private final Context application;

    public AttributionViewModelFactory(Context context) {
        application = context.getApplicationContext();
    }

    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.equals(AttributionViewModel.class)) {
            throw new IllegalArgumentException("Unsupported ViewModel: " + modelClass);
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        Lazy lazy = new Lazy(application);
        return modelClass.cast(new AttributionViewModel(
                () -> new GetContentSources(lazy.get().repository()).execute(), worker, main::post, () -> {
            try { lazy.close(); }
            finally { worker.shutdown(); }
        }));
    }

    /** Accessed only by the serial worker, including close after an in-flight query. */
    private static final class Lazy {
        private final Context context;
        private CatalogProvider provider;
        Lazy(Context context) { this.context = context; }
        CatalogProvider get() {
            if (provider == null) provider = new CatalogProvider(context);
            return provider;
        }
        void close() { if (provider != null) provider.close(); }
    }
}
