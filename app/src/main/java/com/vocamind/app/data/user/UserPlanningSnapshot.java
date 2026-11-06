package com.vocamind.app.data.user;

import com.vocamind.app.data.user.entity.*;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** Consistent userdata-only read. Internal Room rows must be mapped before crossing to domain. */
public final class UserPlanningSnapshot {
    public final List<UserPack> activePacks;
    public final List<UserWordProgress> progress;
    public final UserPreference preference;
    public final int learnedToday;

    UserPlanningSnapshot(List<UserPack> packs, List<UserWordProgress> progress,
                         UserPreference preference, int learnedToday) {
        this.activePacks = Collections.unmodifiableList(new ArrayList<>(packs));
        this.progress = Collections.unmodifiableList(new ArrayList<>(progress));
        this.preference = preference;
        this.learnedToday = learnedToday;
    }
}
