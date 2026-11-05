package com.vocamind.app.data.repository;

import androidx.annotation.WorkerThread;
import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.data.user.entity.UserWordProgress;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.ReviewRepository;
import com.vocamind.app.domain.learning.ScheduleResult;
import com.vocamind.app.domain.learning.WordProgress;

@WorkerThread
public final class RoomReviewRepository implements ReviewRepository {
    private final CatalogDao catalog;
    private final UserDatabase userDb;

    public RoomReviewRepository(CatalogDao catalog, UserDatabase userDb) {
        this.catalog = catalog;
        this.userDb = userDb;
    }

    @Override public RepoResult<WordProgress> recordReview(String id, ScheduleResult result, long reviewedAt) {
        if (id == null || id.trim().isEmpty() || result == null || reviewedAt < 0) {
            return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        }
        // Catalog is a separate file: check logically first, then one transaction in userdata only.
        try {
            if (catalog.vocabulary(id) == null) return RepoResult.failure(RepoError.NOT_FOUND);
        } catch (RuntimeException e) {
            return RepoResult.failure(RepoError.READ_FAILED);
        }
        return RepositoryGuard.write(() -> userDb.runInTransaction(() -> {
            UserWordProgress existing = userDb.userDao().progress(id);
            int storedBox = existing == null ? 0 : existing.box;
            if (storedBox != result.getBoxBefore()) return RepoResult.<WordProgress>failure(RepoError.INVALID_ARGUMENT);
            UserWordProgress next = UserDataMapper.afterReview(id, existing, result, reviewedAt);
            userDb.userDao().recordReview(UserDataMapper.reviewEvent(id, result, reviewedAt), next);
            return RepoResult.success(UserDataMapper.progress(next));
        }));
    }
}
