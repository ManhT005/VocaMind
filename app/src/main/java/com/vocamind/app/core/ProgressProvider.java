package com.vocamind.app.core;

import com.vocamind.app.data.user.RoomProgressRepository;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.progress.ProgressRepository;
import java.util.Objects;

/** Application composition. Reuse an owned UserDatabase; this factory never opens/closes it. */
public final class ProgressProvider {
    private ProgressProvider() { }
    public static ProgressRepository create(UserDatabase database) {
        return new RoomProgressRepository(Objects.requireNonNull(database).userDao());
    }
}
