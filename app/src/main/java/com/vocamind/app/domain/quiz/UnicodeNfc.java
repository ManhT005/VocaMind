package com.vocamind.app.domain.quiz;

import java.util.Arrays;

/** Unicode 16.0 NFC: canonical decomposition, ordering, then blocked composition.
 * Pinned tables make normalization identical on JDK and all supported Android APIs.
 * Hangul decomposition/composition follows the algorithm in UAX #15.
 */
final class UnicodeNfc {
    private static final int S_BASE = 0xac00, L_BASE = 0x1100, V_BASE = 0x1161,
            T_BASE = 0x11a7, L_COUNT = 19, V_COUNT = 21, T_COUNT = 28,
            N_COUNT = V_COUNT * T_COUNT, S_COUNT = L_COUNT * N_COUNT;

    private UnicodeNfc() {}

    static String normalize(String input) {
        Buffer decomposed = new Buffer(input.length());
        for (int offset = 0; offset < input.length();) {
            int cp = input.codePointAt(offset);
            decompose(cp, decomposed);
            offset += Character.charCount(cp);
        }
        // Compact composed output in place. A consumed mark does not block the next.
        int size = 0, starterIndex = -1, starter = -1, lastClass = 0;
        for (int i = 0; i < decomposed.size; i++) {
            int cp = decomposed.points[i];
            int ccc = UnicodeNfcData.combiningClass(cp);
            int composite = starterIndex < 0 ? -1 : compose(starter, cp);
            if (composite >= 0 && (lastClass == 0 || lastClass < ccc)) {
                starter = composite;
                decomposed.points[starterIndex] = composite;
            } else {
                if (ccc == 0) { starterIndex = size; starter = cp; }
                decomposed.points[size++] = cp;
                lastClass = ccc;
            }
        }
        StringBuilder output = new StringBuilder(input.length());
        for (int i = 0; i < size; i++) output.appendCodePoint(decomposed.points[i]);
        return output.toString();
    }

    private static void decompose(int cp, Buffer out) {
        int syllable = cp - S_BASE;
        if (syllable >= 0 && syllable < S_COUNT) {
            out.appendOrdered(L_BASE + syllable / N_COUNT);
            out.appendOrdered(V_BASE + (syllable % N_COUNT) / T_COUNT);
            int trailing = syllable % T_COUNT;
            if (trailing != 0) out.appendOrdered(T_BASE + trailing);
            return;
        }
        String mapping = UnicodeNfcData.decomposition(cp);
        if (mapping == null) {
            out.appendOrdered(cp);
        } else {
            for (int offset = 0; offset < mapping.length();) {
                int part = mapping.codePointAt(offset);
                decompose(part, out);
                offset += Character.charCount(part);
            }
        }
    }

    private static int compose(int first, int second) {
        int leading = first - L_BASE, vowel = second - V_BASE;
        if (leading >= 0 && leading < L_COUNT && vowel >= 0 && vowel < V_COUNT)
            return S_BASE + (leading * V_COUNT + vowel) * T_COUNT;
        int syllable = first - S_BASE, trailing = second - T_BASE;
        if (syllable >= 0 && syllable < S_COUNT && syllable % T_COUNT == 0
                && trailing > 0 && trailing < T_COUNT) return first + trailing;
        return UnicodeNfcData.composition(first, second);
    }

    private static final class Buffer {
        private int[] points;
        private int size;
        Buffer(int initialSize) { points = new int[Math.max(8, initialSize)]; }
        void appendOrdered(int cp) {
            if (size == points.length) points = Arrays.copyOf(points, size * 2);
            int ccc = UnicodeNfcData.combiningClass(cp);
            int position = size;
            if (ccc != 0) while (position > 0
                    && UnicodeNfcData.combiningClass(points[position - 1]) > ccc) {
                points[position] = points[position - 1];
                position--;
            }
            points[position] = cp;
            size++;
        }
    }
}
