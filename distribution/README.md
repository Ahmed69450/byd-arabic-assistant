# BYD Voice Assistant - Dedicated Release Distribution Repository

## Overview & Repository Policy

This dedicated distribution repository (`Ahmed69450/byd-voice-assistant-releases`) is exclusively reserved for hosting **compiled binary release APKs** and the public update manifest (`version.json`).

### 🛡️ Strict Zero-Source-Code Policy
- **No Source Code:** Under no circumstances should source code files (`.java`, `.kt`, `.xml`, `.gradle`, build configs, internal tools, or proprietary model files) ever be committed or pushed to this repository.
- **Binary & Manifest Only:** This repository exists solely to distribute the final installation artifacts (`assistant-release.apk`) and update metadata (`version.json`).
- **Core Repository Isolation:** All development, testing, compilation, and issue tracking take place within the main application development repository (`Ahmed69450/byd-arabic-assistant`).
- **Automated Verification:** CI/CD deployment pipelines enforce strict pre-publish checks that reject any build output containing source files or developer secrets.

---

## 📡 In-App Update Engine Architecture

The BYD Arabic Offline Voice Assistant includes an integrated automatic updater (`com.byd.assistant.updater.AppUpdateManager`) that polls this repository without requiring Google Play Store or external third-party app stores.

### 1. Update Manifest Endpoint
The client queries the raw manifest hosted on the default branch:
```
https://raw.githubusercontent.com/Ahmed69450/byd-voice-assistant-releases/main/version.json
```

### 2. Manifest Schema (`version.json`)
The update manifest defines release metadata in JSON format:

```json
{
  "versionCode": 200,
  "versionName": "2.0.0",
  "apkUrl": "https://github.com/Ahmed69450/byd-voice-assistant-releases/releases/latest/download/assistant-release.apk",
  "changelog": "إصدار 2.0.0: مساعد صوتي محلي بالكامل للسيارات، تحكم بالتكييف والنوافذ والإضاءة المحيطية، ودعم Home Assistant، والتحقق التلقائي من التحديثات داخل التطبيق.",
  "minAppVersion": 100
}
```

#### Field Specifications:
| Field | Type | Description |
| :--- | :--- | :--- |
| `versionCode` | `int` | Monotonically increasing integer code used for version comparison. |
| `versionName` | `string` | Semantic version string displayed to the user (e.g. `2.0.0`). |
| `apkUrl` | `string` | Direct HTTP/HTTPS download link to the compiled `assistant-release.apk`. |
| `changelog` | `string` | Localized Arabic description of new features, bug fixes, and improvements. |
| `minAppVersion` | `int` | Minimum installed version required for an incremental update. |

---

## 🔄 Client Update Lifecycle

```
 +---------------------------------------------+
 |              In-App Trigger                 |
 | (Manual click or background startup check)  |
 +---------------------------------------------+
                        |
                        v
 +---------------------------------------------+
 |         Fetch remote version.json           |
 |    (AppUpdateManager.checkForUpdates())     |
 +---------------------------------------------+
                        |
                        v
 +---------------------------------------------+
 |             Version Evaluation              |
 |      Is remote versionCode > installed?     |
 +---------------------------------------------+
            /                       \
        [Yes]                       [No]
          v                           v
+------------------------+  +-------------------+
| Display Update Dialog  |  | "Up to date" toast|
| with Arabic changelog  |  +-------------------+
+------------------------+
          |
   (User confirms)
          v
+---------------------------------------------+
|        Stream Download to App Cache         |
|  (AppUpdateManager.downloadApk with chunks) |
|         Progress 0% -> 100% Bar             |
+---------------------------------------------+
          |
          v
+---------------------------------------------+
|          Launch Package Installer           |
| (AppUpdateManager.createInstallIntent via   |
|  FileProvider "com.byd.assistant.provider") |
+---------------------------------------------+
```

1. **Manifest Retrieval:** `AppUpdateManager.checkForUpdates()` fetches the JSON manifest asynchronously via `HttpURLConnection` with follow-redirect support.
2. **Evaluation:** `AppUpdateManager.isUpdateAvailable(currentVersionCode, info)` compares the local Android `versionCode` with the remote `versionCode`.
3. **User Prompt:** If an update is detected, an Arabic dialog is presented showing `versionName` and `changelog`.
4. **Streaming Download:** `AppUpdateManager.downloadApk()` streams the APK to the application's internal cache directory, broadcasting progress percentages to the UI progress bar.
5. **Secure Installation:** Once verified, `AppUpdateManager.createInstallIntent()` invokes Android's package installer using `androidx.core.content.FileProvider` (`authority: com.byd.assistant.provider`) with `FLAG_GRANT_READ_URI_PERMISSION` and `FLAG_ACTIVITY_NEW_TASK`.

---

## 🚀 CI/CD Release Automation

Releases are published through GitHub Actions (`.github/workflows/publish-release-apk.yml`):

1. **Clean Build:** Compiles `assistant-release.apk` inside a sandboxed Ubuntu environment using Gradle and JDK 17.
2. **Zero-Source Verification:** Inspects the deployment payload to ensure no Java, Kotlin, XML, or build configuration files are staged.
3. **Manifest Sync:** Synchronizes `version.json` with the updated release version and changelog.
4. **Binary Publishing:** Pushes the compiled APK and `version.json` to GitHub Releases on this dedicated release repository.
