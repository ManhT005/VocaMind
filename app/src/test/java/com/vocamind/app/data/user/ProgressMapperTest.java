package com.vocamind.app.data.user;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ProgressMapperTest {
    private BoxProgressCount group(int box, long count) {
        BoxProgressCount row = new BoxProgressCount(); row.box = box; row.wordCount = count; return row;
    }
    @Test public void aggregationUsesLongRatherThanTruncatingToInt() {
        long count = (long) Integer.MAX_VALUE + 1;
        var snapshot = ProgressMapper.toDomain(new ProgressRows(count,
                Collections.singletonList(group(5, count)), Collections.emptyList()));
        assertEquals(count, snapshot.getSummary().getTotalLearned());
        assertEquals(count, snapshot.getSummary().getMasteredCount());
    }
    @Test public void inconsistentDistinctAndGroupedTotalsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProgressMapper.toDomain(
                new ProgressRows(2, Collections.singletonList(group(0, 1)), Collections.emptyList())));
    }
    @Test public void duplicateOrInvalidGroupsAreNotSilentlyOverwritten() {
        assertThrows(IllegalArgumentException.class, () -> ProgressMapper.toDomain(
                new ProgressRows(2, Arrays.asList(group(0, 1), group(0, 1)), Collections.emptyList())));
        assertThrows(IllegalArgumentException.class, () -> ProgressMapper.toDomain(
                new ProgressRows(1, Collections.singletonList(group(6, 1)), Collections.emptyList())));
    }
}
