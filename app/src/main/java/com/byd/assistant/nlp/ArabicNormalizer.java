package com.byd.assistant.nlp;

import java.util.regex.Pattern;

public final class ArabicNormalizer {
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("[\\u064B-\\u065F\\u0670]");
    private static final Pattern TATWEEL_PATTERN = Pattern.compile("\\u0640+");
    private static final Pattern ALEF_PATTERN = Pattern.compile("[\\u0622\\u0623\\u0625\\u0671]"); // آ, أ, إ, ٱ
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}،؟؛«»!\\.]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private ArabicNormalizer() {}

    public static String normalize(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "";
        }

        String text = rawText;
        // 1. Remove diacritics / tashkeel
        text = DIACRITICS_PATTERN.matcher(text).replaceAll("");

        // 2. Remove tatweel (kashida)
        text = TATWEEL_PATTERN.matcher(text).replaceAll("");

        // 3. Normalize Alef variants to bare Alef (ا)
        text = ALEF_PATTERN.matcher(text).replaceAll("ا");

        // 4. Normalize Taa Marbuta (ة) to Haa (ه)
        text = text.replace('ة', 'ه');

        // 5. Normalize Alef Maqsura (ى) to Yaa (ي)
        text = text.replace('ى', 'ي');

        // 6. Replace punctuation with space
        text = PUNCTUATION_PATTERN.matcher(text).replaceAll(" ");

        // 7. Collapse spaces and trim
        text = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ").trim();

        return text;
    }
}
