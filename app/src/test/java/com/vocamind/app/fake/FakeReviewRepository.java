package com.vocamind.app.fake;

import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.ReviewRepository;
import com.vocamind.app.domain.learning.ScheduleResult;
import com.vocamind.app.domain.learning.WordProgress;
import java.util.ArrayList;
import java.util.List;

/** Records every accepted review. Set {@code failure} to simulate a rolled-back write. */
public final class FakeReviewRepository implements ReviewRepository {
    public static final class Call {
        public final String vocabularyId;
        public final ScheduleResult result;
        public final long reviewedAt;
        Call(String id, ScheduleResult result, long reviewedAt) {
            this.vocabularyId = id; this.result = result; this.reviewedAt = reviewedAt;
        }
    }
    public final List<Call> calls = new ArrayList<>();
    public RepoError failure;

    @Override public RepoResult<WordProgress> recordReview(String id, ScheduleResult r, long reviewedAt) {
        if (failure != null) return RepoResult.failure(failure);
        calls.add(new Call(id, r, reviewedAt));
        return RepoResult.success(new WordProgress(id, r.getBoxAfter(), r.getDueAtEpochMillis(), reviewedAt,
                reviewedAt, r.getConsecutiveMistakes(), r.isLeech(), calls.size(), false));
    }
}
