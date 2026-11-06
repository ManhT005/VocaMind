package com.vocamind.app.fake;

import com.vocamind.app.data.repository.UserDataMapper;
import com.vocamind.app.data.user.entity.UserWordProgress;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.*;
import java.time.ZoneOffset;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fakes behave like the contracts so ViewModel tests can rely on them; mapper keeps invariants. */
public class FakeRepositoriesTest {
    private static final long NOW = 1_700_000_000_000L;

    private static ScheduleResult remembered() {
        return new LeitnerScheduler().schedule(LeitnerState.newWord(), Rating.REMEMBERED, NOW, ZoneOffset.UTC);
    }

    @Test public void resultNeverMixesValueAndError() {
        RepoResult<String> ok = RepoResult.success("x");
        assertTrue(ok.isSuccess());
        assertThrows(IllegalStateException.class, ok::getError);
        RepoResult<String> bad = RepoResult.failure(RepoError.READ_FAILED);
        assertFalse(bad.isSuccess());
        assertThrows(IllegalStateException.class, bad::get);
        assertThrows(IllegalArgumentException.class, () -> RepoResult.success(null));
        assertThrows(IllegalArgumentException.class, () -> RepoResult.failure(null));
    }

    @Test public void fakeReviewRecordsCallsAndCanFail() {
        FakeReviewRepository fake = new FakeReviewRepository();
        WordProgress p = fake.recordReview("w", remembered(), NOW).get();
        assertEquals(1, p.getBox());
        assertEquals(1, fake.calls.size());
        fake.failure = RepoError.WRITE_FAILED;
        assertEquals(RepoError.WRITE_FAILED, fake.recordReview("w", remembered(), NOW).getError());
        assertEquals(1, fake.calls.size());
    }

    @Test public void fakeStudyHonoursLimitAndFailure() {
        FakeStudyRepository fake = new FakeStudyRepository();
        assertTrue(fake.newWords(3).get().isEmpty());
        assertEquals(RepoError.INVALID_ARGUMENT, fake.newWords(0).getError());
        fake.failure = RepoError.READ_FAILED;
        assertEquals(RepoError.READ_FAILED, fake.dueItems(NOW, 1).getError());
    }

    @Test public void mapperRoundTripsAndFlagsInvalidRows() {
        UserWordProgress row = UserDataMapper.afterReview("w", null, remembered(), NOW);
        WordProgress domain = UserDataMapper.progress(row);
        UserWordProgress back = UserDataMapper.progressRow(domain);
        assertEquals(row.box, back.box);
        assertEquals(row.dueAt, back.dueAt);
        assertEquals(row.learnedAt, back.learnedAt);
        assertEquals(1, back.reviewCount);
        row.box = 7;
        assertThrows(RuntimeException.class, () -> UserDataMapper.progress(row));
    }
}
