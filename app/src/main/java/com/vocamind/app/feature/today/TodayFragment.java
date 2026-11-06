package com.vocamind.app.feature.today;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.vocamind.app.R;
import com.vocamind.app.core.PacksViewModelFactory;
import com.vocamind.app.databinding.FragmentTodayBinding;
import com.vocamind.app.feature.packs.AsyncListViewModel;
import com.vocamind.app.feature.packs.PacksViewModel;

/** Active-pack boundary only; session planning belongs to TODAY-01/02. */
public class TodayFragment extends Fragment {
    private PacksViewModel model;
    public TodayFragment() { super(R.layout.fragment_today); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentTodayBinding binding = FragmentTodayBinding.bind(view);
        model = new ViewModelProvider(requireActivity(), new PacksViewModelFactory(requireContext(), null))
                .get(PacksViewModel.class);
        binding.retry.setOnClickListener(v -> model.retry());
        binding.explore.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.packsFragment));
        model.state().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state.status == AsyncListViewModel.Status.LOADING;
            boolean error = state.status == AsyncListViewModel.Status.ERROR;
            boolean noActive = !loading && !error && state.items.stream().noneMatch(p -> p.isActive());
            binding.loading.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.retry.setVisibility(error ? View.VISIBLE : View.GONE);
            binding.explore.setVisibility(noActive ? View.VISIBLE : View.GONE);
            binding.message.setText(loading ? R.string.pack_activation_pending : error ? R.string.packs_error
                    : noActive ? R.string.today_choose_pack : R.string.today_active_boundary);
        });
    }

    @Override public void onResume() { super.onResume(); model.refresh(); }
}
