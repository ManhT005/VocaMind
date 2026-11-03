package com.vocamind.app.domain.quiz;

/** Created only by a completed session. No implicit clock or persistence. */
public final class QuizResult {
    private final int questionCount, correctCount;
    QuizResult(int questionCount, int correctCount) {
        if (questionCount < 4 || questionCount > 10 || correctCount < 0 || correctCount > questionCount)
            throw new IllegalArgumentException("invalid result counts");
        this.questionCount = questionCount;
        this.correctCount = correctCount;
    }
    public int getQuestionCount() { return questionCount; }
    public int getCorrectCount() { return correctCount; }
    public int getWrongCount() { return questionCount - correctCount; }
    public int getScorePercent() { return (int) Math.round(correctCount * 100.0 / questionCount); }
}
