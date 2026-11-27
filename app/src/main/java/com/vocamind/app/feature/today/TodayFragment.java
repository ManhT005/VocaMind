package com.vocamind.app.feature.today;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import java.text.NumberFormat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.vocamind.app.R;
import com.vocamind.app.core.PacksViewModelFactory;
import com.vocamind.app.core.TodayPlanViewModelFactory;
import com.vocamind.app.databinding.FragmentTodayBinding;
import com.vocamind.app.feature.packs.AsyncListViewModel;
import com.vocamind.app.feature.packs.PacksViewModel;

/**
 * Today: pack state decides loading / error / "choose a pack"; with an active pack the Today
 * plan shows due and new counts, a Start button, or the done state with Quiz / Explore.
 */
public class TodayFragment extends Fragment {
    private PacksViewModel model;
    private TodayPlanViewModel plan;
    private FragmentTodayBinding binding;
    private AsyncListViewModel.State<com.vocamind.app.domain.pack.PackState> packState;
    private TodayPlanViewModel.State planState;

    public TodayFragment() { super(R.layout.fragment_today); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding = FragmentTodayBinding.bind(view);
        boolean stacked = getResources().getConfiguration().screenWidthDp < 360
                || getResources().getConfiguration().fontScale >= 1.3f;
        binding.statsRow.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        for (int i = 0; i < binding.statsRow.getChildCount(); i++) {
            var params = new LinearLayout.LayoutParams(
                    stacked ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                    stacked ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                    stacked ? 0 : 1);
            if (i > 0) {
                int gap = getResources().getDimensionPixelSize(R.dimen.ui_card_gap);
                if (stacked) params.topMargin = gap;
                else params.setMarginStart(gap);
            }
            binding.statsRow.getChildAt(i).setLayoutParams(params);
        }
        model = new ViewModelProvider(requireActivity(), new PacksViewModelFactory(requireContext(), null))
                .get(PacksViewModel.class);
        plan = new ViewModelProvider(requireActivity(), new TodayPlanViewModelFactory(requireContext()))
                .get(TodayPlanViewModel.class);
        binding.retry.setOnClickListener(v -> {
            model.retry();
            plan.retry();
        });
        binding.start.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.flashcardFragment));
        binding.explore.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.packsFragment));
        binding.doneExplore.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.packsFragment));
        binding.doneQuiz.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.quizFragment));
        model.state().observe(getViewLifecycleOwner(), state -> {
            packState = state;
            render();
        });
        plan.state().observe(getViewLifecycleOwner(), state -> {
            planState = state;
            render();
        });
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        packState = null;
        planState = null;
    }

    private void render() {
        if (binding == null || packState == null) return;
        boolean loading = packState.status == AsyncListViewModel.Status.LOADING;
        boolean error = packState.status == AsyncListViewModel.Status.ERROR;
        boolean noActive = !loading && !error && packState.items.stream().noneMatch(p -> p.isActive());
        boolean active = !loading && !error && !noActive;

        TodayPlanViewModel.Status ps = planState == null ? TodayPlanViewModel.Status.LOADING : planState.status;
        boolean planLoading = active && ps == TodayPlanViewModel.Status.LOADING;
        boolean planError = active && ps == TodayPlanViewModel.Status.ERROR;
        boolean ready = active && ps == TodayPlanViewModel.Status.READY;
        boolean done = active && (ps == TodayPlanViewModel.Status.DONE);

        binding.loading.setVisibility(loading || planLoading ? View.VISIBLE : View.GONE);
        binding.retry.setVisibility(error || planError ? View.VISIBLE : View.GONE);
        binding.explore.setVisibility(noActive ? View.VISIBLE : View.GONE);
        binding.start.setVisibility(ready ? View.VISIBLE : View.GONE);
        binding.summary.setVisibility(ready ? View.VISIBLE : View.GONE);
        binding.doneQuiz.setVisibility(done ? View.VISIBLE : View.GONE);
        binding.doneExplore.setVisibility(done ? View.VISIBLE : View.GONE);
        binding.message.setVisibility(ready ? View.GONE : View.VISIBLE);
        if (ready) {
            NumberFormat numbers = NumberFormat.getIntegerInstance();
            String due = numbers.format(planState.dueCount);
            binding.dueCount.setText(due);
            binding.start.setText(planState.dueCount > 0 && planState.newCount == 0
                    ? getString(R.string.ui_today_review_due, due) : getString(R.string.today_start));
            binding.newCount.setText(numbers.format(planState.newCount));
        }
        binding.message.setBackgroundResource(done ? R.drawable.ui_success : R.drawable.ui_row);
        binding.message.setText(loading ? R.string.pack_activation_pending
                : error || planError ? R.string.packs_error
                : noActive ? R.string.today_choose_pack
                : planLoading ? R.string.today_preparing
                : done ? R.string.today_done
                : R.string.today);
        if (done && planState.nextReviewAt.isPresent()) {
            String next = DateUtils.formatDateTime(requireContext(), planState.nextReviewAt.getAsLong(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR);
            binding.message.setText(getString(R.string.ui_today_next_review, getString(R.string.today_done), next));
        }
    }

    @Override public void onResume() {
        super.onResume();
        model.refresh();
        plan.refresh();
    }
}
