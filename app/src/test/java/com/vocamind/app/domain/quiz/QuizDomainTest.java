package com.vocamind.app.domain.quiz;

import org.junit.Test;
import java.text.Normalizer;
import java.util.*;
import static org.junit.Assert.*;

public class QuizDomainTest {
    // Fixture only: deliberately NOT a production full Unicode casefold implementation.
    private static final MeaningNormalizer FIXTURE = s -> Normalizer.normalize(s, Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT);
    private static QuizVocabulary word(int i, String meaning) {
        return new QuizVocabulary(String.format(Locale.ROOT, "%016x", i), "word" + i, "noun", meaning, "sense " + i);
    }
    private static List<QuizVocabulary> words(int n) {
        List<QuizVocabulary> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(word(i, "nghĩa " + i));
        return list;
    }
    private static List<QuizOption> options() {
        return new ArrayList<>(Arrays.asList(new QuizOption("a", "nghĩa 0", "nghĩa 0"),
                new QuizOption("b", "nghĩa 1", "nghĩa 1"), new QuizOption("c", "nghĩa 2", "nghĩa 2"),
                new QuizOption("d", "nghĩa 3", "nghĩa 3")));
    }
    private static List<QuizQuestion> questions(int n) {
        List<QuizQuestion> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new QuizQuestion(word(i, "nghĩa 0"), options(), "a", FIXTURE));
        return list;
    }
    private static QuizSession complete(int n, int correct) {
        QuizSession session = new QuizSession(questions(n));
        for (int i = 0; i < n; i++) {
            session = session.answer(session.getCurrentQuestion().getId(), i < correct ? "a" : "b").getSession();
            if (i < n - 1) session = session.next().getSession();
        }
        return session;
    }
    @Test public void inputGuards() {
        for (String id : new String[]{null, "", "  ", "123", "gggggggggggggggg"})
            assertThrows(IllegalArgumentException.class, () -> new QuizVocabulary(id, "x", "noun", "x", ""));
        for (String text : new String[]{null, "", " "}) {
            assertThrows(IllegalArgumentException.class, () -> new QuizVocabulary("0000000000000000", text, "noun", "x", ""));
            assertThrows(IllegalArgumentException.class, () -> new QuizVocabulary("0000000000000000", "x", text, "x", ""));
            assertThrows(IllegalArgumentException.class, () -> new QuizVocabulary("0000000000000000", "x", "noun", text, ""));
            assertThrows(IllegalArgumentException.class, () -> new QuizOption(text, "x", "x"));
            assertThrows(IllegalArgumentException.class, () -> new QuizOption("a", text, "x"));
            assertThrows(IllegalArgumentException.class, () -> new QuizOption("a", "x", text));
        }
        assertThrows(IllegalArgumentException.class, () -> new QuizPool(null, FIXTURE));
        assertThrows(IllegalArgumentException.class, () -> new QuizPool(Arrays.asList((QuizVocabulary) null), FIXTURE));
        assertThrows(IllegalArgumentException.class, () -> new QuizPool(words(4), null));
        assertThrows(IllegalArgumentException.class, () -> new QuizPool(words(4), s -> " "));
    }
    @Test public void targetCounts() {
        int[] counts = {0, 3, 4, 7, 10, 12}, targets = {0, 0, 4, 7, 10, 10};
        for (int i = 0; i < counts.length; i++) {
            QuizPool pool = new QuizPool(words(counts[i]), FIXTURE);
            assertEquals(targets[i], pool.getTargetQuestionCount());
            assertEquals(i < 2 ? QuizPool.Availability.INSUFFICIENT_VOCABULARY : QuizPool.Availability.READY, pool.getAvailability());
        }
    }
    @Test public void dedupConflictsAndSnapshots() {
        assertEquals("abcdef0123456789", new QuizVocabulary("ABCDEF0123456789", "x", "noun", "x", "").getStableId());
        List<QuizVocabulary> input = words(4); input.add(input.get(0));
        QuizPool pool = new QuizPool(input, FIXTURE); input.clear();
        assertEquals(4, pool.getVocabulary().size());
        assertThrows(UnsupportedOperationException.class, () -> pool.getVocabulary().clear());
        List<QuizVocabulary> conflicts = words(4); conflicts.add(word(0, "other"));
        assertThrows(IllegalArgumentException.class, () -> new QuizPool(conflicts, FIXTURE));
        assertThrows(IllegalArgumentException.class, () -> pool.getMeaningKey("unknown"));
    }
    @Test public void meaningBoundaryAndTargetUsesIds() {
        QuizPool insufficient = new QuizPool(Arrays.asList(word(0, " BÀN "), word(1, "bàn"),
                word(2, Normalizer.normalize("bàn", Normalizer.Form.NFD)), word(3, "ban")), FIXTURE);
        assertEquals(QuizPool.Availability.INSUFFICIENT_MEANINGS, insufficient.getAvailability());
        assertEquals(0, insufficient.getTargetQuestionCount());
        assertEquals("INSUFFICIENT_MEANINGS", QuizGenerationOutcome.empty(insufficient).getReason());
        assertNotEquals(insufficient.getMeaningKey("0000000000000000"), insufficient.getMeaningKey("0000000000000003"));
        List<QuizVocabulary> input = words(7); input.set(6, word(6, "nghĩa 0"));
        assertEquals(7, new QuizPool(input, FIXTURE).getTargetQuestionCount());
    }
    @Test public void questionGuardsAndSnapshot() {
        for (int n : new int[]{3, 5}) {
            List<QuizOption> opts = options();
            if (n == 3) opts.remove(0); else opts.add(new QuizOption("e", "e", "e"));
            assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), opts, "a", FIXTURE));
        }
        List<QuizOption> dupId = options(); dupId.set(1, new QuizOption("a", "other", "other"));
        List<QuizOption> dupKey = options(); dupKey.set(1, new QuizOption("b", "other", "nghĩa 0"));
        List<QuizOption> nullItem = options(); nullItem.set(0, null);
        for (List<QuizOption> invalid : Arrays.asList(dupId, dupKey, nullItem))
            assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), invalid, "a", FIXTURE));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), options(), "unknown", FIXTURE));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(null, options(), "a", FIXTURE));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), null, "a", FIXTURE));
        List<QuizOption> original = options(); QuizQuestion q = new QuizQuestion(word(0, "nghĩa 0"), original, "a", FIXTURE); original.clear();
        assertEquals(4, q.getOptions().size());
        assertThrows(UnsupportedOperationException.class, () -> q.getOptions().clear());
    }
    @Test public void rejectsCorrectOptionWithWrongTargetMeaning() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion(word(0, "nghĩa 0"), options(), "b", FIXTURE));
    }

    @Test public void questionValidatesAllOptionKeysAndNormalizer() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion(word(0, "nghĩa 0"), options(), "a", null));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion(word(0, "nghĩa 0"), options(), "a", s -> " "));
        List<QuizOption> forgedCorrect = options();
        forgedCorrect.set(0, new QuizOption("a", "cam", "nghĩa 0"));
        List<QuizOption> forgedDistractor = options();
        forgedDistractor.set(1, new QuizOption("b", "nghĩa 0", "nghĩa 1"));
        for (List<QuizOption> invalid : Arrays.asList(forgedCorrect, forgedDistractor))
            assertThrows(IllegalArgumentException.class,
                    () -> new QuizQuestion(word(0, "nghĩa 0"), invalid, "a", FIXTURE));
    }

    @Test public void poolQuestionUsesSameNormalizationAndScoresCorrectly() {
        // Test adapter includes one full-casefold vector; it is not the production adapter.
        MeaningNormalizer normalizer = s -> FIXTURE.normalize(s).replace("ß", "ss");
        for (String target : Arrays.asList(" BÀN ", Normalizer.normalize("BÀN", Normalizer.Form.NFD), " STRAßE ")) {
            String key = normalizer.normalize(target);
            QuizVocabulary vocabulary = word(0, target);
            QuizPool pool = new QuizPool(Arrays.asList(vocabulary, word(1, "cam"),
                    word(2, "chuối"), word(3, "lê")), normalizer);
            List<QuizOption> opts = Arrays.asList(new QuizOption("a", key, key),
                    new QuizOption("b", "cam", "cam"), new QuizOption("c", "chuối", "chuối"),
                    new QuizOption("d", "lê", "lê"));
            QuizQuestion question = pool.createQuestion(vocabulary.getStableId(), opts, "a");
            assertEquals(pool.getMeaningKey(question.getId()), question.getOptions().get(0).getMeaningKey());
            assertThrows(IllegalArgumentException.class,
                    () -> pool.createQuestion(vocabulary.getStableId(), opts, "b"));
            assertThrows(IllegalArgumentException.class,
                    () -> pool.createQuestion("unknown", opts, "a"));
            List<QuizQuestion> sessionQuestions = new ArrayList<>();
            sessionQuestions.add(question);
            sessionQuestions.addAll(questions(4).subList(1, 4));
            QuizSession session = new QuizSession(sessionQuestions);
            for (int i = 0; i < 4; i++) {
                session = session.answer(session.getCurrentQuestion().getId(), "a").getSession();
                if (i < 3) session = session.next().getSession();
            }
            assertEquals(100, session.getResult().get().getScorePercent());
            List<QuizOption> duplicate = new ArrayList<>(opts);
            duplicate.set(1, new QuizOption("b", target, key));
            assertThrows(IllegalArgumentException.class,
                    () -> pool.createQuestion(vocabulary.getStableId(), duplicate, "a"));
        }
    }

    @Test public void sessionGuardsAndSnapshots() {
        for (int n : new int[]{0, 3, 11}) assertThrows(IllegalArgumentException.class, () -> new QuizSession(questions(n)));
        assertThrows(IllegalArgumentException.class, () -> new QuizSession(null));
        List<QuizQuestion> duplicate = questions(4); duplicate.set(1, duplicate.get(0));
        assertThrows(IllegalArgumentException.class, () -> new QuizSession(duplicate));
        List<QuizQuestion> nullItem = questions(4); nullItem.set(0, null);
        assertThrows(IllegalArgumentException.class, () -> new QuizSession(nullItem));
        List<QuizQuestion> input = questions(4); QuizSession s = new QuizSession(input); input.clear();
        assertEquals(4, s.getQuestions().size());
        assertThrows(UnsupportedOperationException.class, () -> s.getQuestions().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.getAnswers().put("x", "a"));
    }
    @Test public void answersLockAndRejectStaleClicks() {
        QuizSession s = new QuizSession(questions(4)); String id = s.getCurrentQuestion().getId();
        assertEquals(QuizSession.Outcome.ANSWER_REQUIRED, s.next().getOutcome());
        assertSame(s, s.answer("old", "a").getSession());
        assertEquals(QuizSession.Outcome.INVALID_QUESTION, s.answer("old", "a").getOutcome());
        assertEquals(QuizSession.Outcome.INVALID_OPTION, s.answer(id, "unknown").getOutcome());
        assertFalse(s.getResult().isPresent());
        QuizSession locked = s.answer(id, "b").getSession();
        assertTrue(s.getAnswers().isEmpty());
        assertEquals(QuizSession.Outcome.ALREADY_ANSWERED, locked.answer(id, "a").getOutcome());
        assertEquals("b", locked.getAnswers().get(id));
        QuizSession next = locked.next().getSession();
        assertEquals(1, next.getPosition());
        assertEquals(QuizSession.Outcome.INVALID_QUESTION, next.answer(id, "a").getOutcome());
    }
    @Test public void abandonmentAndCompletionAreTerminal() {
        QuizSession abandoned = new QuizSession(questions(4)).abandon().getSession();
        assertEquals(QuizSession.State.ABANDONED, abandoned.getState());
        assertFalse(abandoned.getResult().isPresent());
        QuizSession completed = complete(4, 4);
        assertEquals(QuizSession.State.COMPLETED, completed.getState());
        assertTrue(completed.getResult().isPresent());
        for (QuizSession terminal : Arrays.asList(abandoned, completed)) {
            assertSame(terminal, terminal.next().getSession());
            assertEquals(QuizSession.Outcome.TERMINAL, terminal.answer(terminal.getCurrentQuestion().getId(), "a").getOutcome());
            assertSame(terminal, terminal.abandon().getSession());
        }
        assertSame(completed.getResult().get(), completed.next().getSession().getResult().get());
        QuizSession partial = new QuizSession(questions(4));
        partial = partial.answer(partial.getCurrentQuestion().getId(), "a").getSession();
        assertFalse(partial.abandon().getSession().getResult().isPresent());
    }
    @Test public void scoring() {
        int[][] cases = {{4, 0, 0}, {4, 4, 100}, {6, 1, 17}, {7, 2, 29}};
        for (int[] c : cases) {
            QuizResult r = complete(c[0], c[1]).getResult().get();
            assertEquals(c[0], r.getQuestionCount()); assertEquals(c[1], r.getCorrectCount());
            assertEquals(c[0] - c[1], r.getWrongCount()); assertEquals(c[2], r.getScorePercent());
        }
    }
    private static QuizGenerator seededFake() {
        return (pool, random) -> {
            if (pool == null || random == null) return QuizGenerationOutcome.invalidInput("null input");
            if (pool.getAvailability() != QuizPool.Availability.READY) return QuizGenerationOutcome.empty(pool);
            List<QuizVocabulary> shuffled = new ArrayList<>(pool.getVocabulary()); Collections.shuffle(shuffled, random);
            List<QuizQuestion> output = new ArrayList<>();
            for (QuizVocabulary v : shuffled.subList(0, pool.getTargetQuestionCount())) {
                List<QuizOption> opts = new ArrayList<>();
                String correctKey = pool.getMeaningKey(v.getStableId());
                opts.add(new QuizOption("correct", v.getMeaning(), correctKey));
                Set<String> usedMeaningKeys = new HashSet<>();
                usedMeaningKeys.add(correctKey);
                for (QuizVocabulary distractor : pool.getVocabulary()) {
                    if (opts.size() == 4) break;
                    String key = pool.getMeaningKey(distractor.getStableId());
                    if (usedMeaningKeys.add(key))
                        opts.add(new QuizOption(distractor.getStableId(), distractor.getMeaning(), key));
                }
                output.add(pool.createQuestion(v.getStableId(), opts, "correct"));
            }
            return QuizGenerationOutcome.ready(output);
        };
    }

    @Test public void generatorContractWithSeededFake() {
        QuizGenerator fake = seededFake();
        QuizPool pool = new QuizPool(words(7), FIXTURE);
        QuizGenerationOutcome a = fake.generate(pool, new Random(42)), b = fake.generate(pool, new Random(42));
        assertEquals(QuizGenerationOutcome.Status.READY, a.getStatus()); assertEquals(7, a.getQuestions().size());
        for (int i = 0; i < 7; i++) assertEquals(a.getQuestions().get(i).getId(), b.getQuestions().get(i).getId());
        assertEquals(QuizGenerationOutcome.Status.EMPTY, fake.generate(new QuizPool(words(3), FIXTURE), new Random(42)).getStatus());
        assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, fake.generate(null, new Random(42)).getStatus());
        assertThrows(IllegalArgumentException.class, () -> QuizGenerationOutcome.empty(pool));
        assertThrows(IllegalArgumentException.class, () -> QuizGenerationOutcome.ready(questions(3)));
        assertThrows(UnsupportedOperationException.class, () -> a.getQuestions().clear());
    }
    private static QuizPool repeatedMeaningPool(int count) {
        List<QuizVocabulary> input = new ArrayList<>();
        String[] meanings = {"A", "A", "B", "C", "D"};
        for (int i = 0; i < count; i++) input.add(word(i, meanings[i % meanings.length]));
        return new QuizPool(input, FIXTURE);
    }

    private static void assertReadyQuestions(QuizPool pool, int target, QuizGenerationOutcome outcome) {
        assertEquals(QuizGenerationOutcome.Status.READY, outcome.getStatus());
        assertEquals(target, outcome.getQuestions().size());
        Map<String, QuizVocabulary> inputById = new HashMap<>();
        for (QuizVocabulary v : pool.getVocabulary()) inputById.put(v.getStableId(), v);
        Set<String> questionIds = new HashSet<>();
        for (QuizQuestion q : outcome.getQuestions()) {
            assertTrue("question stableId must be unique", questionIds.add(q.getId()));
            assertTrue("question must belong to pool", inputById.containsKey(q.getId()));
            assertEquals(inputById.get(q.getId()), q.getVocabulary());
            assertEquals(4, q.getOptions().size());
            Set<String> optionIds = new HashSet<>(), keys = new HashSet<>();
            int correctOptions = 0;
            for (QuizOption option : q.getOptions()) {
                assertTrue(optionIds.add(option.getId()));
                assertTrue("option meanings must be distinct", keys.add(option.getMeaningKey()));
                if (option.getId().equals(q.getCorrectOptionId())) {
                    correctOptions++;
                    assertEquals(pool.getMeaningKey(q.getId()), option.getMeaningKey());
                    assertEquals(inputById.get(q.getId()).getMeaning(), option.getMeaning());
                }
            }
            assertEquals(1, correctOptions);
        }
    }

    // REG-01: five IDs must still yield five questions despite only four meanings.
    @Test public void seededFakeHandlesFiveIdsWithRepeatedMeaning() {
        QuizPool pool = repeatedMeaningPool(5);
        assertEquals(QuizPool.Availability.READY, pool.getAvailability());
        assertEquals(5, pool.getTargetQuestionCount());
        assertReadyQuestions(pool, 5, seededFake().generate(pool, new Random(42)));
    }

    // REG-02/03: target depends on unique IDs, not distinct meanings.
    @Test public void seededFakePreservesIdTargetWithRepeatedMeanings() {
        for (int[] testCase : new int[][]{{7, 7}, {12, 10}}) {
            QuizPool pool = repeatedMeaningPool(testCase[0]);
            assertEquals(QuizPool.Availability.READY, pool.getAvailability());
            assertEquals(testCase[1], pool.getTargetQuestionCount());
            assertReadyQuestions(pool, testCase[1], seededFake().generate(pool, new Random(42)));
        }
    }

    // REG-04/05: insufficient pools must not produce questions or a session.
    @Test public void seededFakeReturnsEmptyForInsufficientPools() {
        QuizPool meanings = new QuizPool(Arrays.asList(word(0, "A"), word(1, "A"),
                word(2, "B"), word(3, "C")), FIXTURE);
        QuizPool vocabulary = new QuizPool(words(3), FIXTURE);
        assertEquals(QuizPool.Availability.INSUFFICIENT_MEANINGS, meanings.getAvailability());
        assertEquals(QuizPool.Availability.INSUFFICIENT_VOCABULARY, vocabulary.getAvailability());
        for (QuizPool pool : Arrays.asList(meanings, vocabulary)) {
            assertEquals(0, pool.getTargetQuestionCount());
            QuizGenerationOutcome outcome = seededFake().generate(pool, new Random(42));
            assertEquals(QuizGenerationOutcome.Status.EMPTY, outcome.getStatus());
            assertEquals(pool.getAvailability().name(), outcome.getReason());
            assertTrue(outcome.getQuestions().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> new QuizSession(outcome.getQuestions()));
        }
    }

    // REG-06: compare order, not unordered membership; use a capped pool too.
    @Test public void seededFakeRepeatsQuestionOrderForSameSeed() {
        QuizPool pool = repeatedMeaningPool(12);
        QuizGenerator fake = seededFake();
        QuizGenerationOutcome first = fake.generate(pool, new Random(42));
        QuizGenerationOutcome second = fake.generate(pool, new Random(42));
        assertReadyQuestions(pool, 10, first);
        assertReadyQuestions(pool, 10, second);
        for (int i = 0; i < 10; i++)
            assertEquals(first.getQuestions().get(i).getId(), second.getQuestions().get(i).getId());
    }

    // REG-07: null randomness is invalid even for an otherwise empty pool.
    @Test public void seededFakeRejectsNullInputs() {
        QuizGenerator fake = seededFake();
        QuizPool ready = repeatedMeaningPool(5);
        for (QuizGenerationOutcome outcome : Arrays.asList(
                fake.generate(null, new Random(42)), fake.generate(ready, null),
                fake.generate(null, null), fake.generate(new QuizPool(words(3), FIXTURE), null))) {
            assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, outcome.getStatus());
            assertFalse(outcome.getReason().isEmpty());
            assertTrue(outcome.getQuestions().isEmpty());
        }
    }

}
