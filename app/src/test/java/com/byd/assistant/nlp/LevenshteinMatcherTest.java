package com.byd.assistant.nlp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LevenshteinMatcherTest {

    public static void main(String[] args) {
        testExactMatch();
        testFuzzyMatchWithTypo();
        testNoMatchBelowThreshold();
        testSimilarityCalculation();
        testDistanceCalculations();
        testEdgeCasesAndNullHandling();
        testCompatibilityWithArabicNormalizer();
        System.out.println("ALL_LEVENSHTEIN_MATCHER_TESTS_PASSED");
    }

    public static void testExactMatch() {
        List<String> dictionary = Arrays.asList("افتح النافذة", "شغل المكيف", "اغلق النوافذ");
        String match = LevenshteinMatcher.findBestMatch("شغل المكيف", dictionary, 0.8);
        assertEquals("شغل المكيف", match);
    }

    public static void testFuzzyMatchWithTypo() {
        List<String> dictionary = Arrays.asList("افتح النافذة", "شغل المكيف", "اغلق النوافذ");
        // Typo: "شغل المكف" instead of "شغل المكيف"
        String match = LevenshteinMatcher.findBestMatch("شغل المكف", dictionary, 0.75);
        assertEquals("شغل المكيف", match);
    }

    public static void testNoMatchBelowThreshold() {
        List<String> dictionary = Arrays.asList("افتح النافذة", "شغل المكيف");
        String match = LevenshteinMatcher.findBestMatch("مساء الخير يا صديقي", dictionary, 0.8);
        assertNull(match);
    }

    public static void testSimilarityCalculation() {
        double simExact = LevenshteinMatcher.similarity("مكيف", "مكيف");
        assertEquals(1.0, simExact, 0.001);

        double partial = LevenshteinMatcher.similarity("مكيف", "مكف");
        assertTrue(partial >= 0.75);

        double zeroSim = LevenshteinMatcher.similarity("abc", "xyz");
        assertEquals(0.0, zeroSim, 0.001);
    }

    public static void testDistanceCalculations() {
        assertEquals(0, LevenshteinMatcher.distance("", ""));
        assertEquals(3, LevenshteinMatcher.distance("abc", ""));
        assertEquals(3, LevenshteinMatcher.distance("", "abc"));
        assertEquals(0, LevenshteinMatcher.distance("شغل", "شغل"));
        assertEquals(1, LevenshteinMatcher.distance("شغل المكيف", "شغل المكف"));
        assertEquals(3, LevenshteinMatcher.distance("kitten", "sitting"));
    }

    public static void testEdgeCasesAndNullHandling() {
        // Null strings in distance
        assertEquals(3, LevenshteinMatcher.distance(null, "abc"));
        assertEquals(3, LevenshteinMatcher.distance("abc", null));
        assertEquals(0, LevenshteinMatcher.distance(null, null));

        // Null strings in similarity
        assertEquals(0.0, LevenshteinMatcher.similarity(null, "abc"), 0.001);
        assertEquals(0.0, LevenshteinMatcher.similarity("abc", null), 0.001);
        assertEquals(0.0, LevenshteinMatcher.similarity(null, null), 0.001);

        // Null or empty candidates in findBestMatch
        assertNull(LevenshteinMatcher.findBestMatch(null, Arrays.asList("test"), 0.5));
        assertNull(LevenshteinMatcher.findBestMatch("test", null, 0.5));
        assertNull(LevenshteinMatcher.findBestMatch("test", Collections.emptyList(), 0.5));
    }

    public static void testCompatibilityWithArabicNormalizer() {
        // Raw voice transcription with diacritics and tatweel
        String rawVoiceInput = "شَغِّـــــل الْمُكَيِّفْ";
        String normalizedInput = ArabicNormalizer.normalize(rawVoiceInput);

        List<String> dictionary = Arrays.asList("افتح النافذة", "شغل المكيف", "اغلق النوافذ");
        String match = LevenshteinMatcher.findBestMatch(normalizedInput, dictionary, 0.85);
        assertEquals("شغل المكيف", match);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: " + expected + ", but got: " + actual);
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("Expected: " + expected + " (+/-" + delta + "), but got: " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition expected to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Condition expected to be false");
        }
    }

    private static void assertNull(Object object) {
        if (object != null) {
            throw new AssertionError("Expected null, but got: " + object);
        }
    }
}
