package com.vocamind.app.fake;

import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.learning.*;
import java.util.*;

/** Immutable snapshots and one compare-and-commit path, shared by both review APIs. */
public final class FakeReviewRepository implements ReviewRepository {
    public final List<ReviewProgress> calls = new ArrayList<>();
    private final Map<String, ReviewProgress> progress = new HashMap<>();
    public RepoError failure;

    private void checkFailure() {
        if (failure == null) return;
        ReviewWriteException.Reason reason;
        switch (failure) {
            case NOT_FOUND: reason = ReviewWriteException.Reason.NOT_FOUND; break;
            case READ_FAILED: reason = ReviewWriteException.Reason.READ_FAILED; break;
            case INVALID_DATA: reason = ReviewWriteException.Reason.INVALID_DATA; break;
            case CONFLICT: reason = ReviewWriteException.Reason.CONFLICT; break;
            case INVALID_ARGUMENT: throw new IllegalArgumentException("test failure");
            default: reason = ReviewWriteException.Reason.STORAGE;
        }
        throw new ReviewWriteException(reason, null);
    }
    @Override public synchronized Optional<ReviewProgress> getProgress(String id) {
        ReviewProgress.requireId(id); checkFailure();
        return Optional.ofNullable(progress.get(id));
    }
    @Override public synchronized void recordReview(Optional<ReviewProgress> expected,
                                                    ReviewProgress next, Rating rating, long now) {
        checkFailure();
        if (!Objects.equals(expected.orElse(null), progress.get(next.vocabularyId)))
            throw new ReviewWriteException(ReviewWriteException.Reason.CONFLICT, null);
        calls.add(next); progress.put(next.vocabularyId, next);
    }
}
