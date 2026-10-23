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
        for (int i = 0; i < n; i++) list.add(new QuizQuestion(word(i, "nghĩa 0"), options(), "a"));
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
            assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), opts, "a"));
        }
        List<QuizOption> dupId = options(); dupId.set(1, new QuizOption("a", "other", "other"));
        List<QuizOption> dupKey = options(); dupKey.set(1, new QuizOption("b", "other", "nghĩa 0"));
        List<QuizOption> nullItem = options(); nullItem.set(0, null);
        for (List<QuizOption> invalid : Arrays.asList(dupId, dupKey, nullItem))
            assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), invalid, "a"));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), options(), "unknown"));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(null, options(), "a"));
        assertThrows(IllegalArgumentException.class, () -> new QuizQuestion(word(0, "x"), null, "a"));
        List<QuizOption> original = options(); QuizQuestion q = new QuizQuestion(word(0, "x"), original, "a"); original.clear();
        assertEquals(4, q.getOptions().size());
        assertThrows(UnsupportedOperationException.class, () -> q.getOptions().clear());
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
    @Test public void generatorContractWithSeededFake() {
        QuizGenerator fake = (pool, random) -> {
            if (pool == null || random == null) return QuizGenerationOutcome.invalidInput("null input");
            if (pool.getAvailability() != QuizPool.Availability.READY) return QuizGenerationOutcome.empty(pool);
            List<QuizVocabulary> shuffled = new ArrayList<>(pool.getVocabulary()); Collections.shuffle(shuffled, random);
            List<QuizQuestion> output = new ArrayList<>();
            for (QuizVocabulary v : shuffled.subList(0, pool.getTargetQuestionCount())) {
                List<QuizOption> opts = new ArrayList<>();
                opts.add(new QuizOption("correct", v.getMeaning(), pool.getMeaningKey(v.getStableId())));
                for (QuizVocabulary distractor : pool.getVocabulary()) {
                    if (!pool.getMeaningKey(distractor.getStableId()).equals(pool.getMeaningKey(v.getStableId())) && opts.size() < 4)
                        opts.add(new QuizOption(distractor.getStableId(), distractor.getMeaning(), pool.getMeaningKey(distractor.getStableId())));
                }
                output.add(new QuizQuestion(v, opts, "correct"));
            }
            return QuizGenerationOutcome.ready(output);
        };
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
}
