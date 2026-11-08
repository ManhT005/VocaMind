package com.vocamind.app.core;

import android.content.Context;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import androidx.savedstate.SavedStateRegistryOwner;
import com.vocamind.app.domain.quiz.GenerateQuiz;
import com.vocamind.app.feature.quiz.QuizViewModel;
import java.util.Random;
import java.util.concurrent.*;

public final class QuizViewModelFactory extends AbstractSavedStateViewModelFactory {
    private final Context context;
    public QuizViewModelFactory(Context context, SavedStateRegistryOwner owner) {
        super(owner, null); this.context=context.getApplicationContext();
    }
    @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key,
            @NonNull Class<T> type, @NonNull SavedStateHandle handle) {
        if (type != QuizViewModel.class) throw new IllegalArgumentException("Unsupported model");
        ExecutorService worker=Executors.newSingleThreadExecutor();
        Handler main=new Handler(Looper.getMainLooper());
        Lazy lazy=new Lazy(context);
        return type.cast(new QuizViewModel(handle,
                () -> new GenerateQuiz(lazy.get().quizzes()).execute(new Random()),
                (token,result,time) -> lazy.get().quizzes().saveAttemptOnce(token,result,time),
                worker, main::post, SystemClock::elapsedRealtime, System::currentTimeMillis,
                () -> { try { lazy.close(); } finally { worker.shutdown(); } }));
    }
    private static final class Lazy {
        private final Context context;
        private CatalogProvider catalog;
        private RepositoryProvider repositories;
        Lazy(Context context) { this.context=context; }
        RepositoryProvider get() {
            if (repositories == null) {
                if (catalog == null) catalog=new CatalogProvider(context);
                repositories=new RepositoryProvider(context,catalog);
            }
            return repositories;
        }
        void close() {
            try { if (repositories != null) repositories.close(); }
            finally { if (catalog != null) catalog.close(); }
        }
    }
}
