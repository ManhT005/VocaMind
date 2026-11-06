package com.vocamind.app.data.repository;

import androidx.annotation.WorkerThread;
import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.user.UserDao;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.data.user.ReviewStateChangedException;
import com.vocamind.app.data.user.entity.ReviewEvent;
import com.vocamind.app.domain.learning.*;
import java.util.Objects;
import java.util.Optional;

/** Single production writer for DATA-04 compatibility and DATA-06 use cases. */
@WorkerThread
public final class RoomReviewRepository implements ReviewRepository {
    private final CatalogDao catalog;
    private final UserDao dao;

    public RoomReviewRepository(CatalogDao catalog, UserDatabase userDb) {
        this.catalog = Objects.requireNonNull(catalog);
        this.dao = Objects.requireNonNull(userDb).userDao();
    }

    private void requireCatalogWord(String id) {
        ReviewProgress.requireId(id);
        try {
            if (catalog.vocabulary(id) == null)
                throw new ReviewWriteException(ReviewWriteException.Reason.NOT_FOUND, null);
        } catch (ReviewWriteException failure) { throw failure; }
        catch (RuntimeException failure) {
            throw new ReviewWriteException(ReviewWriteException.Reason.READ_FAILED, failure);
        }
    }

    @Override public Optional<ReviewProgress> getProgress(String id) {
        requireCatalogWord(id);
        try {
            return Optional.ofNullable(dao.progress(id))
                    .map(row -> ReviewProgress.fromWordProgress(UserDataMapper.progress(row)));
        } catch (RepositoryGuard.InvalidDataException | IllegalArgumentException invalid) {
            throw new ReviewWriteException(ReviewWriteException.Reason.INVALID_DATA, invalid);
        } catch (RuntimeException failure) {
            throw new ReviewWriteException(ReviewWriteException.Reason.READ_FAILED, failure);
        }
    }

    @Override public void recordReview(Optional<ReviewProgress> expected, ReviewProgress updated,
                                       Rating rating, long reviewedAt) {
        Objects.requireNonNull(expected); Objects.requireNonNull(updated); Objects.requireNonNull(rating);
        ReviewProgress old = expected.orElse(null);
        if (old != null && !old.vocabularyId.equals(updated.vocabularyId))
            throw new IllegalArgumentException("Review stableId mismatch");
        Long learnedAt = old == null || old.learnedAt == null ? Long.valueOf(reviewedAt) : old.learnedAt;
        if (reviewedAt < 0 || !Objects.equals(updated.lastReviewedAt, reviewedAt)
                || updated.reviewCount != (old == null ? 1 : Math.addExact(old.reviewCount, 1))
                || !Objects.equals(updated.learnedAt, learnedAt)
                || updated.isSaved != (old != null && old.isSaved))
            throw new IllegalArgumentException("Review history invariants");
        // Catalog is read-only and separate: existence check precedes userdata's transaction.
        requireCatalogWord(updated.vocabularyId);
        ReviewEvent event = new ReviewEvent();
        event.vocabularyId = updated.vocabularyId; event.rating = rating.name();
        event.reviewedAt = reviewedAt; event.boxBefore = old == null ? 0 : old.box;
        event.boxAfter = updated.box; event.nextDueAt = updated.dueAt;
        try {
            dao.recordReviewIfUnchanged(expected.map(p -> UserDataMapper.progressRow(p.toWordProgress())).orElse(null),
                    event, UserDataMapper.progressRow(updated.toWordProgress()));
        } catch (ReviewStateChangedException conflict) {
            throw new ReviewWriteException(ReviewWriteException.Reason.CONFLICT, conflict);
        } catch (RuntimeException failure) {
            throw new ReviewWriteException(ReviewWriteException.Reason.STORAGE, failure);
        }
    }
}
