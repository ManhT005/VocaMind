package com.vocamind.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.domain.pack.GetPackWords;
import com.vocamind.app.domain.pack.GetPacks;
import com.vocamind.app.domain.pack.SetPackActive;
import com.vocamind.app.feature.packs.PackWordsViewModel;
import com.vocamind.app.feature.packs.PacksViewModel;
import com.vocamind.app.feature.vocabulary.VocabularyDetailViewModel;
import com.vocamind.app.domain.vocabulary.GetVocabularyDetail;
import com.vocamind.app.domain.vocabulary.ToggleSaved;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Composition root: each model owns its databases and serial worker. Packs state is activity scoped. */
public final class PacksViewModelFactory implements ViewModelProvider.Factory {
    private final Context application;
    private final String packId;
    private final String vocabularyId;

    public PacksViewModelFactory(Context context, String packId, String vocabularyId) {
        this.application = context.getApplicationContext();
        this.packId = packId;
        this.vocabularyId = vocabularyId;
    }

    /** packId is needed for pack words. Null packId on detail is the explicit catalog route. */
    public PacksViewModelFactory(Context context, String packId) {
        this(context, packId, null);
    }

    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        boolean list = modelClass.equals(PacksViewModel.class);
        boolean detail = modelClass.equals(VocabularyDetailViewModel.class);
        if (!list && !detail && !modelClass.equals(PackWordsViewModel.class)) {
            throw new IllegalArgumentException("Unsupported ViewModel: " + modelClass);
        }
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        // Databases are opened lazily on the worker thread, never on the main thread.
        Lazy lazy = new Lazy(application);
        Runnable close = () -> {
            try { lazy.close(); }
            finally { worker.shutdown(); }
        };
        if (detail) {
            return modelClass.cast(new VocabularyDetailViewModel(
                    () -> packId == null
                            ? new GetVocabularyDetail(lazy.get().vocabulary()).executeCatalog(vocabularyId)
                            : new GetVocabularyDetail(lazy.get().vocabulary()).execute(packId, vocabularyId),
                    target -> packId == null
                            ? new ToggleSaved(lazy.get().vocabulary()).executeCatalog(vocabularyId, target)
                            : new ToggleSaved(lazy.get().vocabulary()).execute(packId, vocabularyId, target),
                    worker, main::post, close));
        }
        if (list) {
            return modelClass.cast(new PacksViewModel(() -> new GetPacks(lazy.get().packs()).execute(),
                    (id, active) -> new SetPackActive(lazy.get().packs(), System::currentTimeMillis).execute(id, active),
                    worker, main::post, close));
        }
        return modelClass.cast(new PackWordsViewModel(
                () -> new GetPackWords(lazy.get().packs()).execute(packId), worker, main::post, close));
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
