package com.vocamind.app.feature.qa;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.feature.discovery.DiscoveryHarness;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;

/** Controlled four-word packaged fixture; production planner and real user input. */
public final class Qa02SharedPackInstrumentedTest extends DiscoveryHarness {
    private void tapScroll(int id) {
        boolean[] displayed={false};
        scenario.onActivity(a -> displayed[0]=isDisplayingAtLeast(90).matches(a.findViewById(id)));
        if (!displayed[0]) onView(withId(id)).perform(androidx.test.espresso.action.ViewActions.scrollTo());
        tap(id);
    }
    private JSONObject snapshot() {
        return user.runInTransaction(() -> {
            var out=new JSONObject(); var sql=user.getOpenHelper().getReadableDatabase();
            for (String[] table:new String[][]{{"UserPreference","id"},{"UserPack","packId"},
                    {"UserWordProgress","vocabularyId"},{"ReviewEvent","id"},{"QuizAttempt","id"},{"sqlite_sequence","name"}}) {
                var rows=new JSONArray();
                try (var cursor=sql.query("SELECT * FROM "+table[0]+" ORDER BY "+table[1])) {
                    while (cursor.moveToNext()) {
                        var row=new JSONObject();
                        for (int i=0;i<cursor.getColumnCount();i++) row.put(cursor.getColumnName(i),
                                cursor.isNull(i) ? JSONObject.NULL : cursor.getType(i)==android.database.Cursor.FIELD_TYPE_INTEGER
                                        ? cursor.getLong(i) : cursor.getString(i));
                        rows.put(row);
                    }
                }
                out.put(table[0],rows);
            }
            return out;
        });
    }
    @Override @Before public void isolatedInput() throws Exception {
        Assume.assumeTrue("Requires owned flash03 synthetic profile",context.getPackageName().equals("com.vocamind.app.flash03validation"));
        user = UserDatabase.open(context);
        assertTrue(user.userDao().packs().isEmpty()); assertTrue(user.userDao().reviews().isEmpty());
        assertTrue(user.userDao().allProgress().isEmpty());
        oracleName = "qa02-overlap-"+UUID.randomUUID()+".db"; oracle = CatalogDatabase.open(context,oracleName);
        assertEquals(4,oracle.catalogDao().allVocabulary().size());
    }
    @Test public void overlappingActivePacksDoNotDuplicateNewCards() throws Exception {
        var packs = oracle.catalogDao().packs(); var first = oracle.catalogDao().words(packs.get(0).id);
        Set<String> firstIds = new HashSet<>(), secondIds = new HashSet<>();
        for (var w : first) firstIds.add(w.id);
        for (var w : oracle.catalogDao().words(packs.get(1).id)) secondIds.add(w.id);
        assertEquals(4,firstIds.size()); assertEquals(firstIds,secondIds);
        assertEquals(5,user.userDao().preference().dailyNewWords);
        var preference = user.userDao().preference(); preference.dailyNewWords = 8; user.userDao().savePreference(preference);
        for (int i=0;i<2;i++) user.userDao().activatePack(packs.get(i).id,System.currentTimeMillis());
        var folder = new java.io.File(context.getFilesDir(),"qa02"); assertTrue(folder.isDirectory() || folder.mkdirs());
        var fixture = new JSONObject().put("setup","two active packs with identical four stableIds, eight memberships; quota8 exceeds distinct pool so truncation cannot conceal duplicates; empty progress/reviews")
                .put("expectedIds",new JSONArray(firstIds)).put("before",snapshot());
        try (var stream = new java.io.FileOutputStream(new java.io.File(folder,"overlap-fixture.json"))) {
            stream.write(fixture.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        launch(); await("Home",a -> visible(a,R.id.open_today)); tapScroll(R.id.open_today);
        await("unique new4",a -> text(a,R.id.summary,a.getString(R.string.today_summary,0,4))); tapScroll(R.id.start);
        long last = -10000;
        for (int i=0;i<4;i++) {
            final int position=i+1;
            await("card position",a -> text(a,R.id.progress_label,a.getString(R.string.flashcard_progress,position,4)));
            onView(withId(R.id.card_lemma)).check(matches(withText(first.get(i).lemma)));
            tapScroll(R.id.card); await("answer",a -> visible(a,R.id.rate_remembered));
            onView(withId(R.id.card_meaning)).check(matches(withText(first.get(i).meaningVi)));
            while (android.os.SystemClock.elapsedRealtime()-last<650) new CountDownLatch(1).await(20,TimeUnit.MILLISECONDS);
            tapScroll(R.id.rate_remembered); last=android.os.SystemClock.elapsedRealtime();
        }
        await("finished",a -> text(a,R.id.message,a.getString(R.string.flashcard_finished)));
        assertEquals(4,user.userDao().reviews().size()); assertEquals(4,user.userDao().allProgress().size());
        Set<String> reviewed=new HashSet<>(); for (var r:user.userDao().reviews()) reviewed.add(r.vocabularyId);
        assertEquals(firstIds,reviewed);
        fixture.put("reviewedIds",new JSONArray(reviewed)).put("reviews",4).put("progress",4).put("after",snapshot());
        try (var stream = new java.io.FileOutputStream(new java.io.File(folder,"overlap-result.json"))) {
            stream.write(fixture.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
