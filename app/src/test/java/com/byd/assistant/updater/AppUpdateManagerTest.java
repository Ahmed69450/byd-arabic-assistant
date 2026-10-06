package com.byd.assistant.updater;

import android.content.Context;
import android.content.Intent;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AppUpdateManagerTest {

    public static void main(String[] args) throws Exception {
        testUpdateUrlConstants();
        testDistributionVersionJsonSchema();
        testVersionComparisons();
        testIsUpdateAvailable();
        testParseVersionJsonStandard();
        testParseVersionJsonEdgeCases();
        testDownloadApkProgressCallbackSimulation();
        testDownloadApkErrorSimulation();
        testCreateInstallIntent();
        testCheckForUpdatesSimulation();
        System.out.println("ALL_UPDATER_TESTS_PASSED");
    }

    private static void testUpdateUrlConstants() {
        assertEquals("https://raw.githubusercontent.com/Ahmed69450/byd-voice-assistant-releases/main/version.json",
                AppUpdateManager.UPDATE_URL, "UPDATE_URL constant mismatch");
        assertEquals(AppUpdateManager.UPDATE_URL, AppUpdateManager.DEFAULT_VERSION_URL,
                "DEFAULT_VERSION_URL should match UPDATE_URL");
    }

    private static void testDistributionVersionJsonSchema() throws IOException {
        File distFile = new File("distribution/version.json");
        if (!distFile.exists()) {
            distFile = new File("../distribution/version.json");
        }
        assertTrue(distFile.exists(), "distribution/version.json file not found");
        String content = new String(Files.readAllBytes(distFile.toPath()), "UTF-8");
        AppUpdateManager.UpdateInfo info = AppUpdateManager.parseVersionJson(content);

        assertNotNull(info, "Parsed distribution/version.json should not be null");
        assertEquals(200, info.getVersionCode(), "versionCode should be 200");
        assertEquals("2.0.0", info.getVersionName(), "versionName should be 2.0.0");
        assertEquals("https://github.com/Ahmed69450/byd-voice-assistant-releases/releases/latest/download/assistant-release.apk",
                info.getApkUrl(), "apkUrl should match release binary URL");
        assertTrue(info.getChangelog().contains("مساعد صوتي محلي بالكامل للسيارات"), "changelog missing key phrase");
        assertTrue(info.getChangelog().contains("Home Assistant"), "changelog missing Home Assistant support");
        assertEquals(100, info.getMinAppVersion(), "minAppVersion should be 100");

        // Verify update detection
        assertTrue(AppUpdateManager.isUpdateAvailable(5, info), "Installed version 5 should detect update 200");
        assertTrue(AppUpdateManager.isUpdateAvailable(100, info), "Installed version 100 should detect update 200");
        assertFalse(AppUpdateManager.isUpdateAvailable(200, info), "Installed version 200 should not detect update 200");
    }

    private static void testCheckForUpdatesSimulation() throws Exception {
        String testJson = "{\n" +
                "  \"versionCode\": 200,\n" +
                "  \"versionName\": \"2.0.0\",\n" +
                "  \"apkUrl\": \"https://github.com/Ahmed69450/byd-voice-assistant-releases/releases/latest/download/assistant-release.apk\",\n" +
                "  \"changelog\": \"مساعد صوتي محلي بالكامل للسيارات\",\n" +
                "  \"minAppVersion\": 100\n" +
                "}";

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/version.json", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] bytes = testJson.getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        String updateUrl = "http://127.0.0.1:" + port + "/version.json";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> tagRef = new AtomicReference<>(null);
        AtomicReference<String> urlRef = new AtomicReference<>(null);
        AtomicReference<String> notesRef = new AtomicReference<>(null);
        AtomicReference<String> errRef = new AtomicReference<>(null);

        AppUpdateManager.checkForUpdates(null, updateUrl, new AppUpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String latestVersion, String downloadUrl, String releaseNotes) {
                tagRef.set(latestVersion);
                urlRef.set(downloadUrl);
                notesRef.set(releaseNotes);
                latch.countDown();
            }

            @Override
            public void onUpToDate(String currentVersion) {
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                errRef.set(errorMessage);
                latch.countDown();
            }
        });

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        server.stop(0);

        assertTrue(finished, "checkForUpdates simulation timed out");
        assertNull(errRef.get(), "checkForUpdates reported error: " + errRef.get());
        assertEquals("2.0.0", tagRef.get(), "Reported version mismatch");
        assertEquals("https://github.com/Ahmed69450/byd-voice-assistant-releases/releases/latest/download/assistant-release.apk",
                urlRef.get(), "Reported apkUrl mismatch");
        assertTrue(notesRef.get().contains("مساعد صوتي محلي"), "Reported notes mismatch");
    }

    private static void testVersionComparisons() {
        if (!AppUpdateManager.isNewerVersion("v1.1.0", "1.0.0")) {
            throw new AssertionError("v1.1.0 should be newer than 1.0.0");
        }
        if (!AppUpdateManager.isNewerVersion("v2.0.0", "1.9.9")) {
            throw new AssertionError("v2.0.0 should be newer than 1.9.9");
        }
        if (AppUpdateManager.isNewerVersion("v1.0.0", "1.0.0")) {
            throw new AssertionError("v1.0.0 should not be newer than 1.0.0");
        }
        if (AppUpdateManager.isNewerVersion("v1.0.0", "1.1.0")) {
            throw new AssertionError("v1.0.0 should not be newer than 1.1.0");
        }
    }

    private static void testIsUpdateAvailable() {
        AppUpdateManager.UpdateInfo info = new AppUpdateManager.UpdateInfo();
        info.versionCode = 200;
        info.versionName = "2.0.0";
        info.apkUrl = "https://example.com/app.apk";
        info.changelog = "تحسينات جديدة";
        info.minAppVersion = 100;

        assertTrue(AppUpdateManager.isUpdateAvailable(100, info), "100 < 200 should have update");
        assertTrue(AppUpdateManager.isUpdateAvailable(199, info), "199 < 200 should have update");
        assertFalse(AppUpdateManager.isUpdateAvailable(200, info), "200 == 200 should not have update");
        assertFalse(AppUpdateManager.isUpdateAvailable(201, info), "201 > 200 should not have update");
        assertFalse(AppUpdateManager.isUpdateAvailable(100, null), "null info should return false");

        // Test constructor and getters
        AppUpdateManager.UpdateInfo constructed = new AppUpdateManager.UpdateInfo(
                300, "3.0.0", "https://example.com/v3.apk", "إصدار 3", 150
        );
        assertEquals(300, constructed.getVersionCode(), "versionCode getter failed");
        assertEquals("3.0.0", constructed.getVersionName(), "versionName getter failed");
        assertEquals("https://example.com/v3.apk", constructed.getApkUrl(), "apkUrl getter failed");
        assertEquals("إصدار 3", constructed.getChangelog(), "changelog getter failed");
        assertEquals(150, constructed.getMinAppVersion(), "minAppVersion getter failed");
        assertTrue(AppUpdateManager.isUpdateAvailable(299, constructed), "299 < 300 should have update");
    }

    private static void testParseVersionJsonStandard() {
        String json = "{\n" +
                "  \"versionCode\": 200,\n" +
                "  \"versionName\": \"2.0.0\",\n" +
                "  \"apkUrl\": \"https://github.com/Ahmed69450/byd-voice-assistant-releases/releases/latest/download/app-release.apk\",\n" +
                "  \"changelog\": \"إصدار 2.0.0: مساعد صوتي محلي بالكامل للسيارات، تحكم بالتكييف والنوافذ والإضاءة، ودعم Home Assistant.\",\n" +
                "  \"minAppVersion\": 100\n" +
                "}";

        AppUpdateManager.UpdateInfo parsed = AppUpdateManager.parseVersionJson(json);
        assertNotNull(parsed, "Parsed UpdateInfo should not be null");
        assertEquals(200, parsed.getVersionCode(), "versionCode should be 200");
        assertEquals("2.0.0", parsed.getVersionName(), "versionName should be 2.0.0");
        assertEquals("https://github.com/Ahmed69450/byd-voice-assistant-releases/releases/latest/download/app-release.apk",
                parsed.getApkUrl(), "apkUrl match failed");
        assertTrue(parsed.getChangelog().contains("مساعد صوتي محلي"), "changelog should contain description");
        assertEquals(100, parsed.getMinAppVersion(), "minAppVersion should be 100");
    }

    private static void testParseVersionJsonEdgeCases() {
        // null or empty
        assertNull(AppUpdateManager.parseVersionJson(null), "null json should return null");
        assertNull(AppUpdateManager.parseVersionJson(""), "empty json should return null");
        assertNull(AppUpdateManager.parseVersionJson("   "), "whitespace json should return null");
        assertNull(AppUpdateManager.parseVersionJson("invalid non-json"), "invalid json should return null");

        // GitHub Release format fallback parsing
        String ghJson = "{\n" +
                "  \"tag_name\": \"v1.5.0\",\n" +
                "  \"body\": \"Fixed HVAC voice commands\\nAdded volume mute support\",\n" +
                "  \"assets\": [{\n" +
                "    \"browser_download_url\": \"https://github.com/org/repo/releases/download/v1.5.0/app.apk\"\n" +
                "  }]\n" +
                "}";
        AppUpdateManager.UpdateInfo ghParsed = AppUpdateManager.parseVersionJson(ghJson);
        assertNotNull(ghParsed, "GitHub JSON should parse into UpdateInfo");
        assertTrue(ghParsed.getVersionName().contains("1.5.0"), "versionName should contain tag");
        assertEquals("https://github.com/org/repo/releases/download/v1.5.0/app.apk", ghParsed.getApkUrl(), "apkUrl fallback failed");
        assertTrue(ghParsed.getChangelog().contains("Fixed HVAC"), "body changelog extraction failed");
    }

    private static void testDownloadApkProgressCallbackSimulation() throws Exception {
        byte[] payload = new byte[32768]; // 32 KB test payload
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 127);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/update.apk", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().set("Content-Type", "application/vnd.android.package-archive");
                exchange.sendResponseHeaders(200, payload.length);
                OutputStream os = exchange.getResponseBody();
                // Write in chunks to verify progress steps
                int chunkSize = 4096;
                for (int off = 0; off < payload.length; off += chunkSize) {
                    int len = Math.min(chunkSize, payload.length - off);
                    os.write(payload, off, len);
                    os.flush();
                }
                os.close();
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        String downloadUrl = "http://127.0.0.1:" + port + "/update.apk";

        File tempApk = File.createTempFile("byd_test_download", ".apk");
        tempApk.deleteOnExit();

        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> progressUpdates = new ArrayList<>();
        AtomicBoolean successInvoked = new AtomicBoolean(false);
        AtomicReference<String> errorOccurred = new AtomicReference<>(null);

        AppUpdateManager.downloadApk(downloadUrl, tempApk, new AppUpdateManager.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                progressUpdates.add(percent);
            }

            @Override
            public void onSuccess(File downloadedApk) {
                successInvoked.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                errorOccurred.set(error);
                latch.countDown();
            }
        });

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        server.stop(0);

        assertTrue(finished, "Download timed out");
        assertNull(errorOccurred.get(), "Download reported error: " + errorOccurred.get());
        assertTrue(successInvoked.get(), "onSuccess was not invoked");
        assertTrue(!progressUpdates.isEmpty(), "No progress updates recorded");
        assertEquals(100, progressUpdates.get(progressUpdates.size() - 1).intValue(), "Final progress should be 100%");
        assertEquals((long) payload.length, tempApk.length(), "Downloaded file size mismatch");

        tempApk.delete();
    }

    private static void testDownloadApkErrorSimulation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorMsg = new AtomicReference<>(null);

        File tempApk = File.createTempFile("byd_err_test", ".apk");
        tempApk.deleteOnExit();

        // 127.0.0.1 on a port that is guaranteed not listening or 404
        AppUpdateManager.downloadApk("http://127.0.0.1:59997/nonexistent.apk", tempApk, new AppUpdateManager.DownloadCallback() {
            @Override
            public void onProgress(int percent) {}

            @Override
            public void onSuccess(File downloadedApk) {
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                errorMsg.set(error);
                latch.countDown();
            }
        });

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        assertTrue(finished, "Error callback timed out");
        assertNotNull(errorMsg.get(), "onError should have been called for connection failure");
        tempApk.delete();
    }

    private static void testCreateInstallIntent() throws IOException {
        File fakeApk = File.createTempFile("byd_app_release", ".apk");
        fakeApk.deleteOnExit();

        Context context = new Context();
        String authority = "com.byd.assistant.provider";

        Intent intent = AppUpdateManager.createInstallIntent(context, fakeApk, authority);
        assertNotNull(intent, "Intent should not be null");
        assertEquals(Intent.ACTION_VIEW, intent.getAction(), "Intent action should be ACTION_VIEW");
        assertEquals("application/vnd.android.package-archive", intent.getType(), "Intent mime type mismatch");
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0, "Missing FLAG_GRANT_READ_URI_PERMISSION");
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0, "Missing FLAG_ACTIVITY_NEW_TASK");
        assertNotNull(intent.getData(), "Intent data URI should not be null");
        assertTrue(intent.getData().toString().contains(authority), "URI should contain authority");
        assertTrue(intent.getData().toString().contains(fakeApk.getName()), "URI should contain apk file name");

        // Test null apk file validation
        boolean caught = false;
        try {
            AppUpdateManager.createInstallIntent(context, null, authority);
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        assertTrue(caught, "createInstallIntent with null file should throw IllegalArgumentException");

        fakeApk.delete();
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " (Expected: '" + expected + "', got: '" + actual + "')");
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (Expected: " + expected + ", got: " + actual + ")");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Condition failed: " + message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Condition expected to be false: " + message);
        }
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError("Object was null: " + message);
        }
    }

    private static void assertNull(Object obj, String message) {
        if (obj != null) {
            throw new AssertionError("Object was not null: " + message);
        }
    }
}
