package com.vocamind.app.core;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import androidx.savedstate.SavedStateRegistryOwner;
import com.vocamind.app.feature.search.SearchViewModel;

public final class SearchViewModelFactory extends AbstractSavedStateViewModelFactory {
    private final Context app;
    public SearchViewModelFactory(SavedStateRegistryOwner owner, Context context, Bundle args) {
        super(owner, args); app = context.getApplicationContext();
    }
    @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key,
            @NonNull Class<T> type, @NonNull SavedStateHandle saved) {
        Handler main = new Handler(Looper.getMainLooper());
        return type.cast(new SearchViewModel(saved, SearchComposition.get(app)::search,
                (delay, task) -> { main.postDelayed(task, delay); return () -> main.removeCallbacks(task); }, main::post));
    }
}
