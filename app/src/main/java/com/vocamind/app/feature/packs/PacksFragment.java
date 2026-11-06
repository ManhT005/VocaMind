package com.vocamind.app.feature.packs;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.vocamind.app.R;
import com.vocamind.app.core.PacksViewModelFactory;
import com.vocamind.app.databinding.FragmentPacksBinding;
import com.vocamind.app.databinding.ItemPackBinding;
import com.vocamind.app.domain.pack.PackState;

/** Pack list. Reads only through PacksViewModel; no DAO or Room here. */
public class PacksFragment extends Fragment {
    public static final String ARG_PACK_ID = "packId";
    public static final String ARG_PACK_TITLE = "packTitle";

    private PacksViewModel model;

    @Override public void onResume() {
        super.onResume();
        model.refresh();
    }

    public PacksFragment() { super(R.layout.fragment_packs); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentPacksBinding binding = FragmentPacksBinding.bind(view);
        ViewCompat.setAccessibilityHeading(binding.destinationTitle, true);
        model = new ViewModelProvider(requireActivity(),
                new PacksViewModelFactory(requireContext(), null)).get(PacksViewModel.class);
        binding.retry.setOnClickListener(v -> model.retry());
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.loading.setVisibility(state.status == AsyncListViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
            boolean error = state.status == AsyncListViewModel.Status.ERROR;
            boolean empty = state.status == AsyncListViewModel.Status.EMPTY;
            binding.message.setVisibility(error || empty ? View.VISIBLE : View.GONE);
            binding.message.setText(error ? R.string.packs_error : R.string.packs_empty);
            binding.retry.setVisibility(error || empty ? View.VISIBLE : View.GONE);
            binding.packList.removeAllViews();
            for (PackState pack : state.items) {
                ItemPackBinding row = ItemPackBinding.inflate(getLayoutInflater(), binding.packList, false);
                row.packTitle.setText(pack.isActive()
                        ? getString(R.string.pack_title_active, pack.getPack().title, getString(R.string.pack_active))
                        : pack.getPack().title);
                ViewCompat.setAccessibilityHeading(row.packTitle, true);
                row.packDescription.setText(pack.getPack().description);
                row.packProgressText.setText(getString(R.string.pack_progress,
                        pack.getLearnedCount(), pack.getPack().wordCount));
                row.packProgress.setMax(Math.max(1, pack.getPack().wordCount));
                row.packProgress.setProgress(pack.getLearnedCount());
                row.packRoot.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putString(ARG_PACK_ID, pack.getPack().id);
                    args.putString(ARG_PACK_TITLE, pack.getPack().title);
                    NavHostFragment.findNavController(this).navigate(R.id.packDetailFragment, args);
                });
                binding.packList.addView(row.getRoot());
            }
        });
    }
}
