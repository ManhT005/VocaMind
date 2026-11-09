package com.vocamind.app.data.catalog.upgrade;

import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.Set;

/** Startup-only read: no user writers may be exposed until the selected generation is committed. */
final class CatalogReferenceChecker {
    private CatalogReferenceChecker() { }
    static void check(File userdata, Set<String> vocabularyIds, Set<String> packIds) {
        if (!userdata.exists()) return;
        // Android's default corruption handler may delete even a read-only database.
        // A failed validation must preserve the user's file for recovery/retry.
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(userdata.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY,
                corrupted -> { throw new CatalogUpgradeException("Corrupt userdata; file retained", null); })) {
            // IDs and table names are constants. Never migrate, seed preferences, or edit user data here.
            try (var rows = db.rawQuery("SELECT vocabularyId FROM UserWordProgress UNION SELECT vocabularyId FROM ReviewEvent", null)) {
                while (rows.moveToNext()) if (!vocabularyIds.contains(rows.getString(0)))
                    throw new IllegalStateException("Candidate removes a referenced vocabulary ID");
            }
            try (var rows = db.rawQuery("SELECT packId FROM UserPack", null)) {
                while (rows.moveToNext()) if (!packIds.contains(rows.getString(0)))
                    throw new IllegalStateException("Candidate removes a referenced pack ID");
            }
        }
    }
}
