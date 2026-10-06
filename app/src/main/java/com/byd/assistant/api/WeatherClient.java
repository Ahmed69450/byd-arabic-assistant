package com.byd.assistant.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherClient {

    public static final String OPEN_METEO_BASE_URL = "https://api.open-meteo.com/v1/forecast";

    public static String buildForecastUrl(double lat, double lon) {
        return String.format(Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&current_weather=true",
                OPEN_METEO_BASE_URL, lat, lon);
    }

    public static String formatWeatherReportArabic(double temp, double windSpeed, int weatherCode) {
        String description = translateWmoCode(weatherCode);
        String tempStr = (temp == Math.floor(temp)) ? String.valueOf((long) temp) : String.format(Locale.US, "%.1f", temp);
        String windStr = (windSpeed == Math.floor(windSpeed)) ? String.valueOf((long) windSpeed) : String.format(Locale.US, "%.1f", windSpeed);

        return "الطقس " + description + "، درجة الحرارة " + tempStr + " درجة مئوية، وسرعة الرياح " + windStr + " كم/ساعة.";
    }

    public static String translateWmoCode(int code) {
        if (code == 0) {
            return "صافٍ";
        } else if (code >= 1 && code <= 3) {
            return "غائم جزئياً";
        } else if (code == 45 || code == 48) {
            return "ضباب";
        } else if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return "ماطر";
        } else if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
            return "ثلجي";
        } else if (code >= 95 && code <= 99) {
            return "عاصف";
        }
        return "معتدل";
    }

    public static String parseWeatherResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return "الطقس معتدل حالياً.";
        }

        double temp = 22.0;
        double wind = 10.0;
        int code = 0;

        Pattern tempPattern = Pattern.compile("\"temperature(?:_2m)?\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
        Matcher tempMatcher = tempPattern.matcher(jsonResponse);
        if (tempMatcher.find()) {
            try {
                temp = Double.parseDouble(tempMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        Pattern windPattern = Pattern.compile("\"wind(?:_)?speed(?:_10m)?\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
        Matcher windMatcher = windPattern.matcher(jsonResponse);
        if (windMatcher.find()) {
            try {
                wind = Double.parseDouble(windMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        Pattern codePattern = Pattern.compile("\"weather(?:_)?code\"\\s*:\\s*(-?[0-9]+)");
        Matcher codeMatcher = codePattern.matcher(jsonResponse);
        if (codeMatcher.find()) {
            try {
                code = Integer.parseInt(codeMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        return formatWeatherReportArabic(temp, wind, code);
    }

    public static String fetchForecast(String urlString, int timeoutMs) throws Exception {
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

        return parseWeatherResponse(sb.toString());
    }
}
