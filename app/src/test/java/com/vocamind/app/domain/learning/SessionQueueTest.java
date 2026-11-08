package com.vocamind.app.domain.learning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vocamind.app.domain.catalog.CatalogVocabulary;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SessionQueueTest {
    private static StudyItem card(String id) {
        return new StudyItem(new CatalogVocabulary(id, id, id, "noun", "m", "e", "v", null, "s", "1"), null);
    }

    private static SessionQueue queue(String... ids) {
        List<StudyItem> items = new ArrayList<>();
        for (String id : ids) items.add(card(id));
        return new SessionQueue(items);
    }

    private static List<String> drain(SessionQueue q, Rating rating) {
        List<String> order = new ArrayList<>();
        while (!q.isFinished()) {
            order.add(q.current().getVocabulary().stableId);
            q.advance(rating);
        }
        return order;
    }

    @Test public void rememberedAndHardNeverRequeue() {
        SessionQueue q = queue("a", "b");
        assertFalse(q.advance(Rating.HARD));
        assertFalse(q.advance(Rating.REMEMBERED));
        assertTrue(q.isFinished());
        assertNull(q.current());
        assertEquals(0, q.position());
        assertEquals(2, q.size());
    }

    @Test public void forgotReturnsAfterTwoOtherCards() {
        SessionQueue q = queue("a", "b", "c", "d");
        assertTrue(q.advance(Rating.FORGOT));
        assertEquals(5, q.size());
        assertEquals(List.of("b", "c", "a", "d"), drain(q, Rating.REMEMBERED));
    }

    @Test public void forgotWithFewerThanTwoCardsLeftGoesToTheEnd() {
        SessionQueue q = queue("a", "b");
        q.advance(Rating.FORGOT);
        assertEquals(List.of("b", "a"), drain(q, Rating.REMEMBERED));
    }

    @Test public void forgotOnTheOnlyCardShowsItAgainImmediately() {
        SessionQueue q = queue("a");
        assertTrue(q.advance(Rating.FORGOT));
        assertEquals("a", q.current().getVocabulary().stableId);
        assertEquals(2, q.size());
        assertEquals(2, q.position());
    }

    @Test public void secondForgotOfTheSameWordDoesNotRequeueSoSessionEnds() {
        SessionQueue q = queue("a", "b", "c");
        assertTrue(q.advance(Rating.FORGOT));
        List<String> order = drain(q, Rating.FORGOT);
        assertTrue(q.isFinished());
        int forgotCount = 0;
        for (String id : order) if (id.equals("a")) forgotCount++;
        assertEquals(1, forgotCount); // the relearn copy; its FORGOT did not add a third
        assertTrue(q.hasRelearned("a"));
    }

    @Test public void onlyOneRelearnCopyPerWordEvenWithRepeatedForgot() {
        SessionQueue q = queue("a");
        int shown = 0;
        while (!q.isFinished()) {
            shown++;
            q.advance(Rating.FORGOT);
            assertTrue("loop guard", shown < 10);
        }
        assertEquals(2, shown); // original + one relearn
    }

    @Test(expected = IllegalStateException.class) public void advancingAFinishedSessionFails() {
        SessionQueue q = queue("a");
        q.advance(Rating.REMEMBERED);
        q.advance(Rating.REMEMBERED);
    }
}
