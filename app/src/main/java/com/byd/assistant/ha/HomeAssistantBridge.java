package com.byd.assistant.ha;

import com.byd.assistant.model.LightingIntent;
import com.byd.assistant.model.VehicleIntent;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HomeAssistantBridge {

    public static final String DOMAIN_CLIMATE = "climate";
    public static final String DOMAIN_COVER = "cover";
    public static final String DOMAIN_LIGHT = "light";

    public static final String SERVICE_SET_TEMPERATURE = "set_temperature";
    public static final String SERVICE_OPEN_COVER = "open_cover";
    public static final String SERVICE_CLOSE_COVER = "close_cover";
    public static final String SERVICE_TURN_ON = "turn_on";
    public static final String SERVICE_TURN_OFF = "turn_off";

    public static final String ENTITY_CLIMATE_AC = "climate.car_ac";
    public static final String ENTITY_COVER_WINDOWS = "cover.car_windows";
    public static final String ENTITY_COVER_SUNROOF = "cover.car_sunroof";
    public static final String ENTITY_LIGHT_AMBIENT = "light.car_ambient_lights";
    public static final String ENTITY_SENSOR_BATTERY = "sensor.car_battery_level";
    public static final String ENTITY_BINARY_SENSOR_DOORS = "binary_sensor.car_doors_status";

    private HaConfig config;
    private boolean simulationMode;

    private String lastCallDomain;
    private String lastCallService;
    private String lastCallEntityId;
    private Map<String, Object> lastCallData;
    private String lastDispatchedPayload;

    public HomeAssistantBridge() {
        this(new HaConfig(), false);
    }

    public HomeAssistantBridge(HaConfig config) {
        this(config, false);
    }

    public HomeAssistantBridge(HaConfig config, boolean simulationMode) {
        this.config = config;
        this.simulationMode = simulationMode;
    }

    public HaConfig getConfig() {
        return config;
    }

    public void setConfig(HaConfig config) {
        this.config = config;
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    public String getLastCallDomain() {
        return lastCallDomain;
    }

    public String getLastCallService() {
        return lastCallService;
    }

    public String getLastCallEntityId() {
        return lastCallEntityId;
    }

    public Map<String, Object> getLastCallData() {
        return lastCallData != null ? Collections.unmodifiableMap(lastCallData) : Collections.emptyMap();
    }

    public String getLastDispatchedPayload() {
        return lastDispatchedPayload;
    }

    public static String buildAuthPayload(String accessToken) {
        return "{\"type\": \"auth\", \"access_token\": \"" + escapeJson(accessToken) + "\"}";
    }

    public static String buildServicePayload(int id, String domain, String service, String targetEntity, String serviceDataJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\": ").append(id).append(", ");
        sb.append("\"type\": \"call_service\", ");
        sb.append("\"domain\": \"").append(escapeJson(domain)).append("\", ");
        sb.append("\"service\": \"").append(escapeJson(service)).append("\"");

        if (targetEntity != null && !targetEntity.trim().isEmpty()) {
            sb.append(", \"target\": {\"entity_id\": \"").append(escapeJson(targetEntity.trim())).append("\"}");
        }

        if (serviceDataJson != null && !serviceDataJson.trim().isEmpty()) {
            String data = serviceDataJson.trim();
            if (data.startsWith("{") && data.endsWith("}")) {
                sb.append(", \"service_data\": ").append(data);
            } else {
                sb.append(", \"service_data\": {").append(data).append("}");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    public static String buildServicePayload(String domain, String service, String serviceDataJson) {
        return buildServicePayload(1, domain, service, null, serviceDataJson);
    }

    public static String buildSubscribeEventsPayload(int id, String eventType) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\": ").append(id).append(", ");
        sb.append("\"type\": \"subscribe_events\"");

        if (eventType != null && !eventType.trim().isEmpty()) {
            sb.append(", \"event_type\": \"").append(escapeJson(eventType.trim())).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    public boolean dispatchClimate(double temperature) {
        Map<String, Object> data = new HashMap<>();
        data.put("temperature", temperature);
        return sendServiceCall(DOMAIN_CLIMATE, SERVICE_SET_TEMPERATURE, ENTITY_CLIMATE_AC, data);
    }

    public boolean dispatchWindow(boolean open) {
        String service = open ? SERVICE_OPEN_COVER : SERVICE_CLOSE_COVER;
        return sendServiceCall(DOMAIN_COVER, service, ENTITY_COVER_WINDOWS, null);
    }

    public boolean dispatchSunroof(boolean open) {
        String service = open ? SERVICE_OPEN_COVER : SERVICE_CLOSE_COVER;
        return sendServiceCall(DOMAIN_COVER, service, ENTITY_COVER_SUNROOF, null);
    }

    public boolean dispatchAmbientLight(String colorName, int brightness) {
        Map<String, Object> data = new HashMap<>();
        if (colorName != null && !colorName.isEmpty()) {
            data.put("color_name", colorName);
        }
        data.put("brightness", Math.max(0, Math.min(100, brightness)));
        return sendServiceCall(DOMAIN_LIGHT, SERVICE_TURN_ON, ENTITY_LIGHT_AMBIENT, data);
    }

    public boolean dispatchVehicleIntent(VehicleIntent intent) {
        if (intent == null || intent instanceof VehicleIntent.Unknown) {
            return false;
        }

        if (intent instanceof VehicleIntent.Climate) {
            VehicleIntent.Climate c = (VehicleIntent.Climate) intent;
            if (c.targetTemp != null) {
                return dispatchClimate(c.targetTemp);
            } else {
                String service = c.enabled ? SERVICE_TURN_ON : SERVICE_TURN_OFF;
                return sendServiceCall(DOMAIN_CLIMATE, service, ENTITY_CLIMATE_AC, null);
            }
        } else if (intent instanceof VehicleIntent.Window) {
            VehicleIntent.Window w = (VehicleIntent.Window) intent;
            return dispatchWindow(w.open);
        } else if (intent instanceof LightingIntent) {
            LightingIntent l = (LightingIntent) intent;
            if (!l.isEnabled()) {
                return sendServiceCall(DOMAIN_LIGHT, SERVICE_TURN_OFF, ENTITY_LIGHT_AMBIENT, null);
            }
            return dispatchAmbientLight(l.getColorName(), l.getBrightness());
        }
        return false;
    }

    public boolean sendServiceCall(String domain, String service, String entityId, Map<String, Object> data) {
        this.lastCallDomain = domain;
        this.lastCallService = service;
        this.lastCallEntityId = entityId;
        this.lastCallData = (data != null) ? new HashMap<>(data) : new HashMap<>();

        String payload = buildRestPayload(entityId, data);
        this.lastDispatchedPayload = payload;

        if (simulationMode) {
            return true;
        }

        if (config == null || !config.isValid()) {
            return false;
        }

        try {
            String urlString = config.getRestServiceUrl(domain, service);
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + config.getAccessToken());
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            byte[] outputBytes = payload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(outputBytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(outputBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            // Graceful offline fallback / connection failure
            return false;
        }
    }

    public static String buildRestPayload(String entityId, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean hasField = false;

        if (entityId != null && !entityId.trim().isEmpty()) {
            sb.append("\"entity_id\": \"").append(escapeJson(entityId.trim())).append("\"");
            hasField = true;
        }

        if (data != null && !data.isEmpty()) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (hasField) {
                    sb.append(", ");
                }
                sb.append("\"").append(escapeJson(entry.getKey())).append("\": ");
                Object val = entry.getValue();
                if (val == null) {
                    sb.append("null");
                } else if (val instanceof Number || val instanceof Boolean) {
                    sb.append(val);
                } else {
                    sb.append("\"").append(escapeJson(val.toString())).append("\"");
                }
                hasField = true;
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        String hex = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(hex.substring(hex.length() - 4));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
