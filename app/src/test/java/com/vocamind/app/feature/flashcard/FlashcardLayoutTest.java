package com.vocamind.app.feature.flashcard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.R;
import com.vocamind.app.databinding.FragmentFlashcardBinding;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class FlashcardLayoutTest {
    private FragmentFlashcardBinding inflate() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_VocaMind);
        return FragmentFlashcardBinding.inflate(LayoutInflater.from(context));
    }

    @Test public void ratingButtonsStartDisabledAndAreAtLeast48dp() {
        FragmentFlashcardBinding b = inflate();
        float density = b.getRoot().getResources().getDisplayMetrics().density;
        for (View button : new View[] {b.rateForgot, b.rateHard, b.rateRemembered}) {
            assertFalse(button.isEnabled());
            assertTrue(((com.google.android.material.button.MaterialButton) button).getMinHeight() >= 48 * density);
        }
    }

    @Test public void answerSideStartsHidden() {
        FragmentFlashcardBinding b = inflate();
        assertEquals(View.GONE, b.cardBack.getVisibility());
        assertEquals(View.VISIBLE, b.cardHint.getVisibility());
    }
}
