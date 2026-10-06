package com.byd.assistant.nlp;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Named Entity Recognition (NER) and slot extraction for numbers, target entities,
 * and command parameters in Arabic automotive voice interaction.
 */
public final class SlotExtractor {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");

    private static final Map<String, Integer> UNITS_MAP = new HashMap<>();
    private static final Map<String, Integer> TENS_MAP = new HashMap<>();
    private static final Map<String, Integer> TEENS_MAP = new HashMap<>();

    static {
        // Units (0 - 9)
        UNITS_MAP.put("صفر", 0);
        UNITS_MAP.put("واحد", 1);
        UNITS_MAP.put("واحده", 1);
        UNITS_MAP.put("وحده", 1);
        UNITS_MAP.put("احد", 1);
        UNITS_MAP.put("احدي", 1);
        UNITS_MAP.put("اثنان", 2);
        UNITS_MAP.put("اثنين", 2);
        UNITS_MAP.put("اثنتان", 2);
        UNITS_MAP.put("اثنتين", 2);
        UNITS_MAP.put("ثنتين", 2);
        UNITS_MAP.put("ثنتان", 2);
        UNITS_MAP.put("ثلاثه", 3);
        UNITS_MAP.put("ثلاث", 3);
        UNITS_MAP.put("اربعه", 4);
        UNITS_MAP.put("اربع", 4);
        UNITS_MAP.put("خمسه", 5);
        UNITS_MAP.put("خمس", 5);
        UNITS_MAP.put("سته", 6);
        UNITS_MAP.put("ست", 6);
        UNITS_MAP.put("سبعه", 7);
        UNITS_MAP.put("سبع", 7);
        UNITS_MAP.put("ثمانيه", 8);
        UNITS_MAP.put("ثماني", 8);
        UNITS_MAP.put("ثمان", 8);
        UNITS_MAP.put("تسعه", 9);
        UNITS_MAP.put("تسع", 9);

        // Tens (10, 20, 30 ... 90, 100)
        TENS_MAP.put("عشره", 10);
        TENS_MAP.put("عشر", 10);
        TENS_MAP.put("عشرون", 20);
        TENS_MAP.put("عشرين", 20);
        TENS_MAP.put("ثلاثون", 30);
        TENS_MAP.put("ثلاثين", 30);
        TENS_MAP.put("اربعون", 40);
        TENS_MAP.put("اربعين", 40);
        TENS_MAP.put("خمسون", 50);
        TENS_MAP.put("خمسين", 50);
        TENS_MAP.put("ستون", 60);
        TENS_MAP.put("ستين", 60);
        TENS_MAP.put("سبعون", 70);
        TENS_MAP.put("سبعين", 70);
        TENS_MAP.put("ثمانون", 80);
        TENS_MAP.put("ثمانين", 80);
        TENS_MAP.put("تسعون", 90);
        TENS_MAP.put("تسعين", 90);
        TENS_MAP.put("ميه", 100);
        TENS_MAP.put("مائه", 100);

        // Teens (11 - 19)
        TEENS_MAP.put("احد عشر", 11);
        TEENS_MAP.put("احدعش", 11);
        TEENS_MAP.put("حداش", 11);
        TEENS_MAP.put("حدعش", 11);
        TEENS_MAP.put("اثنا عشر", 12);
        TEENS_MAP.put("اثني عشر", 12);
        TEENS_MAP.put("اثناعش", 12);
        TEENS_MAP.put("اثنعش", 12);
        TEENS_MAP.put("ثنعش", 12);
        TEENS_MAP.put("ثلاثه عشر", 13);
        TEENS_MAP.put("ثلاث عشر", 13);
        TEENS_MAP.put("تلطاش", 13);
        TEENS_MAP.put("تلطعش", 13);
        TEENS_MAP.put("ثلطعش", 13);
        TEENS_MAP.put("اربعة عشر", 14);
        TEENS_MAP.put("اربعه عشر", 14);
        TEENS_MAP.put("اربع عشر", 14);
        TEENS_MAP.put("اربعطاش", 14);
        TEENS_MAP.put("اربعطعش", 14);
        TEENS_MAP.put("خمسة عشر", 15);
        TEENS_MAP.put("خمسه عشر", 15);
        TEENS_MAP.put("خمس عشر", 15);
        TEENS_MAP.put("خمسطاش", 15);
        TEENS_MAP.put("خمسطعش", 15);
        TEENS_MAP.put("ستة عشر", 16);
        TEENS_MAP.put("سته عشر", 16);
        TEENS_MAP.put("ست عشر", 16);
        TEENS_MAP.put("ستطاش", 16);
        TEENS_MAP.put("ستطعش", 16);
        TEENS_MAP.put("سبعة عشر", 17);
        TEENS_MAP.put("سبعه عشر", 17);
        TEENS_MAP.put("سبع عشر", 17);
        TEENS_MAP.put("سبعطاش", 17);
        TEENS_MAP.put("سبعطعش", 17);
        TEENS_MAP.put("ثمانية عشر", 18);
        TEENS_MAP.put("ثمانيه عشر", 18);
        TEENS_MAP.put("ثماني عشر", 18);
        TEENS_MAP.put("ثمان عشر", 18);
        TEENS_MAP.put("ثمنطاش", 18);
        TEENS_MAP.put("ثمنطعش", 18);
        TEENS_MAP.put("تسعة عشر", 19);
        TEENS_MAP.put("تسعه عشر", 19);
        TEENS_MAP.put("تسع عشر", 19);
        TEENS_MAP.put("تسعطاش", 19);
        TEENS_MAP.put("تسعطعش", 19);
    }

    private SlotExtractor() {}

