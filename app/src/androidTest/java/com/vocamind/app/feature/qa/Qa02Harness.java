package com.vocamind.app.feature.qa;

import static org.junit.Assert.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.catalog.upgrade.CatalogStartupCoordinator;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.feature.discovery.DiscoveryHarness;
import com.vocamind.app.feature.flashcard.FlashcardFragment;
import com.vocamind.app.feature.flashcard.FlashcardViewModel;
import com.vocamind.app.feature.quiz.QuizViewModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.*;

/** Reuses UI-02 real-window actions/network checks; never resets learning data in a journey. */
public abstract class Qa02Harness extends DiscoveryHarness {
    public static final String PACKAGE = "com.vocamind.app.qa02validation.qa";
    protected String phase;
    protected final JSONArray timeline = new JSONArray();
    protected String initialCatalog;

    @Override @Before public void isolatedInput() throws Exception {
        Assume.assumeTrue("Requires the dedicated QA-02 catalogQa profile", PACKAGE.equals(context.getPackageName()));
        phase = InstrumentationRegistry.getArguments().getString("qa02Phase", "case");
        user = UserDatabase.open(context);
        if ("probe".equals(phase)) return; // Read existing committed data; no fixture writes.
        assertTrue("Fresh dedicated installation/reset is required", user.userDao().packs().isEmpty());
        assertTrue(user.userDao().allProgress().isEmpty());
        assertTrue(user.userDao().reviews().isEmpty());
        assertTrue(user.userDao().quizzes().isEmpty());
        int originalQuota = user.userDao().preference().dailyNewWords;
        if ("journey".equals(phase)) {
            assertEquals("Verify the real default before the declared QA-only setup", 5, originalQuota);
            var preference = user.userDao().preference();
            assertEquals(10, preference.dailyGoalMinutes);
            assertFalse(preference.reminderEnabled);
            assertEquals(20, preference.reminderHour);
            preference.dailyNewWords = 10;
            user.userDao().savePreference(preference);
        }
        oracleName = "qa02-oracle-" + UUID.randomUUID() + ".db";
        oracle = CatalogDatabase.open(context, oracleName);
        var words = oracle.catalogDao().allVocabulary();
        assertEquals(150, words.size());
        assertEquals(150, words.stream().map(w -> w.id).distinct().count());
        var packs = oracle.catalogDao().packs();
        assertEquals(4, packs.size());
        List<Integer> counts = new ArrayList<>();
        for (var pack : packs) counts.add(oracle.catalogDao().wordCount(pack.id));
        Collections.sort(counts);
        assertEquals(Arrays.asList(30, 40, 40, 40), counts);
        write("fixture.json", new JSONObject().put("package", PACKAGE).put("phase", phase)
                .put("initialPreferenceQuota", originalQuota).put("journeyQuota", user.userDao().preference().dailyNewWords)
                .put("prelaunchLearningData", userdata()).put("words", words.size())
                .put("packMembershipCountsSorted", new JSONArray(counts))
                .put("zone", ZoneId.systemDefault().getId()).put("locale", Locale.getDefault().toLanguageTag())
                .put("setup", "Only quota is configured before the first Activity; no learning rows/packs/attempts seeded"));
    }

