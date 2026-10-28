package com.vocamind.app.data.user.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Internal Room row; references to catalog are logical stable IDs. */
@Entity
public class UserPack {
    @PrimaryKey
    @NonNull
    public String packId;
    public long activatedAt;
    public boolean isActive;
}
