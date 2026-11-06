package com.vocamind.app.feature.packs;

import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.pack.PackState;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** query runs on the worker, e.g. {@code getPacks::execute}. */
public final class PacksViewModel extends AsyncListViewModel<PackState> {
    private final BiFunction<String, Boolean, RepoResult<List<PackState>>> write;

    public PacksViewModel(Supplier<RepoResult<List<PackState>>> query, Executor worker, Executor main, Runnable close) {
        this(query, (id, active) -> RepoResult.failure(com.vocamind.app.domain.common.RepoError.INVALID_ARGUMENT),
                worker, main, close);
    }

    public PacksViewModel(Supplier<RepoResult<List<PackState>>> query,
            BiFunction<String, Boolean, RepoResult<List<PackState>>> write,
            Executor worker, Executor main, Runnable close) {
        super(query, worker, main, close);
        this.write = write;
    }

    public void setActive(String id, boolean active) {
        mutate(() -> write.apply(id, active));
    }
}
