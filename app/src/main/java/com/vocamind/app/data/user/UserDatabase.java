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
        QuizAttempt.class, UserPreference.class}, version = 2, exportSchema = true)
public abstract class UserDatabase extends RoomDatabase {
    public static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE QuizAttempt ADD COLUMN completionKey TEXT");
            db.execSQL("CREATE UNIQUE INDEX index_QuizAttempt_completionKey ON QuizAttempt(completionKey)");
        }
    };
    public static final String NAME = "userdata.db";
    public abstract UserDao userDao();

    public static UserDatabase open(Context context) { return open(context, NAME); }

    public static UserDatabase open(Context context, String name) {
        UserDatabase db = Room.databaseBuilder(context.getApplicationContext(), UserDatabase.class, name)
                .addMigrations(MIGRATION_1_2)
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
