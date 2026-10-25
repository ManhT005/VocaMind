package com.vocamind.app.data;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.vocamind.app.data.catalog.*;
import com.vocamind.app.data.catalog.entity.*;
import com.vocamind.app.data.user.*;
import com.vocamind.app.data.user.entity.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

/** Real file-backed Room/SQLite fixtures. Cross-DB composition here is a handoff harness only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class DaoFoundationTest {
    @Rule public org.junit.rules.TestRule workerThread = (base, description) ->
            new org.junit.runners.model.Statement() {
                @Override public void evaluate() throws Throwable {
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    Thread worker = new Thread(() -> {
                        try { base.evaluate(); } catch (Throwable error) { failure.set(error); }
                    }, "data03-test-worker");
                    worker.start(); worker.join(60000);
                    if (worker.isAlive()) { worker.interrupt(); fail("DAO test timed out"); }
                    if (failure.get() != null) throw failure.get();
                }
            };
    private Context context;
    private CatalogDatabase catalog;
    private UserDatabase user;
    private String catalogName, userName;
    private CatalogDao cd;
    private UserDao ud;

    @Before public void open() {
        context = ApplicationProvider.getApplicationContext();
        String token = UUID.randomUUID().toString();
        catalogName = "data03-catalog-" + token + ".db";
        userName = "data03-user-" + token + ".db";
        catalog = CatalogDatabase.open(context, catalogName);
        cd = catalog.catalogDao();
        user = UserDatabase.open(context, userName); ud = user.userDao();
        // Only this UUID test copy is changed. No fixture writes in production DAO/API.
        SupportSQLiteDatabase sql = catalog.getOpenHelper().getWritableDatabase();
        sql.execSQL("DELETE FROM PackWord"); sql.execSQL("DELETE FROM VocabularyFts");
        sql.execSQL("DELETE FROM VocabularyEntry"); sql.execSQL("DELETE FROM LearningPack");
        sql.execSQL("DELETE FROM ContentSource");
        sql.execSQL("INSERT INTO ContentSource VALUES ('test-source','Original test content',"
                + "'https://example.invalid','LicenseRef-Test','https://example.invalid/license','2026-10-25')");
        for (String id : Arrays.asList("A", "B", "C", "E")) {
            sql.execSQL("INSERT INTO LearningPack (id,title,description,category,orderNo,version) VALUES (?,?,?,?,?,?)",
                    new Object[]{id, id, "test", "test", id.equals("C") ? 2 : id.equals("E") ? 3 : 1, "1"});
        }
        for (String id : Arrays.asList("shared", "a", "b", "new-a", "new-b", "new-c", "paused")) addWord(id);
        membership("A", "shared", 1); membership("A", "a", 2);
        membership("A", "new-a", 3); membership("A", "new-b", 4);
        membership("B", "shared", 0); membership("B", "new-b", 1);
        membership("B", "new-c", 2); membership("B", "b", 3);
        membership("C", "paused", 0);
    }

    @After public void close() {
        if (catalog != null) catalog.close(); if (user != null) user.close();
        if (context != null) {
            if (catalogName != null) context.deleteDatabase(catalogName);
            if (userName != null) context.deleteDatabase(userName);
        }
    }
    private void addWord(String id) {
        catalog.getOpenHelper().getWritableDatabase().execSQL("INSERT INTO VocabularyEntry "
                + "(id,lemma,normalizedLemma,pos,meaningVi,exampleEn,exampleVi,ipa,sourceId,contentVersion) "
                + "VALUES (?,?,?,?,?,?,?,NULL,?,?)", new Object[]{id,id,id,"noun","meaning " + id,
                        "Original example", "Original translation", "test-source", "1"});
    }
    private void membership(String pack, String word, int order) {
        catalog.getOpenHelper().getWritableDatabase().execSQL("INSERT INTO PackWord VALUES (?,?,?)",
                new Object[]{pack, word, order});
    }
    private UserWordProgress progress(String id, int box, long due, Long learned) {
        UserWordProgress row = new UserWordProgress(); row.vocabularyId = id;
        row.box = box; row.dueAt = due; row.learnedAt = learned; row.reviewCount = 1;
        ud.saveProgress(row); return row;
    }
    private ReviewEvent event(String id, long time, int box, long nextDue) {
        ReviewEvent row = new ReviewEvent(); row.vocabularyId = id; row.rating = "HARD";
        row.reviewedAt = time; row.boxBefore = box; row.boxAfter = box; row.nextDueAt = nextDue;
        return row;
    }
    private QuizAttempt quiz(long time) {
        QuizAttempt row = new QuizAttempt(); row.completedAt = time;
        row.questionCount = 4; row.correctCount = 3; row.scorePercent = 75; return row;
    }
    private List<String> eligible() {
        return cd.uniqueVocabularyIdsForPacks(ud.activePacks().stream().map(p -> p.packId).collect(Collectors.toList()));
    }
    private static List<String> ids(List<UserWordProgress> rows) {
        return rows.stream().map(p -> p.vocabularyId).collect(Collectors.toList());
    }

    @Test public void catalogOrderEmptyNullableAndAttribution() {
        assertEquals(Arrays.asList("A","B","C","E"), cd.packs().stream().map(p -> p.id).collect(Collectors.toList()));
        assertEquals(Arrays.asList("shared","a","new-a","new-b"),
                cd.words("A").stream().map(v -> v.id).collect(Collectors.toList()));
        assertTrue(cd.words("E").isEmpty()); assertTrue(cd.words("unknown").isEmpty());
        assertNull(cd.pack("unknown")); assertNull(cd.vocabulary("unknown"));
        assertNull(cd.sourceForVocabulary("unknown")); assertEquals(4, cd.wordCount("A"));
        assertNull(cd.vocabulary("a").ipa); assertEquals("test-source", cd.sourceForVocabulary("a").id);
        assertEquals("https://example.invalid/license", cd.sources().get(0).licenseUrl);
    }

    @Test public void membershipDuplicatesAreIntentionalButVocabularyPoolIsUnique() {
        assertEquals(8, cd.membershipsForPacks(Arrays.asList("B","A","A")).size());
        assertEquals(Arrays.asList("shared","a","new-a","new-b","new-c","b"),
                cd.uniqueVocabularyIdsForPacks(Arrays.asList("B","A","A")));
        assertEquals(Arrays.asList("a","shared"), cd.vocabulariesByIds(Arrays.asList("shared","a","shared","missing"))
                .stream().map(v -> v.id).collect(Collectors.toList()));
    }

    @Test public void dueFiltersActiveAndOrphanBeforeGlobalLimit() {
        ud.activatePack("A", 1); ud.activatePack("B", 2);
        progress("shared", 0, 10, 1L); progress("a", 1, 100, 1L);
        progress("b", 2, 101, 1L); progress("paused", 3, 0, 1L); progress("orphan", 4, 0, 1L);
        assertEquals(Arrays.asList("shared","a"), ids(ud.dueProgress(eligible(), 100, 10)));
        assertEquals(Collections.singletonList("shared"), ids(ud.dueProgress(eligible(), 100, 1)));
        assertEquals(Collections.singletonList("shared"), ids(ud.progressByBox(eligible(), 0)));
    }

    @Test public void pausedOrNoActivePacksHaveNoCandidatesAndNeverActivateAutomatically() {
        progress("shared", 0, 0, 1L);
        assertTrue(eligible().isEmpty()); assertTrue(ud.dueProgress(eligible(), 100, 5).isEmpty());
        ud.activatePack("C", 4); ud.pausePack("C");
        assertTrue(eligible().isEmpty()); assertTrue(ud.activePacks().isEmpty());
        assertEquals(0, ud.pausePack("missing")); assertNull(ud.pack("missing"));
        ud.activatePack("A", 15); ud.pausePack("A"); ud.activatePack("A", 999);
        assertEquals(15, ud.pack("A").activatedAt);
    }

    @Test public void newIsAbsenceOfAnyProgressAndLimitComesAfterDedup() {
        ud.activatePack("A", 1); ud.activatePack("B", 2);
        progress("shared", 0, 0, 1L); progress("a", 1, 0, 1L);
        progress("b", 2, 0, 1L); progress("new-b", 0, 999, null);
        List<String> candidates = eligible();
        Set<String> existing = new HashSet<>(ud.existingProgressIds(candidates));
        List<String> fresh = candidates.stream().filter(id -> !existing.contains(id)).limit(2).collect(Collectors.toList());
        assertEquals(Arrays.asList("new-a","new-c"), fresh);
        List<String> repeated = new ArrayList<>(candidates); repeated.addAll(candidates);
        assertEquals(fresh, ud.newVocabularyIds(repeated, 2));
        assertEquals(4, existing.size());
    }

    @Test public void quotaCountsUniqueFirstLearnedTimesNotReviewEvents() {
        progress("a", 1, 0, 99L); progress("b", 2, 0, 100L);
        progress("shared", 0, 0, 199L); progress("paused", 4, 0, 200L);
        progress("new-b", 0, 0, null);
        ud.insertReview(event("shared", 150, 0, 1000)); ud.insertReview(event("shared", 151, 0, 1000));
        assertEquals(2, ud.learnedCount(100, 200));
        assertEquals(0, ud.learnedCount(201, 300));
    }

    @Test public void progressBoxAndDueTieOrderingAreStable() {
        for (int box = 0; box <= 5; box++) progress("box-" + box, box, 100, 1L);
        List<String> keys = Arrays.asList("box-5","box-3","box-1","box-4","box-0","box-2","box-0");
        assertEquals(Arrays.asList("box-0","box-1","box-2","box-3","box-4","box-5"),
                ids(ud.dueProgress(keys, 100, 9)));
        for (int box = 0; box <= 5; box++) assertEquals(Collections.singletonList("box-" + box),
                ids(ud.progressByBox(keys, box)));
        assertEquals(6, ud.progressByIds(keys).size()); assertNull(ud.progress("missing"));
    }

    @Test public void invalidAndEmptyInputsCannotBecomeFullTableQueries() {
        progress("a", 1, 0, 1L);
        assertTrue(cd.vocabulariesByIds(Collections.emptyList()).isEmpty());
        assertTrue(cd.membershipsForPacks(Collections.emptyList()).isEmpty());
        assertTrue(ud.existingProgressIds(Collections.emptyList()).isEmpty());
        assertTrue(ud.newVocabularyIds(Collections.emptyList(), 1).isEmpty());
        assertTrue(ud.progressByIds(Collections.emptyList()).isEmpty());
        assertTrue(ud.dueProgress(Collections.emptyList(), 100, 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> cd.pack(" "));
        assertThrows(IllegalArgumentException.class, () -> cd.vocabulariesByIds(Arrays.asList("a",null)));
        assertThrows(IllegalArgumentException.class, () -> ud.progressByIds(null));
        assertThrows(IllegalArgumentException.class, () -> ud.newVocabularyIds(Arrays.asList("a"), 0));
        assertThrows(IllegalArgumentException.class, () -> ud.progressByBox(Arrays.asList("a"), 6));
        assertThrows(IllegalArgumentException.class, () -> ud.dueProgress(Arrays.asList("a"), 100, 0));
        assertThrows(IllegalArgumentException.class, () -> ud.learnedCount(100,100));
        assertThrows(IllegalArgumentException.class, () -> ud.quizHistory(200,100,1));
        assertThrows(IllegalArgumentException.class, () -> ud.reviewHistory("a",0,100,-1));
        assertThrows(IllegalArgumentException.class, () -> ud.recordReview(null,null));
    }

    @Test public void savedFlagDoesNotInventProgressOrResetLearning() {
        progress("a", 4, 123, 50L); ud.insertReview(event("a", 60, 4, 123));
        assertEquals(1, ud.setSaved("a", true));
        assertEquals(4, ud.progress("a").box); assertEquals(123, ud.progress("a").dueAt);
        assertEquals(Long.valueOf(50), ud.progress("a").learnedAt);
        assertEquals(1, ud.reviews().size()); assertEquals(0, ud.setSaved("new-a", true));
        assertNull(ud.progress("new-a"));
    }

    @Test public void historyUsesTimestampThenIdAndPreservesRepeatedEvents() {
        long older = ud.insertReview(event("a", 20, 1, 50));
        long newer = ud.insertReview(event("a", 30, 1, 50));
        long tie = ud.insertReview(event("a", 30, 1, 50));
        ud.insertReview(event("b", 35, 1, 50));
        assertEquals(Arrays.asList(tie,newer), ud.reviewHistory("a",20,31,2).stream().map(r -> r.id).collect(Collectors.toList()));
        assertEquals(older, ud.reviewHistory("a",20,30,2).get(0).id);
        assertTrue(ud.reviewHistory("missing",0,100,10).isEmpty());
        long q1 = ud.insertQuiz(quiz(20)); long q2 = ud.insertQuiz(quiz(30)); long q3 = ud.insertQuiz(quiz(30));
        assertEquals(Arrays.asList(q3,q2), ud.quizHistory(20,31,2).stream().map(q -> q.id).collect(Collectors.toList()));
        assertEquals(q1, ud.quizHistory(20,30,2).get(0).id);
        assertTrue(ud.quizHistory(40,50,2).isEmpty());
    }

    @Test public void reviewTransactionRollsBackOnRealSqlFailureAfterInsert() {
        UserWordProgress old = progress("a", 1, 10, 1L);
        user.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_progress BEFORE UPDATE ON UserWordProgress "
                + "BEGIN SELECT RAISE(ABORT, 'test injected progress failure'); END");
        old.dueAt = 99;
        assertThrows(RuntimeException.class, () -> ud.recordReview(event("a",20,1,99), old));
        assertTrue(ud.reviews().isEmpty()); assertEquals(10, ud.progress("a").dueAt);
        user.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER fail_progress");
        ud.recordReview(event("a",20,1,99), old);
        assertEquals(1, ud.reviews().size()); assertEquals(99, ud.progress("a").dueAt);
    }

    @Test public void planningSnapshotAndFileReopenPreserveState() {
        ud.activatePack("A", 9); progress("a", 1, 100, 10L);
        ud.insertQuiz(quiz(80)); ud.insertReview(event("a",70,1,100));
        UserPlanningSnapshot snapshot = ud.planningSnapshot(0,50);
        assertEquals(1, snapshot.activePacks.size()); assertEquals(1, snapshot.progress.size());
        assertEquals(1, snapshot.learnedToday); assertEquals(5, snapshot.preference.dailyNewWords);
        user.close(); user = UserDatabase.open(context,userName); ud = user.userDao();
        assertEquals(9, ud.activePacks().get(0).activatedAt);
        assertEquals(100, ud.progress("a").dueAt);
        assertEquals(1, ud.quizHistory(0,100,10).size()); assertEquals(1, ud.reviewHistory("a",0,100,10).size());
    }

    @Test public void largeIdBatchesMergeBeforeLimitAndStayUnique() {
        List<String> keys = new ArrayList<>();
        user.runInTransaction(() -> {
            for (int i = 0; i < 1005; i++) {
                String id = String.format(Locale.ROOT,"batch-%04d",i); keys.add(id);
                progress(id, i%6, 2000-i, 1L);
            }
        });
        List<String> duplicated = new ArrayList<>(keys); duplicated.addAll(keys);
        assertEquals(1005, ud.progressByIds(duplicated).size());
        assertEquals(1005, ud.existingProgressIds(duplicated).size());
        List<String> newCandidates = new ArrayList<>(duplicated);
        newCandidates.addAll(Arrays.asList("fresh-1", "fresh-1", "fresh-2", "fresh-3"));
        assertEquals(Arrays.asList("fresh-1","fresh-2"), ud.newVocabularyIds(newCandidates, 2));
        assertEquals(Arrays.asList("batch-1004","batch-1003"), ids(ud.dueProgress(duplicated,3000,2)));
        List<String> packs = new ArrayList<>();
        for (int i=0;i<1005;i++) packs.add("unknown-pack-"+i);
        packs.add("A"); packs.add("B"); packs.add("A");
        assertEquals(8, cd.membershipsForPacks(packs).size());
        List<String> catalogIds = new ArrayList<>(keys); catalogIds.add("a"); catalogIds.add("a");
        assertEquals(1, cd.vocabulariesByIds(catalogIds).size());
    }

    @Test public void closedDatabaseErrorsAreNotSilentlyReturnedAsEmpty() {
        user.close();
        assertThrows(RuntimeException.class, () -> ud.progressByIds(Arrays.asList("a")));
        catalog.close();
        assertThrows(RuntimeException.class, () -> cd.vocabulariesByIds(Arrays.asList("a")));
    }
}
