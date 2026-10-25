package com.vocamind.app.domain.progress;

/** A persisted, completed QuizAttempt; never an active/abandoned QuizSession result. */
public final class QuizHistoryItem {
    private final long attemptId, completedAtEpochMillis;
    private final int questionCount, correctCount, scorePercent;

    /** Validates stored score rather than silently repairing inconsistent persisted data. */
    public QuizHistoryItem(long attemptId, long completedAtEpochMillis,
                           int questionCount, int correctCount, int scorePercent) {
        if (attemptId <= 0 || completedAtEpochMillis < 0) {
            throw new IllegalArgumentException("persisted ID > 0 and completion time >= 0 required");
        }
        if (questionCount < 4 || questionCount > 10 || correctCount < 0 || correctCount > questionCount) {
            throw new IllegalArgumentException("invalid quiz counts");
        }
        if (scorePercent != (int) Math.round(correctCount * 100.0 / questionCount)) {
            throw new IllegalArgumentException("stored score does not match quiz counts");
        }
        this.attemptId = attemptId;
        this.completedAtEpochMillis = completedAtEpochMillis;
        this.questionCount = questionCount;
        this.correctCount = correctCount;
        this.scorePercent = scorePercent;
    }

    public long getAttemptId() { return attemptId; }
    public long getCompletedAtEpochMillis() { return completedAtEpochMillis; }
    public int getQuestionCount() { return questionCount; }
    public int getCorrectCount() { return correctCount; }
    public int getWrongCount() { return questionCount - correctCount; }
    public int getScorePercent() { return scorePercent; }
}
