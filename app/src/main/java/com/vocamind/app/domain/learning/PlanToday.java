package com.vocamind.app.domain.learning;

import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.common.RepoResult;
import com.vocamind.app.domain.pack.PackRepository;
import com.vocamind.app.domain.pack.PackState;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Today Planner. Blocking: call from a worker executor.
 * Tier 1: due words, dueAt ASC. Tier 3: never-seen words in pack order, limited to the
 * remaining daily quota. Words are de-duplicated by stableId; only active packs contribute.
 */
public final class PlanToday {
    /** Safety cap on due words in one session so a long absence cannot build an endless queue. */
    public static final int MAX_DUE = 200;

    private final PackRepository packs;
    private final StudyRepository study;
    private final Clock clock;
    private final ZoneId zone;

    public PlanToday(PackRepository packs, StudyRepository study, Clock clock, ZoneId zone) {
        this.packs = Objects.requireNonNull(packs);
        this.study = Objects.requireNonNull(study);
        this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone);
    }

    public RepoResult<TodayPlan> execute() {
        RepoResult<List<PackState>> states = packs.getPackStates();
        if (!states.isSuccess()) return RepoResult.failure(states.getError());
        boolean anyActive = false;
        for (PackState s : states.get()) anyActive |= s.isActive();
        if (!anyActive) return RepoResult.success(new TodayPlan(TodayPlan.Status.NO_ACTIVE_PACK, List.of(), List.of()));

        long now = clock.millis();
        long dayStart = clock.instant().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
        long nextDayStart = clock.instant().atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        RepoResult<DailyQuota> quota = study.dailyQuota(dayStart, nextDayStart);
        if (!quota.isSuccess()) return RepoResult.failure(quota.getError());
        RepoResult<List<StudyItem>> dueResult = study.dueItems(now, MAX_DUE);
        if (!dueResult.isSuccess()) return RepoResult.failure(dueResult.getError());

        Set<String> seen = new HashSet<>();
        List<StudyItem> due = new ArrayList<>();
        for (StudyItem item : dueResult.get()) if (seen.add(item.getVocabulary().stableId)) due.add(item);

        List<CatalogVocabulary> fresh = new ArrayList<>();
        int remaining = quota.get().remaining();
        if (remaining > 0) {
            RepoResult<List<CatalogVocabulary>> newResult = study.newWords(remaining);
            if (!newResult.isSuccess()) return RepoResult.failure(newResult.getError());
            for (CatalogVocabulary v : newResult.get()) {
                if (fresh.size() == remaining) break;
                if (seen.add(v.stableId)) fresh.add(v);
            }
        }
        TodayPlan.Status status = due.isEmpty() && fresh.isEmpty() ? TodayPlan.Status.DONE : TodayPlan.Status.READY;
        return RepoResult.success(new TodayPlan(status, due, fresh));
    }
}
