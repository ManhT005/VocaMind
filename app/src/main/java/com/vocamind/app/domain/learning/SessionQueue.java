package com.vocamind.app.domain.learning;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered cards of one study session with the relearn rule: a word rated FORGOT is shown
 * again once in the same session, after at least {@link #RELEARN_GAP} other cards (fewer if
 * the queue has fewer left, then at the end). A second FORGOT of the same word does not
 * enqueue it again, so a session always ends. Not thread safe; use from one thread.
 * The word's dueAt is not touched here: the committed review already set it to tomorrow.
 */
public final class SessionQueue {
    public static final int RELEARN_GAP = 2;

    private final List<StudyItem> cards;
    private final Set<String> relearned = new HashSet<>();
    private int index;

    public SessionQueue(List<StudyItem> initial) {
        this.cards = new ArrayList<>(Objects.requireNonNull(initial));
    }

    public boolean isFinished() { return index >= cards.size(); }

    /** The card to show, or null when the session is finished. */
    public StudyItem current() { return isFinished() ? null : cards.get(index); }

    /** 1-based position of the current card; 0 when finished. */
    public int position() { return isFinished() ? 0 : index + 1; }

    /** Cards in the session so far, including relearn repeats. */
    public int size() { return cards.size(); }

    public boolean hasRelearned(String vocabularyId) { return relearned.contains(vocabularyId); }

    /**
     * Moves past the current card after its rating was committed.
     * @return true when the card was queued again for relearning
     */
    public boolean advance(Rating rating) {
        Objects.requireNonNull(rating);
        if (isFinished()) throw new IllegalStateException("session finished");
        StudyItem done = cards.get(index);
        index++;
        if (rating != Rating.FORGOT || !relearned.add(done.getVocabulary().stableId)) return false;
        cards.add(Math.min(cards.size(), index + RELEARN_GAP), done);
        return true;
    }
}
