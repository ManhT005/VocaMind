package com.vocamind.app.domain.learning;

import com.vocamind.app.domain.catalog.CatalogVocabulary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ordered study queue for today: due words first, then new words. Immutable. */
public final class TodayPlan {
    public enum Status {
        /** No active pack: the screen should send the user to pick one. */
        NO_ACTIVE_PACK,
        /** Active packs exist but nothing is due and no new word is left today. */
        DONE,
        /** There is something to study. */
        READY
    }

    private final Status status;
    private final List<StudyItem> due;
    private final List<CatalogVocabulary> fresh;

    TodayPlan(Status status, List<StudyItem> due, List<CatalogVocabulary> fresh) {
        this.status = status;
        this.due = Collections.unmodifiableList(new ArrayList<>(due));
        this.fresh = Collections.unmodifiableList(new ArrayList<>(fresh));
    }

    public Status getStatus() { return status; }
    public List<StudyItem> getDue() { return due; }
    public List<CatalogVocabulary> getNewWords() { return fresh; }
    public int dueCount() { return due.size(); }
    public int newCount() { return fresh.size(); }

    /** Session order: every due word (dueAt ASC), then the new words (pack order). */
    public List<StudyItem> queue() {
        List<StudyItem> out = new ArrayList<>(due);
        for (CatalogVocabulary v : fresh) out.add(new StudyItem(v, null));
        return Collections.unmodifiableList(out);
    }
}
