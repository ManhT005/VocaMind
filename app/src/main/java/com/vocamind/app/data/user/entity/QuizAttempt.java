package com.vocamind.app.data.user.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Internal Room row; references to catalog are logical stable IDs. */
@Entity(indices = {@androidx.room.Index(value = "completionKey", unique = true)})
public class QuizAttempt {
    @androidx.annotation.Nullable
    public String completionKey;
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long completedAt;
    public int questionCount;
    public int correctCount;
    public int scorePercent;
}
