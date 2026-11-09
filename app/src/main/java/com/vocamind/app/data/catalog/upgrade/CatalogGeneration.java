package com.vocamind.app.data.catalog.upgrade;

import android.content.Context;
import androidx.room.Room;
import com.vocamind.app.data.catalog.*;
import com.vocamind.app.domain.quiz.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Frozen companion files and a separate, logically read-only Room working copy. */
public final class CatalogGeneration {
    final File directory;
    public final String id;
    CatalogGeneration(File directory, String id) { this.directory = directory; this.id = id; }
    public File bundleFile(String name) {
        if (!CatalogStartupCoordinator.FILES.contains(name)) throw new IllegalArgumentException("Unknown bundle file");
        return new File(new File(directory, "bundle"), name);
    }
    public CatalogDatabase openDatabase(Context context) {
        File runtime = new File(directory, "runtime");
        if (!runtime.isDirectory() && !runtime.mkdirs()) throw new IllegalStateException("Cannot create catalog runtime");
        return Room.databaseBuilder(context, CatalogDatabase.class, new File(runtime, "catalog.db").getAbsolutePath())
                .createFromFile(bundleFile("catalog.db")).build();
    }
    public ContentCredits credits() { return ContentCredits.parse(text("content-credits.json")); }
    public QuizDistractorPolicy quizPolicy(List<QuizVocabulary> words) {
        return QuizPolicyAsset.parse(text("quiz-policy.json"), words);
    }
    String text(String name) {
        try { return new String(Files.readAllBytes(bundleFile(name).toPath()), StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { throw new CatalogUpgradeException("Cannot read catalog companion", e); }
    }
}
