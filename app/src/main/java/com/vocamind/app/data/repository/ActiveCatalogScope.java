package com.vocamind.app.data.repository;

import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.user.UserDao;
import com.vocamind.app.data.user.entity.UserPack;
import java.util.ArrayList;
import java.util.List;

/**
 * The only place that joins the two databases: userdata says which packs are active,
 * the catalog expands them to stable IDs. Logical IDs only, no cross-DB FK or JOIN.
 */
final class ActiveCatalogScope {
    private final CatalogDao catalog;
    private final UserDao user;

    ActiveCatalogScope(CatalogDao catalog, UserDao user) {
        this.catalog = catalog;
        this.user = user;
    }

    /** Distinct stable IDs of active packs in pack order. Orphan/paused packs contribute nothing. */
    List<String> eligibleIds() {
        List<String> packIds = new ArrayList<>();
        for (UserPack p : user.activePacks()) packIds.add(p.packId);
        if (packIds.isEmpty()) return new ArrayList<>();
        return catalog.uniqueVocabularyIdsForPacks(packIds);
    }
}
