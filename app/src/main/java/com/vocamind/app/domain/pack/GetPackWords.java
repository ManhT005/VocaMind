package com.vocamind.app.domain.pack;

import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.StudyItem;
import java.util.List;
import java.util.Objects;

/** Blocking query: words of one pack in catalog order with progress. Use a worker executor. */
public final class GetPackWords {
    private final PackRepository repository;

    public GetPackWords(PackRepository repository) { this.repository = Objects.requireNonNull(repository); }

    public RepoResult<List<StudyItem>> execute(String packId) { return repository.getPackWords(packId); }
}
