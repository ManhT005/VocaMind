package com.vocamind.app.core;

import android.content.Context;
import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.catalog.RoomCatalogRepository;
import com.vocamind.app.data.catalog.ContentCredits;
import com.vocamind.app.domain.catalog.CatalogRepository;

/** Application-scoped factory. Inject repository() into use cases, never the DAO. */
public final class CatalogProvider implements AutoCloseable {
    private final CatalogDatabase database;
    private final CatalogRepository repository;
    public CatalogProvider(Context context) {
        database = CatalogDatabase.open(context, "catalog.db");
        var assets = context.getApplicationContext().getAssets();
        repository = new RoomCatalogRepository(database.catalogDao(), () -> ContentCredits.load(assets));
    }
    public CatalogRepository repository() { return repository; }
    /** Package-private: only RepositoryProvider may coordinate catalog and userdata. */
    CatalogDao catalogDao() { return database.catalogDao(); }
    SearchProvider searchProvider() { return new SearchProvider(database); }
    @Override public void close() { database.close(); }
}
