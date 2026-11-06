package com.vocamind.app.domain.quiz;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Official Unicode 16.0 NFC invariants, including scalars absent from Part 1.
 * Inherited unchanged by AndroidNfcConformanceTest on API 26 and API 35. */
public class NfcConformanceTest {
    private static Path data(String name) {
        Path root = Paths.get("tools/unicode");
        if (!Files.exists(root)) root = Paths.get("../tools/unicode");
        return root.resolve(name);
    }

    private static String decode(String field) {
        StringBuilder result = new StringBuilder();
        for (String hex : field.trim().split(" +")) result.appendCodePoint(Integer.parseInt(hex, 16));
        return result.toString();
    }

    @Test public void allOfficialNfcVectorsAndUnlistedScalars() throws Exception {
        int rows = 0;
        boolean partOne = false;
        BitSet listed = new BitSet(0x110000);
        for (String line : Files.readAllLines(data("NormalizationTest-16.0.0.txt"), StandardCharsets.UTF_8)) {
            if (line.startsWith("@")) { partOne = line.startsWith("@Part1 "); continue; }
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] fields = line.split(";");
            String[] columns = new String[5];
            for (int i = 0; i < 5; i++) columns[i] = decode(fields[i]);
            if (partOne) {
                assertEquals(1, columns[0].codePointCount(0, columns[0].length()));
                listed.set(columns[0].codePointAt(0));
            }
            for (int i = 0; i < 5; i++) {
                String expected = columns[i < 3 ? 1 : 3];
                String actual = UnicodeNfc.normalize(columns[i]);
                if (!expected.equals(actual)) fail("Unicode 16 NFC row " + (rows + 1)
                        + " column " + (i + 1) + " input " + fields[i]);
            }
            rows++;
        }
        assertEquals(19965, rows);
        assertTrue("Part 1 must be parsed", listed.cardinality() > 10000);
        for (int cp = 0; cp <= 0x10ffff; cp++) {
            if ((cp >= 0xd800 && cp <= 0xdfff) || listed.get(cp)) continue;
            String scalar = new String(Character.toChars(cp));
            if (!scalar.equals(UnicodeNfc.normalize(scalar)))
                fail("Unlisted scalar changed: U+" + Integer.toHexString(cp));
        }
    }

    @Test public void productionAdapterHandlesPostJdkUnicodeCompositions() {
        MeaningNormalizer normalizer = new UnicodeMeaningNormalizer();
        // Unicode 16 Todhri canonical decomposition: 105C9 -> 105D2 0307.
        // The platform JDK 17 normalizer failed this official vector before the fix.
        String composed = new String(Character.toChars(0x105c9));
        String decomposed = new String(Character.toChars(0x105d2)) + "\u0307";
        assertEquals(composed, UnicodeNfc.normalize(decomposed));
        assertEquals(normalizer.normalize(composed), normalizer.normalize(decomposed));
    }
}
