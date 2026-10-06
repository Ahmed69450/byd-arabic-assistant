package com.byd.assistant.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExternalApiDispatcher {

    public enum ApiRoute {
        WEATHER,
        WOLFRAM,
        DUCKDUCKGO,
        UNKNOWN
    }

    public static final int DEFAULT_TIMEOUT_MS = 2000;
    public static final String OFFLINE_FALLBACK = "عذراً، خدمة المعلومات عبر الإنترنت غير متاحة حالياً.";

    // Default GPS location: Baghdad (Latitude: 33.3152, Longitude: 44.3661)
    public static final double DEFAULT_LATITUDE = 33.3152;
    public static final double DEFAULT_LONGITUDE = 44.3661;

    private int connectTimeout = DEFAULT_TIMEOUT_MS;
    private int readTimeout = DEFAULT_TIMEOUT_MS;
    private boolean simulationMode = false;
    private String wolframAppKey = "DEMO";

    private double defaultLatitude = DEFAULT_LATITUDE;
    private double defaultLongitude = DEFAULT_LONGITUDE;

    // Test URL overrides
    private String testWeatherUrl;
    private String testDuckDuckGoUrl;
    private String testWolframUrl;

    public ExternalApiDispatcher() {
        this(false);
    }

    public ExternalApiDispatcher(boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    public ExternalApiDispatcher(double latitude, double longitude, String wolframAppKey) {
        this.defaultLatitude = latitude;
        this.defaultLongitude = longitude;
        this.wolframAppKey = wolframAppKey;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = Math.min(connectTimeout, DEFAULT_TIMEOUT_MS);
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = Math.min(readTimeout, DEFAULT_TIMEOUT_MS);
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    public void setBaseUrlsForTesting(String weatherUrl, String duckDuckGoUrl, String wolframUrl) {
        this.testWeatherUrl = weatherUrl;
        this.testDuckDuckGoUrl = duckDuckGoUrl;
        this.testWolframUrl = wolframUrl;
    }

    public ApiRoute route(String query) {
        if (query == null || query.trim().isEmpty()) {
            return ApiRoute.UNKNOWN;
        }

        String q = query.trim();

        // 1. Exclude car controls (HVAC, Windows, Screen, Volume, Navigation commands)
        if (isCarControlQuery(q)) {
            return ApiRoute.UNKNOWN;
        }

        // 2. Weather keywords: "طقس", "جو", "درجة حرارة الطقس", "درجة حرارة الجو"
        if (q.contains("طقس") || q.contains("درجة حرارة الطقس") || q.contains("حرارة الطقس")
                || containsWord(q, "جو") || containsWord(q, "الجو") || q.contains("حالة الجو")) {
            return ApiRoute.WEATHER;
        }

        // 3. Math / calculations: "احسب", or numbers with +, -, *, /, x, etc.
        if (isMathQuery(q)) {
            return ApiRoute.WOLFRAM;
        }

        // 4. Knowledge: "من هو", "ما هو", "عاصمة", "تعريف", etc.
        if (isKnowledgeQuery(q)) {
            return ApiRoute.DUCKDUCKGO;
        }

        return ApiRoute.UNKNOWN;
    }

    public boolean canHandle(String query) {
        return route(query) != ApiRoute.UNKNOWN;
    }

    public String dispatch(String query) {
        return dispatch(query, defaultLatitude, defaultLongitude);
    }

    public String dispatch(String query, double lat, double lon) {
        if (query == null || query.trim().isEmpty()) {
            return OFFLINE_FALLBACK;
        }

        ApiRoute targetRoute = route(query);
        if (targetRoute == ApiRoute.UNKNOWN) {
            return OFFLINE_FALLBACK;
        }

        if (simulationMode) {
            return dispatchSimulated(targetRoute, query.trim());
        }

        try {
            switch (targetRoute) {
                case WEATHER: {
                    String url = (testWeatherUrl != null) ? testWeatherUrl : WeatherClient.buildForecastUrl(lat, lon);
                    return WeatherClient.fetchForecast(url, connectTimeout);
                }
                case WOLFRAM: {
                    String url = (testWolframUrl != null) ? testWolframUrl : WolframClient.buildShortAnswerUrl(wolframAppKey, query);
                    return WolframClient.fetchShortAnswer(url, connectTimeout);
                }
                case DUCKDUCKGO: {
                    String url = (testDuckDuckGoUrl != null) ? testDuckDuckGoUrl : InstantAnswerClient.buildQueryUrl(query);
                    String summary = InstantAnswerClient.fetchSummary(url, connectTimeout);
                    if (summary != null && !summary.isEmpty()) {
                        return summary;
                    }
                    return OFFLINE_FALLBACK;
                }
                default:
                    return OFFLINE_FALLBACK;
            }
        } catch (Exception e) {
            // Graceful fallback on network timeout or connection failure
            return OFFLINE_FALLBACK;
        }
    }

    private String dispatchSimulated(ApiRoute route, String query) {
        switch (route) {
            case WEATHER:
                return WeatherClient.formatWeatherReportArabic(26.0, 14.0, 0);

            case WOLFRAM: {
                Double eval = evaluateSimpleMath(query);
                if (eval != null) {
                    if (eval == Math.floor(eval)) {
                        return "النتيجة: " + ((long) eval.doubleValue());
                    } else {
                        return "النتيجة: " + eval;
                    }
                }
                return "النتيجة: تم حساب العملية الحسابية بنجاح.";
            }

            case DUCKDUCKGO:
                if (query.contains("عاصمة العراق")) {
                    return "بغداد هي عاصمة جمهورية العراق وأكبر مدنه.";
                } else if (query.contains("المتنبي")) {
                    return "أبو الطيب المتنبي هو أحد أعظم شعراء العرب في العصر العباسي.";
                } else if (query.contains("فرنسا")) {
                    return "باريس هي عاصمة فرنسا وأكبر مدنها.";
                }
                return "معلومات عامة: " + query;

            default:
                return OFFLINE_FALLBACK;
        }
    }

    private static boolean isCarControlQuery(String q) {
        // Explicit vehicle actions
        if (q.contains("شغل") || q.contains("طفي") || q.contains("شعل") || q.contains("اطفئ")
                || q.contains("افتح") || q.contains("اغلق") || q.contains("نزل") || q.contains("صعد")
                || q.contains("فر") || q.contains("اقلب") || q.contains("سوي") || q.contains("علي")
                || q.contains("نصي") || q.contains("اكتم") || q.contains("ارفع") || q.contains("اخفض")) {
            if (q.contains("مكيف") || q.contains("تبريد") || q.contains("حرارة") || q.contains("جامة")
                    || q.contains("جامات") || q.contains("نوافذ") || q.contains("نافذة") || q.contains("شاشة")
                    || q.contains("صوت") || q.contains("اغنية") || q.contains("خرايط") || q.contains("ملاحة")
                    || q.contains("تطبيق") || q.contains("اعدادات") || q.contains("ضبط")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMathQuery(String q) {
        if (q.contains("احسب") || q.contains("احسبلي") || q.contains("احسب لي")) {
            return true;
        }
        Pattern mathPattern = Pattern.compile("\\d+\\s*[+\\-*/×÷^]\\s*\\d+");
        return mathPattern.matcher(q).find();
    }

    private static boolean isKnowledgeQuery(String q) {
        return q.contains("من هو") || q.contains("من هي") || q.contains("من هم")
                || q.contains("ما هو") || q.contains("ما هي") || q.contains("ماذا يعني")
                || q.contains("ماذا تعني") || q.contains("عاصمة") || q.contains("تعريف")
                || q.contains("اين يقع") || q.contains("أين يقع") || q.contains("اين تقع")
                || q.contains("أين تقع");
    }

    private static boolean containsWord(String text, String word) {
        Pattern p = Pattern.compile("(?:^|\\s)" + Pattern.quote(word) + "(?:$|\\s)");
        return p.matcher(text).find();
    }

    private static Double evaluateSimpleMath(String query) {
        Pattern pattern = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/×÷])\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            try {
                double a = Double.parseDouble(matcher.group(1));
                String op = matcher.group(2);
                double b = Double.parseDouble(matcher.group(3));
                switch (op) {
                    case "+": return a + b;
                    case "-": return a - b;
                    case "*":
                    case "×": return a * b;
                    case "/":
                    case "÷": return (b != 0) ? (a / b) : 0;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
