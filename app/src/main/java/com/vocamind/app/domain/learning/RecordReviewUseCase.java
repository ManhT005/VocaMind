package com.vocamind.app.domain.learning;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/** Call on a worker. Session queue/600ms UI guard belong to FLASH-02/03. */
public final class RecordReviewUseCase {
    private final ReviewRepository repository;
    private final LeitnerScheduler scheduler;
    private final Clock clock;
    private final ZoneId zone;
    public RecordReviewUseCase(ReviewRepository repository, LeitnerScheduler scheduler,
                              Clock clock, ZoneId zone) {
        this.repository = Objects.requireNonNull(repository);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone);
    }
    /** A result is returned only after the repository transaction commits. */
    public ScheduleResult execute(String vocabularyId, Rating rating) {
        ReviewProgress.requireId(vocabularyId);
        Objects.requireNonNull(rating, "rating");
        Optional<ReviewProgress> previous = repository.getProgress(vocabularyId);
        if (previous.isPresent() && !vocabularyId.equals(previous.get().vocabularyId))
            throw new IllegalStateException("Repository returned a different vocabulary ID");
        long now = clock.millis();
        LeitnerState state = previous.map(ReviewProgress::schedulingState).orElseGet(LeitnerState::newWord);
        ScheduleResult result = scheduler.schedule(state, rating, now, zone);
        ReviewProgress updated = ReviewProgress.afterReview(vocabularyId, previous, result, now);
        repository.recordReview(previous, updated, rating, now);
        return result;
    }
}
