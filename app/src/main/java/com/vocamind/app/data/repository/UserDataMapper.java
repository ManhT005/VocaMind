package com.vocamind.app.data.repository;

import com.vocamind.app.data.user.entity.QuizAttempt;
import com.vocamind.app.data.user.entity.ReviewEvent;
import com.vocamind.app.data.user.entity.UserPack;
import com.vocamind.app.data.user.entity.UserWordProgress;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.learning.Rating;
import com.vocamind.app.domain.learning.ScheduleResult;
import com.vocamind.app.domain.learning.WordProgress;
import com.vocamind.app.domain.quiz.QuizResult;
import com.vocamind.app.domain.quiz.QuizVocabulary;

/** Entity to Domain mapping (and back) for userdata. Entities never leave the data package. */
public final class UserDataMapper {
    private UserDataMapper() { }

    public static WordProgress progress(UserWordProgress row) {
        try {
            return new WordProgress(row.vocabularyId, row.box, row.dueAt, row.learnedAt,
                    row.lastReviewedAt, row.consecutiveMistakes, row.isLeech, row.reviewCount, row.isSaved);
        } catch (IllegalArgumentException e) {
            throw new RepositoryGuard.InvalidDataException(e);
        }
    }

    public static UserWordProgress progressRow(WordProgress p) {
        UserWordProgress row = new UserWordProgress();
        row.vocabularyId = p.getVocabularyId();
        row.box = p.getBox();
        row.dueAt = p.getDueAtEpochMillis();
        row.learnedAt = p.getLearnedAtEpochMillis();
        row.lastReviewedAt = p.getLastReviewedAtEpochMillis();
        row.consecutiveMistakes = p.getConsecutiveMistakes();
        row.isLeech = p.isLeech();
        row.reviewCount = p.getReviewCount();
        row.isSaved = p.isSaved();
        return row;
    }

    /**
     * Next progress after a review. learnedAt is set once, saved flag is kept, reviewCount grows by 1.
     * existing is null for a word without progress.
     */
    public static UserWordProgress afterReview(String id, UserWordProgress existing, ScheduleResult r, long reviewedAt) {
        UserWordProgress row = new UserWordProgress();
        row.vocabularyId = id;
        row.box = r.getBoxAfter();
        row.dueAt = r.getDueAtEpochMillis();
        row.learnedAt = existing != null && existing.learnedAt != null ? existing.learnedAt : Long.valueOf(reviewedAt);
        row.lastReviewedAt = reviewedAt;
        row.consecutiveMistakes = r.getConsecutiveMistakes();
        row.isLeech = r.isLeech();
        row.reviewCount = existing == null ? 1 : existing.reviewCount + 1;
        row.isSaved = existing != null && existing.isSaved;
        return row;
    }

    public static ReviewEvent reviewEvent(String id, ScheduleResult r, long reviewedAt) {
        ReviewEvent row = new ReviewEvent();
        row.vocabularyId = id;
        row.rating = ratingName(r.getRating());
        row.reviewedAt = reviewedAt;
        row.boxBefore = r.getBoxBefore();
        row.boxAfter = r.getBoxAfter();
        row.nextDueAt = r.getDueAtEpochMillis();
        return row;
    }

    public static String ratingName(Rating rating) { return rating.name(); }

    public static QuizAttempt quizAttempt(QuizResult r, long completedAt) {
        QuizAttempt row = new QuizAttempt();
        row.completedAt = completedAt;
        row.questionCount = r.getQuestionCount();
        row.correctCount = r.getCorrectCount();
        row.scorePercent = r.getScorePercent();
        return row;
    }

    public static QuizVocabulary quizVocabulary(CatalogVocabulary v) {
        try {
            return new QuizVocabulary(v.stableId, v.lemma, v.pos, v.meaningVi, v.exampleEn == null ? "" : v.exampleEn);
        } catch (IllegalArgumentException e) {
            throw new RepositoryGuard.InvalidDataException(e);
        }
    }

    public static Long activatedAt(UserPack row) { return row == null ? null : row.activatedAt; }
}
