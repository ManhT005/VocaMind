package com.vocamind.app.core;

import android.content.Context;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.vocamind.app.feature.search.CatalogDetailViewModel;

public final class CatalogDetailViewModelFactory implements ViewModelProvider.Factory {
    private final Context app;
    private final String id;
    public CatalogDetailViewModelFactory(Context context, String id) { app = context.getApplicationContext(); this.id = id; }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
        Handler main = new Handler(Looper.getMainLooper());
        return type.cast(new CatalogDetailViewModel(id, SearchComposition.get(app)::detail, main::post));
    }
}
