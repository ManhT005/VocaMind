package com.vocamind.app.feature.personal;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public final class PersonalPreferences {
    private PersonalPreferences() {}

    static SharedPreferences open(Context context) {
        return context.getSharedPreferences("personal", Context.MODE_PRIVATE);
    }

    public static int nightMode(Context context) {
        return open(context).getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
