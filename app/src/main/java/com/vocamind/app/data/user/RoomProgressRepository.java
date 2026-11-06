package com.vocamind.app.data.user;

import androidx.annotation.WorkerThread;
import com.vocamind.app.domain.progress.ProgressReadResult;
import com.vocamind.app.domain.progress.ProgressRepository;
import java.util.Objects;

/** Blocking repository; caller owns executor/lifecycle. DB failures are never empty success. */
@WorkerThread
public final class RoomProgressRepository implements ProgressRepository {
    private final UserDao dao;
    public RoomProgressRepository(UserDao dao) { this.dao = Objects.requireNonNull(dao); }
    @Override public ProgressReadResult readProgress() {
        final ProgressRows rows;
        try { rows = dao.progressSnapshot(); }
        catch (RuntimeException failure) {
            return ProgressReadResult.failure(ProgressReadResult.Error.READ_FAILED);
        }
        try { return ProgressReadResult.success(ProgressMapper.toDomain(rows)); }
        catch (IllegalArgumentException | ArithmeticException invalid) {
            return ProgressReadResult.failure(ProgressReadResult.Error.INVALID_DATA);
        }
    }
}
