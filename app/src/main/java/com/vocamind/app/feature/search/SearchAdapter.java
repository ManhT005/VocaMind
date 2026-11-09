package com.vocamind.app.feature.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.vocamind.app.databinding.ItemSearchWordBinding;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import java.util.function.BiConsumer;

public final class SearchAdapter extends ListAdapter<SearchAdapter.Row, SearchAdapter.Holder> {
    public static final class Row {
        public final CatalogVocabulary word;
        public final long generation;
        public Row(CatalogVocabulary word, long generation) { this.word = word; this.generation = generation; }
    }
    public static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) { return a.word.stableId.equals(b.word.stableId); }
        @Override public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.generation == b.generation && a.word.lemma.equals(b.word.lemma) && a.word.meaningVi.equals(b.word.meaningVi);
        }
    };
    private final BiConsumer<Long, String> click;
    public SearchAdapter(BiConsumer<Long, String> click) { super(DIFF); this.click = click; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemSearchWordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = getItem(position);
        holder.binding.wordTitle.setText(row.word.lemma);
        holder.binding.wordMeaning.setText(row.word.meaningVi);
        holder.itemView.setOnClickListener(v -> click.accept(row.generation, row.word.stableId));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemSearchWordBinding binding;
        Holder(ItemSearchWordBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
