package com.vocamind.app.domain.quiz;

import java.util.*;

/** Stateless generation from a snapshot. No storage, clock or implicit randomness. */
public final class DefaultQuizGenerator implements QuizGenerator {
    private final MeaningNormalizer promptNormalizer = new UnicodeMeaningNormalizer();

    @Override public QuizGenerationOutcome generate(QuizPool pool, Random random) {
        if (pool == null || random == null)
            return QuizGenerationOutcome.invalidInput("pool/random required");
        if (pool.getAvailability() != QuizPool.Availability.READY)
            return QuizGenerationOutcome.empty(pool);
        try {
            return generateReady(pool, random);
        } catch (IllegalArgumentException invalid) {
            return QuizGenerationOutcome.invalidInput(invalid.getMessage());
        }
    }

    private QuizGenerationOutcome generateReady(QuizPool pool, Random random) {
        List<QuizVocabulary> candidates = new ArrayList<>(pool.getVocabulary());
        candidates.sort(Comparator.comparing(QuizVocabulary::getStableId));
        Map<String, List<QuizVocabulary>> buckets = new TreeMap<>();
        Map<String, Set<String>> lemmaMeanings = new HashMap<>();
        for (QuizVocabulary v : candidates) {
            String key = pool.getMeaningKey(v.getStableId());
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(v);
            lemmaMeanings.computeIfAbsent(promptNormalizer.normalize(v.getLemma()),
                    ignored -> new HashSet<>()).add(key);
        }
        Collections.shuffle(candidates, random);
        List<QuizQuestion> questions = new ArrayList<>();
        for (QuizVocabulary target : candidates.subList(0, pool.getTargetQuestionCount())) {
            String correctKey = pool.getMeaningKey(target.getStableId());
            // Conservative policy: no alternative sense of the same lemma in options,
            // even when POS/context could distinguish it. Never silently shorten N.
            Set<String> excluded = lemmaMeanings.get(promptNormalizer.normalize(target.getLemma()));
            Map<String, List<QuizVocabulary>> eligible = new TreeMap<>();
            for (Map.Entry<String, List<QuizVocabulary>> bucket : buckets.entrySet()) {
                if (excluded.contains(bucket.getKey())) continue;
                List<QuizVocabulary> safe = new ArrayList<>();
                for (QuizVocabulary entry : bucket.getValue())
                    if (pool.getPolicy().allows(target.getStableId(), entry.getStableId())) safe.add(entry);
                if (!safe.isEmpty()) eligible.put(bucket.getKey(), safe);
            }
            List<String> distractorKeys = new ArrayList<>(eligible.keySet());
            if (distractorKeys.size() < 3)
                return QuizGenerationOutcome.invalidInput("AMBIGUOUS_DISTRACTORS: " + target.getStableId());
            Collections.shuffle(distractorKeys, random);
            List<QuizOption> options = new ArrayList<>();
            options.add(new QuizOption(target.getStableId(), target.getMeaning(), correctKey));
            for (String key : distractorKeys.subList(0, 3)) {
                List<QuizVocabulary> bucket = eligible.get(key);
                QuizVocabulary entry = bucket.get(random.nextInt(bucket.size()));
                options.add(new QuizOption(entry.getStableId(), entry.getMeaning(), key));
            }
            Collections.shuffle(options, random);
            questions.add(pool.createQuestion(target.getStableId(), options, target.getStableId()));
        }
        return QuizGenerationOutcome.ready(questions);
    }
}
