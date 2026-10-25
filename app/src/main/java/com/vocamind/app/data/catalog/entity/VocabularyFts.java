package com.vocamind.app.data.catalog.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Fts4;

/** Standalone index populated at catalog build time, never written by app business code. */
@Fts4
@Entity
public class VocabularyFts {
    @NonNull public String lemma;
    @NonNull public String normalizedLemma;
    @NonNull public String meaningVi;
}
