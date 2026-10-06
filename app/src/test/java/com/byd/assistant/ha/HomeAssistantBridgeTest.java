package com.byd.assistant.ha;

import com.byd.assistant.model.LightingIntent;
import com.byd.assistant.model.VehicleIntent;
import com.byd.assistant.model.WindowTarget;

import java.util.HashMap;
import java.util.Map;

public class HomeAssistantBridgeTest {

    public static void main(String[] args) {
        testConfigValidation();
        testWebSocketUrlTransformation();
        testRestServiceUrl();
        testAuthPayload();
        testServicePayloadJsonRpc();
        testSubscribeEventsPayload();
        testVehicleActionDispatchMapping();
        testVehicleIntentIntegration();
        testSimulationModeAndOfflineResilience();
        testUnreachableHostGracefulFallback();
        System.out.println("ALL_HA_BRIDGE_TESTS_PASSED");
    }

    public static void testConfigValidation() {
        HaConfig validHttp = new HaConfig("http://192.168.1.100:8123", "token_123");
        assertTrue(validHttp.isValid());
        assertEquals("http://192.168.1.100:8123", validHttp.getBaseUrl());
        assertEquals("token_123", validHttp.getAccessToken());

        HaConfig validHttps = new HaConfig("https://homeassistant.local:8123", "token_456");
        assertTrue(validHttps.isValid());

        HaConfig emptyUrl = new HaConfig("", "token_123");
        assertFalse(emptyUrl.isValid());

        HaConfig nullUrl = new HaConfig(null, "token_123");
        assertFalse(nullUrl.isValid());

        HaConfig emptyToken = new HaConfig("http://192.168.1.100:8123", "");
        assertFalse(emptyToken.isValid());

        HaConfig nullToken = new HaConfig("http://192.168.1.100:8123", null);
        assertFalse(nullToken.isValid());

        HaConfig invalidScheme = new HaConfig("ftp://192.168.1.100:8123", "token_123");
        assertFalse(invalidScheme.isValid());

        HaConfig noScheme = new HaConfig("192.168.1.100:8123", "token_123");
        assertFalse(noScheme.isValid());
    }

    public static void testWebSocketUrlTransformation() {
        HaConfig config1 = new HaConfig("http://192.168.1.100:8123", "token");
        assertEquals("ws://192.168.1.100:8123/api/websocket", config1.getWebSocketUrl());

        HaConfig config2 = new HaConfig("http://192.168.1.100:8123/", "token");
        assertEquals("ws://192.168.1.100:8123/api/websocket", config2.getWebSocketUrl());

        HaConfig config3 = new HaConfig("https://homeassistant.local:8123", "token");
        assertEquals("wss://homeassistant.local:8123/api/websocket", config3.getWebSocketUrl());

        HaConfig config4 = new HaConfig("https://homeassistant.local:8123/", "token");
        assertEquals("wss://homeassistant.local:8123/api/websocket", config4.getWebSocketUrl());

        HaConfig config5 = new HaConfig("http://10.0.0.5:8123/api/websocket", "token");
        assertEquals("ws://10.0.0.5:8123/api/websocket", config5.getWebSocketUrl());
    }

    public static void testRestServiceUrl() {
        HaConfig config = new HaConfig("http://192.168.1.100:8123", "token");
        assertEquals("http://192.168.1.100:8123/api/services/climate/set_temperature",
                config.getRestServiceUrl("climate", "set_temperature"));
        assertEquals("http://192.168.1.100:8123/api/services/cover/open_cover",
                config.getRestServiceUrl("cover", "open_cover"));

        HaConfig configTrailing = new HaConfig("http://192.168.1.100:8123/", "token");
        assertEquals("http://192.168.1.100:8123/api/services/light/turn_on",
                configTrailing.getRestServiceUrl("light", "turn_on"));
    }

    public static void testAuthPayload() {
        String authPayload = HomeAssistantBridge.buildAuthPayload("my_secret_token_xyz");
        assertTrue(authPayload.contains("\"type\": \"auth\""));
        assertTrue(authPayload.contains("\"access_token\": \"my_secret_token_xyz\""));
    }

    public static void testServicePayloadJsonRpc() {
        // 5-arg version
        String payload5 = HomeAssistantBridge.buildServicePayload(
                1,
                "climate",
                "set_temperature",
                "climate.car_ac",
                "{\"temperature\": 22}"
        );
        assertTrue(payload5.contains("\"id\": 1"));
        assertTrue(payload5.contains("\"type\": \"call_service\""));
        assertTrue(payload5.contains("\"domain\": \"climate\""));
        assertTrue(payload5.contains("\"service\": \"set_temperature\""));
        assertTrue(payload5.contains("climate.car_ac"));
        assertTrue(payload5.contains("\"temperature\": 22"));

        // 3-arg version overload
        String payload3 = HomeAssistantBridge.buildServicePayload(
                "climate",
                "set_temperature",
                "{\"temperature\": 22}"
        );
        assertTrue(payload3.contains("\"type\": \"call_service\""));
        assertTrue(payload3.contains("\"domain\": \"climate\""));
        assertTrue(payload3.contains("\"service\": \"set_temperature\""));

        // Target without serviceDataJson
        String payloadTargetOnly = HomeAssistantBridge.buildServicePayload(
                2,
                "cover",
                "open_cover",
                "cover.car_windows",
                null
        );
        assertTrue(payloadTargetOnly.contains("\"id\": 2"));
        assertTrue(payloadTargetOnly.contains("\"domain\": \"cover\""));
        assertTrue(payloadTargetOnly.contains("\"service\": \"open_cover\""));
        assertTrue(payloadTargetOnly.contains("cover.car_windows"));
    }

