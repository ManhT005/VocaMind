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
    ProgressRows(long totalWords, List<BoxProgressCount> boxes, List<QuizAttempt> quizHistory) {
        this.totalWords = totalWords;
        this.boxes = Collections.unmodifiableList(new ArrayList<>(boxes));
        this.quizHistory = Collections.unmodifiableList(new ArrayList<>(quizHistory));
    }
}
