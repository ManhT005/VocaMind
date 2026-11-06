package com.vocamind.app.data.catalog;

import android.content.res.AssetManager;
import com.vocamind.app.domain.quiz.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.json.*;

/** Required companion file bound to the full catalog projection, not the active subset. */
public final class QuizPolicyAsset {
    public static final String ASSET = "database/quiz-policy.json";
    private QuizPolicyAsset() { }

    public static QuizDistractorPolicy load(AssetManager assets, List<QuizVocabulary> catalog) {
        try (InputStream input = assets.open(ASSET)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8), catalog);
        } catch (IOException error) {
            throw new IllegalArgumentException("Required catalog quiz policy unavailable", error);
        }
    }

    public static QuizDistractorPolicy parse(String json, List<QuizVocabulary> catalog) {
        try {
            JSONObject root = new JSONObject(json);
            if (!(root.get("schemaVersion") instanceof Integer) || root.getInt("schemaVersion") != 1)
                throw new IllegalArgumentException("Unsupported quiz policy schema");
            if (!root.getString("datasetSha256").matches("[0-9a-f]{64}") ||
                    !projectionDigest(catalog).equals(root.getString("projectionSha256")))
                throw new IllegalArgumentException("Quiz policy/catalog mismatch");
            Set<String> ids = new HashSet<>();
            for (QuizVocabulary v : catalog) ids.add(v.getStableId());
            List<List<String>> pairs = new ArrayList<>();
            JSONArray rows = root.getJSONArray("forbiddenDistractorPairs");
            for (int i = 0; i < rows.length(); i++) {
                JSONArray pair = rows.getJSONArray(i);
                if (pair.length() != 2) throw new IllegalArgumentException("Invalid exclusion pair");
                String a = pair.getString(0), b = pair.getString(1);
                if (!ids.contains(a) || !ids.contains(b)) throw new IllegalArgumentException("Unknown policy ID");
                pairs.add(Arrays.asList(a, b));
            }
            return new QuizDistractorPolicy(pairs);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Malformed quiz policy", error);
        }
    }

    /** SHA-256 of sorted rows, each field framed by its unsigned big-endian UTF-8 byte length. */
    public static String projectionDigest(List<QuizVocabulary> catalog) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<QuizVocabulary> sorted = new ArrayList<>(catalog);
            sorted.sort(Comparator.comparing(QuizVocabulary::getStableId));
            Set<String> ids = new HashSet<>();
            for (QuizVocabulary v : sorted) {
                if (!ids.add(v.getStableId())) throw new IllegalArgumentException("Duplicate catalog ID");
                for (String value : Arrays.asList(v.getStableId(), v.getLemma(), v.getPos(), v.getMeaning(), v.getContext())) {
                    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
