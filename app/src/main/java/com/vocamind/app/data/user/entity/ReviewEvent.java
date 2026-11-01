package com.vocamind.app.data.user.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Internal Room row; references to catalog are logical stable IDs. */
@Entity
public class ReviewEvent {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String vocabularyId;
    @NonNull
    public String rating;
    public long reviewedAt;
    public int boxBefore;
    public int boxAfter;
    public long nextDueAt;
}
