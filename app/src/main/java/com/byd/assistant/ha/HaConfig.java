package com.byd.assistant.ha;

public class HaConfig {
    private String baseUrl;
    private String accessToken;

    public HaConfig() {
    }

    public HaConfig(String baseUrl, String accessToken) {
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean isValid() {
        if (baseUrl == null || accessToken == null) {
            return false;
        }
        String trimmedUrl = baseUrl.trim();
        String trimmedToken = accessToken.trim();
        if (trimmedUrl.isEmpty() || trimmedToken.isEmpty()) {
            return false;
        }
        return trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://");
    }

    public String getWebSocketUrl() {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "";
        }
        String cleanUrl = baseUrl.trim();
        if (cleanUrl.endsWith("/api/websocket")) {
            if (cleanUrl.startsWith("https://")) {
                return "wss://" + cleanUrl.substring("https://".length());
            } else if (cleanUrl.startsWith("http://")) {
                return "ws://" + cleanUrl.substring("http://".length());
            }
            return cleanUrl;
        }

        while (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }

        if (cleanUrl.startsWith("https://")) {
            cleanUrl = "wss://" + cleanUrl.substring("https://".length());
        } else if (cleanUrl.startsWith("http://")) {
            cleanUrl = "ws://" + cleanUrl.substring("http://".length());
        }
        return cleanUrl + "/api/websocket";
    }

    public String getRestServiceUrl(String domain, String service) {
        if (baseUrl == null) {
            return "";
        }
        String cleanUrl = baseUrl.trim();
        while (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        return cleanUrl + "/api/services/" + domain + "/" + service;
    }
}
