package com.vocamind.app.data.user;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.vocamind.app.data.user.entity.*;

/** Open and use on a worker executor. Opening failures propagate; user data is never reset. */
@Database(entities = {UserPack.class, UserWordProgress.class, ReviewEvent.class,
        QuizAttempt.class, UserPreference.class}, version = 1, exportSchema = true)
public abstract class UserDatabase extends RoomDatabase {
    public static final String NAME = "userdata.db";
    public abstract UserDao userDao();

    public static UserDatabase open(Context context) { return open(context, NAME); }

    public static UserDatabase open(Context context, String name) {
        UserDatabase db = Room.databaseBuilder(context.getApplicationContext(), UserDatabase.class, name)
                .addCallback(new Callback() {
                    @Override public void onOpen(@NonNull SupportSQLiteDatabase connection) {
                        connection.execSQL("INSERT OR IGNORE INTO UserPreference "
                                + "(id,dailyNewWords,dailyGoalMinutes,reminderEnabled,reminderHour) "
                                + "VALUES (1,5,10,0,20)");
                    }
                }).build();
        try {
            // Force validation and default initialization before publishing this instance.
            db.getOpenHelper().getWritableDatabase();
            return db;
        } catch (RuntimeException failure) {
            db.close();
            throw failure;
        }
    }
}
