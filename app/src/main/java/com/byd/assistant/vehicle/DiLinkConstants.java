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
}
