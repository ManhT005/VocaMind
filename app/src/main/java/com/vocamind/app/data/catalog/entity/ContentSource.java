package com.vocamind.app.data.catalog.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity
public class ContentSource {
    @PrimaryKey @NonNull public String id;
    @NonNull public String title;
    @NonNull public String url;
    @NonNull public String licenseCode;
    @NonNull public String licenseUrl;
    @NonNull public String snapshotDate;
}
