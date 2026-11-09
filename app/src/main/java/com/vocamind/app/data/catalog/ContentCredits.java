package com.vocamind.app.data.catalog;

import android.content.res.AssetManager;
import com.vocamind.app.domain.catalog.CatalogSource;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Collections;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local companion metadata matched to the exact source and snapshot. */
public final class ContentCredits {
    public static final String ASSET = "database/content-credits.json";
    private record Key(String sourceId, String snapshotDate) { }
    private record Credit(List<String> authors, String text) { }
    private final Map<Key, Credit> credits;

    private ContentCredits(Map<Key, Credit> credits) { this.credits = Collections.unmodifiableMap(credits); }
    public static ContentCredits empty() { return new ContentCredits(Collections.emptyMap()); }

    public static ContentCredits load(AssetManager assets) {
        try (var input = assets.open(ASSET)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException absentLegacyAsset) {
            return empty();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read local credits", failure);
        }
    }

    public static ContentCredits parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!(root.get("schemaVersion") instanceof Integer) || root.getInt("schemaVersion") != 1) {
                throw new IllegalArgumentException("Unsupported credit schema version");
            }
            JSONArray rows = root.getJSONArray("sources");
            Map<Key, Credit> out = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Key key = new Key(text(row.get("sourceId")), text(row.get("snapshotDate")));
                List<String> authors = new ArrayList<>();
                JSONArray values = row.getJSONArray("authors");
                for (int j = 0; j < values.length(); j++) authors.add(text(values.get(j)));
                String notice = row.isNull("creditText") ? null : text(row.get("creditText"));
                if (out.put(key, new Credit(Collections.unmodifiableList(authors), notice)) != null) {
                    throw new IllegalArgumentException("Duplicate source/snapshot credit");
                }
            }
            return new ContentCredits(out);
        } catch (JSONException invalid) {
            throw new IllegalArgumentException("Malformed local credits", invalid);
        }
    }

    private static String text(Object value) {
        if (!(value instanceof String) || ((String) value).codePoints().allMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Credit strings must be nonblank");
        }
        return (String) value;
    }

    /** Exact generation binding; legacy enrich() remains permissive for old callers. */
    public void verifySources(List<CatalogSource> sources) {
        java.util.Set<Key> expected = new java.util.HashSet<>();
        for (CatalogSource source : sources) expected.add(new Key(source.id, source.snapshotDate));
        if (!expected.equals(credits.keySet())) throw new IllegalArgumentException("Credits/catalog snapshot mismatch");
    }

    public CatalogSource enrich(CatalogSource source) {
        Credit credit = credits.get(new Key(source.id, source.snapshotDate));
        if (credit == null) return source;
        return new CatalogSource(source.id, source.title, source.url, source.licenseCode,
                source.licenseUrl, source.snapshotDate, source.attributionText, credit.authors(), credit.text());
    }
}
