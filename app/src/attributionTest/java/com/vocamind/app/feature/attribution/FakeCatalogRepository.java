package com.vocamind.app.feature.attribution;

import com.vocamind.app.domain.catalog.*;
import java.util.List;
import java.util.Optional;

final class FakeCatalogRepository implements CatalogRepository {
    List<CatalogSource> sources = List.of();
    RuntimeException failure;
    int calls;
    @Override public List<CatalogSource> getSources() {
        calls++;
        if (failure != null) throw failure;
        return sources;
    }
    @Override public List<CatalogPack> getPacks() { throw new AssertionError(); }
    @Override public Optional<CatalogPack> getPack(String id) { throw new AssertionError(); }
    @Override public List<CatalogVocabulary> getWords(String id) { throw new AssertionError(); }
    @Override public Optional<CatalogVocabulary> getVocabulary(String id) { throw new AssertionError(); }
    @Override public Optional<CatalogSource> getSourceForVocabulary(String id) { throw new AssertionError(); }
}
