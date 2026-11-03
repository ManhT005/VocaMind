package com.vocamind.app.data.catalog.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity
public class LearningPack {
    @PrimaryKey @NonNull public String id;
    @NonNull public String title;
    @NonNull public String description;
    @NonNull public String category;
     public int orderNo;
    @NonNull public String version;
}
