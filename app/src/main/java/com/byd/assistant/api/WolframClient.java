package com.byd.assistant.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WolframClient {

    public static final String WOLFRAM_SHORT_ANSWER_BASE_URL = "https://api.wolframalpha.com/v1/result";
    public static final String DEFAULT_APP_ID = "DEMO";

    public static String buildShortAnswerUrl(String appKey, String query) {
        String key = (appKey != null && !appKey.trim().isEmpty()) ? appKey.trim() : DEFAULT_APP_ID;
        String encodedQuery = "";
        try {
            encodedQuery = URLEncoder.encode(query != null ? query : "", StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}

        return WOLFRAM_SHORT_ANSWER_BASE_URL + "?appid=" + key + "&i=" + encodedQuery;
    }

    public static String buildShortAnswerUrl(String query) {
        return buildShortAnswerUrl(DEFAULT_APP_ID, query);
    }

    public static String fetchShortAnswer(String urlString, int timeoutMs) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "text/plain");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP error code: " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line);
            }
        } finally {
            conn.disconnect();
        }

        return sb.toString().trim();
    }
}
