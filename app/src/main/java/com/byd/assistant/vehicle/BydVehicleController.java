package com.byd.assistant.vehicle;

import com.byd.assistant.model.*;
import java.lang.reflect.Method;

public class BydVehicleController {
    private final Object androidContext;
    private final boolean isMockMode;

    public BydVehicleController(Object androidContext, boolean isMockMode) {
        this.androidContext = androidContext;
        this.isMockMode = isMockMode;
    }

    public BydVehicleController(Object androidContext) {
        this(androidContext, false);
    }

    public boolean execute(VehicleIntent intent) {
        if (intent == null || intent instanceof VehicleIntent.Unknown) {
            return false;
        }

        if (isMockMode || androidContext == null) {
            return true;
        }

        if (intent instanceof VehicleIntent.ScreenRotate) {
            return rotateScreen(((VehicleIntent.ScreenRotate) intent).orientation);
        } else if (intent instanceof VehicleIntent.Climate) {
            VehicleIntent.Climate c = (VehicleIntent.Climate) intent;
            return setClimate(c.enabled, c.targetTemp);
        } else if (intent instanceof VehicleIntent.Window) {
            VehicleIntent.Window w = (VehicleIntent.Window) intent;
            return setWindow(w.open, w.targetWindow);
        } else if (intent instanceof VehicleIntent.Volume) {
            return adjustVolume(((VehicleIntent.Volume) intent).action);
        } else if (intent instanceof VehicleIntent.Navigation) {
            return launchNavigation(((VehicleIntent.Navigation) intent).destination);
        } else if (intent instanceof VehicleIntent.Media) {
            return controlMedia(((VehicleIntent.Media) intent).action);
        }

        return false;
    }

