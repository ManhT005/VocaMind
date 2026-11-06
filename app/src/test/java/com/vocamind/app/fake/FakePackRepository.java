package com.vocamind.app.fake;

import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.pack.PackRepository;
import com.vocamind.app.domain.pack.PackState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Holds pack states in memory; activate/pause replace the matching state. */
public final class FakePackRepository implements PackRepository {
    public final List<PackState> states = new ArrayList<>();
    public final Map<String, List<StudyItem>> words = new HashMap<>();
    public RepoError failure;
    public int calls;

    @Override public RepoResult<List<PackState>> getPackStates() {
        calls++;
        if (failure != null) return RepoResult.failure(failure);
        return RepoResult.success(new ArrayList<>(states));
    }

    @Override public RepoResult<PackState> activatePack(String id, long now) {
        if (failure != null) return RepoResult.failure(failure);
        for (int i = 0; i < states.size(); i++) {
            PackState s = states.get(i);
            if (!s.getPack().id.equals(id)) continue;
            Long at = s.getActivatedAtEpochMillis() != null ? s.getActivatedAtEpochMillis() : Long.valueOf(now);
            PackState next = new PackState(s.getPack(), true, at, s.getLearnedCount());
            states.set(i, next);
            return RepoResult.success(next);
        }
        return RepoResult.failure(RepoError.NOT_FOUND);
    }

    @Override public RepoResult<Boolean> pausePack(String id) {
        if (failure != null) return RepoResult.failure(failure);
        for (int i = 0; i < states.size(); i++) {
            PackState s = states.get(i);
            if (!s.getPack().id.equals(id)) continue;
            states.set(i, new PackState(s.getPack(), false, s.getActivatedAtEpochMillis(), s.getLearnedCount()));
            return RepoResult.success(s.isActive());
        }
        return RepoResult.success(false);
    }

    @Override public RepoResult<List<StudyItem>> getPackWords(String id) {
        calls++;
        if (failure != null) return RepoResult.failure(failure);
        List<StudyItem> list = words.get(id);
        if (list == null) return RepoResult.failure(RepoError.NOT_FOUND);
        return RepoResult.success(new ArrayList<>(list));
    }
}
