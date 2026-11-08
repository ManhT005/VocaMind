package com.vocamind.app.feature.progress;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.*;
import androidx.recyclerview.widget.*;
import com.vocamind.app.R;
import com.vocamind.app.domain.progress.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Hold the real ListAdapter diff to expose mixed snapshots and late callbacks deterministically. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ProgressRenderingTest {
 static final class Queue implements Executor {
  final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
  public void execute(Runnable task) { tasks.add(task); }
  void run() { tasks.remove().run(); }
 }
 static ProgressViewModel model;
 static Queue diff;
 public static class FixtureFragment extends ProgressFragment {
  @Override protected QuizHistoryAdapter historyAdapter() {
   return new QuizHistoryAdapter(new AsyncDifferConfig.Builder<>(QuizHistoryAdapter.DIFF)
    .setBackgroundThreadExecutor(diff).setMainThreadExecutor(Runnable::run).build());
  }
  @Override protected ViewModelProvider.Factory viewModelFactory() {
   return new ViewModelProvider.Factory() {
    @NonNull public <T extends ViewModel> T create(@NonNull Class<T> type) { return type.cast(model); }
   };
  }
 }
 Queue worker;
 ProgressReadResult next;
 ActivityController<FragmentActivity> controller;
 FixtureFragment fragment;
 View root;
 @Before public void open() {
  worker=new Queue();diff=new Queue();next=result(1,0);
  model=new ProgressViewModel(() -> next,worker,Runnable::run,() -> {});
  controller=Robolectric.buildActivity(FragmentActivity.class);
  controller.get().setTheme(R.style.Theme_VocaMind);controller.setup();
  fragment=new FixtureFragment();
  controller.get().getSupportFragmentManager().beginTransaction().replace(android.R.id.content,fragment).commitNow();
  root=fragment.requireView();worker.run();assertEquals(View.VISIBLE,list().getVisibility());
 }
 @After public void close() {
  controller.pause().stop().destroy();model=null;diff=null;
 }
 static ProgressReadResult result(long attempt,long total) {
  return ProgressReadResult.success(new ProgressSnapshot(new ProgressSummary(total,new long[]{total,0,0,0,0,0}),
   Collections.singletonList(new QuizHistoryItem(attempt,attempt*1000,4,3,75))));
 }
 RecyclerView list() { return root.findViewById(R.id.progress_list); }
 void load(long attempt,long total) { next=result(attempt,total);model.refresh();worker.run(); }
 @SuppressWarnings({"rawtypes","unchecked"}) String counts() {
  RecyclerView.Adapter adapter=list().getAdapter();
  RecyclerView.ViewHolder holder=adapter.createViewHolder(list(),adapter.getItemViewType(0));
  adapter.bindViewHolder(holder,0);
  return ((TextView)holder.itemView.findViewById(R.id.progress_counts)).getText().toString();
 }
 @Test public void hidesOldHistoryUntilMatchingSummaryAndNewHistoryAreCommitted() {
  load(2,1);
  assertEquals(1,list().getAdapter().getItemId(1)); // Old list exists but must stay invisible.
  assertEquals(View.GONE,list().getVisibility());
  assertEquals(View.VISIBLE,root.findViewById(R.id.progress_loading).getVisibility());
  assertFalse(root.findViewById(R.id.progress_refresh).isEnabled());
  diff.run();
  assertEquals(View.VISIBLE,list().getVisibility());assertEquals(2,list().getAdapter().getItemId(1));
  assertEquals(controller.get().getString(R.string.progress_counts,1L,1L,0L,0L),counts());
  assertEquals(View.GONE,root.findViewById(R.id.progress_loading).getVisibility());
  assertTrue(root.findViewById(R.id.progress_refresh).isEnabled());
 }
 @Test public void lateCommitCannotRevealDataOverNewError() {
  load(2,1);model.refresh();next=ProgressReadResult.failure(ProgressReadResult.Error.READ_FAILED);worker.run();
  diff.run();
  assertEquals(View.GONE,list().getVisibility());
  assertEquals(View.VISIBLE,root.findViewById(R.id.progress_error).getVisibility());
 }
 @Test public void supersededCommitCannotRevealDataWhileNewDiffIsPending() {
  load(2,1);load(3,2);
  diff.run();assertEquals(View.GONE,list().getVisibility());
  diff.run();assertEquals(View.VISIBLE,list().getVisibility());assertEquals(3,list().getAdapter().getItemId(1));
  assertEquals(controller.get().getString(R.string.progress_counts,2L,2L,0L,0L),counts());
 }
 @Test public void commitAfterDestroyViewCannotBindOldView() {
  load(2,1);
  controller.get().getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();
  diff.run();assertEquals(View.GONE,list().getVisibility());assertNull(list().getAdapter());
 }
}
