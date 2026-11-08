package com.vocamind.app.feature.progress;
import com.vocamind.app.domain.progress.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import java.util.concurrent.Executor;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ProgressViewModelTest {
 static class Queue implements Executor {
  final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
  public void execute(Runnable r) { tasks.add(r); }
  void run() { tasks.remove().run(); }
 }
 @Test public void coalescesRefreshAndNeverPublishesSupersededResult() {
  Queue worker=new Queue(),main=new Queue();int[] reads={0};
  ProgressViewModel vm=new ProgressViewModel(() -> { reads[0]++;return ProgressReadResult.success(ProgressSnapshot.empty()); },worker,main,() -> {});
  vm.refresh();for(int i=0;i<30;i++) vm.refresh();
  assertEquals(1,worker.tasks.size());worker.run();assertEquals(1,reads[0]);main.run();
  assertEquals(ProgressViewState.Status.LOADING,vm.state().getValue().getStatus());
  assertEquals(1,worker.tasks.size());worker.run();main.run();
  assertEquals(2,reads[0]);assertEquals(ProgressViewState.Status.EMPTY,vm.state().getValue().getStatus());
  assertTrue(worker.tasks.isEmpty());
 }
 @Test public void retryPreservesInvalidAndReadErrorsAndRefreshHidesOldSnapshot() {
  Queue worker=new Queue();ProgressReadResult[] result={ProgressReadResult.failure(ProgressReadResult.Error.INVALID_DATA)};
  ProgressViewModel vm=new ProgressViewModel(() -> result[0],worker,Runnable::run,() -> {});
  vm.refresh();worker.run();assertEquals(ProgressReadResult.Error.INVALID_DATA,vm.state().getValue().getError());
  result[0]=ProgressReadResult.success(ProgressSnapshot.empty());vm.refresh();worker.run();
  assertTrue(vm.state().getValue().hasSnapshot());vm.refresh();assertFalse(vm.state().getValue().hasSnapshot());
  result[0]=ProgressReadResult.failure(ProgressReadResult.Error.READ_FAILED);worker.run();
  assertEquals(ProgressReadResult.Error.READ_FAILED,vm.state().getValue().getError());
 }
 @Test public void unexpectedFailureIsErrorAndClearDiscardsCompletionAndRefresh() {
  Queue worker=new Queue(),main=new Queue();int[] closed={0};
  ProgressViewModel vm=new ProgressViewModel(() -> { throw new IllegalStateException(); },worker,main,() -> closed[0]++);
  vm.refresh();worker.run();main.run();assertTrue(vm.state().getValue().canRetry());
  vm.refresh();worker.run();vm.onCleared();main.run();vm.refresh();
  assertEquals(1,closed[0]);assertTrue(worker.tasks.isEmpty());assertFalse(vm.state().getValue().hasSnapshot());
 }
 @Test public void deviceZoneChangesCalendarDayWithoutChangingTimestamp() {
  long time=java.time.Instant.parse("2026-11-08T00:30:00Z").toEpochMilli();
  assertEquals("Nov 7, 2026, 4:30 PM",QuizHistoryFormatter.completedAt(time,Locale.US,TimeZone.getTimeZone("America/Los_Angeles")));
  assertEquals("Nov 8, 2026, 7:30 AM",QuizHistoryFormatter.completedAt(time,Locale.US,TimeZone.getTimeZone("Asia/Ho_Chi_Minh")));
  assertNotEquals(QuizHistoryFormatter.completedAt(time,Locale.US,TimeZone.getTimeZone("UTC")),
   QuizHistoryFormatter.completedAt(time,Locale.FRANCE,TimeZone.getTimeZone("UTC")));
 }
}
