package com.vocamind.app.domain.quiz;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizGenerationTest {
    @Test public void generatedTableMatchesEveryOfficialDefaultMapping() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get("tools/unicode/CaseFolding-16.0.0.txt");
        if (!java.nio.file.Files.exists(path)) path = java.nio.file.Paths.get("../tools/unicode/CaseFolding-16.0.0.txt");
        int checked = 0;
        for (String line : java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8)) {
            String[] fields = line.split("#", 2)[0].split(";");
            if (fields.length < 3 || !(fields[1].trim().equals("C") || fields[1].trim().equals("F"))) continue;
            int cp = Integer.parseInt(fields[0].trim(), 16);
            StringBuilder expected = new StringBuilder();
            for (String hex : fields[2].trim().split(" +")) expected.appendCodePoint(Integer.parseInt(hex, 16));
            assertEquals(fields[0], expected.toString(), UnicodeCaseFold.fold(cp));
            checked++;
        }
        assertEquals(1557, checked);
        assertEquals("\ud83d\ude00", UnicodeCaseFold.fold(0x1f600));
    }
    private final MeaningNormalizer normalizer = new UnicodeMeaningNormalizer();
    private final QuizGenerator generator = new DefaultQuizGenerator();
    private List<QuizVocabulary> words(int count, int meanings) {
        List<QuizVocabulary> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(new QuizVocabulary(
                String.format(Locale.ROOT, "%016x", i), "word" + i, "noun", "meaning" + (i % meanings), "context" + i));
        return out;
    }
    private String signature(QuizGenerationOutcome out) {
        StringBuilder s = new StringBuilder();
        for (QuizQuestion q : out.getQuestions()) {
            s.append(q.getId()).append(q.getCorrectOptionId());
            for (QuizOption o : q.getOptions()) s.append(o.getId()).append(o.getMeaningKey());
        }
        return s.toString();
    }
    @Test public void normalizerVectorsAndLocale() {
        Locale previous = Locale.getDefault();
        try {
            for (Locale locale : Arrays.asList(Locale.ROOT, Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale);
                assertEquals("bàn", normalizer.normalize("\u00a0 BA\u0300N \u0085"));
                assertEquals("strasse", normalizer.normalize("Straße"));
                assertEquals("strasse", normalizer.normalize("STRASSE"));
                assertEquals("σσσ", normalizer.normalize("Σσς"));
                assertEquals("i\u0307", normalizer.normalize("İ"));
                assertEquals("i", normalizer.normalize("I"));
                assertNotEquals(normalizer.normalize("ban"), normalizer.normalize("bàn"));
                assertEquals("a  b", normalizer.normalize(" A  B "));
                assertEquals("\ud801\udc28", normalizer.normalize("\ud801\udc00"));
                assertEquals("ａ", normalizer.normalize("Ａ"));
            }
        } finally { Locale.setDefault(previous); }
        for (String invalid : Arrays.asList(null, "", " \u00a0\u0085\u3000"))
            assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(invalid));
    }
    @Test public void countAndInsufficiencyMatrix() {
        for (int count : new int[]{0,3,4,7,9,10,12}) {
            QuizGenerationOutcome out = generator.generate(new QuizPool(words(count, 4), normalizer), new Random(1));
            assertEquals(count < 4 ? QuizGenerationOutcome.Status.EMPTY : QuizGenerationOutcome.Status.READY, out.getStatus());
            assertEquals(count < 4 ? 0 : Math.min(10,count), out.getQuestions().size());
        }
        for (int meanings = 1; meanings < 4; meanings++) {
            QuizGenerationOutcome out = generator.generate(new QuizPool(words(12, meanings), normalizer), new Random(1));
            assertEquals("INSUFFICIENT_MEANINGS", out.getReason());
        }
        assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, generator.generate(null,new Random(1)).getStatus());
        assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, generator.generate(new QuizPool(words(0,1),normalizer),null).getStatus());
    }
    @Test public void seededPropertiesMembershipAndImmutability() {
        Set<Integer> correctPositions = new HashSet<>();
        Set<String> orders = new HashSet<>();
        for (int count : new int[]{4,5,7,10,12,40}) for (int seed = 0; seed < 60; seed++) {
            List<QuizVocabulary> source = words(count, 4 + seed % Math.max(1,count-3));
            // Strongly skewed distribution in alternate fixtures.
            if (seed % 2 == 0) for (int i = 4; i < count; i++) {
                QuizVocabulary v = source.get(i);
                source.set(i,new QuizVocabulary(v.getStableId(),v.getLemma(),v.getPos(),"meaning0",v.getContext()));
            }
            List<QuizVocabulary> before = new ArrayList<>(source);
            QuizPool pool = new QuizPool(source, normalizer);
            QuizGenerationOutcome out = generator.generate(pool,new Random(seed));
            assertEquals(QuizGenerationOutcome.Status.READY,out.getStatus());
            assertEquals(Math.min(count,10),out.getQuestions().size());
            Set<String> ids = new HashSet<>();
            for (QuizQuestion q : out.getQuestions()) {
                assertTrue(ids.add(q.getId()));
                assertTrue(source.contains(q.getVocabulary()));
                Set<String> optionIds = new HashSet<>(), keys = new HashSet<>();
                int correct = 0;
                for (int j=0;j<4;j++) {
                    QuizOption o = q.getOptions().get(j);
                    assertTrue(optionIds.add(o.getId())); assertTrue(keys.add(o.getMeaningKey()));
                    QuizVocabulary original = source.stream().filter(v -> v.getStableId().equals(o.getId())).findFirst().get();
                    assertEquals(original.getMeaning(),o.getMeaning());
                    assertEquals(pool.getMeaningKey(o.getId()),o.getMeaningKey());
                    if (o.getId().equals(q.getCorrectOptionId())) {
                        correct++; correctPositions.add(j);
                        assertEquals(pool.getMeaningKey(q.getId()),o.getMeaningKey());
                    }
                }
                assertEquals(1,correct);
                assertThrows(UnsupportedOperationException.class, () -> q.getOptions().clear());
            }
            assertEquals(before,source);
            Collections.reverse(source);
            assertEquals(signature(out),signature(generator.generate(new QuizPool(source,normalizer),new Random(seed))));
            orders.add(signature(out));
            assertThrows(UnsupportedOperationException.class, () -> out.getQuestions().clear());
        }
        assertEquals(4,correctPositions.size()); assertTrue(orders.size()>1);
    }
    @Test public void dedupConflictAndAmbiguousSensePolicy() {
        List<QuizVocabulary> input = words(4,4);
        input.add(input.get(0));
        assertEquals(4,generator.generate(new QuizPool(input,normalizer),new Random(1)).getQuestions().size());
        QuizVocabulary first = input.get(0);
        input.add(new QuizVocabulary(first.getStableId(),"changed","noun","other",""));
        List<QuizVocabulary> conflicting = input;
        assertThrows(IllegalArgumentException.class, () -> new QuizPool(conflicting,normalizer));
        input = words(4,4);
        QuizVocabulary v = input.get(1);
        input.set(1,new QuizVocabulary(v.getStableId(),"word0","verb",v.getMeaning(),""));
        for (int seed = 0; seed < 60; seed++) {
            QuizGenerationOutcome invalid = generator.generate(new QuizPool(input,normalizer),new Random(seed));
            assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, invalid.getStatus());
            assertTrue(invalid.getReason().startsWith("AMBIGUOUS_DISTRACTORS:"));
            assertTrue(invalid.getQuestions().isEmpty());
        }
        input.add(new QuizVocabulary("00000000000000ff","extra","noun","extra",""));
        QuizPool pool = new QuizPool(input, normalizer);
        for (int seed = 0; seed < 60; seed++) {
            QuizGenerationOutcome out = generator.generate(pool, new Random(seed));
            assertEquals(QuizGenerationOutcome.Status.READY, out.getStatus());
            assertEquals(5, out.getQuestions().size());
            for (QuizQuestion question : out.getQuestions()) {
                QuizVocabulary target = question.getVocabulary();
                // Independent oracle from fixture payloads, not generator internals.
                Set<String> otherSenseKeys = new HashSet<>();
                for (QuizVocabulary entry : input) {
                    if (normalizer.normalize(entry.getLemma()).equals(normalizer.normalize(target.getLemma()))
                            && !normalizer.normalize(entry.getMeaning()).equals(normalizer.normalize(target.getMeaning())))
                        otherSenseKeys.add(normalizer.normalize(entry.getMeaning()));
                }
                for (QuizOption option : question.getOptions())
                    assertFalse("alternative sense leaked into options", otherSenseKeys.contains(option.getMeaningKey()));
                assertEquals(target.getPos(), question.getVocabulary().getPos());
                assertEquals(target.getContext(), question.getVocabulary().getContext());
            }
        }
    }
}
