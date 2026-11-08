package com.vocamind.app.feature.progress;
import androidx.lifecycle.*;
import com.vocamind.app.domain.progress.*;
import java.util.concurrent.Executor;
/** All request bookkeeping and publication run on the main dispatcher. */
public final class ProgressViewModel extends ViewModel {
 private final GetProgressUseCase useCase;
 private final Executor worker, main;
 private final Runnable cleanup;
 private final MutableLiveData<ProgressViewState> state=new MutableLiveData<>(ProgressViewState.loading());
 private boolean running,pending,cleared;
 public ProgressViewModel(GetProgressUseCase useCase,Executor worker,Executor main,Runnable cleanup) {
  this.useCase=useCase;this.worker=worker;this.main=main;this.cleanup=cleanup;
 }
 public LiveData<ProgressViewState> state() { return state; }
 public void refresh() {
  if(cleared) return;
  if(running) { pending=true;return; }
  running=true;state.setValue(ProgressViewState.loading());
  worker.execute(() -> {
   ProgressReadResult result;
   try { result=useCase.execute();if(result==null) result=ProgressReadResult.failure(ProgressReadResult.Error.READ_FAILED); }
   catch(RuntimeException e) { result=ProgressReadResult.failure(ProgressReadResult.Error.READ_FAILED); }
   ProgressReadResult completed=result;
   main.execute(() -> {
    if(cleared) return;
    running=false;
    // Superseded reads never publish over a pending refresh; only one read runs at a time.
    if(pending) { pending=false;refresh(); }
    else state.setValue(ProgressViewState.fromResult(completed));
   });
  });
 }
 @Override protected void onCleared() { cleared=true;pending=false;cleanup.run(); }
}
