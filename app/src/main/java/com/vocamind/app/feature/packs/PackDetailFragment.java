package com.vocamind.app.feature.packs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.vocamind.app.R;
import com.vocamind.app.core.PacksViewModelFactory;
import com.vocamind.app.databinding.FragmentPackDetailBinding;
import com.vocamind.app.databinding.ItemPackWordBinding;
import com.vocamind.app.domain.learning.StudyItem;
import java.util.ArrayList;
import java.util.List;

/** Words of one pack in PackWord.orderNo order, with per-word progress. */
public final class PackDetailFragment extends Fragment {
    private PacksViewModel activationModel;
    @Override public void onResume() {
        super.onResume();
        activationModel.refresh();
    }

    public PackDetailFragment() { super(R.layout.fragment_pack_detail); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentPackDetailBinding binding = FragmentPackDetailBinding.bind(view);
        Bundle args = requireArguments();
        String packId = args.getString(PacksFragment.ARG_PACK_ID);
        String title = args.getString(PacksFragment.ARG_PACK_TITLE);
        if (title != null) binding.detailTitle.setText(title);
        ViewCompat.setAccessibilityHeading(binding.detailTitle, true);
        PackWordsViewModel model = new ViewModelProvider(this,
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
        WordAdapter adapter = new WordAdapter();
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
            adapter.submit(state.items);
        });
    }

    private final class WordAdapter extends RecyclerView.Adapter<WordAdapter.Holder> {
        private final List<StudyItem> items = new ArrayList<>();

        void submit(List<StudyItem> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(ItemPackWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            StudyItem item = items.get(position);
            ItemPackWordBinding row = holder.binding;
            row.wordTitle.setText(getString(R.string.pack_word_title, item.getVocabulary().lemma, item.getVocabulary().pos));
            row.wordMeaning.setText(item.getVocabulary().meaningVi);
            row.wordStatus.setText(item.getProgress() == null ? getString(R.string.pack_word_new)
                    : getString(R.string.pack_word_box, item.getProgress().getBox()));
        }

        @Override public int getItemCount() { return items.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemPackWordBinding binding;
            Holder(ItemPackWordBinding binding) { super(binding.getRoot()); this.binding = binding; }
        }
    }
}
