package com.byd.assistant.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstantAnswerClient {

    public static final String DUCKDUCKGO_BASE_URL = "https://api.duckduckgo.com/";

    public static String buildQueryUrl(String query) {
        String encodedQuery = "";
        try {
            encodedQuery = URLEncoder.encode(query != null ? query : "", StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}
        return DUCKDUCKGO_BASE_URL + "?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";
    }

    public static String extractSummary(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return "";
        }

        String abstractText = extractJsonField(jsonResponse, "AbstractText");
        if (abstractText != null && !abstractText.trim().isEmpty()) {
            return abstractText.trim();
        }

        String answer = extractJsonField(jsonResponse, "Answer");
        if (answer != null && !answer.trim().isEmpty()) {
            return answer.trim();
        }

        return "";
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private static String unescapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case '"':
                        sb.append('"');
                        i++;
                        break;
                    case '\\':
                        sb.append('\\');
                        i++;
                        break;
                    case '/':
                        sb.append('/');
                        i++;
                        break;
                    case 'b':
                        sb.append('\b');
                        i++;
                        break;
                    case 'f':
                        sb.append('\f');
                        i++;
                        break;
                    case 'n':
                        sb.append('\n');
                        i++;
                        break;
                    case 'r':
                        sb.append('\r');
                        i++;
                        break;
                    case 't':
                        sb.append('\t');
                        i++;
                        break;
                    case 'u':
                        if (i + 5 < len) {
                            try {
                                int hexVal = Integer.parseInt(input.substring(i + 2, i + 6), 16);
                                sb.append((char) hexVal);
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String fetchSummary(String urlString, int timeoutMs) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP error code: " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            conn.disconnect();
        }

        return extractSummary(sb.toString());
    }
}
