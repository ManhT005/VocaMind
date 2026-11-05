package com.vocamind.app.feature.packs;

import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.pack.PackState;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** query runs on the worker, e.g. {@code getPacks::execute}. */
public final class PacksViewModel extends AsyncListViewModel<PackState> {
    public PacksViewModel(Supplier<RepoResult<List<PackState>>> query, Executor worker, Executor main, Runnable close) {
        super(query, worker, main, close);
    }
}
