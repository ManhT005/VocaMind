package com.vocamind.app.data.user.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Internal Room row; references to catalog are logical stable IDs. */
@Entity
public class UserWordProgress {
    @PrimaryKey
    @NonNull
    public String vocabularyId;
    public int box;
    public long dueAt;
    public Long learnedAt;
    public Long lastReviewedAt;
    public int consecutiveMistakes;
    public boolean isLeech;
    public int reviewCount;
    public boolean isSaved;
}