    /**
     * Converts Eastern Arabic-Indic numerals (٠-٩) and Persian numerals (۰-۹) to ASCII digits (0-9).
     */
    public static String convertArabicDigitsToAscii(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') {
                sb.append((char) ('0' + (ch - '\u0660')));
            } else if (ch >= '\u06F0' && ch <= '\u06F9') {
                sb.append((char) ('0' + (ch - '\u06F0')));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Extracts an integer from spoken text, supporting ASCII digits, Eastern Arabic digits,
     * and written Arabic words (units, tens, compounds like "واحد وعشرين", teens).
     *
     * @param text Input spoken text
     * @param defaultValue Fallback value if no integer is found
     * @return Extracted integer or defaultValue
     */
    public static int extractInteger(String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }

        // 1. Check for digits (converting Arabic-Indic digits to ASCII first)
        String converted = convertArabicDigitsToAscii(text);
        Matcher digitMatcher = DIGIT_PATTERN.matcher(converted);
        if (digitMatcher.find()) {
            try {
                return Integer.parseInt(digitMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        // 2. Parse written Arabic numbers
        String normalized = ArabicNormalizer.normalize(converted);
        if (normalized.isEmpty()) {
            return defaultValue;
        }

        // 2.1 Check compound numbers: <unit> و <tens> or <unit> و<tens>
        // e.g. "اربعه وعشرين", "واحد وعشرين", "اثنان وعشرون"
        for (Map.Entry<String, Integer> unitEntry : UNITS_MAP.entrySet()) {
            String uName = unitEntry.getKey();
            if (uName.equals("صفر")) continue;

            for (Map.Entry<String, Integer> tenEntry : TENS_MAP.entrySet()) {
                String tName = tenEntry.getKey();
                if (tName.equals("عشره") || tName.equals("عشر")) continue; // not compound with 10

                // Patterns: "واحد وعشرين" or "واحد و عشرين"
                String compound1 = uName + " و" + tName;
                String compound2 = uName + " و " + tName;

                if (containsWordPhrase(normalized, compound1) || containsWordPhrase(normalized, compound2)) {
                    return unitEntry.getValue() + tenEntry.getValue();
                }
            }
        }

        // 2.2 Check teens (11 to 19)
        for (Map.Entry<String, Integer> teenEntry : TEENS_MAP.entrySet()) {
            if (containsWordPhrase(normalized, teenEntry.getKey())) {
                return teenEntry.getValue();
            }
        }

        // 2.3 Check tens standalone (10, 20, 30 ... 90, 100)
        for (Map.Entry<String, Integer> tenEntry : TENS_MAP.entrySet()) {
            if (containsWord(normalized, tenEntry.getKey())) {
                return tenEntry.getValue();
            }
        }

        // 2.4 Check units standalone (0 to 9)
        for (Map.Entry<String, Integer> unitEntry : UNITS_MAP.entrySet()) {
            if (containsWord(normalized, unitEntry.getKey())) {
                return unitEntry.getValue();
            }
        }

        return defaultValue;
    }

    /**
     * Extracts the target entity from spoken text.
     * Supported targets: "DRIVER", "PASSENGER", "SUNROOF", "ALL".
     *
     * @param text Input spoken text
     * @return Target string or null if ambiguous / not specified
     */
    public static String extractTarget(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String normalized = ArabicNormalizer.normalize(text);
        if (normalized.isEmpty()) {
            return null;
        }

        // 1. Sunroof target
        if (normalized.contains("سقف") || normalized.contains("بانوراما")) {
            return "SUNROOF";
        }

        // 2. Driver target
        if (normalized.contains("سائق") || normalized.contains("سايق")
                || normalized.contains("سواقه") || normalized.contains("سواقة")) {
            return "DRIVER";
        }

        // 3. Passenger target
        if (normalized.contains("راكب") || normalized.contains("صفحي")
                || normalized.contains("مرافق") || normalized.contains("معاون")) {
            return "PASSENGER";
        }

        // 4. All target
        if (containsWord(normalized, "الكل") || containsWord(normalized, "كلها")
                || containsWord(normalized, "جميعها") || containsWord(normalized, "الجميع")
                || containsWord(normalized, "جميع") || containsWord(normalized, "كافه")
                || containsWord(normalized, "كافة") || containsWord(normalized, "كامل")
                || containsWord(normalized, "كل")) {
            return "ALL";
        }

        // 5. Fuzzy tolerance using LevenshteinMatcher
        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (LevenshteinMatcher.similarity(token, "السائق") >= 0.8 || LevenshteinMatcher.similarity(token, "السايق") >= 0.8) {
                return "DRIVER";
            }
            if (LevenshteinMatcher.similarity(token, "الراكب") >= 0.8 || LevenshteinMatcher.similarity(token, "المرافق") >= 0.8) {
                return "PASSENGER";
            }
            if (LevenshteinMatcher.similarity(token, "السقف") >= 0.8 || LevenshteinMatcher.similarity(token, "بانوراما") >= 0.8) {
                return "SUNROOF";
            }
            if (LevenshteinMatcher.similarity(token, "الكل") >= 0.8 || LevenshteinMatcher.similarity(token, "كلها") >= 0.8) {
                return "ALL";
            }
        }

        return null;
    }

    private static boolean containsWord(String text, String word) {
        String pattern = "(^|\\s)" + Pattern.quote(word) + "($|\\s)";
        return Pattern.compile(pattern).matcher(text).find();
    }

    private static boolean containsWordPhrase(String text, String phrase) {
        String pattern = "(^|\\s)" + Pattern.quote(phrase) + "($|\\s)";
        return Pattern.compile(pattern).matcher(text).find();
    }
}
