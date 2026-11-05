package com.vocamind.app.feature.packs;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.vocamind.app.R;
import com.vocamind.app.databinding.ItemPackWordBinding;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.learning.WordProgress;
import java.util.function.Consumer;

/** The single PACK-01/VOCAB-01 row implementation. Identity is opaque TEXT. */
final class PackWordAdapter extends ListAdapter<StudyItem, PackWordAdapter.Holder> {
    static final DiffUtil.ItemCallback<StudyItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull StudyItem a, @NonNull StudyItem b) {
            return a.getVocabulary().stableId.equals(b.getVocabulary().stableId);
        }
        @Override public boolean areContentsTheSame(@NonNull StudyItem a, @NonNull StudyItem b) {
            // Immutable domain snapshots have no value equality; always rebind changed snapshots.
            return a == b;
        }
    };
    private final Consumer<String> click;
    PackWordAdapter(Consumer<String> click) {
        super(DIFF);
        this.click = click;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemPackWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        StudyItem item = getItem(position);
        ItemPackWordBinding row = holder.binding;
        android.content.Context c = row.getRoot().getContext();
        row.wordTitle.setText(c.getString(R.string.pack_word_title, item.getVocabulary().lemma, item.getVocabulary().pos));
        row.wordMeaning.setText(item.getVocabulary().meaningVi);
        String ipa = item.getVocabulary().ipa;
        row.wordIpa.setText(ipa == null || ipa.trim().isEmpty() ? c.getString(R.string.word_ipa_missing) : ipa);
        WordProgress p = item.getProgress();
        row.wordStatus.setText(p == null || p.getLearnedAtEpochMillis() == null
                ? c.getString(R.string.word_unreviewed)
                : p.getBox() == 0 ? c.getString(R.string.word_relearn)
                : c.getString(R.string.word_level, p.getBox()));
        row.getRoot().setOnClickListener(v -> click.accept(item.getVocabulary().stableId));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemPackWordBinding binding;
        Holder(ItemPackWordBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
