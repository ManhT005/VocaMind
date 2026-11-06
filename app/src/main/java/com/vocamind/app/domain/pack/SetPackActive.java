package com.vocamind.app.domain.pack;

import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Blocking, explicit user action. Success is a fresh persisted snapshot, never a UI toggle. */
public final class SetPackActive {
    private final PackRepository repository;
    private final LongSupplier clock;

    public SetPackActive(PackRepository repository, LongSupplier clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public RepoResult<List<PackState>> execute(String packId, boolean active) {
        if (packId == null || packId.trim().isEmpty()) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        RepoResult<List<PackState>> before = repository.getPackStates();
        if (!before.isSuccess()) return before;
        if (before.get().stream().noneMatch(p -> p.getPack().id.equals(packId))) {
            return RepoResult.failure(RepoError.NOT_FOUND);
        }
        RepoResult<?> write = active ? repository.activatePack(packId, clock.getAsLong()) : repository.pausePack(packId);
        if (!write.isSuccess()) return RepoResult.failure(write.getError());
        // A read failure here does not imply rollback. Retrying the target state is idempotent.
        return repository.getPackStates();
    }
}
