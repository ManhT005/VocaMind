package com.vocamind.app.feature.flashcard;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.vocamind.app.R;
import com.vocamind.app.core.FlashcardViewModelFactory;
import com.vocamind.app.databinding.FragmentFlashcardBinding;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.learning.ScheduleResult;

/** Front shows only the word; the answer and the three rating buttons appear after the flip. */
public class FlashcardFragment extends Fragment {
    public FlashcardFragment() { super(R.layout.fragment_flashcard); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentFlashcardBinding binding = FragmentFlashcardBinding.bind(view);
        FlashcardViewModel model = new ViewModelProvider(this,
                new FlashcardViewModelFactory(requireContext())).get(FlashcardViewModel.class);
        ViewCompat.setAccessibilityHeading(binding.title, true);
        binding.back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.retry.setOnClickListener(v -> model.retry());
        binding.card.setOnClickListener(v -> model.flip());
        model.state().observe(getViewLifecycleOwner(), state -> render(binding, model, state));
    }

    private void render(FragmentFlashcardBinding b, FlashcardViewModel model, FlashcardViewModel.State state) {
        boolean ready = state.status == FlashcardViewModel.Status.READY;
        b.loading.setVisibility(state.status == FlashcardViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
        b.card.setVisibility(ready ? View.VISIBLE : View.GONE);
        b.ratingBar.setVisibility(ready ? View.VISIBLE : View.GONE);
        b.progressLabel.setText(ready ? getString(R.string.flashcard_progress, state.position, state.total) : "");

        int message = messageFor(state.status);
        b.statePanel.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) b.message.setText(message);
        b.retry.setVisibility(state.status == FlashcardViewModel.Status.ERROR ? View.VISIBLE : View.GONE);
        b.resultHint.setText(hintFor(state));
        b.resultHint.setVisibility(b.resultHint.length() == 0 ? View.GONE : View.VISIBLE);
        if (!ready) return;

        CatalogVocabulary word = state.card.getVocabulary();
        b.cardLemma.setText(word.lemma);
        b.cardPos.setText(word.pos);
        b.cardHint.setVisibility(state.flipped ? View.GONE : View.VISIBLE);
        b.cardBack.setVisibility(state.flipped ? View.VISIBLE : View.GONE);
        b.cardMeaning.setText(word.meaningVi);
        b.cardExampleEn.setText(word.exampleEn);
        b.cardExampleVi.setText(word.exampleVi);
        boolean hasIpa = word.ipa != null && !word.ipa.trim().isEmpty();
        b.cardIpa.setVisibility(hasIpa ? View.VISIBLE : View.GONE);
        b.cardIpa.setText(word.ipa);
        b.card.setContentDescription(state.flipped ? null : getString(R.string.flashcard_flip_action));
        // Capture the occurrence rendered by this view, never look up the current ID at click time.
        b.rateForgot.setOnClickListener(v -> model.rate(state.presentationId, Rating.FORGOT));
        b.rateHard.setOnClickListener(v -> model.rate(state.presentationId, Rating.HARD));
        b.rateRemembered.setOnClickListener(v -> model.rate(state.presentationId, Rating.REMEMBERED));
        b.rateForgot.setEnabled(state.ratingEnabled());
        b.rateHard.setEnabled(state.ratingEnabled());
        b.rateRemembered.setEnabled(state.ratingEnabled());
    }

    private String hintFor(FlashcardViewModel.State state) {
        if (state.saveFailed) return getString(R.string.flashcard_save_failed);
        ScheduleResult result = state.lastResult;
        if (result == null) return "";
        if (result.becameLeech()) return getString(R.string.flashcard_leech);
        return getString(R.string.flashcard_result, result.getBoxAfter(),
                DateUtils.formatDateTime(requireContext(), result.getDueAtEpochMillis(), DateUtils.FORMAT_SHOW_DATE));
    }

    private static int messageFor(FlashcardViewModel.Status status) {
        switch (status) {
            case NO_ACTIVE_PACK: return R.string.flashcard_no_pack;
            case NOTHING_TO_STUDY: return R.string.flashcard_nothing;
            case FINISHED: return R.string.flashcard_finished;
            case ERROR: return R.string.flashcard_error;
            default: return 0;
        }
    }
}
