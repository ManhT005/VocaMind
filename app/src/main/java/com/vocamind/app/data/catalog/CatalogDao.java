package com.vocamind.app.data.catalog;

import androidx.room.Dao;
import androidx.room.Query;
import android.database.Cursor;
import com.vocamind.app.data.catalog.entity.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.vocamind.app.data.local.DaoInputs;

/** Internal read boundary. Never inject this DAO into a ViewModel or Fragment. */
@Dao
@WorkerThread
public interface CatalogDao {
    @Query("SELECT * FROM VocabularyEntry ORDER BY id")
    List<VocabularyEntry> allVocabulary();
    @Query("SELECT * FROM LearningPack ORDER BY orderNo, id")
    List<LearningPack> packs();
    @Query("SELECT * FROM LearningPack WHERE id = :id")
    @Nullable LearningPack readPack(String id);
    @Nullable default LearningPack pack(String id) { DaoInputs.id(id); return readPack(id); }
    @Query("SELECT v.* FROM VocabularyEntry v JOIN PackWord p ON p.vocabularyId = v.id WHERE p.packId = :packId ORDER BY p.orderNo, v.id")
    List<VocabularyEntry> readWords(String packId);
    default List<VocabularyEntry> words(String packId) { DaoInputs.id(packId); return readWords(packId); }
    @Query("SELECT * FROM VocabularyEntry WHERE id = :stableId")
    @Nullable VocabularyEntry readVocabulary(String stableId);
    @Nullable default VocabularyEntry vocabulary(String stableId) {
        DaoInputs.id(stableId); return readVocabulary(stableId);
    }
    @Query("SELECT * FROM ContentSource ORDER BY id")
    List<ContentSource> sources();
    @Query("SELECT s.* FROM ContentSource s JOIN VocabularyEntry v ON v.sourceId = s.id WHERE v.id = :stableId")
    @Nullable ContentSource readSourceForVocabulary(String stableId);
    @Nullable default ContentSource sourceForVocabulary(String stableId) {
        DaoInputs.id(stableId); return readSourceForVocabulary(stableId);
    }
    @Query("SELECT COUNT(*) FROM PackWord WHERE packId = :packId")
    int readWordCount(String packId);
    default int wordCount(String packId) { DaoInputs.id(packId); return readWordCount(packId); }

    @Query("SELECT * FROM VocabularyEntry WHERE id IN (:ids) ORDER BY id")
    List<VocabularyEntry> readVocabularies(List<String> ids);

    /** Unique batch input, bounded binds, globally ordered output; no synthetic missing rows. */
    default List<VocabularyEntry> vocabulariesByIds(List<String> ids) {
        List<VocabularyEntry> result = new ArrayList<>();
        for (List<String> batch : DaoInputs.batches(ids)) result.addAll(readVocabularies(batch));
        result.sort(Comparator.comparing(row -> row.id));
        return result;
    }

    @Query("SELECT p.packId, p.vocabularyId, l.orderNo AS packOrderNo, p.orderNo AS wordOrderNo "
            + "FROM PackWord p JOIN LearningPack l ON l.id=p.packId "
            + "JOIN VocabularyEntry v ON v.id=p.vocabularyId WHERE p.packId IN (:packIds) "
            + "ORDER BY l.orderNo, p.packId, p.orderNo, p.vocabularyId")
    List<PackMembership> readMemberships(List<String> packIds);

    default List<PackMembership> membershipsForPacks(List<String> packIds) {
        List<PackMembership> result = new ArrayList<>();
        for (List<String> batch : DaoInputs.batches(packIds)) result.addAll(readMemberships(batch));
        result.sort(PackMembership.ORDER);
        return result;
    }

    /** Candidate selection only: caller supplies active pack IDs from userdata. */
    default List<String> uniqueVocabularyIdsForPacks(List<String> packIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (PackMembership row : membershipsForPacks(packIds)) ids.add(row.vocabularyId);
        return new ArrayList<>(ids);
    }
    /** Production FTS4 path; the expression is generated from literal tokens, not raw UI syntax. */
    @Query("SELECT v.* FROM VocabularyEntry v JOIN VocabularyFts f ON f.rowid = v.rowid "
            + "WHERE VocabularyFts MATCH :expression")
    Cursor readSearchCursor(String expression);

    // Generated scalar queries use Room's connection/close barrier. The legacy
    // Cursor query alone can reopen a closed SupportSQLiteOpenHelper in Room 2.8.
    // Preserve READ_FAILED after owner close, and Room's worker-thread checks.
    @Query("SELECT 1")
    int checkSearchConnection();

    /**
     * Reading the cursor directly avoids SQLite's temporary B-tree plus the
     * compatibility statement adapter's extra per-column isNull/getText calls.
     * The in-memory sort preserves the published normalized-lemma/id contract.
     * The cursor belongs to this call, never to UI consumers.
     */
    default List<VocabularyEntry> searchVocabulary(String expression) {
        checkSearchConnection();
        try (Cursor cursor = readSearchCursor(expression)) {
            int id = cursor.getColumnIndexOrThrow("id");
            int lemma = cursor.getColumnIndexOrThrow("lemma");
            int normalized = cursor.getColumnIndexOrThrow("normalizedLemma");
            int pos = cursor.getColumnIndexOrThrow("pos");
            int meaning = cursor.getColumnIndexOrThrow("meaningVi");
            int en = cursor.getColumnIndexOrThrow("exampleEn");
            int vi = cursor.getColumnIndexOrThrow("exampleVi");
            int ipa = cursor.getColumnIndexOrThrow("ipa");
            int source = cursor.getColumnIndexOrThrow("sourceId");
            int version = cursor.getColumnIndexOrThrow("contentVersion");
            List<VocabularyEntry> rows = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                VocabularyEntry row = new VocabularyEntry();
                row.id = cursor.getString(id); row.lemma = cursor.getString(lemma);
                row.normalizedLemma = cursor.getString(normalized); row.pos = cursor.getString(pos);
                row.meaningVi = cursor.getString(meaning); row.exampleEn = cursor.getString(en);
                row.exampleVi = cursor.getString(vi); row.ipa = cursor.getString(ipa);
                row.sourceId = cursor.getString(source); row.contentVersion = cursor.getString(version);
                rows.add(row);
            }
            rows.sort(Comparator
                    .comparing((VocabularyEntry row) -> row.normalizedLemma, CatalogDao::compareSqliteNoCase)
                    .thenComparing(row -> row.id));
            return rows;
        }
    }
    /** SQLite NOCASE folds ASCII only; keep non-ASCII spelling order unchanged. */
    static int compareSqliteNoCase(String left, String right) {
        int i = 0, j = 0;
        while (i < left.length() && j < right.length()) {
            int a = left.codePointAt(i), b = right.codePointAt(j);
            i += Character.charCount(a); j += Character.charCount(b);
            if (a >= 'A' && a <= 'Z') a += 'a' - 'A';
            if (b >= 'A' && b <= 'Z') b += 'a' - 'A';
            if (a != b) return Integer.compare(a, b);
        }
        return Integer.compare(left.length(), right.length());
    }
    // Compatibility probe for DATA-01 fixtures.
    @Query("SELECT v.* FROM VocabularyEntry v JOIN VocabularyFts f ON f.rowid = v.rowid WHERE VocabularyFts MATCH :expression ORDER BY v.id")
    List<VocabularyEntry> matchFixture(String expression);
}