    public static void testSubscribeEventsPayload() {
        String payload = HomeAssistantBridge.buildSubscribeEventsPayload(42, "state_changed");
        assertTrue(payload.contains("\"id\": 42"));
        assertTrue(payload.contains("\"type\": \"subscribe_events\""));
        assertTrue(payload.contains("\"event_type\": \"state_changed\""));

        String payloadAll = HomeAssistantBridge.buildSubscribeEventsPayload(43, null);
        assertTrue(payloadAll.contains("\"id\": 43"));
        assertTrue(payloadAll.contains("\"type\": \"subscribe_events\""));
    }

    public static void testVehicleActionDispatchMapping() {
        HaConfig config = new HaConfig("http://192.168.1.100:8123", "test_token");
        HomeAssistantBridge bridge = new HomeAssistantBridge(config, true); // Simulation mode

        // Climate domain mapping
        boolean climateRes = bridge.dispatchClimate(23.5);
        assertTrue(climateRes);
        assertEquals("climate", bridge.getLastCallDomain());
        assertEquals("set_temperature", bridge.getLastCallService());
        assertEquals("climate.car_ac", bridge.getLastCallEntityId());
        assertTrue(bridge.getLastCallData().containsKey("temperature"));
        assertEquals(23.5, bridge.getLastCallData().get("temperature"));

        // Windows domain mapping (Open)
        boolean winOpen = bridge.dispatchWindow(true);
        assertTrue(winOpen);
        assertEquals("cover", bridge.getLastCallDomain());
        assertEquals("open_cover", bridge.getLastCallService());
        assertEquals("cover.car_windows", bridge.getLastCallEntityId());

        // Windows domain mapping (Close)
        boolean winClose = bridge.dispatchWindow(false);
        assertTrue(winClose);
        assertEquals("cover", bridge.getLastCallDomain());
        assertEquals("close_cover", bridge.getLastCallService());
        assertEquals("cover.car_windows", bridge.getLastCallEntityId());

        // Ambient lighting domain mapping
        boolean lightRes = bridge.dispatchAmbientLight("أزرق", 85);
        assertTrue(lightRes);
        assertEquals("light", bridge.getLastCallDomain());
        assertEquals("turn_on", bridge.getLastCallService());
        assertEquals("light.car_ambient_lights", bridge.getLastCallEntityId());
        assertTrue(bridge.getLastCallData().containsKey("brightness"));
        assertEquals(85, bridge.getLastCallData().get("brightness"));
        assertTrue(bridge.getLastCallData().containsKey("color_name"));
        assertEquals("أزرق", bridge.getLastCallData().get("color_name"));
    }

    public static void testVehicleIntentIntegration() {
        HaConfig config = new HaConfig("http://192.168.1.100:8123", "test_token");
        HomeAssistantBridge bridge = new HomeAssistantBridge(config, true);

        // Climate Intent
        VehicleIntent.Climate climateIntent = new VehicleIntent.Climate(true, 21);
        assertTrue(bridge.dispatchVehicleIntent(climateIntent));
        assertEquals("climate", bridge.getLastCallDomain());
        assertEquals("set_temperature", bridge.getLastCallService());
        assertEquals(21.0, ((Number) bridge.getLastCallData().get("temperature")).doubleValue(), 0.001);

        // Window Intent
        VehicleIntent.Window windowIntent = new VehicleIntent.Window(false, WindowTarget.ALL);
        assertTrue(bridge.dispatchVehicleIntent(windowIntent));
        assertEquals("cover", bridge.getLastCallDomain());
        assertEquals("close_cover", bridge.getLastCallService());

        // Ambient Lighting Intent
        LightingIntent lightIntent = new LightingIntent(true, "أحمر", 90);
        assertTrue(bridge.dispatchVehicleIntent(lightIntent));
        assertEquals("light", bridge.getLastCallDomain());
        assertEquals("turn_on", bridge.getLastCallService());
        assertEquals("light.car_ambient_lights", bridge.getLastCallEntityId());
        assertEquals(90, bridge.getLastCallData().get("brightness"));
    }

    public static void testSimulationModeAndOfflineResilience() {
        // Bridge with null config should not crash
        HomeAssistantBridge nullBridge = new HomeAssistantBridge(null, true);
        Map<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        assertTrue(nullBridge.sendServiceCall("light", "turn_on", "light.test", data));

        // Invalid config should not crash
        HaConfig invalidConfig = new HaConfig("", "");
        HomeAssistantBridge invalidBridge = new HomeAssistantBridge(invalidConfig, true);
        assertTrue(invalidBridge.sendServiceCall("climate", "set_temperature", "climate.test", null));
    }

    public static void testUnreachableHostGracefulFallback() {
        // Point to an unreachable port on localhost in non-simulation mode
        HaConfig unreachableConfig = new HaConfig("http://127.0.0.1:59998", "fake_token");
        HomeAssistantBridge bridge = new HomeAssistantBridge(unreachableConfig, false);

        Map<String, Object> data = new HashMap<>();
        data.put("temperature", 22);

        // Must handle network exception cleanly without throwing or crashing
        boolean result = bridge.sendServiceCall("climate", "set_temperature", "climate.car_ac", data);
        assertFalse(result); // Cleanly returned false on connection failure
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: " + expected + ", but got: " + actual);
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
}
