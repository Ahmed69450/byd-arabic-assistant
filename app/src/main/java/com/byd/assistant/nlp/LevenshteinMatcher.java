package com.byd.assistant.nlp;

import java.util.List;

public class LevenshteinMatcher {

    private LevenshteinMatcher() {}

    /**
     * Computes the Levenshtein edit distance between two strings using dynamic programming.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Minimum number of single-character edits (insertions, deletions, substitutions)
     */
    public static int distance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        int[] costs = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            int prevDiagonal = costs[0];
            costs[0] = i;
            for (int j = 1; j <= len2; j++) {
                int temp = costs[j];
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                costs[j] = Math.min(Math.min(costs[j] + 1, costs[j - 1] + 1), prevDiagonal + cost);
                prevDiagonal = temp;
            }
        }

        return costs[len2];
    }

    /**
     * Computes a normalized similarity score between 0.0 and 1.0.
     * 1.0 indicates an exact match; 0.0 indicates completely distinct strings.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Normalized similarity between 0.0 and 1.0
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int dist = distance(s1, s2);
        return Math.max(0.0, 1.0 - ((double) dist / (double) maxLen));
    }

    /**
     * Finds the candidate string with the highest similarity to the input string,
     * provided the similarity meets or exceeds the specified threshold.
     *
     * @param input Input string
     * @param candidates List of candidate strings
     * @param threshold Minimum required similarity (between 0.0 and 1.0)
     * @return Best matching candidate, or null if no candidate reaches the threshold
     */
    public static String findBestMatch(String input, List<String> candidates, double threshold) {
        if (input == null || candidates == null || candidates.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        double highestSim = -1.0;

        for (String candidate : candidates) {
            if (candidate == null) continue;
            double sim = similarity(input.trim(), candidate.trim());
            if (sim > highestSim && sim >= threshold) {
                highestSim = sim;
                bestMatch = candidate;
            }
        }

        return bestMatch;
    }
}
