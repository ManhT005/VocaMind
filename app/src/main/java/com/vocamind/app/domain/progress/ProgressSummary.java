package com.vocamind.app.domain.progress;

/**
 * Counts of distinct UserWordProgress vocabulary IDs across all user data (FR-VM-041/042).
 * No active-pack, saved or learnedAt filter. The data adapter owns DISTINCT and consistent reads.
 */
public final class ProgressSummary {
    public static final int BOX_COUNT = 6;
    private final long totalLearned;
    private final long[] boxCounts;

    public ProgressSummary(long totalLearned, long[] boxCounts) {
        if (totalLearned < 0 || boxCounts == null || boxCounts.length != BOX_COUNT) {
            throw new IllegalArgumentException("nonnegative total and exactly six box counts required");
        }
        long[] copy = boxCounts.clone();
        long sum = 0;
        for (long count : copy) {
            if (count < 0 || count > Long.MAX_VALUE - sum) {
                throw new IllegalArgumentException("negative count or box count overflow");
            }
            sum += count;
        }
        if (sum != totalLearned) {
            throw new IllegalArgumentException("total must equal sum of box counts");
        }
        this.totalLearned = totalLearned;
        this.boxCounts = copy;
    }

    public static ProgressSummary empty() { return new ProgressSummary(0, new long[BOX_COUNT]); }
    public long getTotalLearned() { return totalLearned; }
    public long[] getBoxCounts() { return boxCounts.clone(); }
    public long getBoxCount(int box) {
        if (box < 0 || box >= BOX_COUNT) throw new IllegalArgumentException("box must be 0..5");
        return boxCounts[box];
    }
    public long getLearningCount() { return boxCounts[0] + boxCounts[1]; }
    public long getReviewingCount() { return boxCounts[2] + boxCounts[3]; }
    public long getMasteredCount() { return boxCounts[4] + boxCounts[5]; }
}
