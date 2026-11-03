package com.vocamind.app.data.catalog.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity(indices = @Index("sourceId"), foreignKeys = @ForeignKey(entity = ContentSource.class, parentColumns = "id", childColumns = "sourceId"))
public class VocabularyEntry {
    @PrimaryKey @NonNull public String id;
    @NonNull public String lemma;
    @NonNull public String normalizedLemma;
    @NonNull public String pos;
    @NonNull public String meaningVi;
    @NonNull public String exampleEn;
    @NonNull public String exampleVi;
    @Nullable public String ipa;
    @NonNull public String sourceId;
    @NonNull public String contentVersion;
}
