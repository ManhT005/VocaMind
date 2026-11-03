package com.vocamind.app.data.user;

import com.vocamind.app.data.user.entity.UserWordProgress;
import com.vocamind.app.domain.learning.ReviewProgress;

final class ReviewMapper {
    private ReviewMapper() { }
    static ReviewProgress toDomain(UserWordProgress p) {
        return new ReviewProgress(p.vocabularyId, p.box, p.dueAt, p.learnedAt,
                p.lastReviewedAt, p.consecutiveMistakes, p.isLeech, p.reviewCount, p.isSaved);
    }
    static UserWordProgress toEntity(ReviewProgress p) {
        UserWordProgress row = new UserWordProgress();
        row.vocabularyId = p.vocabularyId; row.box = p.box; row.dueAt = p.dueAt;
        row.learnedAt = p.learnedAt; row.lastReviewedAt = p.lastReviewedAt;
        row.consecutiveMistakes = p.consecutiveMistakes; row.isLeech = p.isLeech;
        row.reviewCount = p.reviewCount; row.isSaved = p.isSaved;
        return row;
    }
}
