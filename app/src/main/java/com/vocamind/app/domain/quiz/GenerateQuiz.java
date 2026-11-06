package com.vocamind.app.domain.quiz;

import com.vocamind.app.domain.common.RepoResult;
import java.util.List;
import java.util.Random;

/** Blocking: execute on caller's worker executor. Reads a fresh snapshot per call. */
public final class GenerateQuiz {
    private final QuizRepository repository;
    private final QuizGenerator generator;
    private final MeaningNormalizer normalizer;

    public GenerateQuiz(QuizRepository repository) {
        this(repository, new DefaultQuizGenerator(), new UnicodeMeaningNormalizer());
    }

    public GenerateQuiz(QuizRepository repository, QuizGenerator generator, MeaningNormalizer normalizer) {
        if (repository == null || generator == null || normalizer == null)
            throw new IllegalArgumentException("dependencies required");
        this.repository = repository;
        this.generator = generator;
        this.normalizer = normalizer;
    }

    public RepoResult<QuizGenerationOutcome> execute(Random random) {
        if (random == null) return RepoResult.success(QuizGenerationOutcome.invalidInput("random required"));
        RepoResult<List<QuizVocabulary>> read = repository.activeVocabulary();
        if (!read.isSuccess()) return RepoResult.failure(read.getError());
        QuizPool pool;
        try {
            pool = new QuizPool(read.get(), normalizer);
        } catch (IllegalArgumentException invalid) {
            return RepoResult.success(QuizGenerationOutcome.invalidInput(invalid.getMessage()));
        }
        return RepoResult.success(generator.generate(pool, random));
    }
}
