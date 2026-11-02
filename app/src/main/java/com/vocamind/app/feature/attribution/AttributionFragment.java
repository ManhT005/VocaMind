package com.vocamind.app.feature.attribution;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.vocamind.app.R;
import com.vocamind.app.core.AttributionViewModelFactory;
import com.vocamind.app.databinding.FragmentAttributionBinding;
import com.vocamind.app.databinding.ItemAttributionSourceBinding;
import com.vocamind.app.domain.catalog.CatalogSource;

public final class AttributionFragment extends Fragment {
    public AttributionFragment() { super(R.layout.fragment_attribution); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentAttributionBinding binding = FragmentAttributionBinding.bind(view);
        ViewCompat.setAccessibilityHeading(binding.attributionTitle, true);
        AttributionViewModel model = new ViewModelProvider(this,
                new AttributionViewModelFactory(requireContext())).get(AttributionViewModel.class);
        binding.back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.retry.setOnClickListener(v -> model.retry());
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.loading.setVisibility(state.status == AttributionViewModel.Status.LOADING
                    ? View.VISIBLE : View.GONE);
            boolean error = state.status == AttributionViewModel.Status.ERROR;
            boolean empty = state.status == AttributionViewModel.Status.EMPTY;
            binding.message.setVisibility(error || empty ? View.VISIBLE : View.GONE);
            binding.message.setText(error ? R.string.attribution_error : R.string.attribution_empty);
            binding.retry.setVisibility(error || empty ? View.VISIBLE : View.GONE);
            binding.sources.removeAllViews();
            for (CatalogSource source : state.sources) {
                ItemAttributionSourceBinding row = ItemAttributionSourceBinding.inflate(
                        getLayoutInflater(), binding.sources, false);
                row.title.setText(display(source.title));
                row.authors.setText(getString(R.string.attribution_authors, source.authors.isEmpty()
                        ? getString(R.string.attribution_authors_missing) : String.join("; ", source.authors)));
                row.credit.setVisibility(source.creditText == null || source.creditText.isBlank()
                        ? View.GONE : View.VISIBLE);
                row.credit.setText(source.creditText);
                ViewCompat.setAccessibilityHeading(row.title, true);
                row.sourceUrl.setText(getString(R.string.attribution_source_url, display(source.url)));
                row.license.setText(getString(R.string.attribution_license, display(source.licenseCode)));
                row.licenseUrl.setText(getString(R.string.attribution_license_url, display(source.licenseUrl)));
                row.snapshot.setText(getString(R.string.attribution_snapshot, display(source.snapshotDate)));
                row.openSource.setOnClickListener(v -> ExternalLinks.open(requireContext(), source.url));
                row.openLicense.setOnClickListener(v -> ExternalLinks.open(requireContext(), source.licenseUrl));
                binding.sources.addView(row.getRoot());
            }
        });
    }

    private String display(String value) {
        return value == null || value.isBlank() ? getString(R.string.attribution_missing) : value;
    }
}
