package com.vocamind.app.domain.quiz;

/** meaningKey is supplied by the same conforming normalizer used for the pool. */
public final class QuizOption {
    private final String id, meaning, meaningKey;
    public QuizOption(String id, String meaning, String meaningKey) {
        this.id = Checks.text(id, "optionId");
        this.meaning = Checks.text(meaning, "meaning");
        this.meaningKey = Checks.text(meaningKey, "meaningKey");
    }
    public String getId() { return id; }
    public String getMeaning() { return meaning; }
    public String getMeaningKey() { return meaningKey; }
}
