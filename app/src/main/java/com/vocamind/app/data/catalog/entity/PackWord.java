package com.vocamind.app.data.catalog.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity(primaryKeys = {"packId", "vocabularyId"}, indices = {@Index("vocabularyId"), @Index(value = {"packId", "orderNo"})}, foreignKeys = {@ForeignKey(entity = LearningPack.class, parentColumns = "id", childColumns = "packId"), @ForeignKey(entity = VocabularyEntry.class, parentColumns = "id", childColumns = "vocabularyId")})
public class PackWord {
    @NonNull public String packId;
    @NonNull public String vocabularyId;
     public int orderNo;
}
