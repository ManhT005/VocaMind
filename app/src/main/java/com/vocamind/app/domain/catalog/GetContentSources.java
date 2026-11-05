package com.vocamind.app.domain.catalog;

import java.util.List;
import java.util.Objects;

/** Blocking offline query. The caller must use a worker executor. */
public final class GetContentSources {
    private final CatalogRepository repository;

    public GetContentSources(CatalogRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<CatalogSource> execute() {
        return repository.getSources();
    }
}
