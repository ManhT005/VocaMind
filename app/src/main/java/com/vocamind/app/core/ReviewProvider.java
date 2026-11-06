package com.vocamind.app.core;

import com.vocamind.app.domain.learning.*;
import java.time.Clock;
import java.time.ZoneId;

/** Borrows the canonical repository; never opens or closes a database. */
public final class ReviewProvider {
    private ReviewProvider() { }
    public static RecordReviewUseCase create(ReviewRepository repository, Clock clock, ZoneId zone) {
        return new RecordReviewUseCase(repository, new LeitnerScheduler(), clock, zone);
    }
}
