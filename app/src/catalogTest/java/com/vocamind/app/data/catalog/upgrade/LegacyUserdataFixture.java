package com.vocamind.app.data.catalog.upgrade;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.data.user.entity.QuizAttempt;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

final class LegacyUserdataFixture {
    static final String SCHEMA = "com.vocamind.app.data.user.UserDatabase/1.json";
    private static final String KEY = "data08-completion";
    private static final String[] TABLES = {
            "UserPack", "UserWordProgress", "ReviewEvent", "QuizAttempt", "UserPreference"
    };

    private LegacyUserdataFixture() { }

    static void create(Context context, String name, InputStream exportedSchema, String vocabularyId) throws Exception {
        assertNotNull(exportedSchema);
        assertFalse(context.getDatabasePath(name).exists());
        try (InputStream input = exportedSchema; SQLiteDatabase db = context.openOrCreateDatabase(name, 0, null)) {
            JSONObject schema = new JSONObject(new String(CatalogGenerationStore.readBytes(input),
                    StandardCharsets.UTF_8)).getJSONObject("database");
            JSONArray entities = schema.getJSONArray("entities");
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.getJSONObject(i);
                String table = entity.getString("tableName");
                db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
                JSONArray indexes = entity.optJSONArray("indices");
                for (int j = 0; indexes != null && j < indexes.length(); j++) {
                    db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
                }
            }
            JSONArray setup = schema.getJSONArray("setupQueries");
            for (int i = 0; i < setup.length(); i++) db.execSQL(setup.getString(i));
            db.execSQL("INSERT INTO UserPack(packId,activatedAt,isActive) VALUES('pack_toeic_basic',42,1),('pack_academic',24,0)");
            db.execSQL("INSERT INTO UserWordProgress(vocabularyId,box,dueAt,learnedAt,lastReviewedAt,"
                            + "consecutiveMistakes,isLeech,reviewCount,isSaved) VALUES(?,2,300,100,200,4,1,7,1)",
                    new Object[]{vocabularyId});
            db.execSQL("INSERT INTO ReviewEvent(id,vocabularyId,rating,reviewedAt,boxBefore,boxAfter,nextDueAt) "
                    + "VALUES(11,?,'HARD',200,2,2,300)", new Object[]{vocabularyId});
            db.execSQL("INSERT INTO QuizAttempt(id,completedAt,questionCount,correctCount,scorePercent) "
                    + "VALUES(12,201,4,3,75),(13,202,4,2,50)");
            db.execSQL("INSERT INTO UserPreference(id,dailyNewWords,dailyGoalMinutes,reminderEnabled,reminderHour) "
                    + "VALUES(1,9,25,1,8)");
            db.setVersion(1);
        }
    }

    static String snapshot(Context context, String name, int expectedVersion, boolean legacyOnly) throws Exception {
        var file = context.getDatabasePath(name);
        assertTrue("Expected the original userdata file", file.isFile());
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            assertEquals(expectedVersion, db.getVersion());
            JSONObject snapshot = new JSONObject();
            for (String table : TABLES) {
                String query = "SELECT * FROM " + table;
                if (legacyOnly && "QuizAttempt".equals(table)) {
                    query = "SELECT id,completedAt,questionCount,correctCount,scorePercent FROM QuizAttempt";
                    if (expectedVersion == 2) query += " WHERE completionKey IS NULL";
                }
                JSONArray rows = new JSONArray();
                try (Cursor cursor = db.rawQuery(query + " ORDER BY 1", null)) {
                    while (cursor.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < cursor.getColumnCount(); i++) {
                            row.put(cursor.getColumnName(i), cursor.isNull(i) ? JSONObject.NULL : cursor.getString(i));
                        }
                        rows.put(row);
                    }
                }
                assertTrue(table + " has no rows", rows.length() > 0);
                snapshot.put(table, rows);
            }
            return snapshot.toString();
        }
    }

    static void verifyCompletionKey(Context context, String name) {
        UserDatabase db = UserDatabase.open(context, name);
        try {
            QuizAttempt attempt = new QuizAttempt();
            attempt.completionKey = KEY;
            attempt.completedAt = 203;
            attempt.questionCount = 4;
            attempt.correctCount = 4;
            attempt.scorePercent = 100;
            long id = db.userDao().insertQuizOnce(attempt);
            assertTrue(id > 13);
            assertEquals(id, db.userDao().insertQuizOnce(attempt));
            attempt.completedAt++;
            assertThrows(com.vocamind.app.data.user.UserDao.CompletionConflict.class,
                    () -> db.userDao().insertQuizOnce(attempt));
            assertEquals(3, db.userDao().quizzes().size());
        } finally {
            db.close();
        }
    }
}
