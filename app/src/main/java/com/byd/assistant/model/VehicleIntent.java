package com.byd.assistant.model;

public abstract class VehicleIntent {

    public static final class Climate extends VehicleIntent {
        public final boolean enabled;
        public final Integer targetTemp;
        public final Integer fanSpeed;

        public Climate(boolean enabled, Integer targetTemp, Integer fanSpeed) {
            this.enabled = enabled;
            this.targetTemp = targetTemp;
            this.fanSpeed = fanSpeed;
        }

        public Climate(boolean enabled, Integer targetTemp) {
            this(enabled, targetTemp, null);
        }

        public Climate(boolean enabled) {
            this(enabled, null, null);
        }

        @Override
        public String toString() {
            return "Climate{enabled=" + enabled + ", targetTemp=" + targetTemp + "}";
        }
    }

    public static final class Window extends VehicleIntent {
        public final boolean open;
        public final WindowTarget targetWindow;

        public Window(boolean open, WindowTarget targetWindow) {
            this.open = open;
            this.targetWindow = targetWindow;
        }

        public Window(boolean open) {
            this(open, WindowTarget.ALL);
        }

        @Override
        public String toString() {
            return "Window{open=" + open + ", target=" + targetWindow + "}";
        }
    }

    public static final class ScreenRotate extends VehicleIntent {
        public final ScreenOrientation orientation;

        public ScreenRotate(ScreenOrientation orientation) {
            this.orientation = orientation;
        }

        @Override
        public String toString() {
            return "ScreenRotate{orientation=" + orientation + "}";
        }
    }

    public static final class Volume extends VehicleIntent {
        public final VolumeAction action;
        public final Integer level;

        public Volume(VolumeAction action, Integer level) {
            this.action = action;
            this.level = level;
        }

        public Volume(VolumeAction action) {
            this(action, null);
        }

        @Override
        public String toString() {
            return "Volume{action=" + action + ", level=" + level + "}";
        }
    }

    public static final class Media extends VehicleIntent {
        public final MediaAction action;

        public Media(MediaAction action) {
            this.action = action;
        }

        @Override
        public String toString() {
            return "Media{action=" + action + "}";
        }
    }

    public static final class Navigation extends VehicleIntent {
        public final String destination;

        public Navigation(String destination) {
            this.destination = destination;
        }

        public Navigation() {
            this(null);
        }

        @Override
        public String toString() {
            return "Navigation{destination='" + destination + "'}";
        }
    }

    public static final class OpenApp extends VehicleIntent {
        public final String appName;

        public OpenApp(String appName) {
            this.appName = appName;
        }

        @Override
        public String toString() {
            return "OpenApp{'" + appName + "'}";
        }
    }

    public static final class Unknown extends VehicleIntent {
        public final String originalText;

        public Unknown(String originalText) {
            this.originalText = originalText;
        }

        @Override
        public String toString() {
            return "Unknown{'" + originalText + "'}";
        }
    }
}
