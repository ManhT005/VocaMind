package com.vocamind.app.data.user;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.data.user.entity.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class UserDatabaseTest {
    @Rule public org.junit.rules.TestRule workerThread = (base, description) ->
            new org.junit.runners.model.Statement() {
                @Override public void evaluate() throws Throwable {
                    java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                            new java.util.concurrent.atomic.AtomicReference<>();
                    Thread thread = new Thread(() -> {
                        try { base.evaluate(); } catch (Throwable t) { failure.set(t); }
                    }, "data02-test-worker");
                    thread.start(); thread.join(60000);
                    if (thread.isAlive()) { thread.interrupt(); fail("Database test timed out"); }
                    if (failure.get() != null) throw failure.get();
                }
            };
    private Context context;
    private UserDatabase db;
    private String name;
    @Before public void open() {
        context = ApplicationProvider.getApplicationContext();
        name = "data02-" + java.util.UUID.randomUUID() + ".db";
        db = UserDatabase.open(context, name);
    }
    @After public void close() { if (db != null) db.close(); context.deleteDatabase(name); }
    private void reopen() { db.close(); db = UserDatabase.open(context, name); }
    private UserWordProgress progress() {
        UserWordProgress p = new UserWordProgress();
        p.vocabularyId = "stable-1"; p.box = 2; p.dueAt = 123456789L;
        p.learnedAt = 111L; p.lastReviewedAt = 222L; p.reviewCount = 3;
        p.consecutiveMistakes = 1; p.isLeech = true; p.isSaved = true;
        return p;
    }
    private ReviewEvent review() {
        ReviewEvent r = new ReviewEvent(); r.vocabularyId = "stable-1";
        r.rating = "HARD"; r.reviewedAt = 222L; r.boxBefore = 2;
        r.boxAfter = 2; r.nextDueAt = 123456789L; return r;
    }
    @Test public void defaultsAndInitializationPreserveEdits() {
        UserPreference p = db.userDao().preference();
        assertEquals(1, p.id); assertEquals(5, p.dailyNewWords);
        assertEquals(10, p.dailyGoalMinutes); assertFalse(p.reminderEnabled);
        assertEquals(20, p.reminderHour); assertTrue(db.userDao().packs().isEmpty());
        p.dailyNewWords = 9; p.dailyGoalMinutes = 25; p.reminderEnabled = true;
        p.reminderHour = 8; db.userDao().savePreference(p);
        reopen(); reopen();
        p = db.userDao().preference(); assertEquals(9, p.dailyNewWords);
        assertEquals(25, p.dailyGoalMinutes); assertTrue(p.reminderEnabled);
        assertEquals(8, p.reminderHour);
    }
    @Test public void concurrentOpenKeepsOnePreference() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            java.util.List<java.util.concurrent.Callable<Integer>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) calls.add(() -> {
                UserDatabase other = UserDatabase.open(context, name);
                try {
                    return other.userDao().preference().dailyNewWords;
                } finally { other.close(); }
            });
            for (var result : pool.invokeAll(calls)) assertEquals(5, (int) result.get());
            try (var c = db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM UserPreference")) {
                assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0));
            }
        } finally { pool.shutdownNow(); }
    }
    @Test public void allFiveTablesSurviveFileReopen() {
        UserPack pack = new UserPack(); pack.packId = "pack-1";
        pack.activatedAt = 77L; pack.isActive = false; db.userDao().savePack(pack);
        db.userDao().recordReview(review(), progress());
        assertEquals(1, db.userDao().setSaved("stable-1", false));
        QuizAttempt q = new QuizAttempt(); q.completedAt = 999L;
        q.questionCount = 7; q.correctCount = 5; q.scorePercent = 71;
        long quizId = db.userDao().insertQuiz(q);
        long reviewId = db.userDao().reviews().get(0).id;
        reopen();
        assertEquals(77L, db.userDao().pack("pack-1").activatedAt);
        assertFalse(db.userDao().pack("pack-1").isActive);
        UserWordProgress p = db.userDao().progress("stable-1");
        assertEquals("stable-1", p.vocabularyId); assertEquals(2, p.box);
        assertEquals(123456789L, p.dueAt); assertEquals(Long.valueOf(111), p.learnedAt);
        assertEquals(Long.valueOf(222), p.lastReviewedAt); assertEquals(3, p.reviewCount);
        assertEquals(1, p.consecutiveMistakes); assertTrue(p.isLeech); assertFalse(p.isSaved);
        ReviewEvent r = db.userDao().reviews().get(0);
        assertEquals(reviewId, r.id); assertEquals("stable-1", r.vocabularyId);
        assertEquals("HARD", r.rating); assertEquals(222L, r.reviewedAt);
        assertEquals(2, r.boxBefore); assertEquals(2, r.boxAfter); assertEquals(p.dueAt, r.nextDueAt);
        q = db.userDao().quizzes().get(0); assertEquals(quizId, q.id);
        assertEquals(999L, q.completedAt); assertEquals(7, q.questionCount);
        assertEquals(5, q.correctCount); assertEquals(71, q.scorePercent);
        assertEquals(5, db.userDao().preference().dailyNewWords);
    }
    @Test public void failureAfterReviewInsertRollsBackBothWrites() {
        db.userDao().saveProgress(progress());
        UserWordProgress invalid = progress(); invalid.reviewCount = -1;
        assertThrows(IllegalArgumentException.class, () -> db.userDao().recordReview(review(), invalid));
        assertTrue(db.userDao().reviews().isEmpty());
        assertEquals(3, db.userDao().progress("stable-1").reviewCount);
        reopen(); assertTrue(db.userDao().reviews().isEmpty());
    }
    @Test public void invalidValuesAreRejectedBeforeWriting() {
        UserWordProgress p = progress(); p.box = 6;
        assertThrows(IllegalArgumentException.class, () -> db.userDao().saveProgress(p));
        ReviewEvent r = review(); r.rating = "UNKNOWN";
        assertThrows(IllegalArgumentException.class, () -> db.userDao().insertReview(r));
        QuizAttempt q = new QuizAttempt(); q.questionCount = 4; q.correctCount = 5;
        assertThrows(IllegalArgumentException.class, () -> db.userDao().insertQuiz(q));
        UserPreference pref = db.userDao().preference(); pref.id = 2;
        assertThrows(IllegalArgumentException.class, () -> db.userDao().savePreference(pref));
        assertNull(db.userDao().progress("stable-1"));
        assertTrue(db.userDao().reviews().isEmpty()); assertTrue(db.userDao().quizzes().isEmpty());
    }
    @Test public void invalidIdentityHashDoesNotEraseProgress() {
        db.userDao().saveProgress(progress());
        db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE room_master_table SET identity_hash='invalid' WHERE id=42");
        db.close();
        assertThrows(IllegalStateException.class, () -> UserDatabase.open(context, name));
        try (android.database.sqlite.SQLiteDatabase raw = android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
             android.database.Cursor c = raw.rawQuery("SELECT vocabularyId, reviewCount FROM UserWordProgress", null)) {
            assertTrue(c.moveToFirst()); assertEquals("stable-1", c.getString(0)); assertEquals(3, c.getInt(1));
        }
    }
    @Test public void testOnlySchemaEvolutionPreservesAllFiveTables() {
        UserPack pack = new UserPack(); pack.packId = "fixture-pack"; pack.activatedAt = 19;
        pack.isActive = true; db.userDao().savePack(pack);
        db.userDao().recordReview(review(), progress());
        QuizAttempt quiz = new QuizAttempt(); quiz.completedAt = 41;
        quiz.questionCount = 4; quiz.correctCount = 3; quiz.scorePercent = 75;
        db.userDao().insertQuiz(quiz);
        UserPreference pref = db.userDao().preference(); pref.dailyNewWords = 8;
        db.userDao().savePreference(pref);
        String before = snapshot();
        // Synthetic future-version fixture only: an auxiliary table is removed by downgrade.
        // This does not claim a production upgrade migration exists.
        db.getOpenHelper().getWritableDatabase().execSQL("CREATE TABLE FixtureOnly (id INTEGER PRIMARY KEY)");
        db.getOpenHelper().getWritableDatabase().setVersion(2); db.close();
        db = androidx.room.Room.databaseBuilder(context, UserDatabase.class, name)
                .addMigrations(new androidx.room.migration.Migration(2, 1) {
                    @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase connection) {
                        connection.execSQL("DROP TABLE FixtureOnly");
                    }
                }).build();
        assertEquals(before, snapshot()); // Opening runs Room's target-schema validation.
        reopen(); assertEquals(before, snapshot());
    }
    private String snapshot() {
        StringBuilder result = new StringBuilder();
        for (String table : new String[]{"UserPack", "UserWordProgress", "ReviewEvent", "QuizAttempt", "UserPreference"}) {
            result.append(table);
            try (var c = db.getOpenHelper().getReadableDatabase().query("SELECT * FROM " + table + " ORDER BY 1")) {
                while (c.moveToNext()) for (int i = 0; i < c.getColumnCount(); i++) {
                    result.append('|').append(c.getColumnName(i)).append('=');
                    result.append(c.isNull(i) ? "<null>" : c.getString(i));
                }
            }
        }
        return result.toString();
    }
    @Test public void missingMigrationRefusesOpenWithoutErasingData() {
        db.userDao().saveProgress(progress());
        db.getOpenHelper().getWritableDatabase().setVersion(2); db.close();
        assertThrows(IllegalStateException.class, () -> UserDatabase.open(context, name));
        android.database.sqlite.SQLiteDatabase raw = android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
        try (android.database.Cursor c = raw.rawQuery("SELECT reviewCount FROM UserWordProgress WHERE vocabularyId='stable-1'", null)) {
            assertTrue(c.moveToFirst()); assertEquals(3, c.getInt(0));
        }
        raw.setVersion(1); raw.close(); db = UserDatabase.open(context, name);
        assertEquals(3, db.userDao().progress("stable-1").reviewCount);
    }
}
