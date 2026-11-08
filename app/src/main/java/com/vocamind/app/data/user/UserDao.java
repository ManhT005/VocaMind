package com.vocamind.app.data.user;

import androidx.room.*;
import com.vocamind.app.data.user.entity.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.vocamind.app.data.local.DaoInputs;

/** Repository-facing primitives only. Scheduling and session completion belong to domain. */
@Dao
@WorkerThread
public abstract class UserDao {
    @Query("SELECT * FROM UserPreference WHERE id=1")
    public abstract UserPreference preference();
    @Update protected abstract void updatePreference(UserPreference row);
    public void savePreference(UserPreference row) {
        require(row != null, "preference row");
        require(row.id == 1 && row.dailyNewWords > 0 && row.dailyGoalMinutes > 0
                && row.reminderHour >= 0 && row.reminderHour <= 23, "preference");
        updatePreference(row);
    }
    @Upsert protected abstract void writePack(UserPack row);
    public void savePack(UserPack row) { require(row != null, "pack row"); id(row.packId); writePack(row); }
    @Query("SELECT * FROM UserPack WHERE packId=:id") @Nullable protected abstract UserPack readPack(String id);
    @Nullable public UserPack pack(String id) { id(id); return readPack(id); }
    @Query("SELECT * FROM UserPack ORDER BY packId") public abstract List<UserPack> packs();
    @Query("SELECT * FROM UserPack WHERE isActive=1 ORDER BY packId")
    public abstract List<UserPack> activePacks();

