package com.vocamind.app.domain.progress;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ProgressDomainTest {
    private static QuizHistoryItem attempt(long id, long time) {
        return new QuizHistoryItem(id, time, 6, 1, 17);
    }

    @Test public void emptySummaryAndSnapshotAreValidSuccessfulData() {
        ProgressSnapshot empty = ProgressSnapshot.empty();
        assertTrue(empty.isEmpty());
        assertArrayEquals(new long[6], empty.getSummary().getBoxCounts());
        assertEquals(0, empty.getSummary().getTotalLearned());
        assertEquals(0, empty.getSummary().getLearningCount());
        assertEquals(0, empty.getSummary().getReviewingCount());
        assertEquals(0, empty.getSummary().getMasteredCount());
        assertTrue(empty.getQuizHistory().isEmpty());
    }

    @Test public void allSixBoxesContributeToTheCorrectGroups() {
        ProgressSummary summary = new ProgressSummary(27, new long[]{2, 3, 4, 5, 6, 7});
        assertEquals(27, summary.getTotalLearned());
        assertEquals(5, summary.getLearningCount());
        assertEquals(9, summary.getReviewingCount());
        assertEquals(13, summary.getMasteredCount());
        for (int box = 0; box < 6; box++) {
            long[] counts = new long[6];
            counts[box] = 1;
            ProgressSummary single = new ProgressSummary(1, counts);
            assertEquals(1, single.getBoxCount(box));
            assertEquals(box < 2 ? 1 : 0, single.getLearningCount());
            assertEquals(box >= 2 && box < 4 ? 1 : 0, single.getReviewingCount());
            assertEquals(box >= 4 ? 1 : 0, single.getMasteredCount());
        }
    }

    @Test public void summaryRejectsNullWrongSizeNegativeAndMismatchedCounts() {
        assertThrows(IllegalArgumentException.class, () -> new ProgressSummary(0, null));
        for (int size : new int[]{0, 5, 7}) {
            assertThrows(IllegalArgumentException.class, () -> new ProgressSummary(0, new long[size]));
        }
        assertThrows(IllegalArgumentException.class, () -> new ProgressSummary(-1, new long[6]));
        assertThrows(IllegalArgumentException.class, () -> new ProgressSummary(1, new long[6]));
        for (int box = 0; box < 6; box++) {
            long[] counts = new long[6];
            counts[box] = -1;
            assertThrows(IllegalArgumentException.class, () -> new ProgressSummary(0, counts));
        }
        assertThrows(IllegalArgumentException.class, () -> ProgressSummary.empty().getBoxCount(-1));
        assertThrows(IllegalArgumentException.class, () -> ProgressSummary.empty().getBoxCount(6));
    }

    @Test public void longCountsDoNotNarrowOrOverflow() {
        long count = (long) Integer.MAX_VALUE + 1;
        ProgressSummary large = new ProgressSummary(count, new long[]{0, 0, count, 0, 0, 0});
        assertEquals(count, large.getReviewingCount());
        ProgressSummary max = new ProgressSummary(Long.MAX_VALUE,
                new long[]{Long.MAX_VALUE - 5, 1, 1, 1, 1, 1});
        assertEquals(Long.MAX_VALUE, max.getLearningCount() + max.getReviewingCount() + max.getMasteredCount());
        assertThrows(IllegalArgumentException.class, () -> new ProgressSummary(Long.MAX_VALUE,
                new long[]{Long.MAX_VALUE, 0, 0, 0, 0, 1}));
    }

    @Test public void summaryDefensivelyCopiesBothInputAndOutput() {
        long[] input = {2, 3, 4, 5, 6, 7};
        ProgressSummary summary = new ProgressSummary(27, input);
        input[0] = 100;
        summary.getBoxCounts()[1] = 100;
        assertArrayEquals(new long[]{2, 3, 4, 5, 6, 7}, summary.getBoxCounts());
    }

    @Test public void historyValidatesPersistedIdentityTimeCountsAndScore() {
        for (long id : new long[]{-1, 0}) {
            assertThrows(IllegalArgumentException.class, () -> attempt(id, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> attempt(1, -1));
        for (int n : new int[]{0, 3, 11, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new QuizHistoryItem(1, 0, n, 0, 0));
        }
        for (int correct : new int[]{-1, 7}) {
            assertThrows(IllegalArgumentException.class, () -> new QuizHistoryItem(1, 0, 6, correct, 0));
        }
        for (int score : new int[]{-1, 16, 18, 101}) {
            assertThrows(IllegalArgumentException.class, () -> new QuizHistoryItem(1, 0, 6, 1, score));
        }
        QuizHistoryItem item = attempt(Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, item.getAttemptId());
        assertEquals(Long.MAX_VALUE, item.getCompletedAtEpochMillis());
        assertEquals(5, item.getWrongCount());
    }

    @Test public void scoresMatchQuizResultRoundingAcrossEverySupportedCount() {
        for (int n = 4; n <= 10; n++) {
            for (int correct = 0; correct <= n; correct++) {
                int expected = (int) Math.round(correct * 100.0 / n);
                QuizHistoryItem item = new QuizHistoryItem(1, 0, n, correct, expected);
                assertEquals(n, item.getQuestionCount());
                assertEquals(correct, item.getCorrectCount());
                assertEquals(n - correct, item.getWrongCount());
                assertEquals(expected, item.getScorePercent());
            }
        }
        assertEquals(17, new QuizHistoryItem(1, 0, 6, 1, 17).getScorePercent());
        assertEquals(29, new QuizHistoryItem(1, 0, 7, 2, 29).getScorePercent());
    }

    @Test public void snapshotOrdersByTimeThenIdWithoutOverflowOrTruncation() {
        List<QuizHistoryItem> input = new ArrayList<>(Arrays.asList(
                attempt(100, 0), attempt(1, Long.MAX_VALUE), attempt(Long.MAX_VALUE, Long.MAX_VALUE)));
        for (int i = 2; i <= 15; i++) input.add(attempt(i, 1));
        ProgressSnapshot snapshot = new ProgressSnapshot(ProgressSummary.empty(), input);
        List<QuizHistoryItem> history = snapshot.getQuizHistory();
        assertEquals(17, history.size());
        assertEquals(Long.MAX_VALUE, history.get(0).getAttemptId());
        assertEquals(1, history.get(1).getAttemptId());
        assertEquals(15, history.get(2).getAttemptId());
        assertEquals(100, history.get(16).getAttemptId());
        assertEquals(100, input.get(0).getAttemptId());
        assertFalse(snapshot.isEmpty());
    }

    @Test public void snapshotRejectsMissingPayloadsAndDuplicateIdsEvenAtDifferentTimes() {
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshot(null, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshot(ProgressSummary.empty(), null));
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshot(ProgressSummary.empty(),
                Arrays.asList(attempt(1, 0), null)));
        assertThrows(IllegalArgumentException.class, () -> new ProgressSnapshot(ProgressSummary.empty(),
                Arrays.asList(attempt(1, 0), attempt(1, 10))));
    }

    @Test public void snapshotCopiesInputAndReturnsReadOnlyHistory() {
        List<QuizHistoryItem> input = new ArrayList<>(Collections.singletonList(attempt(1, 0)));
        ProgressSnapshot snapshot = new ProgressSnapshot(ProgressSummary.empty(), input);
        input.clear();
        assertEquals(1, snapshot.getQuizHistory().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getQuizHistory().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getQuizHistory().add(attempt(2, 0)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getQuizHistory().set(0, attempt(2, 0)));
    }

    @Test public void resultEnforcesExclusiveSuccessAndFailurePayloads() {
        assertThrows(IllegalArgumentException.class, () -> ProgressReadResult.success(null));
        assertThrows(IllegalArgumentException.class, () -> ProgressReadResult.failure(null));
        ProgressReadResult success = ProgressReadResult.success(ProgressSnapshot.empty());
        assertTrue(success.isSuccess());
        assertTrue(success.getSnapshot().isEmpty());
        assertThrows(IllegalStateException.class, success::getError);
        for (ProgressReadResult.Error error : ProgressReadResult.Error.values()) {
            ProgressReadResult failure = ProgressReadResult.failure(error);
            assertFalse(failure.isSuccess());
            assertEquals(error, failure.getError());
            assertThrows(IllegalStateException.class, failure::getSnapshot);
        }
    }
}
