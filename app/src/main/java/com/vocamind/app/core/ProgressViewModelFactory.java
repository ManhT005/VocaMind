package com.vocamind.app.core;
import android.content.Context;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.vocamind.app.domain.progress.*;
import com.vocamind.app.feature.progress.ProgressViewModel;
import java.util.concurrent.*;
public final class ProgressViewModelFactory implements ViewModelProvider.Factory {
 private final Context context;
 public ProgressViewModelFactory(Context context) { this.context=context.getApplicationContext(); }
 @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
  if(type!=ProgressViewModel.class) throw new IllegalArgumentException("Unsupported model");
  ExecutorService worker=Executors.newSingleThreadExecutor();
  Handler main=new Handler(Looper.getMainLooper());
  Source source=new Source(context);
  return type.cast(new ProgressViewModel(source,worker,main::post,() -> {
   worker.execute(source::close);worker.shutdown();
  }));
 }
 /** Private connections are reused and closed on their worker after the last read. */
 private static final class Source implements GetProgressUseCase {
  final Context context;CatalogProvider catalog;RepositoryProvider repositories;
  Source(Context context) { this.context=context; }
  public ProgressReadResult execute() {
   if(repositories==null) {
    if(catalog==null) catalog=new CatalogProvider(context);
    repositories=new RepositoryProvider(context,catalog);
   }
   return repositories.progressUseCase().execute();
  }
  void close() {
   try { if(repositories!=null) repositories.close(); }
   finally { if(catalog!=null) catalog.close(); }
  }
 }
}
