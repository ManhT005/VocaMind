package com.vocamind.app.domain.progress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable logical snapshot. Repository must supply counts/history from a consistent read. */
public final class ProgressSnapshot {
    private final ProgressSummary summary;
    private final List<QuizHistoryItem> quizHistory;

    public ProgressSnapshot(ProgressSummary summary, Collection<QuizHistoryItem> quizHistory) {
        if (summary == null || quizHistory == null) {
            throw new IllegalArgumentException("summary and history required");
        }
        List<QuizHistoryItem> copy = new ArrayList<>(quizHistory);
        Set<Long> ids = new HashSet<>();
        for (QuizHistoryItem item : copy) {
            if (item == null || !ids.add(item.getAttemptId())) {
                throw new IllegalArgumentException("history must contain nonnull, unique attempts");
            }
        }
        copy.sort(Comparator.comparingLong(QuizHistoryItem::getCompletedAtEpochMillis)
                .thenComparingLong(QuizHistoryItem::getAttemptId).reversed());
        this.summary = summary;
        this.quizHistory = Collections.unmodifiableList(copy);
    }

    public static ProgressSnapshot empty() {
        return new ProgressSnapshot(ProgressSummary.empty(), Collections.emptyList());
    }
    public ProgressSummary getSummary() { return summary; }
    /** All completed attempts, completion time DESC then ID DESC; no implicit limit/pagination. */
    public List<QuizHistoryItem> getQuizHistory() { return quizHistory; }
    public boolean isEmpty() { return summary.getTotalLearned() == 0 && quizHistory.isEmpty(); }
}
