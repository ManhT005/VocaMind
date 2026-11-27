package com.vocamind.app.feature.home;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.vocamind.app.R;
import com.vocamind.app.MainActivity;
import com.vocamind.app.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FragmentHomeBinding binding = FragmentHomeBinding.bind(view);
        binding.openProfile.setOnClickListener(v -> navigateFromHome(R.id.profileFragment));
        binding.openAttribution.setOnClickListener(v -> navigateFromHome(R.id.attributionFragment));
        binding.openPacks.setOnClickListener(v -> navigateFromHome(R.id.packsFragment));
        binding.openToday.setOnClickListener(v -> ((MainActivity) requireActivity()).selectStudyTab());
        binding.openQuiz.setOnClickListener(v -> navigateFromHome(R.id.quizFragment));
    }

    private void navigateFromHome(@IdRes int destinationId) {
        NavController navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.homeFragment) {
            navController.navigate(destinationId);
        }
    }
}
