package com.vocamind.app.domain.quiz;

/** Adapter must apply NFC, trim and full Unicode casefold, preserving accents.
 * Production implementation and Unicode conformance vectors belong to QUIZ-02. */
public interface MeaningNormalizer {
    String normalize(String meaning);
}
