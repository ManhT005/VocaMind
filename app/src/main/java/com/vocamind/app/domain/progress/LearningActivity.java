package com.vocamind.app.domain.progress;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Calendar summaries of persisted learning activity in the device time zone. */
public final class LearningActivity {
    public enum Period { TODAY, WEEK, ALL }
    public static final class Totals {
        private long learned, reviews, quizzes, correct, questions;
        public long learned() { return learned; }
        public long reviews() { return reviews; }
        public long quizzes() { return quizzes; }
        public int accuracy() { return questions == 0 ? 0 : (int) Math.round(100.0 * correct / questions); }
        private void add(Totals day) {
            learned += day.learned;
            reviews += day.reviews;
            quizzes += day.quizzes;
            correct += day.correct;
            questions += day.questions;
        }
    }

    public record ReviewDay(LocalDate date, long count) {}

    private final LocalDate today;
    private final NavigableMap<LocalDate, Totals> days = new TreeMap<>();
    private final int goal;

    public LearningActivity(LocalDate today, ZoneId zone, int goal, List<Long> learned,
                            List<Long> reviews, List<QuizHistoryItem> quizzes) {
        this.today = today;
        this.goal = goal;
        for (long time : learned) day(time, zone).learned++;
        for (long time : reviews) day(time, zone).reviews++;
        for (QuizHistoryItem quiz : quizzes) {
            Totals totals = day(quiz.getCompletedAtEpochMillis(), zone);
            totals.quizzes++;
            totals.correct += quiz.getCorrectCount();
            totals.questions += quiz.getQuestionCount();
        }
    }

    public static LearningActivity empty() {
        ZoneId zone = ZoneId.systemDefault();
        return new LearningActivity(LocalDate.now(zone), zone, 0, List.of(), List.of(), List.of());
    }

    private Totals day(long epochMillis, ZoneId zone) {
        return days.computeIfAbsent(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(),
                ignored -> new Totals());
    }

    public Totals totals(Period period) {
        LocalDate start = period == Period.TODAY ? today : period == Period.WEEK
                ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : LocalDate.MIN;
        Totals result = new Totals();
        for (Totals day : days.subMap(start, true, today, true).values()) result.add(day);
        return result;
    }

    public int goal() { return goal; }

    public int streak() {
        LocalDate date = studied(today) ? today : today.minusDays(1);
        int count = 0;
        while (studied(date)) {
            count++;
            date = date.minusDays(1);
        }
        return count;
    }

    private boolean studied(LocalDate date) {
        Totals day = days.get(date);
        return day != null && (day.reviews > 0 || day.quizzes > 0);
    }

    public List<ReviewDay> reviewHistory() {
        List<ReviewDay> result = new ArrayList<>();
        days.headMap(today, true).descendingMap().forEach((date, day) -> {
            if (day.reviews > 0) result.add(new ReviewDay(date, day.reviews));
        });
        return result;
    }
}
