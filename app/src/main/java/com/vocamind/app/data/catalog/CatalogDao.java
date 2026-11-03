package com.vocamind.app.data.catalog;

import androidx.room.Dao;
import androidx.room.Query;
import com.vocamind.app.data.catalog.entity.*;
import java.util.List;

/** Internal read boundary. Never inject this DAO into a ViewModel or Fragment. */
@Dao
public interface CatalogDao {
    @Query("SELECT * FROM LearningPack ORDER BY orderNo, id")
    List<LearningPack> packs();
    @Query("SELECT * FROM LearningPack WHERE id = :id")
    LearningPack pack(String id);
    @Query("SELECT v.* FROM VocabularyEntry v JOIN PackWord p ON p.vocabularyId = v.id WHERE p.packId = :packId ORDER BY p.orderNo, v.id")
    List<VocabularyEntry> words(String packId);
    @Query("SELECT * FROM VocabularyEntry WHERE id = :stableId")
    VocabularyEntry vocabulary(String stableId);
    @Query("SELECT * FROM ContentSource ORDER BY id")
    List<ContentSource> sources();
    @Query("SELECT s.* FROM ContentSource s JOIN VocabularyEntry v ON v.sourceId = s.id WHERE v.id = :stableId")
    ContentSource sourceForVocabulary(String stableId);
    @Query("SELECT COUNT(*) FROM PackWord WHERE packId = :packId")
    int wordCount(String packId);
    // Used to prove the DATA-01 mapping. Full search UX/contracts belong to DATA-05.
    @Query("SELECT v.* FROM VocabularyEntry v JOIN VocabularyFts f ON f.rowid = v.rowid WHERE VocabularyFts MATCH :expression ORDER BY v.id")
    List<VocabularyEntry> matchFixture(String expression);
}
