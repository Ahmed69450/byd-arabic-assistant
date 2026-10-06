package com.byd.assistant.vehicle;

import com.byd.assistant.model.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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
            boolean res = setClimate(c.enabled, c.targetTemp);
            if (c.fanSpeed != null) {
                res = setFanSpeed(c.fanSpeed) && res;
            }
            return res;
        } else if (intent instanceof VehicleIntent.Window) {
            VehicleIntent.Window w = (VehicleIntent.Window) intent;
            return setWindow(w.open, w.targetWindow);
        } else if (intent instanceof VehicleIntent.Volume) {
            return adjustVolume(((VehicleIntent.Volume) intent).action);
        } else if (intent instanceof VehicleIntent.Navigation) {
            return launchNavigation(((VehicleIntent.Navigation) intent).destination);
        } else if (intent instanceof VehicleIntent.Media) {
            return controlMedia(((VehicleIntent.Media) intent).action);
        } else if (intent instanceof VehicleIntent.OpenApp) {
            return launchApplication(((VehicleIntent.OpenApp) intent).appName);
        } else if (intent instanceof LightingIntent) {
            LightingIntent l = (LightingIntent) intent;
            return setAmbientLightColor(l.getColorName(), l.getBrightness());
        }

        return false;
    }

    public boolean launchApplication(String targetName) {
        if (isMockMode || androidContext == null) {
            return true;
        }

        try {
            Class<?> contextClass = Class.forName("android.content.Context");

            // Check if settings
            if (targetName.contains("اعدادات") || targetName.contains("ضبط") || targetName.equalsIgnoreCase("settings")) {
                Class<?> intentClass = Class.forName("android.content.Intent");
                Class<?> settingsClass = Class.forName("android.provider.Settings");
                String action = (String) settingsClass.getField("ACTION_SETTINGS").get(null);
                Object intent = intentClass.getConstructor(String.class).newInstance(action);
                Method addFlags = intentClass.getMethod("addFlags", int.class);
                addFlags.invoke(intent, 0x10000000); // FLAG_ACTIVITY_NEW_TASK
                Method startActivity = contextClass.getMethod("startActivity", intentClass);
                startActivity.invoke(androidContext, intent);
                return true;
            }

            // PackageManager dynamic search
            Method getPackageManager = contextClass.getMethod("getPackageManager");
            Object pm = getPackageManager.invoke(androidContext);
            Class<?> pmClass = pm.getClass();

            Method getInstalledApplications = pmClass.getMethod("getInstalledApplications", int.class);
            java.util.List<?> apps = (java.util.List<?>) getInstalledApplications.invoke(pm, 0);

            java.util.List<String> keywords = getAppSearchKeywords(targetName);

            for (Object appInfo : apps) {
                Method loadLabel = appInfo.getClass().getMethod("loadLabel", pmClass);
                CharSequence label = (CharSequence) loadLabel.invoke(appInfo, pm);
                String labelStr = (label != null) ? label.toString().toLowerCase() : "";

                java.lang.reflect.Field pkgField = appInfo.getClass().getField("packageName");
                String pkgName = (String) pkgField.get(appInfo);

                boolean matches = false;
                for (String kw : keywords) {
                    if (labelStr.contains(kw) || pkgName.toLowerCase().contains(kw)) {
                        matches = true;
                        break;
                    }
                }

                if (matches) {
                    Method getLaunchIntent = pmClass.getMethod("getLaunchIntentForPackage", String.class);
                    Object launchIntent = getLaunchIntent.invoke(pm, pkgName);
                    if (launchIntent != null) {
                        Method addFlags = launchIntent.getClass().getMethod("addFlags", int.class);
                        addFlags.invoke(launchIntent, 0x10000000);
                        Method startActivity = contextClass.getMethod("startActivity", launchIntent.getClass());
                        startActivity.invoke(androidContext, launchIntent);
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    private java.util.List<String> getAppSearchKeywords(String targetName) {
        java.util.List<String> keywords = new java.util.ArrayList<>();
        String clean = targetName.toLowerCase().trim();
        keywords.add(clean);

        if (clean.contains("يوتيوب")) keywords.add("youtube");
        if (clean.contains("سبوتيفاي")) keywords.add("spotify");
        if (clean.contains("نتفلكس") || clean.contains("نتفليكس")) keywords.add("netflix");
        if (clean.contains("تيك توك")) keywords.add("tiktok");
        if (clean.contains("واتساب") || clean.contains("واتس")) keywords.add("whatsapp");
        if (clean.contains("انغامي") || clean.contains("أنغامي")) keywords.add("anghami");
        if (clean.contains("ويز") || clean.contains("وايز")) keywords.add("waze");
        if (clean.contains("تليجرام") || clean.contains("تيليجرام")) keywords.add("telegram");
        if (clean.contains("كروم") || clean.contains("متصفح")) { keywords.add("chrome"); keywords.add("browser"); }
        if (clean.contains("كاميرا") || clean.contains("كاميرات")) { keywords.add("camera"); keywords.add("avm"); }
        if (clean.contains("راديو")) keywords.add("radio");
        if (clean.contains("موسيقى") || clean.contains("اغاني")) keywords.add("music");

        return keywords;
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

    public boolean setAmbientLightColor(String colorName, int brightness) {
        if (isMockMode || androidContext == null) {
            return true;
        }

        try {
            int clampedBrightness = Math.max(0, Math.min(100, brightness));
            boolean enabled = clampedBrightness > 0;

            // 1. Try DiLink body service reflection
            Object bodyService = getDiLinkService(DiLinkConstants.SERVICE_BYD_BODY);
            if (bodyService != null) {
                try {
                    Method lightMethod = bodyService.getClass().getMethod("setAmbientLight", String.class, int.class, boolean.class);
                    lightMethod.invoke(bodyService, colorName, clampedBrightness, enabled);
                    return true;
                } catch (Throwable ignored) {}
            }

            // 2. Broadcast fallback
            Map<String, Object> extras = new HashMap<>();
            extras.put(DiLinkConstants.EXTRA_LIGHT_COLOR, colorName != null ? colorName : "أبيض");
            extras.put(DiLinkConstants.EXTRA_LIGHT_BRIGHTNESS, clampedBrightness);
            extras.put(DiLinkConstants.EXTRA_LIGHT_ENABLED, enabled);
            sendBroadcast(DiLinkConstants.ACTION_SET_AMBIENT_LIGHT, extras);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean setFanSpeed(int speed) {
        if (isMockMode || androidContext == null) {
            return true;
        }

        try {
            int clamped = Math.max(1, Math.min(7, speed));
            Object airService = getDiLinkService(DiLinkConstants.SERVICE_BYD_AIR);
            if (airService != null) {
                try {
                    Method fanMethod = airService.getClass().getMethod("setFanSpeed", int.class);
                    fanMethod.invoke(airService, clamped);
                    return true;
                } catch (Throwable ignored) {}
            }

            Map<String, Object> extras = new HashMap<>();
            extras.put(DiLinkConstants.EXTRA_AC_FAN_SPEED, clamped);
            sendBroadcast(DiLinkConstants.ACTION_AC_CONTROL, extras);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean setRecirculation(boolean internal) {
        if (isMockMode || androidContext == null) {
            return true;
        }

        try {
            Object airService = getDiLinkService(DiLinkConstants.SERVICE_BYD_AIR);
            if (airService != null) {
                try {
                    Method recircMethod = airService.getClass().getMethod("setRecirculation", boolean.class);
                    recircMethod.invoke(airService, internal);
                    return true;
                } catch (Throwable ignored) {}
            }

            Map<String, Object> extras = new HashMap<>();
            extras.put(DiLinkConstants.EXTRA_AC_RECIRCULATION, internal);
            sendBroadcast(DiLinkConstants.ACTION_AC_CONTROL, extras);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean ventWindows() {
        if (isMockMode || androidContext == null) {
            return true;
        }

        try {
            Object windowService = getDiLinkService(DiLinkConstants.SERVICE_BYD_WINDOW);
            if (windowService != null) {
                try {
                    Method ventMethod = windowService.getClass().getMethod("ventWindows", int.class);
                    ventMethod.invoke(windowService, 15);
                    return true;
                } catch (Throwable ignored) {}
            }

            Map<String, Object> extras = new HashMap<>();
            extras.put(DiLinkConstants.EXTRA_WINDOW_MODE, DiLinkConstants.MODE_VENTILATION);
            extras.put(DiLinkConstants.EXTRA_WINDOW_POSITION, 15);
            sendBroadcast(DiLinkConstants.ACTION_WINDOW_VENT, extras);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public TelemetryData getVehicleTelemetry() {
        if (isMockMode || androidContext == null) {
            return new TelemetryData();
        }

        try {
            Object bodyService = getDiLinkService(DiLinkConstants.SERVICE_BYD_BODY);
            if (bodyService != null) {
                try {
                    Method getSocMethod = bodyService.getClass().getMethod("getBatterySoc");
                    int soc = (Integer) getSocMethod.invoke(bodyService);
                    Method getRangeMethod = bodyService.getClass().getMethod("getRemainingRangeKm");
                    int range = (Integer) getRangeMethod.invoke(bodyService);
                    Method getFuelMethod = bodyService.getClass().getMethod("getFuelLevel");
                    int fuel = (Integer) getFuelMethod.invoke(bodyService);
                    Method getDtcMethod = bodyService.getClass().getMethod("hasDtcFault");
                    boolean fault = (Boolean) getDtcMethod.invoke(bodyService);
                    Method getDtcMsg = bodyService.getClass().getMethod("getDiagnosticSummary");
                    String msg = (String) getDtcMsg.invoke(bodyService);
                    return new TelemetryData(soc, range, fuel, fault, msg);
                } catch (Throwable ignored) {}
            }
            return new TelemetryData();
        } catch (Throwable e) {
            return new TelemetryData();
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
        if (androidContext == null) return;
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
                } else if (extraVal instanceof String) {
                    Method putExtra = intentClass.getMethod("putExtra", String.class, String.class);
                    putExtra.invoke(intent, extraKey, (String) extraVal);
                }
            }
            Class<?> contextClass = Class.forName("android.content.Context");
            Method sendBroadcast = contextClass.getMethod("sendBroadcast", intentClass);
            sendBroadcast.invoke(androidContext, intent);
        } catch (Throwable ignored) {}
    }

    private void sendBroadcast(String action, Map<String, Object> extras) {
        if (androidContext == null) return;
        try {
            Class<?> intentClass = Class.forName("android.content.Intent");
            Object intent = intentClass.getConstructor(String.class).newInstance(action);
            if (extras != null) {
                for (Map.Entry<String, Object> entry : extras.entrySet()) {
                    String k = entry.getKey();
                    Object v = entry.getValue();
                    if (v instanceof Integer) {
                        Method putExtra = intentClass.getMethod("putExtra", String.class, int.class);
                        putExtra.invoke(intent, k, (Integer) v);
                    } else if (v instanceof Boolean) {
                        Method putExtra = intentClass.getMethod("putExtra", String.class, boolean.class);
                        putExtra.invoke(intent, k, (Boolean) v);
                    } else if (v instanceof String) {
                        Method putExtra = intentClass.getMethod("putExtra", String.class, String.class);
                        putExtra.invoke(intent, k, (String) v);
                    }
                }
            }
            Class<?> contextClass = Class.forName("android.content.Context");
            Method sendBroadcast = contextClass.getMethod("sendBroadcast", intentClass);
            sendBroadcast.invoke(androidContext, intent);
        } catch (Throwable ignored) {}
    }

    private void sendAcBroadcast(boolean enabled, Integer targetTemp) {
        if (androidContext == null) return;
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
