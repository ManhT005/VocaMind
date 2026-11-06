package com.vocamind.app.domain.learning;

import org.junit.Test;
import java.time.*;
import java.util.Optional;
import static org.junit.Assert.*;

public class RecordReviewUseCaseTest {
    private static final String ID = "0123456789abcdef";
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"), ZONE);
    private static final class Fake implements ReviewRepository {
        Optional<ReviewProgress> stored = Optional.empty();
        Optional<ReviewProgress> expected;
        ReviewProgress updated;
        long reviewedAt;
        Rating rating;
        int reads, writes;
        ReviewWriteException failure;
        public Optional<ReviewProgress> getProgress(String id) { reads++; return stored; }
        public void recordReview(Optional<ReviewProgress> expected, ReviewProgress next, Rating rating, long now) {
            writes++; this.expected = expected;
            if (failure != null) throw failure;
            this.updated = next; this.reviewedAt = now; this.rating = rating;
            stored = Optional.of(next);
        }
    }
    private RecordReviewUseCase useCase(Fake repo) {
        return new RecordReviewUseCase(repo, new LeitnerScheduler(), CLOCK, ZONE);
    }
    @Test public void firstReviewUsesSingleTimestampAndCommitsOnce() {
        Fake repo = new Fake(); ScheduleResult result = useCase(repo).execute(ID, Rating.REMEMBERED);
        assertEquals(1, repo.reads); assertEquals(1, repo.writes); assertFalse(repo.expected.isPresent());
        assertEquals(1, repo.updated.box); assertEquals(1, repo.updated.reviewCount);
        assertEquals(Long.valueOf(CLOCK.millis()), repo.updated.learnedAt);
        assertEquals(Long.valueOf(CLOCK.millis()), repo.updated.lastReviewedAt);
        assertEquals(CLOCK.millis(), repo.reviewedAt); assertEquals(Rating.REMEMBERED, repo.rating);
        assertEquals(result.getDueAtEpochMillis(), repo.updated.dueAt); assertFalse(repo.updated.isSaved);
    }
    @Test public void allRatingsKeepSavedAndOriginalLearnedTimestamp() {
        for (Rating rating : Rating.values()) {
            Fake repo = new Fake();
            ReviewProgress old = new ReviewProgress(ID, 3, 9L, 123L, 456L, 3, true, 7, true);
            repo.stored = Optional.of(old);
            ScheduleResult result = useCase(repo).execute(ID, rating);
            assertSame(old, repo.expected.get()); assertEquals(8, repo.updated.reviewCount);
            assertEquals(Long.valueOf(123), repo.updated.learnedAt); assertTrue(repo.updated.isSaved);
            assertEquals(result.getBoxAfter(), repo.updated.box);
            assertEquals(result.getConsecutiveMistakes(), repo.updated.consecutiveMistakes);
            assertEquals(result.isLeech(), repo.updated.isLeech);
            assertEquals(7, old.reviewCount); assertEquals(456L, (long) old.lastReviewedAt);
        }
    }
    @Test public void savedButUnreviewedWordGetsFirstLearnedTimestamp() {
        Fake repo = new Fake(); repo.stored = Optional.of(new ReviewProgress(ID, 0, 0, null, null, 0, false, 0, true));
        useCase(repo).execute(ID, Rating.HARD);
        assertTrue(repo.updated.isSaved); assertEquals(1, repo.updated.reviewCount);
        assertEquals(Long.valueOf(CLOCK.millis()), repo.updated.learnedAt);
    }
    @Test public void failedCommitDoesNotReturnSuccessOrMutateSnapshot() {
        Fake repo = new Fake();
        ReviewProgress old = new ReviewProgress(ID, 2, 9L, 123L, 456L, 0, false, 7, true);
        repo.stored = Optional.of(old);
        repo.failure = new ReviewWriteException(ReviewWriteException.Reason.STORAGE, new RuntimeException("test"));
        assertSame(repo.failure, assertThrows(ReviewWriteException.class, () -> useCase(repo).execute(ID, Rating.FORGOT)));
        assertSame(old, repo.stored.get()); assertEquals(1, repo.writes); assertNull(repo.updated);
    }
    @Test public void countOverflowDoesNotWrite() {
        Fake repo = new Fake(); repo.stored = Optional.of(new ReviewProgress(ID, 5, 9L, 1L, 2L, 0, false, Integer.MAX_VALUE, false));
        assertThrows(ArithmeticException.class, () -> useCase(repo).execute(ID, Rating.REMEMBERED));
        assertEquals(0, repo.writes);
    }
    @Test public void invalidCommandDoesNotTouchRepository() {
        Fake repo = new Fake();
        assertThrows(IllegalArgumentException.class, () -> useCase(repo).execute("bad-id", Rating.HARD));
        assertThrows(NullPointerException.class, () -> useCase(repo).execute(ID, null));
        assertEquals(0, repo.reads); assertEquals(0, repo.writes);
    }
}
