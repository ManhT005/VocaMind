package com.vocamind.app.feature.progress;

import com.vocamind.app.domain.progress.*;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ProgressViewStateTest {
    /** Test-only wiring; production IO/scheduling is a PROGRESS-02 responsibility. */
    private static final class FakeRepository implements ProgressRepository {
        ProgressReadResult next;
        int reads;
        @Override public ProgressReadResult readProgress() { reads++; return next; }
    }
    private static ProgressViewState read(ProgressReadResult result) {
        return ProgressViewState.fromResult(result);
    }

    @Test public void loadingHasNoInventedZeroSnapshotOrError() {
        ProgressViewState state = ProgressViewState.loading();
        assertEquals(ProgressViewState.Status.LOADING, state.getStatus());
        assertFalse(state.hasSnapshot());
        assertFalse(state.canRetry());
        assertThrows(IllegalStateException.class, state::getSnapshot);
        assertThrows(IllegalStateException.class, state::getError);
        assertThrows(IllegalStateException.class, state::isProgressEmpty);
        assertThrows(IllegalStateException.class, state::isHistoryEmpty);
        assertThrows(IllegalArgumentException.class, () -> read(null));
    }

    @Test public void fakeUseCaseDelegatesEachReadIncludingRetryAndDoesNotConvertErrorsToEmpty() {
        FakeRepository repository = new FakeRepository();
        GetProgressUseCase useCase = repository::readProgress;
        repository.next = ProgressReadResult.failure(ProgressReadResult.Error.READ_FAILED);
        ProgressViewState failure = read(useCase.execute());
        assertEquals(1, repository.reads);
        assertEquals(ProgressViewState.Status.ERROR, failure.getStatus());
        assertEquals(ProgressReadResult.Error.READ_FAILED, failure.getError());
        assertTrue(failure.canRetry());
        assertFalse(failure.hasSnapshot());
        assertThrows(IllegalStateException.class, failure::getSnapshot);
        repository.next = ProgressReadResult.success(ProgressSnapshot.empty());
        ProgressReadResult retry = useCase.execute();
        assertSame(repository.next, retry);
        ProgressViewState empty = read(retry);
        assertEquals(2, repository.reads);
        assertEquals(ProgressViewState.Status.EMPTY, empty.getStatus());
        assertTrue(empty.hasSnapshot());
        assertTrue(empty.isProgressEmpty());
        assertTrue(empty.isHistoryEmpty());
        assertFalse(empty.canRetry());
        assertThrows(IllegalStateException.class, empty::getError);
    }

    @Test public void invalidStoredDataIsErrorRatherThanEmptyOrRepairedHistory() {
        ProgressRepository repository = () -> {
            try {
                new QuizHistoryItem(1, 0, 6, 1, 16);
                throw new AssertionError("invalid persisted score accepted");
            } catch (IllegalArgumentException invalid) {
                return ProgressReadResult.failure(ProgressReadResult.Error.INVALID_DATA);
            }
        };
        GetProgressUseCase useCase = repository::readProgress;
        ProgressViewState state = read(useCase.execute());
        assertEquals(ProgressViewState.Status.ERROR, state.getStatus());
        assertEquals(ProgressReadResult.Error.INVALID_DATA, state.getError());
        assertTrue(state.canRetry());
        assertFalse(state.hasSnapshot());
    }

    @Test public void historyAloneIsContentWithAnEmptyProgressSection() {
        ProgressSnapshot snapshot = new ProgressSnapshot(ProgressSummary.empty(),
                Collections.singletonList(new QuizHistoryItem(1, 0, 7, 2, 29)));
        ProgressReadResult result = ProgressReadResult.success(snapshot);
        ProgressRepository repository = () -> result;
        GetProgressUseCase useCase = repository::readProgress;
        ProgressViewState state = read(useCase.execute());
        assertEquals(ProgressViewState.Status.CONTENT, state.getStatus());
        assertSame(snapshot, state.getSnapshot());
        assertTrue(state.isProgressEmpty());
        assertFalse(state.isHistoryEmpty());
        assertEquals(29, state.getSnapshot().getQuizHistory().get(0).getScorePercent());
    }

    @Test public void progressAloneIsContentWithAnEmptyHistorySection() {
        ProgressSnapshot snapshot = new ProgressSnapshot(
                new ProgressSummary(1, new long[]{1, 0, 0, 0, 0, 0}), Collections.emptyList());
        ProgressViewState state = read(ProgressReadResult.success(snapshot));
        assertEquals(ProgressViewState.Status.CONTENT, state.getStatus());
        assertFalse(state.isProgressEmpty());
        assertTrue(state.isHistoryEmpty());
        assertFalse(state.canRetry());
    }

    @Test public void bothSectionsWithDataRemainContent() {
        ProgressSnapshot snapshot = new ProgressSnapshot(
                new ProgressSummary(1, new long[]{0, 0, 0, 0, 0, 1}),
                Collections.singletonList(new QuizHistoryItem(1, 0, 4, 4, 100)));
        ProgressViewState state = read(ProgressReadResult.success(snapshot));
        assertEquals(ProgressViewState.Status.CONTENT, state.getStatus());
        assertFalse(state.isProgressEmpty());
        assertFalse(state.isHistoryEmpty());
    }

    @Test public void labelsExplainEveryBoxGroupMasteredAndRecovery() {
        assertEquals("Tổng từ đã học", ProgressViewState.TOTAL_LABEL);
        assertEquals("Học lại", ProgressViewState.getBoxLabel(0));
        for (int box = 1; box <= 5; box++) {
            assertEquals("Cấp ôn " + box, ProgressViewState.getBoxLabel(box));
        }
        assertThrows(IllegalArgumentException.class, () -> ProgressViewState.getBoxLabel(-1));
        assertThrows(IllegalArgumentException.class, () -> ProgressViewState.getBoxLabel(6));
        assertEquals("Learning — nhóm box 0–1", ProgressViewState.LEARNING_LABEL);
        assertEquals("Reviewing — nhóm box 2–3", ProgressViewState.REVIEWING_LABEL);
        assertEquals("Mastered — nhóm box 4–5", ProgressViewState.MASTERED_LABEL);
        assertEquals("Mastered là trạng thái ôn tập trong ứng dụng, không phải chứng nhận năng lực ngoại ngữ.",
                ProgressViewState.MASTERED_EXPLANATION);
        assertFalse(ProgressViewState.EMPTY_PROGRESS_MESSAGE.isEmpty());
        assertFalse(ProgressViewState.EMPTY_HISTORY_MESSAGE.isEmpty());
        assertFalse(ProgressViewState.ERROR_MESSAGE.isEmpty());
        assertEquals("Thử lại", ProgressViewState.RETRY_LABEL);
    }
}
