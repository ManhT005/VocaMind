package com.vocamind.app.feature.vocabulary;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.vocamind.app.R;

/** VOCAB-02 receiving boundary: pack routes validate membership; catalog search resolves stableId directly. */
public final class VocabularyDetailFragment extends Fragment {
    public static final String ARG_VOCABULARY_ID = "vocabularyId";
    public static final String ARG_PACK_ID = "packId";
    public VocabularyDetailFragment() { super(R.layout.fragment_vocabulary_detail); }
    public static Bundle arguments(String packId, String stableId) {
        Bundle args = new Bundle();
        args.putString(ARG_PACK_ID, packId);
        args.putString(ARG_VOCABULARY_ID, stableId);
        return args;
    }
    public static boolean validArguments(Bundle args) {
        return args != null && present(args.getString(ARG_VOCABULARY_ID))
                && args.getString(ARG_VOCABULARY_ID).matches("[0-9a-f]{16}")
                && (!args.containsKey(ARG_PACK_ID) || args.getString(ARG_PACK_ID) == null || present(args.getString(ARG_PACK_ID)));
    }
    public static Bundle searchArguments(String stableId) {
        Bundle args = new Bundle(); args.putString(ARG_VOCABULARY_ID, stableId); return args;
    }
    private static boolean present(String id) { return id != null && !id.trim().isEmpty(); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        TextView message = view.findViewById(R.id.route_message);
        message.setText(R.string.word_route_invalid);
        if (validArguments(args) && !present(args.getString(ARG_PACK_ID))) {
            com.vocamind.app.feature.search.CatalogDetailViewModel model = new androidx.lifecycle.ViewModelProvider(this,
                    new com.vocamind.app.core.CatalogDetailViewModelFactory(requireContext(), args.getString(ARG_VOCABULARY_ID)))
                    .get(com.vocamind.app.feature.search.CatalogDetailViewModel.class);
            model.state().observe(getViewLifecycleOwner(), state -> {
                switch (state.status) {
                    case LOADING: message.setText(R.string.word_route_loading); break;
                    case NOT_FOUND: message.setText(R.string.word_route_invalid); break;
                    case ERROR: message.setText(R.string.search_error); break;
                    case FOUND: message.setText(getString(R.string.search_detail_preview, state.word.lemma,
                            state.word.meaningVi, state.word.stableId)); break;
                }
            });
        } else if (validArguments(args)) {
            com.vocamind.app.feature.packs.PackWordsViewModel model = new androidx.lifecycle.ViewModelProvider(this,
                    new com.vocamind.app.core.PacksViewModelFactory(requireContext(), args.getString(ARG_PACK_ID)))
                    .get(com.vocamind.app.feature.packs.PackWordsViewModel.class);
            model.state().observe(getViewLifecycleOwner(), state -> {
                if (state.status == com.vocamind.app.feature.packs.AsyncListViewModel.Status.LOADING) {
                    message.setText(R.string.word_route_loading);
                } else if (state.status == com.vocamind.app.feature.packs.AsyncListViewModel.Status.ERROR) {
                    message.setText(R.string.pack_detail_error);
                } else if (state.items.stream().noneMatch(i -> i.getVocabulary().stableId.equals(args.getString(ARG_VOCABULARY_ID)))) {
                    message.setText(R.string.word_route_invalid);
                } else {
                    message.setText(getString(R.string.word_route_pending, args.getString(ARG_VOCABULARY_ID), args.getString(ARG_PACK_ID)));
                }
            });
        }
        view.findViewById(R.id.back).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
    }
}
