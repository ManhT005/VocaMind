package com.vocamind.app.data.repository;

import androidx.annotation.WorkerThread;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.catalog.*;
import com.vocamind.app.domain.common.*;
import com.vocamind.app.domain.vocabulary.*;

/** Read-only catalog boundary plus targeted, transactional userdata writes. No progress copies. */
@WorkerThread
public final class RoomVocabularyRepository implements VocabularyRepository {
    private final CatalogRepository catalog;
    private final UserDatabase user;
    private final java.util.function.LongSupplier clock;
    public RoomVocabularyRepository(CatalogRepository catalog, UserDatabase user) {
        this(catalog, user, System::currentTimeMillis);
    }
    public RoomVocabularyRepository(CatalogRepository catalog, UserDatabase user, java.util.function.LongSupplier clock) {
        this.catalog = catalog;
        this.user = user;
        this.clock = clock;
    }
    private RepoResult<CatalogVocabulary> word(String pack, String id) {
        if (pack == null || pack.trim().isEmpty() || id == null || id.trim().isEmpty())
            return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.read(() -> {
            var found = catalog.getVocabulary(id);
            if (found.isEmpty() || catalog.getWords(pack).stream().noneMatch(w -> w.stableId.equals(id)))
                return RepoResult.failure(RepoError.NOT_FOUND);
            return RepoResult.success(found.get());
        });
    }
    @Override public RepoResult<VocabularyDetail> getDetail(String pack, String id) {
        var result = word(pack, id);
        if (!result.isSuccess()) return RepoResult.failure(result.getError());
        return RepositoryGuard.read(() -> {
            var progress = user.userDao().progress(id);
            return RepoResult.success(new VocabularyDetail(result.get(), progress != null && progress.isSaved));
        });
    }
    @Override public RepoResult<Boolean> setSaved(String pack, String id, boolean saved) {
        var result = word(pack, id);
        if (!result.isSuccess()) return RepoResult.failure(result.getError());
        return RepositoryGuard.write(() -> user.runInTransaction(() -> {
            int affected = user.userDao().setSaved(id, saved);
            if (affected == 0) {
                if (!saved) {
                    // Confirm missing-row unsave; never create progress just to store false.
                    if (user.userDao().progress(id) != null) throw new IllegalStateException("Save update skipped");
                } else {
                    long inserted = user.userDao().insertSavedProgressIfAbsent(id, clock.getAsLong());
                    if (inserted == -1 && user.userDao().setSaved(id, true) != 1)
                        throw new IllegalStateException("Save was not persisted");
                }
            } else if (affected != 1) throw new IllegalStateException("Unexpected save row count");
            // UPDATE and commit are the confirmation; no fallible post-commit reread.
            return RepoResult.success(saved);
        }));
    }
}
