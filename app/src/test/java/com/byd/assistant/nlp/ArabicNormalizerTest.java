package com.byd.assistant.nlp;

public class ArabicNormalizerTest {
    public static void main(String[] args) {
        testNormalizesHamzasAndDiacritics();
        testNormalizesTaaMarbutaAndAlefMaqsura();
        testStripsTatweelAndPunctuation();
        System.out.println("ALL_TESTS_PASSED");
    }

    public static void testNormalizesHamzasAndDiacritics() {
        String input = "أَفْتَحُ الشَّبَابِيكَ يَا أَخِي!";
        String expected = "افتح الشبابيك يا اخي";
        String actual = ArabicNormalizer.normalize(input);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'");
        }
    }

    public static void testNormalizesTaaMarbutaAndAlefMaqsura() {
        String input = "شغّل التدفئة إِلى أقصى درجة";
        String expected = "شغل التدفئه الي اقصي درجه";
        String actual = ArabicNormalizer.normalize(input);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'");
        }
    }

    public static void testStripsTatweelAndPunctuation() {
        String input = "فـــــرّ الـــشـــاشـــة...";
        String expected = "فر الشاشه";
        String actual = ArabicNormalizer.normalize(input);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'");
        }
    }
}
