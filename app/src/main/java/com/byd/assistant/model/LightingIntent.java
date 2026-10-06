package com.byd.assistant.model;

import java.util.Objects;

/**
 * Vehicle intent representing cabin ambient lighting adjustments.
 */
public class LightingIntent extends VehicleIntent {
    public final boolean enabled;
    public final String colorName;
    public final int brightness;

    public LightingIntent(boolean enabled, String colorName, int brightness) {
        this.enabled = enabled;
        this.colorName = (colorName != null && !colorName.trim().isEmpty()) ? colorName.trim() : "أبيض";
        this.brightness = Math.max(0, Math.min(100, brightness));
    }

    public LightingIntent(String colorName, int brightness) {
        this(true, colorName, brightness);
    }

    public LightingIntent(boolean enabled) {
        this(enabled, "أبيض", enabled ? 100 : 0);
    }

    public LightingIntent() {
        this(true, "أبيض", 100);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getColorName() {
        return colorName;
    }

    public int getBrightness() {
        return brightness;
    }

    /**
     * Resolves hex color code for common Arabic color designations.
     */
    public String getColorHex() {
        if (!enabled || brightness == 0) return "#000000";
        if (colorName == null) return "#FFFFFF";

        String lower = colorName.toLowerCase();
        if (lower.contains("أزرق") || lower.contains("ازرق") || lower.contains("blue")) {
            return "#007BFF";
        } else if (lower.contains("أحمر") || lower.contains("احمر") || lower.contains("red")) {
            return "#DC3545";
        } else if (lower.contains("أخضر") || lower.contains("اخضر") || lower.contains("green")) {
            return "#28A745";
        } else if (lower.contains("أبيض") || lower.contains("ابيض") || lower.contains("white")) {
            return "#FFFFFF";
        } else if (lower.contains("دافئ") || lower.contains("دافي") || lower.contains("warm") || lower.contains("اصفر") || lower.contains("أصفر")) {
            return "#FFAA00";
        } else if (lower.contains("بنفسجي") || lower.contains("ارجواني") || lower.contains("purple")) {
            return "#6F42C1";
        } else if (lower.contains("وردي") || lower.contains("pink")) {
            return "#E83E8C";
        } else if (lower.contains("سماوي") || lower.contains("cyan")) {
            return "#17A2B8";
        }
        return "#FFFFFF";
    }

    @Override
    public String toString() {
        return "LightingIntent{" +
                "enabled=" + enabled +
                ", colorName='" + colorName + '\'' +
                ", brightness=" + brightness +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LightingIntent that = (LightingIntent) o;
        return enabled == that.enabled &&
                brightness == that.brightness &&
                Objects.equals(colorName, that.colorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, colorName, brightness);
    }
}
