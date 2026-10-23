package com.vocamind.app.domain.quiz;

import java.util.Random;

/** QUIZ-02 implements sampling/shuffle. Null arguments are invalid input, never EMPTY.
 * Ready output must contain pool.targetCount unique IDs drawn from that pool, with
 * each correct option matching its vocabulary meaningKey. Random may be seeded. */
public interface QuizGenerator {
    QuizGenerationOutcome generate(QuizPool pool, Random random);
}
