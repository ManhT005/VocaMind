package com.vocamind.app.data.user;

import androidx.annotation.WorkerThread;
import com.vocamind.app.data.user.entity.ReviewEvent;
import com.vocamind.app.domain.learning.*;
import java.util.Objects;
import java.util.Optional;

@WorkerThread
public final class RoomReviewRepository implements ReviewRepository {
    private final UserDao dao;
    public RoomReviewRepository(UserDao dao) { this.dao = Objects.requireNonNull(dao); }
    @Override public Optional<ReviewProgress> getProgress(String vocabularyId) {
        ReviewProgress.requireId(vocabularyId);
        try {
            return Optional.ofNullable(dao.progress(vocabularyId)).map(ReviewMapper::toDomain);
        } catch (RuntimeException failure) {
            throw new ReviewWriteException(ReviewWriteException.Reason.STORAGE, failure);
        }
    }
    @Override public void recordReview(Optional<ReviewProgress> expected, ReviewProgress updated,
                                       Rating rating, long reviewedAt) {
        Objects.requireNonNull(expected); Objects.requireNonNull(updated); Objects.requireNonNull(rating);
        if (expected.isPresent() && !expected.get().vocabularyId.equals(updated.vocabularyId))
            throw new IllegalArgumentException("Review stableId mismatch");
        ReviewEvent event = new ReviewEvent();
        event.vocabularyId = updated.vocabularyId; event.rating = rating.name();
        event.reviewedAt = reviewedAt; event.boxBefore = expected.map(p -> p.box).orElse(0);
        event.boxAfter = updated.box; event.nextDueAt = updated.dueAt;
        try {
            dao.recordReviewIfUnchanged(expected.map(ReviewMapper::toEntity).orElse(null),
                    event, ReviewMapper.toEntity(updated));
        } catch (ReviewStateChangedException conflict) {
            throw new ReviewWriteException(ReviewWriteException.Reason.CONFLICT, conflict);
        } catch (RuntimeException failure) {
            throw new ReviewWriteException(ReviewWriteException.Reason.STORAGE, failure);
        }
    }
}
