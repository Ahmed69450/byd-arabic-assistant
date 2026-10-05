package com.byd.assistant.updater;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppUpdateManager {
    public static final String GITHUB_API_URL = "https://api.github.com/repos/Ahmed69450/byd-arabic-assistant/releases/latest";

    public interface UpdateCallback {
        void onUpdateAvailable(String latestVersion, String downloadUrl, String releaseNotes);
        void onUpToDate(String currentVersion);
        void onError(String errorMessage);
    }

    public static String getInstalledVersion(Object context) {
        return getCurrentVersionName(context);
    }

    public static void checkForUpdates(Object context, UpdateCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "BYD-Arabic-Assistant-App");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    postError(callback, "فشل الاتصال بـ GitHub (كود: " + responseCode + ")");
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String json = sb.toString();

                // Extract tag_name
                String latestTag = extractJsonField(json, "tag_name");
                if (latestTag == null) {
                    postError(callback, "تعذر قراءة رقم الإصدار الجديد");
                    return;
                }

                // Extract APK download URL
                String apkUrl = extractApkDownloadUrl(json);
                if (apkUrl == null) {
                    apkUrl = "https://github.com/Ahmed69450/byd-arabic-assistant/releases/download/" + latestTag + "/BydArabicAssistant.apk";
                }

                String releaseBody = extractJsonField(json, "body");
                if (releaseBody == null) releaseBody = "";

                String currentVersion = getCurrentVersionName(context);

                if (isNewerVersion(latestTag, currentVersion)) {
                    final String fTag = latestTag;
                    final String fUrl = apkUrl;
                    final String fNotes = releaseBody;
                    postToMain(() -> callback.onUpdateAvailable(fTag, fUrl, fNotes));
                } else {
                    final String fCurr = currentVersion;
                    postToMain(() -> callback.onUpToDate(fCurr));
                }

            } catch (Exception e) {
                postError(callback, "خطأ أثناء فحص التحديثات: " + e.getMessage());
            }
        }).start();
    }

    public static void downloadAndInstall(Object context, String downloadUrl) {
        if (context == null) return;
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Class<?> uriClass = Class.forName("android.net.Uri");
            Method parseUri = uriClass.getMethod("parse", String.class);
            Object uriObj = parseUri.invoke(null, downloadUrl);

            // Open download url directly via browser / download intent
            Class<?> intentClass = Class.forName("android.content.Intent");
            Object intent = intentClass.getConstructor(String.class, uriClass).newInstance("android.intent.action.VIEW", uriObj);
            Method addFlags = intentClass.getMethod("addFlags", int.class);
            addFlags.invoke(intent, 0x10000000); // FLAG_ACTIVITY_NEW_TASK

            Method startActivity = contextClass.getMethod("startActivity", intentClass);
            startActivity.invoke(context, intent);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void postError(UpdateCallback callback, String msg) {
        postToMain(() -> callback.onError(msg));
    }

    private static void postToMain(Runnable r) {
        try {
            Class<?> looperClass = Class.forName("android.os.Looper");
            Method getMainLooper = looperClass.getMethod("getMainLooper");
            Object mainLooper = getMainLooper.invoke(null);

            Class<?> handlerClass = Class.forName("android.os.Handler");
            Constructor<?> constructor = handlerClass.getConstructor(looperClass);
            Object handler = constructor.newInstance(mainLooper);

            Method postMethod = handlerClass.getMethod("post", Runnable.class);
            postMethod.invoke(handler, r);
        } catch (Throwable fallback) {
            r.run();
        }
    }

    public static String getCurrentVersionName(Object context) {
        if (context == null) return "1.1.0";
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Method getPackageManager = contextClass.getMethod("getPackageManager");
            Object pm = getPackageManager.invoke(context);

            Method getPackageName = contextClass.getMethod("getPackageName");
            String pkgName = (String) getPackageName.invoke(context);

            Method getPackageInfo = pm.getClass().getMethod("getPackageInfo", String.class, int.class);
            Object pInfo = getPackageInfo.invoke(pm, pkgName, 0);

            Field versionNameField = pInfo.getClass().getField("versionName");
            return (String) versionNameField.get(pInfo);
        } catch (Throwable e) {
            return "1.1.0";
        }
    }

    public static boolean isNewerVersion(String latestTag, String currentVersion) {
        if (latestTag == null || currentVersion == null) return false;
        String cleanLatest = latestTag.replaceAll("[^0-9.]", "").trim();
        String cleanCurrent = currentVersion.replaceAll("[^0-9.]", "").trim();

        String[] latestParts = cleanLatest.split("\\.");
        String[] currentParts = cleanCurrent.split("\\.");

        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int l = (i < latestParts.length && !latestParts[i].isEmpty()) ? Integer.parseInt(latestParts[i]) : 0;
            int c = (i < currentParts.length && !currentParts[i].isEmpty()) ? Integer.parseInt(currentParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private static String extractJsonField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\":\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\n", "\n").replace("\\r", "");
        }
        return null;
    }

    private static String extractApkDownloadUrl(String json) {
        Pattern pattern = Pattern.compile("\"browser_download_url\":\\s*\"([^\"]+\\.apk)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
