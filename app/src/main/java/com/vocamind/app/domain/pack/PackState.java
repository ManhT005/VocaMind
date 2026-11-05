package com.vocamind.app.domain.pack;

import com.vocamind.app.domain.catalog.CatalogPack;

/** Catalog pack merged with the user's activation and learned count. */
public final class PackState {
    private final CatalogPack pack;
    private final boolean active;
    private final Long activatedAtEpochMillis;
    private final int learnedCount;

    public PackState(CatalogPack pack, boolean active, Long activatedAtEpochMillis, int learnedCount) {
        if (pack == null) throw new IllegalArgumentException("pack");
        if (learnedCount < 0 || learnedCount > pack.wordCount) throw new IllegalArgumentException("learnedCount");
        this.pack = pack;
        this.active = active;
        this.activatedAtEpochMillis = activatedAtEpochMillis;
        this.learnedCount = learnedCount;
    }

    public CatalogPack getPack() { return pack; }
    public boolean isActive() { return active; }
    /** First activation time; null if the user never activated the pack. */
    public Long getActivatedAtEpochMillis() { return activatedAtEpochMillis; }
    public int getLearnedCount() { return learnedCount; }
}
