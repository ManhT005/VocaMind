package com.vocamind.app.domain.quiz;

import java.util.*;

public final class QuizGenerationOutcome {
    public enum Status { READY, EMPTY, INVALID_INPUT }
    private final Status status;
    private final String reason;
    private final List<QuizQuestion> questions;
    private QuizGenerationOutcome(Status status, String reason, List<QuizQuestion> questions) {
        this.status = status; this.reason = reason; this.questions = questions;
    }
    public static QuizGenerationOutcome ready(Collection<QuizQuestion> questions) {
        QuizSession validated = new QuizSession(questions);
        return new QuizGenerationOutcome(Status.READY, "", validated.getQuestions());
    }
    public static QuizGenerationOutcome empty(QuizPool pool) {
        if (pool == null || pool.getAvailability() == QuizPool.Availability.READY) throw new IllegalArgumentException("insufficient pool required");
        return new QuizGenerationOutcome(Status.EMPTY, pool.getAvailability().name(), Collections.emptyList());
    }
    public static QuizGenerationOutcome invalidInput(String reason) {
        return new QuizGenerationOutcome(Status.INVALID_INPUT, Checks.text(reason, "reason"), Collections.emptyList());
    }
    public Status getStatus() { return status; }
    public String getReason() { return reason; }
    public List<QuizQuestion> getQuestions() { return questions; }
}
