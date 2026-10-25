package com.vocamind.app.core;

import android.content.Context;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.catalog.RoomCatalogRepository;
import com.vocamind.app.domain.catalog.CatalogRepository;

/** Application-scoped factory. Inject repository() into use cases, never the DAO. */
public final class CatalogProvider implements AutoCloseable {
    private final CatalogDatabase database;
    private final CatalogRepository repository;
    public CatalogProvider(Context context) {
        database = CatalogDatabase.open(context, "catalog.db");
        repository = new RoomCatalogRepository(database.catalogDao());
    }
    public CatalogRepository repository() { return repository; }
    @Override public void close() { database.close(); }
}
