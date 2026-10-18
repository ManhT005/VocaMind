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
import com.vocamind.app.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FragmentHomeBinding binding = FragmentHomeBinding.bind(view);
        binding.openPacks.setOnClickListener(v -> navigateFromHome(R.id.packsFragment));
        binding.openVocabulary.setOnClickListener(v -> navigateFromHome(R.id.vocabularyFragment));
        binding.openToday.setOnClickListener(v -> navigateFromHome(R.id.todayFragment));
        binding.openQuiz.setOnClickListener(v -> navigateFromHome(R.id.quizFragment));
        binding.openProgress.setOnClickListener(v -> navigateFromHome(R.id.progressFragment));
    }

    private void navigateFromHome(@IdRes int destinationId) {
        NavController navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.homeFragment) {
            navController.navigate(destinationId);
        }
    }
}
