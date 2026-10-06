package com.byd.assistant.api;

public class ExternalApiDispatcherTest {

    public static void main(String[] args) {
        testDuckDuckGoUrlBuilding();
        testOpenMeteoUrlBuilding();
        testWolframUrlBuilding();
        testWeatherReportFormattingArabic();
        testOpenMeteoJsonParsing();
        testDuckDuckGoSummaryExtraction();
        testIntentRouting();
        testSimulationModeDispatch();
        testOfflineResilienceAndTimeoutFallback();
        testNullAndEmptyQueries();
        System.out.println("ALL_EXTERNAL_API_TESTS_PASSED");
    }

    public static void testDuckDuckGoUrlBuilding() {
        String url = InstantAnswerClient.buildQueryUrl("عاصمة العراق");
        assertTrue(url.contains("duckduckgo.com"));
        assertTrue(url.contains("format=json"));
        assertTrue(url.contains("no_html=1"));
        assertTrue(url.contains("skip_disambig=1"));
        assertTrue(url.contains("q="));

        String urlSpaces = InstantAnswerClient.buildQueryUrl("who is alan turing");
        assertTrue(urlSpaces.contains("who+is+alan+turing") || urlSpaces.contains("who%20is%20alan%20turing"));
    }

    public static void testOpenMeteoUrlBuilding() {
        String url = WeatherClient.buildForecastUrl(33.3152, 44.3661);
        assertTrue(url.contains("open-meteo.com"));
        assertTrue(url.contains("latitude=33.3152"));
        assertTrue(url.contains("longitude=44.3661"));
        assertTrue(url.contains("current_weather=true"));
    }

    public static void testWolframUrlBuilding() {
        String url = WolframClient.buildShortAnswerUrl("DEMO_APPID", "2 + 2");
        assertTrue(url.contains("wolframalpha.com"));
        assertTrue(url.contains("appid=DEMO_APPID"));
        assertTrue(url.contains("i=2+%2B+2") || url.contains("i=2%20%2B%202") || url.contains("2"));
    }

    public static void testWeatherReportFormattingArabic() {
        // WMO code 0: صافٍ
        String rep0 = WeatherClient.formatWeatherReportArabic(25.0, 15.0, 0);
        assertTrue(rep0.contains("صافٍ"));
        assertTrue(rep0.contains("25"));
        assertTrue(rep0.contains("15"));
        assertTrue(rep0.contains("درجة الحرارة"));
        assertTrue(rep0.contains("الرياح"));

        // WMO code 1-3: غائم جزئياً
        String rep2 = WeatherClient.formatWeatherReportArabic(18.5, 20.0, 2);
        assertTrue(rep2.contains("غائم جزئياً"));
        assertTrue(rep2.contains("18.5"));

        // WMO code 45: ضباب
        String rep45 = WeatherClient.formatWeatherReportArabic(12.0, 5.0, 45);
        assertTrue(rep45.contains("ضباب"));

        // WMO code 51-65: ماطر
        String rep61 = WeatherClient.formatWeatherReportArabic(14.0, 22.0, 61);
        assertTrue(rep61.contains("ماطر"));

        // WMO code 71-77: ثلجي
        String rep73 = WeatherClient.formatWeatherReportArabic(-3.0, 30.0, 73);
        assertTrue(rep73.contains("ثلجي"));

        // WMO code 95: عاصف
        String rep95 = WeatherClient.formatWeatherReportArabic(22.0, 45.0, 95);
        assertTrue(rep95.contains("عاصف"));
    }

    public static void testOpenMeteoJsonParsing() {
        String json = "{\"latitude\":33.32,\"longitude\":44.36,\"current_weather\":{\"temperature\":28.4,\"windspeed\":12.5,\"weathercode\":1}}";
        String report = WeatherClient.parseWeatherResponse(json);
        assertTrue(report.contains("غائم جزئياً"));
        assertTrue(report.contains("28.4"));
        assertTrue(report.contains("12.5"));
    }

    public static void testDuckDuckGoSummaryExtraction() {
        String jsonWithAbstract = "{\"AbstractText\": \"بغداد هي عاصمة جمهورية العراق وأكبر مدنه.\", \"Answer\": \"\"}";
        String summary1 = InstantAnswerClient.extractSummary(jsonWithAbstract);
        assertEquals("بغداد هي عاصمة جمهورية العراق وأكبر مدنه.", summary1);

        String jsonWithAnswer = "{\"AbstractText\": \"\", \"Answer\": \"42 هو المعنى الشامل.\"}";
        String summary2 = InstantAnswerClient.extractSummary(jsonWithAnswer);
        assertEquals("42 هو المعنى الشامل.", summary2);

        String jsonEmpty = "{\"AbstractText\": \"\", \"Answer\": \"\"}";
        String summary3 = InstantAnswerClient.extractSummary(jsonEmpty);
        assertTrue(summary3 == null || summary3.isEmpty());
    }

