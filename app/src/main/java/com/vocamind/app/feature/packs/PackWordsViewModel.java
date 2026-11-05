package com.vocamind.app.feature.packs;

import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.StudyItem;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class PackWordsViewModel extends AsyncListViewModel<StudyItem> {
    public PackWordsViewModel(Supplier<RepoResult<List<StudyItem>>> query, Executor worker, Executor main, Runnable close) {
        super(query, worker, main, close);
    }
}
