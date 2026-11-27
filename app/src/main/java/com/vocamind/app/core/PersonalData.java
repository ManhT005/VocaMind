package com.vocamind.app.core;

import android.content.Context;
import androidx.annotation.WorkerThread;
import com.vocamind.app.data.catalog.CatalogMapper;
import com.vocamind.app.data.user.UserDatabase;
import com.vocamind.app.domain.catalog.CatalogVocabulary;
import com.vocamind.app.domain.pack.PackState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@WorkerThread
public final class PersonalData {
    private PersonalData() {}

    public static void resetLearningResults(Context context) {
        UserDatabase db = UserDatabase.open(context);
        try {
            db.userDao().resetLearningResults(System.currentTimeMillis());
        } finally {
            db.close();
        }
    }

    public static int dailyGoal(Context context) {
        UserDatabase db = UserDatabase.open(context);
        try {
            return db.userDao().preference().dailyNewWords;
        } finally {
            db.close();
        }
    }

    public static int saveDailyGoal(Context context, int value) {
        if (value < 1 || value > 100) throw new IllegalArgumentException("daily goal");
        UserDatabase db = UserDatabase.open(context);
        try {
            if (db.userDao().updateDailyNewWords(value) != 1) {
                throw new IllegalStateException("Missing preference");
            }
            return value;
        } finally {
            db.close();
        }
    }

    public static List<CatalogVocabulary> savedWords(Context context) {
        UserDatabase db = UserDatabase.open(context);
        try (CatalogProvider catalog = new CatalogProvider(context)) {
            List<String> ids = db.userDao().savedVocabularyIds();
            List<CatalogVocabulary> words = new ArrayList<>();
            // Batch IDs to stay below the bind limit on older devices.
            for (int start = 0; start < ids.size(); start += 500) {
                for (var row : catalog.catalogDao().vocabulariesByIds(ids.subList(start, Math.min(start + 500, ids.size())))) {
                    words.add(CatalogMapper.vocabulary(row));
                }
            }
            words.sort(Comparator.comparing((CatalogVocabulary word) -> word.lemma, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(word -> word.stableId));
            return words;
        } finally {
            db.close();
        }
    }

    public static List<PackState> activePacks(Context context) {
        try (CatalogProvider catalog = new CatalogProvider(context);
             RepositoryProvider repositories = new RepositoryProvider(context, catalog)) {
            var result = repositories.packs().getPackStates();
            if (!result.isSuccess()) throw new IllegalStateException("Could not read packs");
            List<PackState> active = new ArrayList<>();
            for (PackState pack : result.get()) {
                if (pack.isActive()) active.add(pack);
            }
            return active;
        }
    }
}
