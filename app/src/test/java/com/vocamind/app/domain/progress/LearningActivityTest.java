package com.vocamind.app.domain.progress;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;

public class LearningActivityTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

    @Test public void weekStartsOnLocalMondayAndQuizAccuracyIsWeightedByQuestions() {
        long today = midnight(MONDAY);
        long yesterday = today - 1;
        LearningActivity activity = new LearningActivity(MONDAY, ZONE, 5,
                List.of(today, yesterday), List.of(today, today, yesterday),
                List.of(new QuizHistoryItem(1, today, 4, 4, 100),
                        new QuizHistoryItem(2, yesterday, 10, 2, 20)));

        assertEquals(1, activity.totals(LearningActivity.Period.WEEK).learned());
        assertEquals(2, activity.totals(LearningActivity.Period.TODAY).reviews());
        assertEquals(43, activity.totals(LearningActivity.Period.ALL).accuracy());
        assertEquals(2, activity.streak());
        assertEquals(MONDAY, activity.reviewHistory().get(0).date());
        assertEquals(2, activity.reviewHistory().get(0).count());
    }

    @Test public void currentStreakKeepsYesterdayButStopsAcrossAGap() {
        long yesterday = midnight(MONDAY.minusDays(1));
        LearningActivity activity = new LearningActivity(MONDAY, ZONE, 5, List.of(),
                List.of(midnight(MONDAY.minusDays(3))),
                List.of(new QuizHistoryItem(1, yesterday, 4, 0, 0)));
        assertEquals(1, activity.streak());
        assertEquals(0, activity.totals(LearningActivity.Period.TODAY).quizzes());
    }

    private static long midnight(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }
}
