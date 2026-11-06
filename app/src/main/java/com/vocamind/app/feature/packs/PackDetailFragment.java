package com.vocamind.app.feature.packs;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.vocamind.app.R;
import com.vocamind.app.core.PacksViewModelFactory;
import com.vocamind.app.databinding.FragmentPackDetailBinding;

/** Words of one pack in PackWord.orderNo order, with per-word progress. */
public final class PackDetailFragment extends Fragment {
    private PacksViewModel activationModel;
    private PackWordsViewModel model;
    @Override public void onResume() {
        super.onResume();
        activationModel.refresh();
        model.refresh();
    }

    public PackDetailFragment() { super(R.layout.fragment_pack_detail); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentPackDetailBinding binding = FragmentPackDetailBinding.bind(view);
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        String packId = args.getString(PacksFragment.ARG_PACK_ID);
        String title = args.getString(PacksFragment.ARG_PACK_TITLE);
        if (title != null) binding.detailTitle.setText(title);
        ViewCompat.setAccessibilityHeading(binding.detailTitle, true);
        model = new ViewModelProvider(this,
                new PacksViewModelFactory(requireContext(), packId)).get(PackWordsViewModel.class);
        activationModel = new ViewModelProvider(requireActivity(),
                new PacksViewModelFactory(requireContext(), null)).get(PacksViewModel.class);
        activationModel.state().observe(getViewLifecycleOwner(), state -> {
            boolean ready = state.status == AsyncListViewModel.Status.CONTENT;
            com.vocamind.app.domain.pack.PackState pack = state.items.stream()
                    .filter(p -> p.getPack().id.equals(packId)).findFirst().orElse(null);
            boolean error = state.status == AsyncListViewModel.Status.ERROR;
            binding.activation.setEnabled(error || (ready && pack != null));
            binding.activation.setText(error ? R.string.packs_retry
                    : pack != null && pack.isActive() ? R.string.pack_pause : R.string.pack_activate);
            binding.activationStatus.setText(error ? R.string.pack_activation_error
                    : !ready ? R.string.pack_activation_pending
                    : pack != null && pack.isActive() ? R.string.pack_active : R.string.pack_inactive);
            binding.activation.setOnClickListener(v -> {
                if (error) activationModel.retry(); // reconcile durable state before another write
                else if (pack != null) activationModel.setActive(packId, !pack.isActive());
            });
        });
        PackWordAdapter adapter = new PackWordAdapter(id ->
                NavHostFragment.findNavController(this).navigate(R.id.vocabularyDetailFragment,
                        com.vocamind.app.feature.vocabulary.VocabularyDetailFragment.arguments(packId, id)));
        binding.words.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.words.setAdapter(adapter);
        binding.back.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.retry.setOnClickListener(v -> model.retry());
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.loading.setVisibility(state.status == AsyncListViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
            boolean error = state.status == AsyncListViewModel.Status.ERROR;
            boolean empty = state.status == AsyncListViewModel.Status.EMPTY;
            binding.message.setVisibility(error || empty ? View.VISIBLE : View.GONE);
            binding.message.setText(error ? R.string.pack_detail_error : R.string.pack_detail_empty);
            binding.retry.setVisibility(error || empty ? View.VISIBLE : View.GONE);
            if (state.status != AsyncListViewModel.Status.LOADING) adapter.submitList(state.items);
            binding.words.setVisibility(state.status == AsyncListViewModel.Status.CONTENT ? View.VISIBLE : View.INVISIBLE);
        });
    }

}