    protected File file(String name) {
        if (!name.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("artifact name");
        File folder = new File(context.getFilesDir(), "qa02/" + phase);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Cannot create QA evidence directory");
        return new File(folder, name);
    }

    protected void write(String name, JSONObject value) throws Exception {
        try (var stream = new FileOutputStream(file(name))) {
            stream.write(value.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    protected JSONArray table(CatalogDatabase db, String query) throws Exception {
        try (Cursor cursor = db.getOpenHelper().getReadableDatabase().query(query)) { return rows(cursor); }
    }

    private static JSONArray rows(Cursor cursor) throws Exception {
        JSONArray out = new JSONArray();
        while (cursor.moveToNext()) {
            JSONObject row = new JSONObject();
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                Object value = cursor.isNull(i) ? JSONObject.NULL
                        : cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER ? cursor.getLong(i)
                        : cursor.getType(i) == Cursor.FIELD_TYPE_FLOAT ? cursor.getDouble(i) : cursor.getString(i);
                row.put(cursor.getColumnName(i), value);
            }
            out.put(row);
        }
        return out;
    }

    protected JSONObject userdata() {
        return user.runInTransaction(() -> {
            JSONObject out = new JSONObject();
            var sql = user.getOpenHelper().getReadableDatabase();
            for (String[] table : new String[][]{{"UserPreference", "id"}, {"UserPack", "packId"},
                    {"UserWordProgress", "vocabularyId"}, {"ReviewEvent", "id"}, {"QuizAttempt", "id"},
                    {"sqlite_sequence", "name"}}) {
                try (Cursor cursor = sql.query("SELECT * FROM " + table[0] + " ORDER BY " + table[1])) {
                    out.put(table[0], rows(cursor));
                }
            }
            try (Cursor cursor = sql.query("PRAGMA user_version")) { cursor.moveToFirst(); out.put("schemaVersion", cursor.getInt(0)); }
            return out;
        });
    }

    protected static String canonical(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>(); object.keys().forEachRemaining(keys::add); Collections.sort(keys);
            List<String> fields = new ArrayList<>();
            for (String key : keys) fields.add(JSONObject.quote(key) + ":" + canonical(object.get(key)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof JSONArray) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < ((JSONArray) value).length(); i++) items.add(canonical(((JSONArray) value).get(i)));
            return "[" + String.join(",", items) + "]";
        }
        if (value instanceof String) return JSONObject.quote((String) value);
        return String.valueOf(value);
    }

    protected String catalog(CatalogDatabase db) throws Exception {
        JSONObject out = new JSONObject();
        for (String[] entry : new String[][]{{"ContentSource", "id"}, {"VocabularyEntry", "id"},
                {"LearningPack", "id"}, {"PackWord", "packId,vocabularyId"}}) {
            out.put(entry[0], table(db, "SELECT * FROM " + entry[0] + " ORDER BY " + entry[1]));
        }
        return canonical(out);
    }

    protected void assertRuntimeCatalogUnchanged() throws Exception {
        CatalogDatabase actual = CatalogStartupCoordinator.bootstrap(context).openDatabase(context);
        try { assertEquals(initialCatalog, catalog(actual)); }
        finally { actual.close(); }
    }

    protected void checkpoint(String label, JSONObject facts) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        var active = automation.getRootInActiveWindow();
        assertNotNull("No active accessibility window for evidence", active);
        assertEquals("Do not capture unrelated app/notification content", PACKAGE, String.valueOf(active.getPackageName()));
        Bitmap image = automation.takeScreenshot(); assertNotNull(image);
        try (var stream = new FileOutputStream(file(label + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { image.recycle(); }
        facts.put("checkpoint", label).put("utcMillis", System.currentTimeMillis())
                .put("zone", ZoneId.systemDefault().getId()).put("userdata", userdata());
        timeline.put(facts); write("timeline.json", new JSONObject().put("checkpoints", timeline));
    }

    protected void tapScroll(int id) {
        boolean[] displayed = {false};
        scenario.onActivity(a -> displayed[0] = isDisplayingAtLeast(90).matches(a.findViewById(id)));
        if (!displayed[0]) onView(withId(id)).perform(scrollTo());
        tap(id);
    }

    protected void rotateAndRestore() throws Exception {
        scenario.onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        await("actual landscape configuration", a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        scenario.onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await("actual portrait configuration", a -> a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
    }

    protected FlashcardViewModel.State flash(MainActivity activity) {
        var host = (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        var current = host.getChildFragmentManager().getPrimaryNavigationFragment();
        return current instanceof FlashcardFragment ? new ViewModelProvider(current).get(FlashcardViewModel.class).state().getValue() : null;
    }

    protected FlashcardViewModel.State flash() {
        AtomicReference<FlashcardViewModel.State> out = new AtomicReference<>(); scenario.onActivity(a -> out.set(flash(a))); return out.get();
    }

    protected QuizViewModel.State quiz() {
        AtomicReference<QuizViewModel.State> out = new AtomicReference<>();
        scenario.onActivity(a -> { if (a.findViewById(R.id.quiz_status) != null) out.set(new ViewModelProvider(a).get(QuizViewModel.class).state().getValue()); });
        return out.get();
    }

    protected void debounceWindow(long previousTap) throws Exception {
        long limit = android.os.SystemClock.elapsedRealtime() + 2000;
        while (android.os.SystemClock.elapsedRealtime() - previousTap < 650) {
            if (android.os.SystemClock.elapsedRealtime() > limit) fail("Monotonic clock did not advance");
            new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS);
        }
    }

    protected void visibleListChild(int listId, int targetId) throws Exception {
        for (int i = 0; i < 12; i++) {
            boolean[] shown = {false}, above = {false};
            scenario.onActivity(a -> {
                View target = a.findViewById(targetId), list = a.findViewById(listId);
                if (target == null || list == null) return;
                Rect visible = new Rect(); shown[0] = target.getGlobalVisibleRect(visible) && visible.height() >= target.getHeight() * .9;
                int[] point = new int[2], viewport = new int[2]; target.getLocationOnScreen(point); list.getLocationOnScreen(viewport);
                above[0] = point[1] < viewport[1];
            });
            if (shown[0]) return;
            onView(withId(listId)).perform(above[0] ? swipeDown() : swipeUp());
        }
        fail("Required list content never became readable: " + targetId);
    }

    protected void saveExpected() throws Exception {
        File target = new File(context.getFilesDir(), "qa02/expected-userdata.json");
        try (var stream = new FileOutputStream(target)) { stream.write(userdata().toString(2).getBytes(StandardCharsets.UTF_8)); }
        write("expected-meta.json", new JSONObject().put("snapshotSha256", digest(canonical(userdata())))
                .put("logicalCatalogSha256", digest(initialCatalog))
                .put("reviewCount", user.userDao().reviews().size()).put("quizCount", user.userDao().quizzes().size()));
    }

    protected static String digest(String text) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
}
