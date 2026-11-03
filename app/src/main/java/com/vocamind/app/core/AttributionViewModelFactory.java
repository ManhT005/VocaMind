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
        // Room.build configures the database; asset copying/opening happens on first query.
        CatalogProvider provider = new CatalogProvider(application);
        GetContentSources query = new GetContentSources(provider.repository());
        return modelClass.cast(new AttributionViewModel(query, worker, main::post, () -> {
            try { provider.close(); }
            finally { worker.shutdown(); }
        }));
    }
}
