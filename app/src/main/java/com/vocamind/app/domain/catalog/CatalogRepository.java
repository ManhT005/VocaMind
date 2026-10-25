package com.vocamind.app.domain.catalog;

import java.util.List;
import java.util.Optional;

/** Blocking read contract: call from a worker executor, never the Android main thread.
 * Lists are immutable; unknown IDs return empty values. Storage failures are wrapped
 * in CatalogReadException and must not trigger deletion of user data.
 */
public interface CatalogRepository {
    List<CatalogPack> getPacks();
    Optional<CatalogPack> getPack(String packId);
    List<CatalogVocabulary> getWords(String packId);
    Optional<CatalogVocabulary> getVocabulary(String stableId);
    List<CatalogSource> getSources();
    Optional<CatalogSource> getSourceForVocabulary(String stableId);
}
