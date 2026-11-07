package com.vocamind.app.data.catalog;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.data.repository.RoomPackRepository;
import com.vocamind.app.data.repository.RoomQuizRepository;
import com.vocamind.app.data.repository.RoomVocabularyRepository;
import com.vocamind.app.data.repository.UserDataMapper;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.catalog.VocabularySearchQuery;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.quiz.GenerateQuiz;
import com.vocamind.app.domain.quiz.QuizGenerationOutcome;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import static org.junit.Assert.*;

/** Reads the installed QA app assets through the same Room prepack path as production. */
@RunWith(AndroidJUnit4.class)
public class PackagedQaCatalogInstrumentedTest {
    @Test public void installedQaAssetsOpenThroughRoomAndKeepReviewDataOnReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.vocamind.app.qa", context.getPackageName());
        String catalogName = UUID.randomUUID() + "-catalog.db";
        String userName = UUID.randomUUID() + "-userdata.db";
        CatalogDatabase catalog = CatalogDatabase.open(context, catalogName);
        UserDatabase user = null;
        try {
            user = UserDatabase.open(context, userName);
            ContentCredits credits = ContentCredits.load(context.getAssets());
            var repository = new RoomCatalogRepository(catalog.catalogDao(), () -> credits);
            var packs = repository.getPacks();
            assertEquals(4, packs.size());
            var packRepository = new RoomPackRepository(catalog.catalogDao(), user.userDao());
            var detail = new RoomVocabularyRepository(repository, user);
            var search = new RoomVocabularySearchRepository(catalog.catalogDao());
            var projection = catalog.catalogDao().allVocabulary().stream()
                    .map(CatalogMapper::vocabulary).map(UserDataMapper::quizVocabulary)
                    .collect(java.util.stream.Collectors.toList());
            var policy = QuizPolicyAsset.load(context.getAssets(), projection);
            var quiz = new GenerateQuiz(new RoomQuizRepository(catalog.catalogDao(), user.userDao(), () -> policy));
            assertEquals(QuizGenerationOutcome.Status.EMPTY, quiz.execute(new Random(1)).get().getStatus());
            assertTrue(user.userDao().quizzes().isEmpty());

            var ids = new HashSet<String>();
            for (int i = 0; i < packs.size(); i++) {
                var pack = packs.get(i);
                assertEquals(i == 3 ? 30 : 40, pack.wordCount);
                assertFalse(pack.description.contains("Bản nháp"));
                var words = repository.getWords(pack.id);
                assertEquals(pack.wordCount, words.size());
                for (var word : words) assertTrue(ids.add(word.stableId));
                for (var word : Arrays.asList(words.get(0), words.get(words.size() - 1))) {
                    var loaded = detail.getDetail(pack.id, word.stableId).get().word;
                    assertEquals(word.lemma, loaded.lemma);
                    assertEquals(word.ipa, loaded.ipa);
                    assertFalse(loaded.meaningVi.trim().isEmpty());
                    assertFalse(loaded.exampleEn.trim().isEmpty());
                    assertFalse(loaded.exampleVi.trim().isEmpty());
                    for (String query : Arrays.asList(word.lemma, word.meaningVi))
                        assertTrue(search.search(VocabularySearchQuery.parse(query)).getWords().stream()
                                .anyMatch(match -> word.stableId.equals(match.stableId)));
                }
                assertEquals(RepoError.NOT_FOUND, detail.getDetail(pack.id, "0000000000000000").getError());
                assertTrue(packRepository.activatePack(pack.id, 1000L).isSuccess());
            }
            assertEquals(150, ids.size());
            assertEquals(150, projection.size());
            assertTrue(repository.getSources().get(0).creditText.contains("QA nội bộ"));
            var outcome = quiz.execute(new Random(2)).get();
            assertEquals(QuizGenerationOutcome.Status.READY, outcome.getStatus());
            assertTrue(outcome.getQuestions().size() >= 4 && outcome.getQuestions().size() <= 10);
            var targets = new HashSet<String>();
            for (var question : outcome.getQuestions()) {
                assertTrue(targets.add(question.getId()));
                assertEquals(4L, question.getOptions().stream().map(o -> o.getMeaningKey()).distinct().count());
            }
            assertTrue(user.userDao().quizzes().isEmpty());

            var first = packs.get(0);
            var word = repository.getWords(first.id).get(0);
            assertEquals(RepoError.NOT_FOUND, detail.getDetail(packs.get(1).id, word.stableId).getError());
            assertTrue(detail.setSaved(first.id, word.stableId, true).get());
            user.close();
            user = UserDatabase.open(context, userName);
            assertEquals(4, user.userDao().activePacks().size());
            assertTrue(new RoomVocabularyRepository(repository, user).getDetail(first.id, word.stableId).get().saved);
        } finally {
            if (user != null) user.close();
            catalog.close();
            context.deleteDatabase(userName);
            context.deleteDatabase(catalogName);
        }
    }
}