    /** First activation timestamp survives pause/reactivation. Missing pack pause is a no-op. */
    @Transaction public void activatePack(String packId, long activatedAt) {
        id(packId);
        UserPack row = readPack(packId);
        if (row == null) { row = new UserPack(); row.packId = packId; row.activatedAt = activatedAt; }
        row.isActive = true;
        writePack(row);
    }
    @Query("UPDATE UserPack SET isActive=0 WHERE packId=:packId")
    protected abstract int writePausePack(String packId);
    public int pausePack(String packId) { id(packId); return writePausePack(packId); }
    @Upsert protected abstract void writeProgress(UserWordProgress row);
    public void saveProgress(UserWordProgress row) {
        require(row != null, "progress row");
        id(row.vocabularyId); box(row.box);
        require(row.consecutiveMistakes >= 0 && row.reviewCount >= 0, "counters");
        writeProgress(row);
    }
    @Query("SELECT * FROM UserWordProgress WHERE vocabularyId=:id")
    @Nullable protected abstract UserWordProgress readProgress(String id);
    @Nullable public UserWordProgress progress(String id) { id(id); return readProgress(id); }
    @Query("SELECT * FROM UserWordProgress ORDER BY vocabularyId")
    public abstract List<UserWordProgress> allProgress();
    @Query("SELECT * FROM UserWordProgress WHERE vocabularyId IN (:ids) ORDER BY vocabularyId")
    protected abstract List<UserWordProgress> readProgressByIds(List<String> ids);
    @Transaction public List<UserWordProgress> progressByIds(List<String> ids) {
        List<UserWordProgress> rows = new ArrayList<>();
        for (List<String> batch : DaoInputs.batches(ids)) rows.addAll(readProgressByIds(batch));
        rows.sort(Comparator.comparing(row -> row.vocabularyId));
        return rows;
    }
    @Query("SELECT vocabularyId FROM UserWordProgress WHERE vocabularyId IN (:ids) ORDER BY vocabularyId")
    protected abstract List<String> readExistingIds(List<String> ids);
    @Transaction public List<String> existingProgressIds(List<String> ids) {
        List<String> rows = new ArrayList<>();
        for (List<String> batch : DaoInputs.batches(ids)) rows.addAll(readExistingIds(batch));
        rows.sort(String::compareTo);
        return rows;
    }
    /** Caller supplies catalog's ordered active candidate IDs and an already computed quota. */
    @Transaction public List<String> newVocabularyIds(List<String> orderedEligibleIds, int limit) {
        DaoInputs.limit(limit);
        Set<String> existing = new HashSet<>(existingProgressIds(orderedEligibleIds));
        LinkedHashSet<String> fresh = new LinkedHashSet<>();
        for (String id : orderedEligibleIds) {
            if (!existing.contains(id)) fresh.add(id);
            if (fresh.size() == limit) break;
        }
        return new ArrayList<>(fresh);
    }
    @Query("SELECT * FROM UserWordProgress WHERE vocabularyId IN (:ids) AND dueAt<=:now "
            + "ORDER BY dueAt, vocabularyId")
    protected abstract List<UserWordProgress> readDue(List<String> ids, long now);
    @Transaction public List<UserWordProgress> dueProgress(List<String> eligibleIds, long now, int limit) {
        DaoInputs.limit(limit);
        List<UserWordProgress> rows = new ArrayList<>();
        for (List<String> batch : DaoInputs.batches(eligibleIds)) rows.addAll(readDue(batch, now));
        rows.sort(Comparator.comparingLong((UserWordProgress row) -> row.dueAt)
                .thenComparing(row -> row.vocabularyId));
        return new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
    }
    @Query("SELECT * FROM UserWordProgress WHERE vocabularyId IN (:ids) AND box=:box "
            + "ORDER BY dueAt, vocabularyId")
    protected abstract List<UserWordProgress> readBox(List<String> ids, int box);
    @Transaction public List<UserWordProgress> progressByBox(List<String> eligibleIds, int box) {
        box(box);
        List<UserWordProgress> rows = new ArrayList<>();
        for (List<String> batch : DaoInputs.batches(eligibleIds)) rows.addAll(readBox(batch, box));
        rows.sort(Comparator.comparingLong((UserWordProgress row) -> row.dueAt)
                .thenComparing(row -> row.vocabularyId));
        return rows;
    }
    @Query("SELECT COUNT(*) FROM UserWordProgress WHERE learnedAt>=:start AND learnedAt<:end")
    protected abstract int readLearnedCount(long start, long end);
    public int learnedCount(long start, long end) { DaoInputs.range(start, end); return readLearnedCount(start, end); }
    @Transaction public UserPlanningSnapshot planningSnapshot(long dayStart, long nextDayStart) {
        DaoInputs.range(dayStart, nextDayStart);
        return new UserPlanningSnapshot(activePacks(), allProgress(), preference(), readLearnedCount(dayStart, nextDayStart));
    }
    /** Existing progress only: never overwrites learning fields. */
    @Query("UPDATE UserWordProgress SET isSaved=:saved WHERE vocabularyId=:id")
    protected abstract int writeSaved(String id, boolean saved);
    public int setSaved(String id, boolean saved) { id(id); return writeSaved(id, saved); }
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract long insertSavedProgress(UserWordProgress row);
    /** VOCAB-02 saved-only contract: due now, no fabricated learning/review timestamps. */
    public long insertSavedProgressIfAbsent(String vocabularyId, long savedAt) {
        id(vocabularyId);
        require(savedAt >= 0, "savedAt");
        UserWordProgress row = new UserWordProgress();
        row.vocabularyId = vocabularyId;
        row.isSaved = true;
        row.box = 0;
        row.dueAt = savedAt;
        row.learnedAt = null;
        row.lastReviewedAt = null;
        row.reviewCount = 0;
        row.consecutiveMistakes = 0;
        row.isLeech = false;
        return insertSavedProgress(row);
    }
    @Insert protected abstract long writeReview(ReviewEvent row);
    public long insertReview(ReviewEvent row) {
        require(row != null, "review row");
        id(row.vocabularyId); box(row.boxBefore); box(row.boxAfter);
        require("REMEMBERED".equals(row.rating) || "HARD".equals(row.rating)
                || "FORGOT".equals(row.rating), "rating");
        return writeReview(row);
    }
    @Query("SELECT * FROM ReviewEvent ORDER BY id") public abstract List<ReviewEvent> reviews();
    @Query("SELECT * FROM ReviewEvent WHERE vocabularyId=:id AND reviewedAt>=:start AND reviewedAt<:end "
            + "ORDER BY reviewedAt DESC, id DESC LIMIT :limit")
    protected abstract List<ReviewEvent> readReviewHistory(String id, long start, long end, int limit);
    public List<ReviewEvent> reviewHistory(String id, long start, long end, int limit) {
        id(id); DaoInputs.range(start, end); DaoInputs.limit(limit);
        return readReviewHistory(id, start, end, limit);
    }
    @Insert protected abstract long writeQuiz(QuizAttempt row);
    @Query("SELECT * FROM QuizAttempt WHERE completionKey=:key")
    protected abstract QuizAttempt quizByKey(String key);
    public static final class CompletionConflict extends RuntimeException { }
    @Transaction public long insertQuizOnce(QuizAttempt row) {
        require(row != null, "quiz row");
        id(row.completionKey);
        QuizAttempt old = quizByKey(row.completionKey);
        if (old == null) return insertQuiz(row);
        if (old.completedAt != row.completedAt || old.questionCount != row.questionCount
                || old.correctCount != row.correctCount || old.scorePercent != row.scorePercent)
            throw new CompletionConflict();
        return old.id;
    }
    public long insertQuiz(QuizAttempt row) {
        require(row != null, "quiz row");
        require(row.questionCount >= 4 && row.questionCount <= 10 && row.correctCount >= 0
                && row.correctCount <= row.questionCount, "quiz counts");
        require(row.scorePercent == (int) Math.round(100.0 * row.correctCount / row.questionCount), "score");
        return writeQuiz(row);
    }
    @Query("SELECT * FROM QuizAttempt ORDER BY id") public abstract List<QuizAttempt> quizzes();
    @Query("SELECT * FROM QuizAttempt WHERE completedAt>=:start AND completedAt<:end "
            + "ORDER BY completedAt DESC, id DESC LIMIT :limit")
    protected abstract List<QuizAttempt> readQuizHistory(long start, long end, int limit);
    public List<QuizAttempt> quizHistory(long start, long end, int limit) {
        DaoInputs.range(start, end); DaoInputs.limit(limit);
        return readQuizHistory(start, end, limit);
    }
    @Transaction
    public void recordReview(ReviewEvent event, UserWordProgress progress) {
        require(event != null && progress != null, "review rows");
        id(event.vocabularyId); id(progress.vocabularyId);
        require(event.vocabularyId.equals(progress.vocabularyId), "review stable ID");
        require(event.boxAfter == progress.box && event.nextDueAt == progress.dueAt, "review outcome");
        insertReview(event);
        saveProgress(progress);
    }
    // DATA-07: all progress, independent of active packs, isSaved and learnedAt.
    @Query("SELECT COUNT(DISTINCT vocabularyId) FROM UserWordProgress")
    public abstract long distinctProgressCount();
    @Query("SELECT box, COUNT(DISTINCT vocabularyId) AS wordCount FROM UserWordProgress GROUP BY box ORDER BY box")
    public abstract List<BoxProgressCount> progressCountsByBox();
    @Query("SELECT * FROM QuizAttempt ORDER BY completedAt DESC, id DESC")
    public abstract List<QuizAttempt> completedQuizHistory();
    /** No implicit history LIMIT/date window; counts/history must share one DB snapshot. */
    @Transaction
    public ProgressRows progressSnapshot() {
        return new ProgressRows(distinctProgressCount(), progressCountsByBox(), completedQuizHistory());
    }
    /** Compare and write under the same database transaction; protects first-insert races too. */
    @Transaction
    public void recordReviewIfUnchanged(UserWordProgress expected, ReviewEvent event,
                                        UserWordProgress updated) {
        require(event != null && updated != null, "review rows");
        id(updated.vocabularyId);
        require(expected == null || updated.vocabularyId.equals(expected.vocabularyId), "expected stable ID");
        require(updated.vocabularyId.equals(event.vocabularyId), "review stable ID");
        require(event.boxBefore == (expected == null ? 0 : expected.box), "review starting box");
        UserWordProgress current = progress(updated.vocabularyId);
        if (!sameProgress(expected, current)) throw new ReviewStateChangedException();
        recordReview(event, updated);
    }
    private static boolean sameProgress(UserWordProgress a, UserWordProgress b) {
        if (a == null || b == null) return a == b;
        return java.util.Objects.equals(a.vocabularyId, b.vocabularyId)
                && a.box == b.box && a.dueAt == b.dueAt
                && java.util.Objects.equals(a.learnedAt, b.learnedAt)
                && java.util.Objects.equals(a.lastReviewedAt, b.lastReviewedAt)
                && a.consecutiveMistakes == b.consecutiveMistakes && a.isLeech == b.isLeech
                && a.reviewCount == b.reviewCount && a.isSaved == b.isSaved;
    }
    private static void id(String value) { require(value != null && !value.trim().isEmpty(), "stable ID"); }
    private static void box(int value) { require(value >= 0 && value <= 5, "box"); }
    private static void require(boolean valid, String field) {
        if (!valid) throw new IllegalArgumentException("Invalid " + field);
    }
}