    public boolean rotateScreen(ScreenOrientation orientation) {
        try {
            int angle = -1; // toggle
            if (orientation == ScreenOrientation.PORTRAIT) angle = 0;
            else if (orientation == ScreenOrientation.LANDSCAPE) angle = 90;

            // 1. Try sending DiLink broadcast
            sendBroadcast(DiLinkConstants.ACTION_ROTATE_SCREEN, DiLinkConstants.EXTRA_ROTATE_ANGLE, angle);

            // 2. Try secondary system property
            try {
                Class<?> sysProp = Class.forName("android.os.SystemProperties");
                Method setMethod = sysProp.getMethod("set", String.class, String.class);
                setMethod.invoke(null, DiLinkConstants.PROP_SCREEN_ANGLE, String.valueOf(angle));
            } catch (Throwable ignored) {}

            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean setClimate(boolean enabled, Integer targetTemp) {
        try {
            Object airService = getDiLinkService(DiLinkConstants.SERVICE_BYD_AIR);
            if (airService != null) {
                Method workStateMethod = airService.getClass().getMethod("setAcWorkState", boolean.class);
                workStateMethod.invoke(airService, enabled);
                if (targetTemp != null) {
                    Method tempMethod = airService.getClass().getMethod("setTargetTemp", int.class);
                    tempMethod.invoke(airService, targetTemp);
                }
                return true;
            } else {
                // Fallback broadcast
                sendAcBroadcast(enabled, targetTemp);
                return true;
            }
        } catch (Throwable e) {
            sendAcBroadcast(enabled, targetTemp);
            return true;
        }
    }

    public boolean setWindow(boolean open, WindowTarget target) {
        try {
            Object windowService = getDiLinkService(DiLinkConstants.SERVICE_BYD_WINDOW);
            if (windowService != null) {
                int windowId = 0; // 0 for all / front
                Method windowMethod = windowService.getClass().getMethod("setWindowState", int.class, boolean.class);
                windowMethod.invoke(windowService, windowId, open);
                return true;
            } else {
                sendBroadcast(DiLinkConstants.ACTION_WINDOW_CONTROL, "open", open);
                return true;
            }
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean adjustVolume(VolumeAction action) {
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Method getSystemService = contextClass.getMethod("getSystemService", String.class);
            Object audioManager = getSystemService.invoke(androidContext, "audio");
            if (audioManager == null) return false;

            Class<?> audioManagerClass = Class.forName("android.media.AudioManager");
            int streamMusic = 3; // STREAM_MUSIC
            int flagShowUi = 1; // FLAG_SHOW_UI

            int adjustDirection = 0;
            switch (action) {
                case UP:
                    adjustDirection = 1; // ADJUST_RAISE
                    break;
                case DOWN:
                    adjustDirection = -1; // ADJUST_LOWER
                    break;
                case MUTE:
                    adjustDirection = -100; // ADJUST_MUTE
                    break;
                case UNMUTE:
                    adjustDirection = 100; // ADJUST_UNMUTE
                    break;
                default:
                    return true;
            }

            Method adjustStreamVolume = audioManagerClass.getMethod("adjustStreamVolume", int.class, int.class, int.class);
            adjustStreamVolume.invoke(audioManager, streamMusic, adjustDirection, flagShowUi);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean launchNavigation(String destination) {
        try {
            Class<?> intentClass = Class.forName("android.content.Intent");
            Class<?> uriClass = Class.forName("android.net.Uri");
            Method parseUri = uriClass.getMethod("parse", String.class);

            String uriStr = (destination != null) ? "google.navigation:q=" + destination : "geo:0,0?q=";
            Object uriObj = parseUri.invoke(null, uriStr);

            Object intent = intentClass.getConstructor(String.class, uriClass).newInstance("android.intent.action.VIEW", uriObj);
            Method addFlags = intentClass.getMethod("addFlags", int.class);
            addFlags.invoke(intent, 0x10000000); // FLAG_ACTIVITY_NEW_TASK

            Class<?> contextClass = Class.forName("android.content.Context");
            Method startActivity = contextClass.getMethod("startActivity", intentClass);
            startActivity.invoke(androidContext, intent);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean controlMedia(MediaAction action) {
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Method getSystemService = contextClass.getMethod("getSystemService", String.class);
            Object audioManager = getSystemService.invoke(androidContext, "audio");
            if (audioManager == null) return false;

            int keyCode = 85; // KEYCODE_MEDIA_PLAY_PAUSE
            switch (action) {
                case PLAY:
                    keyCode = 126; // KEYCODE_MEDIA_PLAY
                    break;
                case PAUSE:
                    keyCode = 127; // KEYCODE_MEDIA_PAUSE
                    break;
                case NEXT:
                    keyCode = 87; // KEYCODE_MEDIA_NEXT
                    break;
                case PREVIOUS:
                    keyCode = 88; // KEYCODE_MEDIA_PREVIOUS
                    break;
            }

            Class<?> keyEventClass = Class.forName("android.view.KeyEvent");
            Object downEvent = keyEventClass.getConstructor(int.class, int.class).newInstance(0, keyCode); // ACTION_DOWN
            Object upEvent = keyEventClass.getConstructor(int.class, int.class).newInstance(1, keyCode); // ACTION_UP

            Class<?> audioManagerClass = Class.forName("android.media.AudioManager");
            Method dispatchMediaKeyEvent = audioManagerClass.getMethod("dispatchMediaKeyEvent", keyEventClass);
            dispatchMediaKeyEvent.invoke(audioManager, downEvent);
            dispatchMediaKeyEvent.invoke(audioManager, upEvent);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private Object getDiLinkService(String serviceName) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            return getService.invoke(null, serviceName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void sendBroadcast(String action, String extraKey, Object extraVal) {
        try {
            Class<?> intentClass = Class.forName("android.content.Intent");
            Object intent = intentClass.getConstructor(String.class).newInstance(action);
            if (extraKey != null && extraVal != null) {
                if (extraVal instanceof Integer) {
                    Method putExtra = intentClass.getMethod("putExtra", String.class, int.class);
                    putExtra.invoke(intent, extraKey, (Integer) extraVal);
                } else if (extraVal instanceof Boolean) {
                    Method putExtra = intentClass.getMethod("putExtra", String.class, boolean.class);
                    putExtra.invoke(intent, extraKey, (Boolean) extraVal);
                }
            }
            Class<?> contextClass = Class.forName("android.content.Context");
            Method sendBroadcast = contextClass.getMethod("sendBroadcast", intentClass);
            sendBroadcast.invoke(androidContext, intent);
        } catch (Throwable ignored) {}
    }

    private void sendAcBroadcast(boolean enabled, Integer targetTemp) {
        try {
            Class<?> intentClass = Class.forName("android.content.Intent");
            Object intent = intentClass.getConstructor(String.class).newInstance(DiLinkConstants.ACTION_AC_CONTROL);
            Method putBool = intentClass.getMethod("putExtra", String.class, boolean.class);
            putBool.invoke(intent, "power", enabled);
            if (targetTemp != null) {
                Method putInt = intentClass.getMethod("putExtra", String.class, int.class);
                putInt.invoke(intent, "temp", (int) targetTemp);
            }
            Class<?> contextClass = Class.forName("android.content.Context");
            Method sendBroadcast = contextClass.getMethod("sendBroadcast", intentClass);
            sendBroadcast.invoke(androidContext, intent);
        } catch (Throwable ignored) {}
    }
}
