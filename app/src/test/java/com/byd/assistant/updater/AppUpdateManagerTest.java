package com.byd.assistant.updater;

public class AppUpdateManagerTest {
    public static void main(String[] args) {
        testVersionComparisons();
        System.out.println("ALL_UPDATER_TESTS_PASSED");
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
}
