package com.vocamind.app.data.repository;

import androidx.annotation.WorkerThread;
import com.vocamind.app.data.catalog.CatalogDao;
import com.vocamind.app.data.catalog.CatalogMapper;
import com.vocamind.app.data.catalog.PackMembership;
import com.vocamind.app.data.catalog.entity.LearningPack;
import com.vocamind.app.data.catalog.entity.VocabularyEntry;
import com.vocamind.app.data.user.UserDao;
import com.vocamind.app.data.user.entity.UserPack;
import com.vocamind.app.data.user.entity.UserWordProgress;
import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.learning.StudyItem;
import com.vocamind.app.domain.pack.PackRepository;
import com.vocamind.app.domain.pack.PackState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WorkerThread
public final class RoomPackRepository implements PackRepository {
    private final CatalogDao catalog;
    private final UserDao user;

    public RoomPackRepository(CatalogDao catalog, UserDao user) {
        this.catalog = catalog;
        this.user = user;
    }

    @Override public RepoResult<List<PackState>> getPackStates() {
        return RepositoryGuard.read(() -> {
            List<LearningPack> packs = catalog.packs();
            Map<String, UserPack> userPacks = new HashMap<>();
            for (UserPack p : user.packs()) userPacks.put(p.packId, p);
            List<String> packIds = new ArrayList<>();
            for (LearningPack p : packs) packIds.add(p.id);
            Map<String, Set<String>> wordsByPack = new HashMap<>();
            List<String> allIds = new ArrayList<>();
            for (PackMembership m : catalog.membershipsForPacks(packIds)) {
                wordsByPack.computeIfAbsent(m.packId, k -> new HashSet<>()).add(m.vocabularyId);
                allIds.add(m.vocabularyId);
            }
            Set<String> learned = new HashSet<>();
            for (UserWordProgress p : user.progressByIds(allIds)) if (p.learnedAt != null) learned.add(p.vocabularyId);
            List<PackState> out = new ArrayList<>();
            for (LearningPack p : packs) {
                Set<String> words = wordsByPack.getOrDefault(p.id, Collections.<String>emptySet());
                int count = 0;
                for (String w : words) if (learned.contains(w)) count++;
                out.add(state(p, userPacks.get(p.id), words.size(), count));
            }
            return RepoResult.success(Collections.unmodifiableList(out));
        });
    }

    @Override public RepoResult<PackState> activatePack(String packId, long now) {
        if (packId == null || packId.trim().isEmpty() || now < 0) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.write(() -> {
            if (catalog.pack(packId) == null) return RepoResult.<PackState>failure(RepoError.NOT_FOUND);
            user.activatePack(packId, now);
            return stateOf(packId);
        });
    }

    @Override public RepoResult<Boolean> pausePack(String packId) {
        if (packId == null || packId.trim().isEmpty()) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.write(() -> {
            if (catalog.pack(packId) == null) return RepoResult.<Boolean>failure(RepoError.NOT_FOUND);
            UserPack before = user.pack(packId);
            boolean wasActive = before != null && before.isActive;
            user.pausePack(packId);
            return RepoResult.success(wasActive);
        });
    }

    @Override public RepoResult<List<StudyItem>> getPackWords(String packId) {
        if (packId == null || packId.trim().isEmpty()) return RepoResult.failure(RepoError.INVALID_ARGUMENT);
        return RepositoryGuard.read(() -> {
            if (catalog.pack(packId) == null) return RepoResult.<List<StudyItem>>failure(RepoError.NOT_FOUND);
            List<VocabularyEntry> words = catalog.words(packId);
            List<String> ids = new ArrayList<>();
            for (VocabularyEntry v : words) ids.add(v.id);
            Map<String, UserWordProgress> progress = new HashMap<>();
            for (UserWordProgress p : user.progressByIds(ids)) progress.put(p.vocabularyId, p);
            List<StudyItem> out = new ArrayList<>();
            for (VocabularyEntry v : words) {
                UserWordProgress p = progress.get(v.id);
                out.add(new StudyItem(CatalogMapper.vocabulary(v), p == null ? null : UserDataMapper.progress(p)));
            }
            return RepoResult.success(Collections.unmodifiableList(out));
        });
    }

    private RepoResult<PackState> stateOf(String packId) {
        RepoResult<List<PackState>> states = getPackStates();
        if (!states.isSuccess()) return RepoResult.failure(states.getError());
        for (PackState s : states.get()) if (s.getPack().id.equals(packId)) return RepoResult.success(s);
        return RepoResult.failure(RepoError.NOT_FOUND);
    }

    private static PackState state(LearningPack p, UserPack u, int words, int learned) {
        return new PackState(CatalogMapper.pack(p, words), u != null && u.isActive, UserDataMapper.activatedAt(u), learned);
    }
}
