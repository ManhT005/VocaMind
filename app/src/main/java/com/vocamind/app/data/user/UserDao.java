package com.vocamind.app.data.user;

import androidx.room.*;
import com.vocamind.app.data.user.entity.*;
import java.util.List;

/** Repository-facing primitives only. Scheduling and session completion belong to domain. */
@Dao
public abstract class UserDao {
    @Query("SELECT * FROM UserPreference WHERE id=1")
    public abstract UserPreference preference();
    @Update protected abstract void updatePreference(UserPreference row);
    public void savePreference(UserPreference row) {
        require(row.id == 1 && row.dailyNewWords > 0 && row.dailyGoalMinutes > 0
                && row.reminderHour >= 0 && row.reminderHour <= 23, "preference");
        updatePreference(row);
    }
    @Upsert protected abstract void writePack(UserPack row);
    public void savePack(UserPack row) { id(row.packId); writePack(row); }
    @Query("SELECT * FROM UserPack WHERE packId=:id") public abstract UserPack pack(String id);
    @Query("SELECT * FROM UserPack ORDER BY packId") public abstract List<UserPack> packs();
    @Upsert protected abstract void writeProgress(UserWordProgress row);
    public void saveProgress(UserWordProgress row) {
        id(row.vocabularyId); box(row.box);
        require(row.consecutiveMistakes >= 0 && row.reviewCount >= 0, "counters");
        writeProgress(row);
    }
    @Query("SELECT * FROM UserWordProgress WHERE vocabularyId=:id")
    public abstract UserWordProgress progress(String id);
    /** Existing progress only; creation semantics for saved-only words require progress contract review. */
    @Query("UPDATE UserWordProgress SET isSaved=:saved WHERE vocabularyId=:id")
    public abstract int setSaved(String id, boolean saved);
    @Insert protected abstract long writeReview(ReviewEvent row);
    public long insertReview(ReviewEvent row) {
        id(row.vocabularyId); box(row.boxBefore); box(row.boxAfter);
        require("REMEMBERED".equals(row.rating) || "HARD".equals(row.rating)
                || "FORGOT".equals(row.rating), "rating");
        return writeReview(row);
    }
    @Query("SELECT * FROM ReviewEvent ORDER BY id") public abstract List<ReviewEvent> reviews();
    @Insert protected abstract long writeQuiz(QuizAttempt row);
    public long insertQuiz(QuizAttempt row) {
        require(row.questionCount >= 4 && row.questionCount <= 10 && row.correctCount >= 0
                && row.correctCount <= row.questionCount, "quiz counts");
        require(row.scorePercent == (int) Math.round(100.0 * row.correctCount / row.questionCount), "score");
        return writeQuiz(row);
    }
    @Query("SELECT * FROM QuizAttempt ORDER BY id") public abstract List<QuizAttempt> quizzes();
    @Transaction
    public void recordReview(ReviewEvent event, UserWordProgress progress) {
        require(event.vocabularyId.equals(progress.vocabularyId), "review stable ID");
        require(event.boxAfter == progress.box && event.nextDueAt == progress.dueAt, "review outcome");
        insertReview(event);
        saveProgress(progress);
    }
    private static void id(String value) { require(value != null && !value.trim().isEmpty(), "stable ID"); }
    private static void box(int value) { require(value >= 0 && value <= 5, "box"); }
    private static void require(boolean valid, String field) {
        if (!valid) throw new IllegalArgumentException("Invalid " + field);
    }
}
