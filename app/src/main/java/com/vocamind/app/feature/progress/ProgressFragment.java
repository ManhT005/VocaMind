package com.vocamind.app.feature.progress;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.vocamind.app.R;
import com.vocamind.app.core.ProgressViewModelFactory;
import com.vocamind.app.domain.progress.ProgressSummary;
import java.util.Collections;
public class ProgressFragment extends Fragment {
 private ProgressViewModel model;
 public ProgressFragment() { super(R.layout.fragment_progress); }
 protected ViewModelProvider.Factory viewModelFactory() { return new ProgressViewModelFactory(requireContext()); }
 @Override public void onViewCreated(@NonNull View view,@Nullable Bundle savedState) {
  model=new ViewModelProvider(this,viewModelFactory()).get(ProgressViewModel.class);
  QuizHistoryAdapter history=new QuizHistoryAdapter();SummaryAdapter summary=new SummaryAdapter();
  RecyclerView list=view.findViewById(R.id.progress_list);
  list.setLayoutManager(new LinearLayoutManager(requireContext()));list.setAdapter(new ConcatAdapter(new ConcatAdapter.Config.Builder()
   .setStableIdMode(ConcatAdapter.Config.StableIdMode.SHARED_STABLE_IDS).build(),summary,history));
  view.findViewById(R.id.progress_refresh).setOnClickListener(v -> model.refresh());
  view.findViewById(R.id.progress_retry).setOnClickListener(v -> model.refresh());
  model.state().observe(getViewLifecycleOwner(),state -> {
   boolean loading=state.getStatus()==ProgressViewState.Status.LOADING;
   boolean error=state.getStatus()==ProgressViewState.Status.ERROR;
   view.findViewById(R.id.progress_loading).setVisibility(loading?View.VISIBLE:View.GONE);
   view.findViewById(R.id.progress_error).setVisibility(error?View.VISIBLE:View.GONE);
   view.findViewById(R.id.progress_refresh).setEnabled(!loading);
   list.setVisibility(state.hasSnapshot()?View.VISIBLE:View.GONE);
   summary.update(state);
   history.submitList(state.hasSnapshot()?state.getSnapshot().getQuizHistory():Collections.emptyList());
  });
 }
 @Override public void onResume() { super.onResume();model.refresh(); }
 @Override public void onDestroyView() {
  ((RecyclerView)requireView().findViewById(R.id.progress_list)).setAdapter(null);super.onDestroyView();
 }
 private static final class SummaryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private ProgressViewState state;
  SummaryAdapter() { setHasStableIds(true); }
  @Override public long getItemId(int position) { return 0; } // Quiz attempt IDs are strictly positive.
  void update(ProgressViewState state) {
   boolean hadItem=getItemCount()==1;
   this.state=state;
   boolean hasItem=getItemCount()==1;
   if(hadItem && hasItem) notifyItemChanged(0);
   else if(hadItem) notifyItemRemoved(0);
   else if(hasItem) notifyItemInserted(0);
  }
  public int getItemCount() { return state!=null && state.hasSnapshot()?1:0; }
  @NonNull public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
   return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress_summary,parent,false)) {};
  }
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,int position) {
   View view=holder.itemView;ProgressSummary s=state.getSnapshot().getSummary();
   ((TextView)view.findViewById(R.id.progress_counts)).setText(view.getContext().getString(R.string.progress_counts,
    s.getTotalLearned(),s.getLearningCount(),s.getReviewingCount(),s.getMasteredCount()));
   int[] ids={R.id.box_0,R.id.box_1,R.id.box_2,R.id.box_3,R.id.box_4,R.id.box_5};
   int[] labels={R.string.progress_box_0,R.string.progress_box_1,R.string.progress_box_2,R.string.progress_box_3,R.string.progress_box_4,R.string.progress_box_5};
   for(int i=0;i<6;i++) ((TextView)view.findViewById(ids[i])).setText(view.getContext().getString(
    R.string.progress_box_count,view.getContext().getString(labels[i]),s.getBoxCount(i)));
   view.findViewById(R.id.progress_empty).setVisibility(state.isProgressEmpty()?View.VISIBLE:View.GONE);
   view.findViewById(R.id.history_empty).setVisibility(state.isHistoryEmpty()?View.VISIBLE:View.GONE);
  }
 }
}
