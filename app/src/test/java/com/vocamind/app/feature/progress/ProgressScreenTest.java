package com.vocamind.app.feature.progress;

import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import com.vocamind.app.R;
import com.vocamind.app.core.ProgressProvider;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.data.user.entity.*;
import com.vocamind.app.domain.progress.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real file-backed Room -> repository -> use case -> ViewModel -> Fragment bindings. */
@RunWith(RobolectricTestRunner.class) @Config(sdk={26,35}) @SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ProgressScreenTest {
 static ProgressViewModel model;
 static final class Queue implements Executor {
  final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
  public void execute(Runnable r) { tasks.add(r); }
  void run() { tasks.remove().run(); }
 }
 public static class FixtureFragment extends ProgressFragment {
  @Override protected QuizHistoryAdapter historyAdapter() {
   return new QuizHistoryAdapter(new androidx.recyclerview.widget.AsyncDifferConfig.Builder<>(QuizHistoryAdapter.DIFF)
    .setBackgroundThreadExecutor(Runnable::run).setMainThreadExecutor(Runnable::run).build());
  }
  @Override protected ViewModelProvider.Factory viewModelFactory() {
   return new ViewModelProvider.Factory() {
    @NonNull public <T extends ViewModel> T create(@NonNull Class<T> type) { return type.cast(model); }
   };
  }
 }
 UserDatabase db;ExecutorService io;Queue worker,main;String name;
 ActivityController<FragmentActivity> controller;FixtureFragment fragment;
 @Before public void setup() throws Exception {
  io=Executors.newSingleThreadExecutor();name="progress02-"+UUID.randomUUID()+".db";
  db=io.submit(() -> UserDatabase.open(ApplicationProvider.getApplicationContext(),name)).get();
  worker=new Queue();main=new Queue();
  GetProgressUseCase useCase=ProgressProvider.create(db)::readProgress;
  model=new ProgressViewModel(useCase,worker,main,() -> {});
 }
 @After public void close() throws Exception {
  if(controller!=null) controller.pause().stop().destroy();
  io.submit(() -> db.close()).get();io.shutdownNow();
  ApplicationProvider.getApplicationContext().deleteDatabase(name);model=null;
 }
 void open() {
  controller=Robolectric.buildActivity(FragmentActivity.class);
  controller.get().setTheme(R.style.Theme_VocaMind);controller.setup();
  fragment=new FixtureFragment();
  controller.get().getSupportFragmentManager().beginTransaction().replace(android.R.id.content,fragment).commitNow();
 }
 void complete() throws Exception {
  io.submit(() -> worker.run()).get();main.run();Shadows.shadowOf(Looper.getMainLooper()).idle();
 }
 RecyclerView list() { return fragment.requireView().findViewById(R.id.progress_list); }
 @SuppressWarnings({"rawtypes","unchecked"}) View bind(int position) {
  RecyclerView.Adapter adapter=list().getAdapter();
  RecyclerView.ViewHolder holder=adapter.createViewHolder(list(),adapter.getItemViewType(position));
  adapter.bindViewHolder(holder,position);return holder.itemView;
 }
 String text(View view,int id) { return ((TextView)view.findViewById(id)).getText().toString(); }
 @Test public void emptyAndErrorsRetryRenderWithoutInventedZeros() throws Exception {
  open();assertEquals(View.GONE,list().getVisibility());complete();
  View header=bind(0);assertTrue(text(header,R.id.progress_counts).contains("0"));
  assertEquals(View.VISIBLE,header.findViewById(R.id.progress_empty).getVisibility());
  assertEquals(View.VISIBLE,header.findViewById(R.id.history_empty).getVisibility());
  assertEquals(controller.get().getString(R.string.progress_mastered_explanation),text(header,R.id.mastered_explanation));
  io.submit(() -> { UserWordProgress row=new UserWordProgress();row.vocabularyId="invalid";row.box=0;db.userDao().saveProgress(row);db.getOpenHelper().getWritableDatabase().execSQL("UPDATE UserWordProgress SET box=9"); }).get();
  model.refresh();complete();assertEquals(View.GONE,list().getVisibility());
  assertEquals(View.VISIBLE,fragment.requireView().findViewById(R.id.progress_error).getVisibility());
  io.submit(() -> db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM UserWordProgress")).get();
  fragment.requireView().findViewById(R.id.progress_retry).performClick();complete();
  assertEquals(View.VISIBLE,list().getVisibility());
 }
 @Test public void twelveWordsAllBoxesAndUncappedHistoryMatchFixtureAndRefresh() throws Exception {
  io.submit(() -> {
   int id=0;int[] counts={2,1,3,2,1,3};
   for(int box=0;box<6;box++) for(int n=0;n<counts[box];n++) {
    UserWordProgress row=new UserWordProgress();row.vocabularyId=String.format(Locale.ROOT,"%016x",id++);
    row.box=box;row.dueAt=0;row.isSaved=true;row.learnedAt=null;db.userDao().saveProgress(row);
   }
   for(int i=0;i<12;i++) {
    QuizAttempt q=new QuizAttempt();q.completedAt=1000;q.questionCount=i==0?6:10;
    q.correctCount=i==0?2:7;q.scorePercent=i==0?33:70;db.userDao().insertQuiz(q);
   }
  }).get();
  open();complete();View header=bind(0);
  assertEquals(controller.get().getString(R.string.progress_counts,12L,3L,5L,4L),text(header,R.id.progress_counts));
  int[] ids={R.id.box_0,R.id.box_1,R.id.box_2,R.id.box_3,R.id.box_4,R.id.box_5};
  int[] expected={2,1,3,2,1,3};
  for(int i=0;i<6;i++) assertTrue(text(header,ids[i]).endsWith(": "+expected[i]));
  assertEquals(13,list().getAdapter().getItemCount());
  assertTrue(list().getAdapter().hasStableIds());
  assertTrue(text(bind(1),R.id.history_result).contains("70%"));
  assertTrue(text(bind(12),R.id.history_result).contains("33%"));
  // Leave and return: a local write is reflected by the sole onResume entry point.
  controller.pause();io.submit(() -> { UserWordProgress row=new UserWordProgress();row.vocabularyId="new";row.box=5;db.userDao().saveProgress(row); }).get();
  controller.resume();complete();
  assertEquals(controller.get().getString(R.string.progress_counts,13L,3L,5L,5L),text(bind(0),R.id.progress_counts));
  controller.recreate();
  fragment=(FixtureFragment)controller.get().getSupportFragmentManager().findFragmentById(android.R.id.content);
  complete();assertEquals(13,list().getAdapter().getItemCount());
  assertEquals(controller.get().getString(R.string.progress_counts,13L,3L,5L,5L),text(bind(0),R.id.progress_counts));
 }
 @Test public void historyOnlyAndDestroyedViewDuringReadAreSafe() throws Exception {
  io.submit(() -> { QuizAttempt q=new QuizAttempt();q.completedAt=1000;q.questionCount=4;q.correctCount=3;q.scorePercent=75;db.userDao().insertQuiz(q); }).get();
  open();complete();View header=bind(0);
  assertEquals(View.VISIBLE,header.findViewById(R.id.progress_empty).getVisibility());
  assertEquals(View.GONE,header.findViewById(R.id.history_empty).getVisibility());
  assertEquals(2,list().getAdapter().getItemCount());
  model.refresh();controller.get().getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
  complete();assertTrue(model.state().getValue().hasSnapshot());
 }
}
