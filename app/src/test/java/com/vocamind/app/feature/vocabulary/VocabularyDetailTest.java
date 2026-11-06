package com.vocamind.app.feature.vocabulary;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelStore;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.R;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.*;
import com.vocamind.app.domain.vocabulary.VocabularyDetail;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public class VocabularyDetailTest {
    private VocabularyDetail detail(String id, String ipa, boolean saved) {
        return new VocabularyDetail(new CatalogVocabulary(id, "lemma " + id, "lemma", "verb", "Nghĩa " + id,
                "English example " + id, "Ví dụ " + id, ipa, "source", "1"), saved);
    }
    private String text(View v, int id) { return ((TextView)v.findViewById(id)).getText().toString(); }

    @Test public void renderAllFieldsNullBlankIpaAndReplacePreviousWordWithoutInventingContent() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VocaMind);
        View view = LayoutInflater.from(context).inflate(R.layout.fragment_vocabulary_detail, new FrameLayout(context), false);
        for (String ipa : new String[]{"/test/", null, "  "}) {
            var detail = detail("ffffffffffffffff", ipa, false);
            VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(detail, false, false, null));
            assertEquals(detail.word.lemma, text(view, R.id.detail_lemma));
            assertEquals("verb", text(view, R.id.detail_pos));
            assertEquals(detail.word.meaningVi, text(view, R.id.detail_meaning));
            assertEquals(detail.word.exampleEn, text(view, R.id.detail_example_en));
            assertEquals(detail.word.exampleVi, text(view, R.id.detail_example_vi));
            assertEquals(ipa == null || ipa.trim().isEmpty() ? "Chưa có phiên âm" : ipa, text(view, R.id.detail_ipa));
            assertEquals("Lưu", text(view, R.id.detail_save));
        }
        VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(detail("second", null, true), false, false, null));
        assertEquals("Nghĩa second", text(view, R.id.detail_meaning));
        assertEquals("Bỏ lưu", text(view, R.id.detail_save));
        VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(detail("second", null, true), false, true, null));
        assertFalse(view.findViewById(R.id.detail_save).isEnabled());
        VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(null, false, false, RepoError.NOT_FOUND));
        assertEquals(View.GONE, view.findViewById(R.id.detail_content).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.detail_save).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.detail_retry).getVisibility());
        VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(null, false, false, RepoError.READ_FAILED));
        assertEquals(View.VISIBLE, view.findViewById(R.id.detail_retry).getVisibility());
        VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(detail("second", null, false), false, false, RepoError.SAVED_POLICY_REQUIRED));
        assertEquals(View.GONE, view.findViewById(R.id.detail_retry).getVisibility());
        assertFalse(view.findViewById(R.id.detail_save).isEnabled());
        var missing = new VocabularyDetail(new CatalogVocabulary("missing", "m", "m", "noun", null, "", " ", null, "s", "1"), false);
        VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(missing, false, false, null));
        assertEquals("Chưa có dữ liệu", text(view, R.id.detail_meaning));
        assertEquals("Chưa có dữ liệu", text(view, R.id.detail_example_en));
        assertEquals("Chưa có dữ liệu", text(view, R.id.detail_example_vi));
    }

    @Test public void pendingBlocksDoubleTapAndResumeRetryReconcilesAmbiguousCommitWithoutReplay() {
        Queue<Runnable> worker = new ArrayDeque<>(), main = new ArrayDeque<>();
        boolean[] persisted = {false}; int[] writes = {0}; boolean[] readFailure = {false};
        var model = new VocabularyDetailViewModel(() -> readFailure[0] ? RepoResult.failure(RepoError.READ_FAILED)
                : RepoResult.success(detail("id", null, persisted[0])), target -> {
            writes[0]++; persisted[0] = target;
            return RepoResult.failure(RepoError.WRITE_FAILED); // fake ambiguous completion, reconcile only
        }, worker::add, main::add, () -> {});
        worker.remove().run(); main.remove().run();
        model.toggleSaved(); model.toggleSaved(); model.refresh();
        assertTrue(model.state().getValue().pending); assertEquals(1, worker.size());
        worker.remove().run(); main.remove().run();
        assertEquals(RepoError.WRITE_FAILED, model.state().getValue().error);
        model.toggleSaved(); assertEquals(1, writes[0]);
        readFailure[0] = true; model.refresh(); worker.remove().run(); main.remove().run();
        assertEquals(RepoError.READ_FAILED, model.state().getValue().error);
        readFailure[0] = false; model.refresh(); worker.remove().run(); main.remove().run();
        assertTrue(model.state().getValue().detail.saved); assertEquals(1, writes[0]);
        // Fresh model (process recreation equivalent at state boundary) reads persisted flag.
        var reopened = new VocabularyDetailViewModel(() -> RepoResult.success(detail("id", null, persisted[0])),
                target -> { fail("must not replay save"); return null; }, worker::add, main::add, () -> {});
        worker.remove().run(); main.remove().run();
        assertTrue(reopened.state().getValue().detail.saved);
    }

    @Test public void refreshGenerationAndClearedOwnerRejectLateReadsAndWrites() {
        Queue<Runnable> worker = new ArrayDeque<>(), main = new ArrayDeque<>();
        int[] reads = {0}; boolean[] closed = {false};
        var model = new VocabularyDetailViewModel(() -> RepoResult.success(detail("id" + ++reads[0], null, false)),
                RepoResult::success, worker::add, main::add, () -> closed[0] = true);
        worker.remove().run(); model.refresh(); main.remove().run();
        assertTrue(model.state().getValue().loading);
        worker.remove().run(); main.remove().run();
        assertEquals("id2", model.state().getValue().detail.word.stableId);
        model.toggleSaved(); worker.remove().run();
        ViewModelStore store = new ViewModelStore(); store.put("detail", model); store.clear();
        main.remove().run();
        assertTrue(model.state().getValue().pending); // late result ignored after owner destruction
        worker.remove().run(); assertTrue(closed[0]);
        model.refresh(); assertTrue(worker.isEmpty());
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void longExamplesRemainScrollableAtLargeFontOnSmallScreen() {
        Context base = ApplicationProvider.getApplicationContext();
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        config.fontScale = 1.5f;
        Context context = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_VocaMind);
        View view = LayoutInflater.from(context).inflate(R.layout.fragment_vocabulary_detail, new FrameLayout(context), false);
        String longText = "Synthetic long example for layout verification. ".repeat(80);
        var word = new CatalogVocabulary("long", "long", "long", "noun", longText, longText, longText, null, "s", "1");
        VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(new VocabularyDetail(word, false), false, false, null));
        int width = Math.round(320 * context.getResources().getDisplayMetrics().density);
        int height = Math.round(480 * context.getResources().getDisplayMetrics().density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        assertEquals(longText, text(view, R.id.detail_example_vi));
        TextView example = view.findViewById(R.id.detail_example_vi);
        assertTrue(example.getLineCount() > 2);
        assertNull(example.getEllipsize());
        ScrollView scroll = view.findViewById(R.id.detail_scroll);
        assertTrue(scroll.getChildAt(0).getHeight() > scroll.getHeight());
        assertTrue(view.findViewById(R.id.detail_save).getBottom() <= height);
        assertTrue(scroll.getBottom() <= ((View)view.findViewById(R.id.detail_save).getParent()).getTop());
        assertTrue(view.findViewById(R.id.back).getMeasuredHeight() >= 48 * context.getResources().getDisplayMetrics().density);
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void captureDetailLayoutsWithFixedActionAndPolicyFeedback() throws Exception {
        Context base = ApplicationProvider.getApplicationContext();
        for (boolean night : new boolean[]{false, true}) {
            android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? android.content.res.Configuration.UI_MODE_NIGHT_YES : android.content.res.Configuration.UI_MODE_NIGHT_NO);
            Context context = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_VocaMind);
            View view = LayoutInflater.from(context).inflate(R.layout.fragment_vocabulary_detail, new FrameLayout(context), false);
            // Synthetic UI fixture matches the supplied screenshot; not an assertion of catalog QA.
            var word = new CatalogVocabulary("fixture", "book", "book", "noun", "cuốn sách", "I put the book on the desk.", "Tôi đặt cuốn sách lên bàn.", null, "test", "1");
            for (boolean blocked : new boolean[]{false, true}) {
                VocabularyDetailFragment.render(view, new VocabularyDetailViewModel.State(new VocabularyDetail(word, false), false, false,
                        blocked ? RepoError.SAVED_POLICY_REQUIRED : null));
                int width = Math.round(393 * context.getResources().getDisplayMetrics().density);
                int height = Math.round(780 * context.getResources().getDisplayMetrics().density);
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                view.layout(0, 0, width, height);
                assertEquals(blocked ? View.VISIBLE : View.GONE, view.findViewById(R.id.route_message).getVisibility());
                assertEquals(View.GONE, view.findViewById(R.id.detail_retry).getVisibility());
                assertEquals(!blocked, view.findViewById(R.id.detail_save).isEnabled());
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
                view.draw(new android.graphics.Canvas(bitmap));
                java.io.File folder = new java.io.File("build/reports/vocab-02-ui");
                assertTrue(folder.isDirectory() || folder.mkdirs());
                java.io.File file = new java.io.File(folder, "detail-" + android.os.Build.VERSION.SDK_INT + (night ? "-dark" : "-light") + (blocked ? "-blocked" : "-ready") + ".png");
                try (var stream = new java.io.FileOutputStream(file)) { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)); }
                bitmap.recycle();
            }
        }
    }
}
