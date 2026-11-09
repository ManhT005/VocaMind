package com.vocamind.app.feature.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import androidx.core.view.ViewCompat;
import com.vocamind.app.R;
import com.vocamind.app.databinding.FragmentFlashcardBinding;
import com.vocamind.app.databinding.FragmentPackDetailBinding;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Geometry regressions, not device or TalkBack acceptance evidence. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35}, qualifiers = "mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class ResilienceLayoutTest {
    private LayoutInflater inflater() {
        Context app = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.fontScale = 2f;
        return LayoutInflater.from(new ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_VocaMind));
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    @Test public void flashcardAndRatingsRemainReachableInShortLandscapeWithLargeFont() {
        FragmentFlashcardBinding b = FragmentFlashcardBinding.inflate(inflater());
        b.card.setVisibility(View.VISIBLE);
        b.cardBack.setVisibility(View.VISIBLE);
        b.ratingBar.setVisibility(View.VISIBLE);
        b.resultHint.setVisibility(View.VISIBLE);
        b.resultHint.setText(R.string.flashcard_save_failed);
        b.cardLemma.setText("A long vocabulary word");
        b.cardMeaning.setText("A long meaning that must remain readable without truncation. ".repeat(12));
        measure(b.getRoot(), 568, 240);
        assertTrue("card must not collapse behind rating controls", b.card.getHeight() >= 48);
        for (TextView button : new TextView[]{b.rateForgot, b.rateHard, b.rateRemembered}) {
            assertTrue(button.getHeight() >= 48);
            assertTrue(button.getWidth() >= 48);
            assertEquals(button.length(), button.getLayout().getLineEnd(button.getLayout().getLineCount() - 1));
        }
    }

    @Test public void longPackHeaderDoesNotCollapseTheWordList() {
        FragmentPackDetailBinding b = FragmentPackDetailBinding.inflate(inflater());
        b.detailTitle.setText("A long pack title with multiple lines ".repeat(12));
        b.activation.setText(R.string.pack_activate);
        measure(b.getRoot(), 320, 480);
        assertTrue("words need a viewport even when the header exceeds the screen", b.words.getHeight() >= 48);
        assertTrue("long title must be fully measured", b.detailTitle.getHeight() >= b.detailTitle.getLayout().getHeight());
        assertTrue("scrolling words must be able to move the header off screen",
                ViewCompat.startNestedScroll(b.words, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
        int[] consumed = new int[2];
        ViewCompat.dispatchNestedPreScroll(b.words, 0, 10000, consumed, null, ViewCompat.TYPE_TOUCH);
        assertTrue("header must yield space to the word list", consumed[1] > 0);
        ViewCompat.stopNestedScroll(b.words, ViewCompat.TYPE_TOUCH);
    }

    @Test public void quizOptionsWrapLongMeaningsWithoutEllipsisOnSmallScreen() {
        View root = inflater().inflate(R.layout.fragment_quiz, null);
        String meaning = "A long meaning that must be completely readable. ".repeat(10);
        int[] ids = {R.id.quiz_option_1, R.id.quiz_option_2, R.id.quiz_option_3, R.id.quiz_option_4};
        for (int id : ids) ((TextView) root.findViewById(id)).setText(meaning);
        measure(root, 320, 480);
        for (int id : ids) {
            TextView button = root.findViewById(id);
            assertTrue(button.getHeight() >= 48);
            assertTrue(button.getHeight() >= button.getLayout().getHeight() + button.getCompoundPaddingTop() + button.getCompoundPaddingBottom());
            for (int line = 0; line < button.getLayout().getLineCount(); line++)
                assertEquals(0, button.getLayout().getEllipsisCount(line));
            assertEquals(meaning.length(), button.getLayout().getLineEnd(button.getLayout().getLineCount() - 1));
        }
    }
}