    public static void testIntentRouting() {
        ExternalApiDispatcher dispatcher = new ExternalApiDispatcher();

        // Weather queries
        assertEquals(ExternalApiDispatcher.ApiRoute.WEATHER, dispatcher.route("طقس اليوم في بغداد"));
        assertEquals(ExternalApiDispatcher.ApiRoute.WEATHER, dispatcher.route("كيف الجو الآن في البصرة"));
        assertEquals(ExternalApiDispatcher.ApiRoute.WEATHER, dispatcher.route("كم درجة حرارة الطقس"));

        // Wolfram / Math calculations
        assertEquals(ExternalApiDispatcher.ApiRoute.WOLFRAM, dispatcher.route("احسب 25 + 75"));
        assertEquals(ExternalApiDispatcher.ApiRoute.WOLFRAM, dispatcher.route("احسبلي 12 * 8"));
        assertEquals(ExternalApiDispatcher.ApiRoute.WOLFRAM, dispatcher.route("100 / 4"));
        assertEquals(ExternalApiDispatcher.ApiRoute.WOLFRAM, dispatcher.route("50 - 20"));

        // DuckDuckGo / General Knowledge
        assertEquals(ExternalApiDispatcher.ApiRoute.DUCKDUCKGO, dispatcher.route("من هو المتنبي"));
        assertEquals(ExternalApiDispatcher.ApiRoute.DUCKDUCKGO, dispatcher.route("ما هو الذكاء الاصطناعي"));
        assertEquals(ExternalApiDispatcher.ApiRoute.DUCKDUCKGO, dispatcher.route("عاصمة فرنسا"));
        assertEquals(ExternalApiDispatcher.ApiRoute.DUCKDUCKGO, dispatcher.route("تعريف النسبية العامة"));

        // Car controls & unknown (must NOT route to external APIs)
        assertEquals(ExternalApiDispatcher.ApiRoute.UNKNOWN, dispatcher.route("شغل المكيف وسوي الحرارة 22"));
        assertEquals(ExternalApiDispatcher.ApiRoute.UNKNOWN, dispatcher.route("افتح النوافذ"));
        assertEquals(ExternalApiDispatcher.ApiRoute.UNKNOWN, dispatcher.route("xyz123foobar"));
        assertFalse(dispatcher.canHandle("شغل المكيف"));
        assertTrue(dispatcher.canHandle("طقس بغداد"));
        assertTrue(dispatcher.canHandle("احسب 5 + 5"));
        assertTrue(dispatcher.canHandle("عاصمة العراق"));
    }

    public static void testSimulationModeDispatch() {
        ExternalApiDispatcher simDispatcher = new ExternalApiDispatcher(true);
        assertTrue(simDispatcher.isSimulationMode());

        String weatherRes = simDispatcher.dispatch("كيف الطقس اليوم");
        assertTrue(weatherRes != null && !weatherRes.isEmpty());
        assertTrue(weatherRes.contains("الطقس") || weatherRes.contains("حرارة"));

        String mathRes = simDispatcher.dispatch("احسب 50 + 25");
        assertTrue(mathRes != null && !mathRes.isEmpty());

        String knowRes = simDispatcher.dispatch("عاصمة العراق");
        assertTrue(knowRes != null && !knowRes.isEmpty());
    }

    public static void testOfflineResilienceAndTimeoutFallback() {
        ExternalApiDispatcher dispatcher = new ExternalApiDispatcher(false);
        assertTrue(dispatcher.getConnectTimeout() <= 2000);
        assertTrue(dispatcher.getReadTimeout() <= 2000);
        assertEquals(ExternalApiDispatcher.DEFAULT_TIMEOUT_MS, 2000);

        // When offline / unreachable host is configured, must not crash and must return Arabic fallback
        dispatcher.setBaseUrlsForTesting(
                "http://127.0.0.1:59998/forecast",
                "http://127.0.0.1:59998/ddg",
                "http://127.0.0.1:59998/wolfram"
        );

        String result = dispatcher.dispatch("ما هو الطقس");
        assertEquals(ExternalApiDispatcher.OFFLINE_FALLBACK, result);

        String mathResult = dispatcher.dispatch("احسب 12 + 13");
        assertEquals(ExternalApiDispatcher.OFFLINE_FALLBACK, mathResult);

        String searchResult = dispatcher.dispatch("عاصمة اليابان");
        assertEquals(ExternalApiDispatcher.OFFLINE_FALLBACK, searchResult);
    }

    public static void testNullAndEmptyQueries() {
        ExternalApiDispatcher dispatcher = new ExternalApiDispatcher();
        assertEquals(ExternalApiDispatcher.ApiRoute.UNKNOWN, dispatcher.route(null));
        assertEquals(ExternalApiDispatcher.ApiRoute.UNKNOWN, dispatcher.route(""));
        assertEquals(ExternalApiDispatcher.ApiRoute.UNKNOWN, dispatcher.route("   "));

        assertFalse(dispatcher.canHandle(null));
        assertFalse(dispatcher.canHandle(""));

        assertEquals(ExternalApiDispatcher.OFFLINE_FALLBACK, dispatcher.dispatch(null));
        assertEquals(ExternalApiDispatcher.OFFLINE_FALLBACK, dispatcher.dispatch(""));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: '" + expected + "', but got: '" + actual + "'");
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
}
