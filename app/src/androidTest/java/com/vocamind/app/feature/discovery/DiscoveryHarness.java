package com.vocamind.app.feature.discovery;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import com.vocamind.app.data.catalog.CatalogDatabase;
import com.vocamind.app.data.user.UserDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Device-only helpers. No navigation/VM calls stand in for user actions. */
public abstract class DiscoveryHarness {
    protected final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    protected ActivityScenario<MainActivity> scenario;
    protected CatalogDatabase oracle;
    protected UserDatabase user;
    protected String oracleName;
    private String connectivityBeforeLaunch = "";
    @Rule public final TestRule evidence = (base, description) -> new Statement() {
        @Override public void evaluate() throws Throwable {
            try { base.evaluate(); }
            catch (org.junit.AssumptionViolatedException skipped) { throw skipped; }
            catch (Throwable failure) { capture(description, failure); throw failure; }
            finally {
                if (scenario != null) scenario.close();
                if (oracle != null) oracle.close();
                if (user != null) user.close();
                if (oracleName != null) context.deleteDatabase(oracleName);
            }
        }
    };
    @Before public void isolatedInput() throws Exception {
        assertTrue("Refuse to reset personal/other QA installs", context.getPackageName().equals("com.vocamind.app.ui02validation")
                || context.getPackageName().equals("com.vocamind.app.ui02validation.qa"));
        assertOffline();
        user = UserDatabase.open(context); // instrumentation thread, never main
        user.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM ReviewEvent");
        user.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM UserWordProgress");
        user.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM UserPack");
        user.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM QuizAttempt");
        oracleName = "ui02-oracle-" + java.util.UUID.randomUUID() + ".db";
        oracle = CatalogDatabase.open(context, oracleName);
        oracle.getOpenHelper().getReadableDatabase();
        assertTrue(oracle.catalogDao().packs().size() >= 2);
        try (var meta = context.getAssets().open("database/catalog.metadata.json");
             var asset = context.getAssets().open("database/catalog.db")) {
            var metadata = new org.json.JSONObject(new String(bytes(meta), StandardCharsets.UTF_8));
            var hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes(asset));
            StringBuilder hex = new StringBuilder(); for (byte b : hash) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            assertEquals(metadata.getString("sha256"),hex.toString());
            if (context.getPackageName().equals("com.vocamind.app.ui02validation")) {
                assertEquals("synthetic-test-fixture-ui02-v1",metadata.getString("kind"));
                assertEquals(java.util.List.of("90562a98e8686267","d17314a48504e2ac","5aecfbf5b9a23aee","923d45253cc2c67c"),
                        oracle.catalogDao().words("pack_toeic_basic").stream().map(w -> w.id).collect(java.util.stream.Collectors.toList()));
            }
        }
    }
    private static byte[] bytes(InputStream input) throws IOException {
        var out = new ByteArrayOutputStream(); byte[] block = new byte[8192]; int n;
        while ((n=input.read(block)) != -1) out.write(block,0,n); return out.toByteArray();
    }
    private void assertOffline() {
        assertEquals("Airplane mode before launch", 1,
                Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, -1));
        assertEquals("Wi-Fi flag before launch", 0,
                Settings.Global.getInt(context.getContentResolver(), "wifi_on", -1));
        assertEquals("Mobile data policy before launch", 0,
                Settings.Global.getInt(context.getContentResolver(), "mobile_data", -1));
        var descriptor = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("dumpsys connectivity");
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            connectivityBeforeLaunch = new String(bytes(input), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new AssertionError("Cannot collect offline connectivity evidence", error); }
        var defaults = java.util.regex.Pattern.compile("(?m)^\\s*Active default network:\\s*(\\S+)\\s*$")
                .matcher(connectivityBeforeLaunch);
        assertTrue("Missing active default network evidence", defaults.find());
        assertEquals("Active default network before launch", "none", defaults.group(1));
        assertFalse("Ambiguous active default network evidence", defaults.find());
    }
    protected void launch() { assertOffline(); scenario = ActivityScenario.launch(MainActivity.class); }
    protected void reopen() { scenario.close(); launch(); }
    protected void await(String label, Predicate<MainActivity> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            boolean[] ready = {false};
            scenario.onActivity(a -> ready[0] = condition.test(a));
            if (ready[0]) return;
            new CountDownLatch(1).await(25, TimeUnit.MILLISECONDS); // bounded condition polling, runner thread
        }
        fail("Deadline waiting for " + label);
    }
    protected static boolean visible(MainActivity a, int id) {
        View v = a.findViewById(id); return v != null && v.isShown();
    }
    protected static boolean text(MainActivity a, int id, String value) {
        View v = a.findViewById(id); return v instanceof TextView && ((TextView)v).getText().toString().equals(value);
    }
    protected void tap(int id) { onView(withId(id)).perform(click()); }
    protected androidx.test.espresso.ViewInteraction onView(org.hamcrest.Matcher<View> matcher) {
        View[] decor = new View[1]; scenario.onActivity(a -> decor[0]=a.getWindow().getDecorView());
        org.hamcrest.Matcher<View> focusedWindow = new org.hamcrest.TypeSafeMatcher<>() {
            @Override public void describeTo(org.hamcrest.Description d) { d.appendText("belongs to focused window"); }
            @Override protected boolean matchesSafely(View view) { return view.hasWindowFocus(); }
        };
        return androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(matcher, focusedWindow)).inRoot(
                androidx.test.espresso.matcher.RootMatchers.withDecorView(org.hamcrest.Matchers.is(decor[0])));
    }
    protected void query(String input) throws Exception {
        await("search input", a -> visible(a, R.id.search));
        onView(isAssignableFrom(android.widget.EditText.class)).perform(replaceText(input), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
    }
    protected void message(int string) throws Exception {
        await("search message " + string, a -> text(a, R.id.search_message, a.getString(string)));
    }
    protected void assertHidden(int id) {
        scenario.onActivity(a -> assertFalse("View should be hidden: " + id, visible(a,id)));
    }
    protected void listCount(int id, int count) throws Exception {
        await("list count " + count, a -> {
            RecyclerView list = a.findViewById(id);
            return list != null && list.isShown() && list.getAdapter().getItemCount() == count
                    && !list.isComputingLayout() && !list.hasPendingAdapterUpdates() && !list.isAnimating();
        });
    }
    protected void row(int listId, int position, String meaning, boolean clickRow) throws Exception {
        // Scroll the real RecyclerView, then act on the attached visible item's text.
        scenario.onActivity(a -> {
            RecyclerView list = a.findViewById(listId);
            if (listId == R.id.words) {
                // On the compact API35 CI display the expanded pack header leaves the
                // final row shorter than Espresso's 90% click constraint. Collapse the
                // real scrolling app bar before positioning the row, as a user scroll
                // through the list does.
                com.google.android.material.appbar.AppBarLayout header = a.findViewById(R.id.pack_header);
                if (header != null) header.setExpanded(false, false);
            }
            // scrollToPosition only guarantees some pixels are visible. After returning
            // from detail, API35 can keep the target meaning clipped at the viewport edge.
            ((androidx.recyclerview.widget.LinearLayoutManager)list.getLayoutManager())
                    .scrollToPositionWithOffset(position, 0);
        });
        await("attached row " + position, a -> {
            RecyclerView list = a.findViewById(listId);
            if (list.isComputingLayout() || list.hasPendingAdapterUpdates() || list.isAnimating()) return false;
            var holder = list.findViewHolderForAdapterPosition(position);
            if (holder == null || !holder.itemView.isShown()) return false;
            TextView label = holder.itemView.findViewById(R.id.word_meaning);
            return label != null && label.getText().toString().equals(meaning)
                    && isDisplayingAtLeast(90).matches(label);
        });
        var item = onView(allOf(withId(R.id.word_meaning), withText(meaning), isDescendantOfA(withId(listId))));
        // Match Espresso click's visibility constraint, including after returning from detail.
        item.check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayingAtLeast(90)));
        if (clickRow) item.perform(click());
    }
    private void capture(Description d, Throwable failure) {
        File dir = new File(context.getFilesDir(), "ui02-failures/" + d.getMethodName());
        java.util.List<String> errors = new java.util.ArrayList<>();
        try {
            if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("Artifact directory");
        } catch (Exception error) { artifactError(failure, errors, "directory", error); return; }
        try {
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if (screenshot == null) throw new IOException("Screenshot unavailable");
            try (var out = new FileOutputStream(new File(dir,"screenshot.png"))) {
                if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG compression failed");
            } finally { screenshot.recycle(); }
        } catch (Exception error) { artifactError(failure, errors, "screenshot", error); }
        try (var out = new FileOutputStream(new File(dir,"logcat.txt"))) {
            var descriptor = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("logcat -d -v threadtime");
            // The AutoCloseInputStream owns the descriptor. Do not also close it as a second resource.
            try (var input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                byte[] buffer = new byte[8192]; int n; while ((n=input.read(buffer)) != -1) out.write(buffer,0,n);
            }
        } catch (Exception error) { artifactError(failure, errors, "logcat", error); }
        // Preserve metadata even when another collection stage fails, so CI has a concrete cause.
        try {
            try (var out = new FileOutputStream(new File(dir,"connectivity-before-launch.txt"))) {
                out.write(connectivityBeforeLaunch.getBytes(StandardCharsets.UTF_8));
            }
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            org.json.JSONObject meta = new org.json.JSONObject().put("test",d.getClassName()+"#"+d.getMethodName())
                    .put("api",android.os.Build.VERSION.SDK_INT).put("fingerprint",android.os.Build.FINGERPRINT)
                    .put("package",context.getPackageName()).put("utcMillis",System.currentTimeMillis())
                    .put("testedRevision",InstrumentationRegistry.getArguments().getString("testedRevision","UNSPECIFIED"))
                    .put("phase", "before Activity teardown").put("artifactErrors", new org.json.JSONArray(errors))
                    .put("failure",trace.toString());
            try (var out = new FileOutputStream(new File(dir,"metadata.json"))) { out.write(meta.toString(2).getBytes(StandardCharsets.UTF_8)); }
        } catch (Exception error) { artifactError(failure, errors, "metadata", error); }
    }
    private static void artifactError(Throwable failure, java.util.List<String> errors, String stage, Exception error) {
        errors.add(stage + ": " + error); failure.addSuppressed(error);
        android.util.Log.e("UI02Evidence", "Failure collecting " + stage, error);
    }
}
