package com.byd.assistant.updater;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppUpdateManager {
    public static final String UPDATE_URL = "https://raw.githubusercontent.com/Ahmed69450/byd-voice-assistant-releases/main/version.json";
    public static final String DEFAULT_VERSION_URL = UPDATE_URL;
    public static final String GITHUB_API_URL = "https://api.github.com/repos/Ahmed69450/byd-arabic-assistant/releases/latest";

    public static class UpdateInfo {
        public int versionCode;
        public String versionName;
        public String apkUrl;
        public String changelog;
        public int minAppVersion;

        public UpdateInfo() {}

        public UpdateInfo(int versionCode, String versionName, String apkUrl, String changelog, int minAppVersion) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.changelog = changelog;
            this.minAppVersion = minAppVersion;
        }

        public int getVersionCode() {
            return versionCode;
        }

        public String getVersionName() {
            return versionName;
        }

        public String getApkUrl() {
            return apkUrl;
        }

        public String getChangelog() {
            return changelog;
        }

        public int getMinAppVersion() {
            return minAppVersion;
        }
    }

    public interface UpdateCallback {
        void onUpdateAvailable(String latestVersion, String downloadUrl, String releaseNotes);
        void onUpToDate(String currentVersion);
        void onError(String errorMessage);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess(File downloadedApk);
        void onError(String error);
    }

    public static String getInstalledVersion(Object context) {
        return getCurrentVersionName(context);
    }

    public static UpdateInfo parseVersionJson(String json) {
        if (json == null) return null;
        String trimmed = json.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        try {
            int versionCode = 0;
            String vcStr = extractJsonIntOrString(trimmed, "versionCode");
            if (vcStr == null) vcStr = extractJsonIntOrString(trimmed, "version_code");
            if (vcStr != null) {
                try {
                    versionCode = Integer.parseInt(vcStr);
                } catch (NumberFormatException ignored) {}
            }

            String versionName = extractJsonString(trimmed, "versionName");
            if (versionName == null) versionName = extractJsonString(trimmed, "version_name");
            if (versionName == null) versionName = extractJsonString(trimmed, "tag_name");

            String apkUrl = extractJsonString(trimmed, "apkUrl");
            if (apkUrl == null) apkUrl = extractJsonString(trimmed, "apk_url");
            if (apkUrl == null) apkUrl = extractJsonString(trimmed, "downloadUrl");
            if (apkUrl == null) apkUrl = extractJsonString(trimmed, "download_url");
            if (apkUrl == null) apkUrl = extractApkDownloadUrl(trimmed);

            String changelog = extractJsonString(trimmed, "changelog");
            if (changelog == null) changelog = extractJsonString(trimmed, "releaseNotes");
            if (changelog == null) changelog = extractJsonString(trimmed, "release_notes");
            if (changelog == null) changelog = extractJsonString(trimmed, "body");
            if (changelog == null) changelog = "";

            int minAppVersion = 0;
            String minVerStr = extractJsonIntOrString(trimmed, "minAppVersion");
            if (minVerStr == null) minVerStr = extractJsonIntOrString(trimmed, "min_app_version");
            if (minVerStr != null) {
                try {
                    minAppVersion = Integer.parseInt(minVerStr);
                } catch (NumberFormatException ignored) {}
            }

            if (versionCode == 0 && versionName == null && apkUrl == null) {
                return null;
            }

            return new UpdateInfo(
                    versionCode,
                    versionName != null ? versionName : "",
                    apkUrl != null ? apkUrl : "",
                    changelog,
                    minAppVersion
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isUpdateAvailable(int currentVersionCode, UpdateInfo info) {
        if (info == null) return false;
        return info.versionCode > currentVersionCode;
    }

    public static void downloadApk(String apkUrl, File targetFile, DownloadCallback callback) {
        if (callback == null) return;
        if (apkUrl == null || apkUrl.trim().isEmpty()) {
            callback.onError("رابط التنزيل غير صالح");
            return;
        }
        if (targetFile == null) {
            callback.onError("ملف الحفظ غير صالح");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                URL url = new URL(apkUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "BYD-Arabic-Assistant-App");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307
                        || responseCode == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    if (redirectUrl != null) {
                        conn.disconnect();
                        url = new URL(redirectUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestProperty("User-Agent", "BYD-Arabic-Assistant-App");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);
                        responseCode = conn.getResponseCode();
                    }
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    callback.onError("فشل الاتصال بالخادم: كود " + responseCode);
                    return;
                }

                long contentLength = conn.getContentLengthLong();
                in = conn.getInputStream();
                out = new FileOutputStream(targetFile);

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;
                int lastPercent = -1;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (contentLength > 0) {
                        int percent = (int) Math.min(100, (totalRead * 100) / contentLength);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            callback.onProgress(percent);
                        }
                    }
                }

                out.flush();
                if (lastPercent < 100) {
                    callback.onProgress(100);
                }
                callback.onSuccess(targetFile);
            } catch (Exception e) {
                callback.onError("خطأ أثناء تنزيل التحديث: " + e.getMessage());
            } finally {
                try {
                    if (out != null) out.close();
                } catch (Exception ignored) {}
                try {
                    if (in != null) in.close();
                } catch (Exception ignored) {}
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    public static Intent createInstallIntent(Context context, File apkFile, String authority) {
        if (apkFile == null) {
            throw new IllegalArgumentException("apkFile cannot be null");
        }
        Uri apkUri;
        if (context != null && authority != null && !authority.isEmpty()) {
            apkUri = FileProvider.getUriForFile(context, authority, apkFile);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static void checkForUpdates(Object context, UpdateCallback callback) {
        checkForUpdates(context, UPDATE_URL, callback);
    }

    public static void checkForUpdates(Object context, String updateUrl, UpdateCallback callback) {
        new Thread(() -> {
            try {
                String targetUrl = (updateUrl != null && !updateUrl.trim().isEmpty()) ? updateUrl : UPDATE_URL;
                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "BYD-Arabic-Assistant-App");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    postError(callback, "فشل الاتصال بالخادم (كود: " + responseCode + ")");
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String json = sb.toString();
                UpdateInfo info = parseVersionJson(json);
                String currentVersionName = getCurrentVersionName(context);
                int currentVersionCode = getCurrentVersionCode(context);

                if (info != null) {
                    boolean available = false;
                    if (info.versionCode > 0) {
                        available = isUpdateAvailable(currentVersionCode, info);
                    } else if (info.versionName != null && !info.versionName.isEmpty()) {
                        available = isNewerVersion(info.versionName, currentVersionName);
                    }

                    if (available) {
                        final String fTag = info.versionName;
                        final String fUrl = info.apkUrl;
                        final String fNotes = info.changelog;
                        postToMain(() -> callback.onUpdateAvailable(fTag, fUrl, fNotes));
                    } else {
                        final String fCurr = currentVersionName;
                        postToMain(() -> callback.onUpToDate(fCurr));
                    }
                } else {
                    postError(callback, "تعذر قراءة بيانات الإصدار الجديد");
                }

            } catch (Exception e) {
                postError(callback, "خطأ أثناء فحص التحديثات: " + e.getMessage());
            }
        }).start();
    }

    public static void downloadAndInstall(Object context, String downloadUrl) {
        if (context == null || downloadUrl == null) return;
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Class<?> uriClass = Class.forName("android.net.Uri");
            Method parseUri = uriClass.getMethod("parse", String.class);
            Object uriObj = parseUri.invoke(null, downloadUrl);

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
        if (context == null) return "1.2.0";
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
            return "1.2.0";
        }
    }

    public static int getCurrentVersionCode(Object context) {
        if (context == null) return 5;
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Method getPackageManager = contextClass.getMethod("getPackageManager");
            Object pm = getPackageManager.invoke(context);

            Method getPackageName = contextClass.getMethod("getPackageName");
            String pkgName = (String) getPackageName.invoke(context);

            Method getPackageInfo = pm.getClass().getMethod("getPackageInfo", String.class, int.class);
            Object pInfo = getPackageInfo.invoke(pm, pkgName, 0);

            Field versionCodeField = pInfo.getClass().getField("versionCode");
            return versionCodeField.getInt(pInfo);
        } catch (Throwable e) {
            return 5;
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

    private static String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private static String extractJsonIntOrString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?([0-9]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String unescapeJson(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder();
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = input.charAt(i + 1);
                if (next == 'n') {
                    sb.append('\n');
                    i++;
                } else if (next == 'r') {
                    sb.append('\r');
                    i++;
                } else if (next == 't') {
                    sb.append('\t');
                    i++;
                } else if (next == '"') {
                    sb.append('"');
                    i++;
                } else if (next == '\\') {
                    sb.append('\\');
                    i++;
                } else if (next == 'u' && i + 5 < len) {
                    try {
                        int codePoint = Integer.parseInt(input.substring(i + 2, i + 6), 16);
                        sb.append((char) codePoint);
                        i += 5;
                    } catch (NumberFormatException e) {
                        sb.append(c);
                    }
                } else {
                    sb.append(next);
                    i++;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String extractApkDownloadUrl(String json) {
        Pattern pattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
