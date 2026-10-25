package com.vocamind.app.feature.progress;

import com.vocamind.app.domain.progress.ProgressReadResult;
import com.vocamind.app.domain.progress.ProgressSnapshot;

/** Plain presentation contract. PROGRESS-02 moves copy into resources and binds UI/lifecycle. */
public final class ProgressViewState {
    public enum Status { LOADING, EMPTY, CONTENT, ERROR }
    public static final String TOTAL_LABEL = "Tổng từ đã học";
    public static final String LEARNING_LABEL = "Learning — nhóm box 0–1";
    public static final String REVIEWING_LABEL = "Reviewing — nhóm box 2–3";
    public static final String MASTERED_LABEL = "Mastered — nhóm box 4–5";
    public static final String MASTERED_EXPLANATION =
            "Mastered là trạng thái ôn tập trong ứng dụng, không phải chứng nhận năng lực ngoại ngữ.";
    public static final String EMPTY_PROGRESS_MESSAGE = "Bắt đầu học để xem tiến độ của bạn.";
    public static final String EMPTY_HISTORY_MESSAGE = "Chưa có bài Quiz hoàn tất.";
    public static final String ERROR_MESSAGE = "Không thể tải tiến độ. Vui lòng thử lại.";
    public static final String RETRY_LABEL = "Thử lại";

    private final Status status;
    private final ProgressReadResult result;

    private ProgressViewState(Status status, ProgressReadResult result) {
        this.status = status;
        this.result = result;
    }
    public static ProgressViewState loading() { return new ProgressViewState(Status.LOADING, null); }
    public static ProgressViewState fromResult(ProgressReadResult result) {
        if (result == null) throw new IllegalArgumentException("read result required");
        Status status = !result.isSuccess() ? Status.ERROR
                : result.getSnapshot().isEmpty() ? Status.EMPTY : Status.CONTENT;
        return new ProgressViewState(status, result);
    }
    public static String getBoxLabel(int box) {
        if (box < 0 || box > 5) throw new IllegalArgumentException("box must be 0..5");
        return box == 0 ? "Học lại" : "Cấp ôn " + box;
    }
    public Status getStatus() { return status; }
    public boolean hasSnapshot() { return status == Status.EMPTY || status == Status.CONTENT; }
    public ProgressSnapshot getSnapshot() {
        if (!hasSnapshot()) throw new IllegalStateException("no successful read available");
        return result.getSnapshot();
    }
    public boolean isProgressEmpty() { return getSnapshot().getSummary().getTotalLearned() == 0; }
    public boolean isHistoryEmpty() { return getSnapshot().getQuizHistory().isEmpty(); }
    public ProgressReadResult.Error getError() {
        if (status != Status.ERROR) throw new IllegalStateException("no read error available");
        return result.getError();
    }
    /** UI dispatches a new use case invocation on retry; this state performs no IO/reset. */
    public boolean canRetry() { return status == Status.ERROR; }
}
