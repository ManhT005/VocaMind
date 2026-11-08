package com.vocamind.app.feature.flashcard;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.vocamind.app.R;
import com.vocamind.app.core.FlashcardViewModelFactory;
import com.vocamind.app.databinding.FragmentFlashcardBinding;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.learning.Rating;

/** Front shows only the word; the answer and the three rating buttons appear after the flip. */
public class FlashcardFragment extends Fragment {
    public FlashcardFragment() { super(R.layout.fragment_flashcard); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentFlashcardBinding binding = FragmentFlashcardBinding.bind(view);
        FlashcardViewModel model = new ViewModelProvider(this,
                new FlashcardViewModelFactory(requireContext())).get(FlashcardViewModel.class);
        binding.back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.retry.setOnClickListener(v -> model.retry());
        binding.card.setOnClickListener(v -> model.flip());
        binding.rateForgot.setOnClickListener(v -> model.rate(Rating.FORGOT));
        binding.rateHard.setOnClickListener(v -> model.rate(Rating.HARD));
        binding.rateRemembered.setOnClickListener(v -> model.rate(Rating.REMEMBERED));
        model.state().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(FragmentFlashcardBinding b, FlashcardViewModel.State state) {
        boolean ready = state.status == FlashcardViewModel.Status.READY;
        b.loading.setVisibility(state.status == FlashcardViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
        b.card.setVisibility(ready ? View.VISIBLE : View.GONE);
        b.ratingBar.setVisibility(ready ? View.VISIBLE : View.GONE);
        b.progressLabel.setText(ready ? getString(R.string.flashcard_progress, state.position, state.total) : "");

        int message = messageFor(state.status);
        b.statePanel.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) b.message.setText(message);
        b.retry.setVisibility(state.status == FlashcardViewModel.Status.ERROR ? View.VISIBLE : View.GONE);
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
        b.rateForgot.setEnabled(state.ratingEnabled());
        b.rateHard.setEnabled(state.ratingEnabled());
        b.rateRemembered.setEnabled(state.ratingEnabled());
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
