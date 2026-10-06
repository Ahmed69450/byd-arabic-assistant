package com.byd.assistant.vehicle;

public final class DiLinkConstants {
    private DiLinkConstants() {}

    // Screen Rotation Actions & Settings
    public static final String ACTION_ROTATE_SCREEN = "com.byd.intent.action.ROTATION_SCREEN";
    public static final String ACTION_ROTATE_SCREEN_ALT = "byd.intent.action.SCREEN_ROTATE";
    public static final String EXTRA_ROTATE_ANGLE = "angle"; // -1 = toggle, 0 = portrait, 90 = landscape
    public static final String PROP_SCREEN_ANGLE = "persist.sys.byd.screen_angle";

    // DiLink Vehicle Services (Accessible via android.os.ServiceManager reflection)
    public static final String SERVICE_BYD_AIR = "byd_auto_air";
    public static final String SERVICE_BYD_WINDOW = "byd_auto_window";
    public static final String SERVICE_BYD_BODY = "byd_auto_body";

    // Broadcast Fallbacks
    public static final String ACTION_AC_CONTROL = "byd.intent.action.AC_CONTROL";
    public static final String ACTION_WINDOW_CONTROL = "byd.intent.action.WINDOW_CONTROL";

    // Ambient Lighting Broadcast Actions & Extras
    public static final String ACTION_SET_AMBIENT_LIGHT = "com.byd.intent.action.SET_AMBIENT_LIGHT";
    public static final String EXTRA_LIGHT_COLOR = "color";
    public static final String EXTRA_LIGHT_BRIGHTNESS = "brightness";
    public static final String EXTRA_LIGHT_ENABLED = "enabled";

    // HVAC Extras for Fan Speed and Air Recirculation
    public static final String EXTRA_AC_RECIRCULATION = "recirculation";
    public static final String EXTRA_AC_FAN_SPEED = "fan_speed";

    // Window Ventilation Actions & Modes
    public static final String ACTION_WINDOW_VENT = "com.byd.intent.action.WINDOW_VENT";
    public static final String EXTRA_WINDOW_MODE = "mode";
    public static final String EXTRA_WINDOW_POSITION = "position";
    public static final String MODE_VENTILATION = "ventilation";
}
