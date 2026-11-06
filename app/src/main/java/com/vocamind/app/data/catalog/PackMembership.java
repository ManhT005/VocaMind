package com.vocamind.app.data.catalog;

import java.util.Comparator;

/** Internal projection. Duplicate vocabulary IDs across packs are intentional here. */
public class PackMembership {
    public String packId;
    public String vocabularyId;
    public int packOrderNo;
    public int wordOrderNo;

    public static final Comparator<PackMembership> ORDER = Comparator
            .comparingInt((PackMembership row) -> row.packOrderNo)
            .thenComparing(row -> row.packId)
            .thenComparingInt(row -> row.wordOrderNo)
            .thenComparing(row -> row.vocabularyId);
}
