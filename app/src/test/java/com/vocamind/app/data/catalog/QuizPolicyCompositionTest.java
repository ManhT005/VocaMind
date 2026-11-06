package com.vocamind.app.data.catalog;

import android.content.Context;
import android.content.res.AssetManager;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.core.CatalogProvider;
import com.vocamind.app.core.RepositoryProvider;
import com.vocamind.app.domain.quiz.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class QuizPolicyCompositionTest {
    @Test public void defaultUseCaseLoadsPolicyBoundToBundledCatalogAndVocabularyRepository() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try (CatalogProvider catalog = new CatalogProvider(context);
                     RepositoryProvider repositories = new RepositoryProvider(context, catalog)) {
                    var pack = catalog.repository().getPacks().get(0);
                    var word = catalog.repository().getWords(pack.id).get(0);
                    var detail = new com.vocamind.app.domain.vocabulary.GetVocabularyDetail(repositories.vocabulary());
                    var save = new com.vocamind.app.domain.vocabulary.ToggleSaved(repositories.vocabulary());
                    assertEquals(word.stableId, detail.execute(pack.id, word.stableId).get().word.stableId);
                    assertFalse(detail.execute(pack.id, word.stableId).get().saved);
                    // Existing-progress save only: first-save acceptance remains blocked on policy.
                    var scheduled = new com.vocamind.app.domain.learning.LeitnerScheduler().schedule(
                            com.vocamind.app.domain.learning.LeitnerState.newWord(),
                            com.vocamind.app.domain.learning.Rating.HARD, 1000L, java.time.ZoneOffset.UTC);
                    assertTrue(repositories.reviews().recordReview(word.stableId, scheduled, 1000L).isSuccess());
                    assertTrue(save.execute(pack.id, word.stableId, true).get());
                    GenerateQuiz generate = new GenerateQuiz(repositories.quizzes());
                    assertEquals(QuizGenerationOutcome.Status.EMPTY, generate.execute(new Random(60)).get().getStatus());
                    for (var state : repositories.packs().getPackStates().get())
                        assertTrue(repositories.packs().activatePack(state.getPack().id, 1).isSuccess());
                    QuizGenerationOutcome result = generate.execute(new Random(60)).get();
                    assertEquals(4, repositories.quizzes().activeSnapshot().get().getVocabulary().size());
                    // The bundled four-word fixture contains book noun/verb; preserve same-lemma safety.
                    assertEquals(QuizGenerationOutcome.Status.INVALID_INPUT, result.getStatus());
                    assertTrue(result.getReason().startsWith("AMBIGUOUS_DISTRACTORS:"));
                    assertTrue(result.getQuestions().isEmpty());
                    assertTrue(detail.execute(pack.id, word.stableId).get().saved);
                    assertFalse(save.execute(pack.id, word.stableId, false).get());
                }
            }).get(60, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
            context.deleteDatabase("catalog.db");
            context.deleteDatabase("userdata.db");
        }
    }

    @Test public void absentCompanionAssetIsRejectedInsteadOfUnrestrictedFallback() throws Exception {
        var constructor = AssetManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AssetManager withoutAppAssets = constructor.newInstance();
        try {
            assertThrows(IllegalArgumentException.class, () -> QuizPolicyAsset.load(withoutAppAssets, List.of()));
        } finally {
            withoutAppAssets.close();
        }
    }
}
