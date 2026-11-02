package com.vocamind.app.data.user.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Internal Room row; references to catalog are logical stable IDs. */
@Entity
public class UserPreference {
    @PrimaryKey
    public int id;
    public int dailyNewWords;
    public int dailyGoalMinutes;
    public boolean reminderEnabled;
    public int reminderHour;
}
