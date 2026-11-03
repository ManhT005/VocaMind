package com.vocamind.app.data.catalog;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.vocamind.app.data.catalog.entity.*;

/** Physically separate from userdata.db; catalog business API is read-only. */
@Database(entities = {ContentSource.class, VocabularyEntry.class, VocabularyFts.class,
        LearningPack.class, PackWord.class}, version = 1, exportSchema = true)
public abstract class CatalogDatabase extends RoomDatabase {
    public static final String ASSET = "database/catalog.db";
    public abstract CatalogDao catalogDao();

    public static CatalogDatabase open(Context context, String databaseName) {
        return Room.databaseBuilder(context.getApplicationContext(), CatalogDatabase.class, databaseName)
                .createFromAsset(ASSET)
                .build();
    }
}
