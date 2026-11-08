package com.vocamind.app.feature.progress;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.vocamind.app.R;
import com.vocamind.app.domain.progress.QuizHistoryItem;
import java.util.TimeZone;
final class QuizHistoryAdapter extends ListAdapter<QuizHistoryItem,QuizHistoryAdapter.Holder> {
 QuizHistoryAdapter() {
  super(new DiffUtil.ItemCallback<QuizHistoryItem>() {
   public boolean areItemsTheSame(@NonNull QuizHistoryItem a,@NonNull QuizHistoryItem b) { return a.getAttemptId()==b.getAttemptId(); }
   public boolean areContentsTheSame(@NonNull QuizHistoryItem a,@NonNull QuizHistoryItem b) {
    return a.getCompletedAtEpochMillis()==b.getCompletedAtEpochMillis() && a.getQuestionCount()==b.getQuestionCount()
     && a.getCorrectCount()==b.getCorrectCount() && a.getScorePercent()==b.getScorePercent();
   }
  });setHasStableIds(true);
 }
 @Override public long getItemId(int position) { return getItem(position).getAttemptId(); }
 @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
  return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quiz_history,parent,false));
 }
 @Override public void onBindViewHolder(@NonNull Holder holder,int position) {
  QuizHistoryItem item=getItem(position);
  holder.date.setText(QuizHistoryFormatter.completedAt(item.getCompletedAtEpochMillis(),
   holder.itemView.getResources().getConfiguration().getLocales().get(0),TimeZone.getDefault()));
  holder.result.setText(holder.itemView.getContext().getString(R.string.progress_quiz_result,
   item.getQuestionCount(),item.getCorrectCount(),item.getWrongCount(),item.getScorePercent()));
 }
 static final class Holder extends RecyclerView.ViewHolder {
  final TextView date,result;
  Holder(View view) { super(view);date=view.findViewById(R.id.history_date);result=view.findViewById(R.id.history_result); }
 }
}
