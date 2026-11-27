package com.vocamind.app.data.user;

import com.vocamind.app.data.user.entity.QuizAttempt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Internal results of one UserDatabase read transaction. Map before leaving data layer. */
public final class ProgressRows {
    public final long totalWords;
    public final List<BoxProgressCount> boxes;
    public final List<QuizAttempt> quizHistory;
    final List<Long> learnedTimestamps;
    final List<Long> reviewTimestamps;
    final int dailyGoal;

    ProgressRows(long totalWords, List<BoxProgressCount> boxes, List<QuizAttempt> quizHistory) {
        this(totalWords, boxes, quizHistory, List.of(), List.of(), 0);
    }

    ProgressRows(long totalWords, List<BoxProgressCount> boxes, List<QuizAttempt> quizHistory,
                 List<Long> learnedTimestamps, List<Long> reviewTimestamps, int dailyGoal) {
        this.learnedTimestamps = Collections.unmodifiableList(new ArrayList<>(learnedTimestamps));
        this.reviewTimestamps = Collections.unmodifiableList(new ArrayList<>(reviewTimestamps));
        this.dailyGoal = dailyGoal;
        this.totalWords = totalWords;
        this.boxes = Collections.unmodifiableList(new ArrayList<>(boxes));
        this.quizHistory = Collections.unmodifiableList(new ArrayList<>(quizHistory));
    }
}
