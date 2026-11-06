package com.vocamind.app.data.catalog;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.core.SearchProvider;
import com.vocamind.app.domain.catalog.*;
import org.junit.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Reads the actual shipped prepack; fixture mutations stay in UUID-named copies only. */
public abstract class FtsSearchContract {
    private Context context;
    private CatalogDatabase db;
    private SearchProvider provider;
    private ExecutorService worker;
    private String name;
    @Before public void open() {
        context = ApplicationProvider.getApplicationContext(); name = "data05-" + UUID.randomUUID() + ".db";
        db = CatalogDatabase.open(context, name); provider = new SearchProvider(db);
        worker = Executors.newSingleThreadExecutor();
    }
    @After public void close() throws Exception {
        provider.close(); worker.shutdown(); worker.awaitTermination(10, TimeUnit.SECONDS);
        db.close(); context.deleteDatabase(name);
    }
    private VocabularySearchResult search(String input) throws Exception {
        return provider.search().execute(input).get(15, TimeUnit.SECONDS);
    }
    @Test public void englishAndVietnameseResolveTheSameTextStableId() throws Exception {
        var en = search("bank"); var vi = search("ngân hàng");
        assertTrue(en.isSuccess()); assertTrue(vi.isSuccess());
        assertEquals(1, en.getWords().size()); assertEquals(1, vi.getWords().size());
        assertEquals(en.getWords().get(0).stableId, vi.getWords().get(0).stableId);
        assertEquals("5aecfbf5b9a23aee", en.getWords().get(0).stableId);
        assertEquals("ngân hàng", vi.getWords().get(0).meaningVi);
    }
    @Test public void prefixesCaseAndVietnameseNfdAreNormalized() throws Exception {
        assertEquals(1, search("BAN").getWords().size());
        assertEquals(1, search("ngâ hàn").getWords().size());
        assertEquals(1, search(Normalizer.normalize("NGÂN HÀNG", Normalizer.Form.NFD)).getWords().size());
    }
    @Test public void differentSensesRemainDistinctAndAreNotDuplicatedByPackMembership() throws Exception {
        var result = search("book"); assertEquals(2, result.getWords().size());
        Set<String> ids = new HashSet<>(); for (var word : result.getWords()) ids.add(word.stableId);
        assertEquals(2, ids.size());
    }
    @Test public void searchIdsComposeWithFoundationBatchAndMembershipQueries() throws Exception {
        var matches = search("book").getWords();
        assertEquals(2, matches.size());
        Set<String> ids = new HashSet<>();
        for (var word : matches) ids.add(word.stableId);
        worker.submit(() -> {
            var dao = db.catalogDao();
            List<String> batchIds = new ArrayList<>(ids);
            batchIds.add(matches.get(0).stableId); // Foundation must still deduplicate batch input.
            var rows = dao.vocabulariesByIds(batchIds);
            assertEquals(2, rows.size());
            Set<String> hydrated = new HashSet<>();
            for (var row : rows) hydrated.add(row.id);
            assertEquals(ids, hydrated);
            assertEquals(2, dao.matchFixture(FtsMatchExpression.from(VocabularySearchQuery.parse("book"))).size());
            for (var pack : dao.packs()) {
                assertEquals(4, dao.wordCount(pack.id));
                assertTrue(dao.uniqueVocabularyIdsForPacks(Arrays.asList(pack.id, pack.id)).containsAll(ids));
                var memberships = dao.membershipsForPacks(Collections.singletonList(pack.id));
                assertEquals(4, memberships.size());
                for (var row : memberships) assertEquals(pack.id, row.packId);
            }
            assertThrows(IllegalArgumentException.class, () -> dao.wordCount(" "));
            return null;
        }).get(15, TimeUnit.SECONDS);
    }
    @Test public void blankNoMatchAndOperatorSyntaxAreSafe() throws Exception {
        for (String input : Arrays.asList("", "   ", "*", "notpresentvocabulary", "bank OR airport", "\" OR 1=1 --")) {
            var result = search(input); assertTrue(result.isSuccess()); assertTrue(result.getWords().isEmpty());
        }
        assertEquals(1, search("\"bank\"*").getWords().size());
    }
    @Test public void allThreeIndexedColumnsAreActuallySearched() throws Exception {
        worker.submit(() -> {
            // Clone-only probe differentiates lemma from normalizedLemma without modifying shipped asset.
            var sql = db.getOpenHelper().getWritableDatabase();
            sql.execSQL("UPDATE VocabularyEntry SET normalizedLemma='aliasbank' WHERE lemma='bank'");
            sql.execSQL("UPDATE VocabularyFts SET normalizedLemma='aliasbank' WHERE lemma='bank'");
            return null;
        }).get(15, TimeUnit.SECONDS);
        assertEquals(1, search("aliasbank").getWords().size());
        assertEquals(1, search("bank").getWords().size());
        assertEquals(1, search("ngân").getWords().size());
    }
    @Test public void rowidsMayChangeWhileApplicationIdsStayStable() throws Exception {
        worker.submit(() -> {
            var sql = db.getOpenHelper().getWritableDatabase();
            sql.beginTransaction();
            try {
                sql.execSQL("DELETE FROM VocabularyFts WHERE rowid=(SELECT rowid FROM VocabularyEntry WHERE id='5aecfbf5b9a23aee')");
                sql.execSQL("UPDATE VocabularyEntry SET rowid=9003 WHERE id='5aecfbf5b9a23aee'");
                sql.execSQL("INSERT INTO VocabularyFts(rowid,lemma,normalizedLemma,meaningVi) "
                        + "SELECT rowid,lemma,normalizedLemma,meaningVi FROM VocabularyEntry WHERE id='5aecfbf5b9a23aee'");
                sql.setTransactionSuccessful();
            } finally { sql.endTransaction(); }
            return null;
        }).get(15, TimeUnit.SECONDS);
        assertEquals("5aecfbf5b9a23aee", search("bank").getWords().get(0).stableId);
    }
    @Test public void emptyInputDoesNotOpenClosedDatabaseButReadFailuresRemainErrors() throws Exception {
        // First open completes before close so this cannot silently reopen a never-opened asset.
        assertTrue(search("bank").isSuccess()); db.close();
        assertTrue(search("").isSuccess());
        var failure = search("bank"); assertFalse(failure.isSuccess());
        assertEquals(VocabularySearchResult.Error.READ_FAILED, failure.getError());
    }
    @Test public void reopeningPreservesSearchAndProviderDisposalRejectsNewWork() throws Exception {
        String stableId = search("bank").getWords().get(0).stableId;
        provider.close();
        assertEquals(VocabularySearchResult.Error.EXECUTOR_UNAVAILABLE, search("bank").getError());
        db.close(); db = CatalogDatabase.open(context, name); provider = new SearchProvider(db);
        assertEquals(stableId, search("bank").getWords().get(0).stableId);
    }
}
