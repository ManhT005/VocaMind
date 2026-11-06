package com.vocamind.app.feature.vocabulary;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.vocamind.app.R;
import com.vocamind.app.core.PacksViewModelFactory;
import com.vocamind.app.domain.common.RepoError;

public final class VocabularyDetailFragment extends Fragment {
    public static final String ARG_VOCABULARY_ID = "vocabularyId";
    public static final String ARG_PACK_ID = "packId";
    private VocabularyDetailViewModel model;
    public VocabularyDetailFragment() { super(R.layout.fragment_vocabulary_detail); }
    public static Bundle arguments(String packId, String stableId) {
        Bundle args = new Bundle();
        args.putString(ARG_PACK_ID, packId); args.putString(ARG_VOCABULARY_ID, stableId);
        return args;
    }
    public static boolean validArguments(Bundle args) {
        return args != null && present(args.getString(ARG_PACK_ID)) && present(args.getString(ARG_VOCABULARY_ID));
    }
    private static boolean present(String id) { return id != null && !id.trim().isEmpty(); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.back).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
        if (!validArguments(getArguments())) {
            ((TextView)view.findViewById(R.id.route_message)).setText(R.string.word_route_invalid);
            return;
        }
        Bundle args = requireArguments();
        model = new ViewModelProvider(this, new PacksViewModelFactory(requireContext(),
                args.getString(ARG_PACK_ID), args.getString(ARG_VOCABULARY_ID))).get(VocabularyDetailViewModel.class);
        view.findViewById(R.id.detail_save).setOnClickListener(v -> model.toggleSaved());
        view.findViewById(R.id.detail_retry).setOnClickListener(v -> model.refresh());
        model.state().observe(getViewLifecycleOwner(), state -> render(view, state));
    }
    @Override public void onResume() { super.onResume(); if (model != null) model.refresh(); }
    static void render(View view, VocabularyDetailViewModel.State state) {
        TextView message = view.findViewById(R.id.route_message);
        int error = state.error == RepoError.NOT_FOUND || state.error == RepoError.INVALID_ARGUMENT
                ? R.string.word_route_invalid : R.string.detail_error;
        message.setText(state.loading ? R.string.word_route_loading : state.pending ? R.string.detail_saving
                : state.error != null ? error : R.string.detail_ready);
        message.setVisibility(state.error != null || state.loading ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.detail_loading).setVisibility(state.loading ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.detail_content).setVisibility(state.detail == null ? View.GONE : View.VISIBLE);
        boolean retryable = state.error == RepoError.READ_FAILED || state.error == RepoError.WRITE_FAILED;
        view.findViewById(R.id.detail_retry).setVisibility(retryable ? View.VISIBLE : View.GONE);
        TextView save = view.findViewById(R.id.detail_save);
        save.setVisibility(state.detail == null ? View.GONE : View.VISIBLE);
        save.setEnabled(state.detail != null && !state.pending && !state.loading && state.error == null);
        if (state.detail == null) return;
        var word = state.detail.word;
        text(view, R.id.detail_lemma, word.lemma);
        text(view, R.id.detail_pos, word.pos);
        text(view, R.id.detail_meaning, word.meaningVi);
        text(view, R.id.detail_example_en, word.exampleEn);
        text(view, R.id.detail_example_vi, word.exampleVi);
        ((TextView)view.findViewById(R.id.detail_ipa)).setText(word.ipa == null || word.ipa.trim().isEmpty()
                ? view.getContext().getString(R.string.word_ipa_missing) : word.ipa);
        save.setText(state.pending ? R.string.detail_saving : state.detail.saved ? R.string.detail_unsave : R.string.detail_save);
    }
    private static void text(View view, int id, String value) {
        ((TextView)view.findViewById(id)).setText(value == null || value.trim().isEmpty()
                ? view.getContext().getString(R.string.detail_missing) : value);
    }
}
