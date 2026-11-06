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
    @Test public void defaultUseCaseLoadsPolicyBoundToBundledCatalog() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try (CatalogProvider catalog = new CatalogProvider(context);
                     RepositoryProvider repositories = new RepositoryProvider(context, catalog)) {
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
