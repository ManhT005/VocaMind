package com.vocamind.app.domain.pack;

import com.vocamind.app.domain.common.RepoResult;
import java.util.List;
import java.util.Objects;

/** Blocking query: all catalog packs merged with the user's progress. Use a worker executor. */
public final class GetPacks {
    private final PackRepository repository;

    public GetPacks(PackRepository repository) { this.repository = Objects.requireNonNull(repository); }

    public RepoResult<List<PackState>> execute() { return repository.getPackStates(); }
}
