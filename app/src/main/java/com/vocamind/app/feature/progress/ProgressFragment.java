package com.vocamind.app.feature.progress;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import android.widget.Button;
import android.text.TextUtils;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vocamind.app.domain.progress.LearningActivity;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.vocamind.app.R;
import com.vocamind.app.core.ProgressViewModelFactory;
import com.vocamind.app.domain.progress.ProgressSummary;
public class ProgressFragment extends Fragment {
 private ProgressViewModel model;
 public ProgressFragment() { super(R.layout.fragment_progress); }
 protected QuizHistoryAdapter historyAdapter() { return new QuizHistoryAdapter(); }
 protected ViewModelProvider.Factory viewModelFactory() { return new ProgressViewModelFactory(requireContext()); }
 @Override public void onViewCreated(@NonNull View view,@Nullable Bundle savedState) {
  model=new ViewModelProvider(this,viewModelFactory()).get(ProgressViewModel.class);
  QuizHistoryAdapter history=historyAdapter();SummaryAdapter summary=new SummaryAdapter();
  RecyclerView list=view.findViewById(R.id.progress_list);
  list.setLayoutManager(new LinearLayoutManager(requireContext()));list.setAdapter(new ConcatAdapter(new ConcatAdapter.Config.Builder()
   .setStableIdMode(ConcatAdapter.Config.StableIdMode.SHARED_STABLE_IDS).build(),summary,history));
  view.findViewById(R.id.progress_refresh).setOnClickListener(v -> model.refresh());
  view.findViewById(R.id.progress_retry).setOnClickListener(v -> model.refresh());
  androidx.lifecycle.LifecycleOwner owner=getViewLifecycleOwner();
  long[] renderGeneration={0};
  model.state().observe(owner,state -> {
   long generation=++renderGeneration[0];
   boolean loading=state.getStatus()==ProgressViewState.Status.LOADING;
   boolean error=state.getStatus()==ProgressViewState.Status.ERROR;
   // Hide the entire snapshot until both adapters represent the same successful read.
   list.setVisibility(View.GONE);
   view.findViewById(R.id.progress_loading).setVisibility(loading || state.hasSnapshot()?View.VISIBLE:View.GONE);
   view.findViewById(R.id.progress_error).setVisibility(error?View.VISIBLE:View.GONE);
   view.findViewById(R.id.progress_refresh).setEnabled(!loading && !state.hasSnapshot());
   if (!state.hasSnapshot()) return;
   history.submitList(state.getSnapshot().getQuizHistory(),() -> {
    if (owner.getLifecycle().getCurrentState()==androidx.lifecycle.Lifecycle.State.DESTROYED
        || generation!=renderGeneration[0] || model.state().getValue()!=state) return;
    summary.update(state);
    list.setVisibility(View.VISIBLE);
    view.findViewById(R.id.progress_loading).setVisibility(View.GONE);
    view.findViewById(R.id.progress_refresh).setEnabled(true);
   });
  });
 }
 @Override public void onResume() { super.onResume();model.refresh(); }
 @Override public void onDestroyView() {
  ((RecyclerView)requireView().findViewById(R.id.progress_list)).setAdapter(null);super.onDestroyView();
 }
 private static final class SummaryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private ProgressViewState state;
  private LearningActivity.Period period = LearningActivity.Period.TODAY;
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
   ((TextView)view.findViewById(R.id.total_learned)).setText(
    java.text.NumberFormat.getIntegerInstance().format(s.getTotalLearned()));
   ((TextView)view.findViewById(R.id.progress_counts)).setText(view.getContext().getString(R.string.progress_counts,
    s.getTotalLearned(),s.getLearningCount(),s.getReviewingCount(),s.getMasteredCount()));
   int[] ids={R.id.box_0,R.id.box_1,R.id.box_2,R.id.box_3,R.id.box_4,R.id.box_5};
   int[] labels={R.string.progress_box_0,R.string.progress_box_1,R.string.progress_box_2,R.string.progress_box_3,R.string.progress_box_4,R.string.progress_box_5};
   for(int i=0;i<6;i++) ((TextView)view.findViewById(ids[i])).setText(view.getContext().getString(
    R.string.progress_box_count,view.getContext().getString(labels[i]),s.getBoxCount(i)));
   bindLearningActivity(view, state.getSnapshot().getActivity());
   view.findViewById(R.id.progress_empty).setVisibility(state.isProgressEmpty()?View.VISIBLE:View.GONE);
   view.findViewById(R.id.history_empty).setVisibility(state.isHistoryEmpty()?View.VISIBLE:View.GONE);
  }
  private void bindLearningActivity(View view, LearningActivity activity) {
   android.content.Context context = view.getContext();
   Button selector = view.findViewById(R.id.learning_period);
   int[] labels = {R.string.learning_today, R.string.learning_week, R.string.learning_all};
   LearningActivity.Period[] periods = LearningActivity.Period.values();
   String[] options = new String[labels.length];
   for (int i = 0; i < labels.length; i++) options[i] = context.getString(labels[i]);
   renderTotals(view, activity, options[period.ordinal()]);
   selector.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
     .setTitle(R.string.learning_statistics)
     .setSingleChoiceItems(options, period.ordinal(), (dialog, index) -> {
      period = periods[index];
      renderTotals(view, activity, options[index]);
      dialog.dismiss();
     })
     .setNegativeButton(R.string.personal_cancel, null)
     .show());

   ((TextView) view.findViewById(R.id.learning_streak)).setText(
     context.getString(R.string.learning_streak, activity.streak()));
   TextView goal = view.findViewById(R.id.learning_goal);
   long learned = activity.totals(LearningActivity.Period.TODAY).learned();
   goal.setVisibility(activity.goal() > 0 ? View.VISIBLE : View.GONE);
   goal.setText(context.getString(learned >= activity.goal() ? R.string.learning_goal_met
     : R.string.learning_goal, learned, activity.goal()));

   var goalBar = (com.google.android.material.progressindicator.LinearProgressIndicator)
     view.findViewById(R.id.learning_goal_bar);
   goalBar.setVisibility(goal.getVisibility());
   goalBar.setMax(Math.max(1, activity.goal()));
   goalBar.setProgress((int) Math.min(learned, activity.goal()));

   List<LearningActivity.ReviewDay> days = activity.reviewHistory();
   TextView latest = view.findViewById(R.id.learning_latest_review);
   Button open = view.findViewById(R.id.learning_open_reviews);
   latest.setText(days.isEmpty() ? context.getString(R.string.learning_no_reviews)
     : reviewLabel(context, days.get(0)));
   open.setVisibility(days.size() > 1 ? View.VISIBLE : View.GONE);
   open.setOnClickListener(v -> {
    List<String> rows = new ArrayList<>();
    for (LearningActivity.ReviewDay day : days) rows.add(reviewLabel(context, day));
    new MaterialAlertDialogBuilder(context).setTitle(R.string.learning_review_history)
      .setMessage(TextUtils.join("\n\n", rows))
      .setPositiveButton(R.string.ui_quiz_review_close, null).show();
   });
  }

  private void renderTotals(View view, LearningActivity activity, String label) {
   android.content.Context context = view.getContext();
   LearningActivity.Totals totals = activity.totals(period);
   String accuracy = totals.quizzes() == 0 ? context.getString(R.string.learning_no_quiz)
     : NumberFormat.getPercentInstance().format(totals.accuracy() / 100.0);
   ((Button) view.findViewById(R.id.learning_period)).setText(label);
   metric(view, R.id.learning_metric_new, R.string.ui_metric_new, totals.learned());
   metric(view, R.id.learning_metric_reviews, R.string.ui_metric_reviews, totals.reviews());
   metric(view, R.id.learning_metric_quizzes, R.string.ui_metric_quizzes, totals.quizzes());
   metric(view, R.id.learning_metric_accuracy, R.string.ui_metric_accuracy, accuracy);
  }

  private static void metric(View view, int id, int label, Object value) {
   ((TextView) view.findViewById(id)).setText(view.getContext().getString(label, value));
  }

  private static String reviewLabel(android.content.Context context, LearningActivity.ReviewDay day) {
   var locale = context.getResources().getConfiguration().getLocales().get(0);
   String date = day.date().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
   return context.getString(R.string.learning_review_day, date, day.count());
  }
 }
}
