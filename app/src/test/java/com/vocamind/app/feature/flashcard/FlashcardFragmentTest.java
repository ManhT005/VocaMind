package com.vocamind.app.feature.flashcard;

import static org.junit.Assert.*;

import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.R;
import com.vocamind.app.databinding.FragmentFlashcardBinding;
import com.vocamind.app.domain.catalog.CatalogPack;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.learning.*;
import com.vocamind.app.domain.pack.PackState;
import com.vocamind.app.fake.FakePackRepository;
import com.vocamind.app.fake.FakeStudyRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Real Fragment listeners and retained Fragment ViewModel; worker/clock are controlled. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public class FlashcardFragmentTest {
    private final Queue<Runnable> worker = new ArrayDeque<>(), main = new ArrayDeque<>();
    private long now;
    private int writes;
    private FlashcardViewModel model;
    private ActivityController<Host> controller;

    public static class Host extends FragmentActivity {
        @Override public void onCreate(Bundle saved) {
            setTheme(R.style.Theme_VocaMind);
            super.onCreate(saved);
            FrameLayout container = new FrameLayout(this);
            container.setId(android.R.id.content);
            setContentView(container);
        }
    }

    @Before public void open() {
        FakePackRepository packs = new FakePackRepository();
        FakeStudyRepository study = new FakeStudyRepository();
        packs.states.add(new PackState(new CatalogPack("p", "p", "d", "c", 1, "v1", 10), true, 1L, 0));
        for (String id : new String[]{"aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb"}) {
            study.fresh.add(new CatalogVocabulary(id, id, id, "noun", "nghia", "ex", "vd", null, "src", "v1"));
        }
        Clock clock = Clock.systemUTC();
        PlanToday planner = new PlanToday(packs, study, clock, ZoneOffset.UTC);
        model = new FlashcardViewModel(planner::execute, worker::add, main::add, () -> { },
                (item, rating) -> {
                    writes++;
                    return new LeitnerScheduler().schedule(LeitnerState.newWord(), rating, clock.millis(), ZoneOffset.UTC);
                }, () -> now);
        settle();
        controller = Robolectric.buildActivity(Host.class).setup();
        controller.get().getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override public void onFragmentCreated(@NonNull FragmentManager fm,
                                                            @NonNull Fragment f, Bundle saved) {
                        if (f instanceof FlashcardFragment) {
                            new ViewModelProvider(f, new ViewModelProvider.Factory() {
                                @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                                    return type.cast(model);
                                }
                            }).get(FlashcardViewModel.class);
                        }
                    }
                }, false);
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, new FlashcardFragment(), "flash").commitNow();
    }

    @After public void close() { controller.pause().stop().destroy(); }

    private FragmentFlashcardBinding binding() {
        Fragment f = controller.get().getSupportFragmentManager().findFragmentByTag("flash");
        return FragmentFlashcardBinding.bind(f.requireView());
    }

    private void settle() { worker.remove().run(); main.remove().run(); }

    @Test public void realTapInsideScrollViewFlipsCardAndEnablesRatings() {
        FragmentFlashcardBinding b = binding();
        b.card.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        b.card.layout(0, 0, 600, 800);
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 300, 400, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 10, MotionEvent.ACTION_UP, 300, 400, 0);
        try { b.card.dispatchTouchEvent(down); b.card.dispatchTouchEvent(up); }
        finally { down.recycle(); up.recycle(); }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(model.state().getValue().flipped);
        assertEquals(View.VISIBLE, b.cardBack.getVisibility());
        assertTrue(b.rateRemembered.isEnabled());
    }

    @Test public void oldRenderedListenerCannotRateNextCardAfterDebounce() {
        FragmentFlashcardBinding b = binding();
        b.card.performClick();
        View.OnClickListener old = Shadows.shadowOf(b.rateRemembered).getOnClickListener();
        b.rateRemembered.performClick();
        assertFalse(b.rateForgot.isEnabled());
        assertFalse(b.rateHard.isEnabled());
        assertFalse(b.rateRemembered.isEnabled());
        settle();
        b.card.performClick();
        now = 600;
        old.onClick(b.rateRemembered);
        assertTrue(worker.isEmpty());
        assertEquals(1, writes);
        b.rateHard.performClick();
        settle();
        assertEquals(2, writes);
    }

    @Test public void missingIpaHasTheSameReadableFallbackAsVocabularyDetail() {
        FragmentFlashcardBinding b = binding();
        b.card.performClick();
        assertEquals(View.VISIBLE, b.cardIpa.getVisibility());
        assertEquals(controller.get().getString(R.string.word_ipa_missing), b.cardIpa.getText().toString());
        assertEquals(0, writes);
    }

    @Test public void configurationChangeWhileSavingKeepsLockAndOccurrence() {
        FragmentFlashcardBinding b = binding();
        b.card.performClick();
        long first = model.state().getValue().presentationId;
        b.rateRemembered.performClick();
        controller.configurationChange();
        Fragment fragment = controller.get().getSupportFragmentManager().findFragmentByTag("flash");
        assertSame(model, new ViewModelProvider(fragment).get(FlashcardViewModel.class));
        assertEquals(first, model.state().getValue().presentationId);
        assertTrue(model.state().getValue().saving);
        assertTrue(model.state().getValue().flipped);
        assertFalse(binding().rateRemembered.isEnabled());
        now = 1000;
        model.rate(first, Rating.FORGOT);
        assertEquals(1, worker.size());
        settle();
        assertEquals(1, writes);
        assertFalse(model.state().getValue().flipped);
        assertEquals(2, model.state().getValue().position);
    }
}
