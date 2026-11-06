package com.vocamind.app.domain.quiz;

/** Unicode 16.0 NFC, Unicode White_Space trim, then default full casefold. */
public final class UnicodeMeaningNormalizer implements MeaningNormalizer {
    @Override public String normalize(String meaning) {
        if (meaning == null) throw new IllegalArgumentException("meaning required");
        String nfc = UnicodeNfc.normalize(meaning);
        int start = 0, end = nfc.length();
        while (start < end && whiteSpace(nfc.codePointAt(start)))
            start += Character.charCount(nfc.codePointAt(start));
        while (end > start && whiteSpace(nfc.codePointBefore(end)))
            end -= Character.charCount(nfc.codePointBefore(end));
        if (start == end) throw new IllegalArgumentException("blank meaning");
        StringBuilder result = new StringBuilder();
        for (int i = start; i < end;) {
            int cp = nfc.codePointAt(i);
            result.append(UnicodeCaseFold.fold(cp));
            i += Character.charCount(cp);
        }
        return result.toString();
    }

    private static boolean whiteSpace(int cp) {
        return (cp >= 0x9 && cp <= 0xd) || cp == 0x20 || cp == 0x85
                || cp == 0xa0 || cp == 0x1680 || (cp >= 0x2000 && cp <= 0x200a)
                || cp == 0x2028 || cp == 0x2029 || cp == 0x202f
                || cp == 0x205f || cp == 0x3000;
    }
}
