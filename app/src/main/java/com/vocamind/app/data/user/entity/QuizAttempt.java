package com.vocamind.app.data.user.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Internal Room row; references to catalog are logical stable IDs. */
@Entity
public class QuizAttempt {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long completedAt;
    public int questionCount;
    public int correctCount;
    public int scorePercent;
}
