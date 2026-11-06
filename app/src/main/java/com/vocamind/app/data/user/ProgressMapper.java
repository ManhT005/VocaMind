package com.vocamind.app.data.user;

import com.vocamind.app.domain.progress.*;
import java.util.ArrayList;
import java.util.List;

/** Validate stored data; do not silently repair scores or ignore unsupported boxes. */
final class ProgressMapper {
    private ProgressMapper() { }
    static ProgressSnapshot toDomain(ProgressRows rows) {
        if (rows == null) throw new IllegalArgumentException("Missing progress rows");
        long[] counts = new long[ProgressSummary.BOX_COUNT];
        boolean[] seen = new boolean[ProgressSummary.BOX_COUNT];
        for (BoxProgressCount group : rows.boxes) {
            if (group == null || group.box < 0 || group.box >= counts.length
                    || group.wordCount <= 0 || seen[group.box])
                throw new IllegalArgumentException("Invalid box aggregation");
            seen[group.box] = true;
            counts[group.box] = group.wordCount;
        }
        ProgressSummary summary = new ProgressSummary(rows.totalWords, counts);
        List<QuizHistoryItem> history = new ArrayList<>();
        for (var attempt : rows.quizHistory) {
            if (attempt == null) throw new IllegalArgumentException("Missing quiz attempt");
            history.add(new QuizHistoryItem(attempt.id, attempt.completedAt,
                    attempt.questionCount, attempt.correctCount, attempt.scorePercent));
        }
        return new ProgressSnapshot(summary, history);
    }
}
