package com.vocamind.app.domain.quiz;

import com.vocamind.app.data.catalog.QuizPolicyAsset;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real accepted corpus; expectations use original handoff pairs, independent of generator filtering. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class AcceptedQuizPolicyTest {
    private List<QuizVocabulary> words;
    private List<List<String>> pairs;
    private QuizDistractorPolicy policy;
    private String companion;
    private final MeaningNormalizer normalizer = new UnicodeMeaningNormalizer();

    public static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Before public void load() throws Exception {
        JSONArray entries = new JSONObject(read("docs/data/voc-data-04/accepted-v1/canonical-preview.json")).getJSONArray("entries");
        words = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject v = entries.getJSONObject(i);
            words.add(new QuizVocabulary(v.getString("id"), v.getString("lemma"), v.getString("pos"),
                    v.getString("meaningVi"), v.getString("exampleEn")));
        }
        JSONArray original = new JSONObject(read("docs/data/voc-data-04/accepted-v1/quiz-policy.json"))
                .getJSONArray("forbiddenDistractorPairs");
        pairs = new ArrayList<>();
        for (int i = 0; i < original.length(); i++)
            pairs.add(List.of(original.getJSONArray(i).getString(0), original.getJSONArray(i).getString(1)));
        companion = read("app/src/test/resources/quiz/accepted-policy.json");
        policy = QuizPolicyAsset.parse(companion, words);
    }

    private boolean forbidden(String a, String b) {
        for (List<String> pair : pairs)
            if ((pair.get(0).equals(a) && pair.get(1).equals(b)) ||
                    (pair.get(1).equals(a) && pair.get(0).equals(b))) return true;
        return false;
    }

    @Test public void fullCorpusIncludesSeed60AndNeverEmitsForbiddenPairs() {
        QuizPool pool = new QuizPool(words, normalizer, policy);
        List<QuizVocabulary> reversed = new ArrayList<>(words);
        Collections.reverse(reversed);
        QuizPool reordered = new QuizPool(reversed, normalizer, policy);
        List<QuizVocabulary> before = new ArrayList<>(words);
        int witnessedUnrestrictedViolations = 0;
        for (int seed = 0; seed < 1000; seed++) {
            QuizGenerationOutcome outcome = new DefaultQuizGenerator().generate(pool, new Random(seed));
            assertEquals(QuizGenerationOutcome.Status.READY, outcome.getStatus());
            assertEquals(10, outcome.getQuestions().size());
            Set<String> targets = new HashSet<>();
            for (QuizQuestion q : outcome.getQuestions()) {
                assertTrue(targets.add(q.getId()));
                assertEquals(q.getId(), q.getCorrectOptionId());
                assertEquals(4, q.getOptions().size());
                Set<String> keys = new HashSet<>(), ids = new HashSet<>();
                for (QuizOption option : q.getOptions()) {
                    assertTrue(ids.add(option.getId()));
                    assertTrue(keys.add(option.getMeaningKey()));
                    assertEquals(pool.getMeaningKey(option.getId()), option.getMeaningKey());
                    assertFalse("seed=" + seed, forbidden(q.getId(), option.getId()));
                }
            }
            if (seed == 60) {
                QuizGenerationOutcome unrestricted = new DefaultQuizGenerator().generate(new QuizPool(words, normalizer), new Random(seed));
                for (QuizQuestion q : unrestricted.getQuestions()) for (QuizOption option : q.getOptions())
                    if (forbidden(q.getId(), option.getId())) witnessedUnrestrictedViolations++;
                assertEquals(signature(outcome), signature(new DefaultQuizGenerator().generate(reordered, new Random(seed))));
            }
        }
        assertTrue("fixture must reproduce the original defect", witnessedUnrestrictedViolations > 0);
        assertEquals(before, words);
    }

    private String signature(QuizGenerationOutcome outcome) {
        StringBuilder text = new StringBuilder();
        for (QuizQuestion q : outcome.getQuestions()) {
            text.append(q.getId());
            for (QuizOption option : q.getOptions()) text.append(option.getId());
        }
        return text.toString();
    }

    @Test public void minimalPoolsFailAndSufficientPoolsEnforceBothDirections() {
        for (List<String> pair : pairs) {
            List<QuizVocabulary> minimal = new ArrayList<>();
            for (QuizVocabulary v : words)
                if (pair.contains(v.getStableId()) || List.of("method", "theory").contains(v.getLemma())) minimal.add(v);
            assertEquals(4, minimal.size());
            List<QuizVocabulary> sufficient = new ArrayList<>(minimal);
            sufficient.add(words.stream().filter(v -> v.getLemma().equals("luggage")).findFirst().get());
            for (int seed = 0; seed < 60; seed++) {
                QuizGenerationOutcome failed = new DefaultQuizGenerator().generate(new QuizPool(minimal, normalizer, policy), new Random(seed));
                assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, failed.getStatus());
                assertTrue(failed.getReason().startsWith("AMBIGUOUS_DISTRACTORS:"));
                assertTrue(failed.getQuestions().isEmpty());
                QuizGenerationOutcome success = new DefaultQuizGenerator().generate(new QuizPool(sufficient, normalizer, policy), new Random(seed));
                assertEquals(QuizGenerationOutcome.Status.READY, success.getStatus());
                assertEquals(5, success.getQuestions().size());
                for (QuizQuestion q : success.getQuestions()) for (QuizOption option : q.getOptions())
                    assertFalse(forbidden(q.getId(), option.getId()));
            }
        }
    }

    @Test public void invalidCompanionCannotBecomeUnrestricted() throws Exception {
        JSONObject root = new JSONObject(companion);
        root.put("projectionSha256", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> QuizPolicyAsset.parse(root.toString(), words));
        assertThrows(IllegalArgumentException.class, () -> QuizPolicyAsset.parse(companion, words.subList(0, 4)));
        assertThrows(IllegalArgumentException.class, () -> QuizPolicyAsset.parse("{}", words));
        root.put("projectionSha256", QuizPolicyAsset.projectionDigest(words));
        root.put("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> QuizPolicyAsset.parse(root.toString(), words));
        root.put("schemaVersion", 1);
        root.put("forbiddenDistractorPairs", new JSONArray("[[\"0000000000000000\",\"0000000000000001\"]]"));
        assertThrows(IllegalArgumentException.class, () -> QuizPolicyAsset.parse(root.toString(), words));
        root.remove("forbiddenDistractorPairs");
        assertThrows(IllegalArgumentException.class, () -> QuizPolicyAsset.parse(root.toString(), words));
    }
}
