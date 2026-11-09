package com.vocamind.app.feature.vocabulary;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.*;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.vocamind.app.R;
import com.vocamind.app.core.SearchViewModelFactory;
import com.vocamind.app.databinding.FragmentVocabularyBinding;
import com.vocamind.app.feature.search.*;
import java.util.ArrayList;
import java.util.List;

public class VocabularyFragment extends Fragment {
    public VocabularyFragment() { super(R.layout.fragment_vocabulary); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        FragmentVocabularyBinding b = FragmentVocabularyBinding.bind(view);
        SearchViewModel model = new ViewModelProvider(this,
                new SearchViewModelFactory(this, requireContext(), getArguments())).get(SearchViewModel.class);
        SearchAdapter adapter = new SearchAdapter((generation, id) -> {
            if (model.canOpen(generation, id)) {
                b.search.clearFocus();
                NavHostFragment.findNavController(this).navigate(R.id.vocabularyDetailFragment,
                        VocabularyDetailFragment.searchArguments(id));
            }
        });
        b.results.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.results.setAdapter(adapter);
        b.search.setIconifiedByDefault(false);
        b.search.setQueryHint(getString(R.string.search_hint));
        b.search.setQuery(model.state().getValue().input, false);
        b.search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { b.search.clearFocus(); return true; }
            @Override public boolean onQueryTextChange(String text) { model.input(text); return true; }
        });
        b.reset.setOnClickListener(v -> { model.reset(); b.search.setQuery("", false); });
        b.retry.setOnClickListener(v -> model.retry());
        model.state().observe(getViewLifecycleOwner(), s -> {
            long renderStart = System.nanoTime();
            boolean content = s.status == SearchViewModel.Status.CONTENT;
            b.results.setVisibility(content ? View.VISIBLE : View.GONE);
            b.loading.setVisibility(s.status == SearchViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
            b.retry.setVisibility(s.status == SearchViewModel.Status.ERROR
                    && s.error != com.vocamind.app.domain.catalog.VocabularySearchResult.Error.INVALID_QUERY
                    ? View.VISIBLE : View.GONE);
            int message = s.status == SearchViewModel.Status.NO_INPUT ? R.string.search_no_input
                    : s.status == SearchViewModel.Status.LOADING ? R.string.search_loading
                    : s.status == SearchViewModel.Status.NO_MATCH ? R.string.search_no_match
                    : s.status == SearchViewModel.Status.ERROR ? (s.error == com.vocamind.app.domain.catalog.VocabularySearchResult.Error.INVALID_QUERY
                    ? R.string.search_invalid : R.string.search_error) : R.string.search_content;
            b.searchMessage.setText(message);
            List<SearchAdapter.Row> rows = new ArrayList<>();
            for (var word : s.words) rows.add(new SearchAdapter.Row(word, s.generation));
            adapter.submitList(rows, () -> b.results.post(() -> {
                if (getView() == view && model.state().getValue().generation == s.generation
                        && com.vocamind.app.BuildConfig.DEBUG)
                    android.util.Log.d("SearchTiming", "generation=" + s.generation
                            + " bind_to_post_ms=" + (System.nanoTime() - renderStart) / 1_000_000.0);
            }));
        });
        getViewLifecycleOwner().getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
            @Override public void onDestroy(@NonNull androidx.lifecycle.LifecycleOwner owner) {
                b.search.setOnQueryTextListener(null); b.results.setAdapter(null);
            }
        });
    }
}
