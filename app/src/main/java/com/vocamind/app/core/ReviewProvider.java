package com.vocamind.app.core;

import com.vocamind.app.data.user.RoomReviewRepository;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.learning.*;
import java.time.Clock;
import java.time.ZoneId;

/** Build on a worker using an application-owned UserDatabase. This factory does not close it. */
public final class ReviewProvider {
    private ReviewProvider() { }
    public static RecordReviewUseCase create(UserDatabase database, Clock clock, ZoneId zone) {
        return new RecordReviewUseCase(new RoomReviewRepository(database.userDao()),
                new LeitnerScheduler(), clock, zone);
    }
}
