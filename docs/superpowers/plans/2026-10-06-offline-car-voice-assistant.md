# Offline Automotive Voice Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline-first, highly responsive Android voice assistant for automotive systems (BYD / DiLink) featuring local NLU (Arabic Normalization, Levenshtein fuzzy matching, NER, FSM dialogue manager), vehicle control (AC, windows, ambient lighting, media, telemetry), Home Assistant WebSocket integration, lightweight online APIs, and an in-app auto-updater backed by a dedicated APK-only release repository.

**Architecture:** An Android application anchored by a foreground service with a floating microphone overlay. The speech-to-text pipeline feeds an offline NLU engine (FSM dialogue state, Levenshtein fuzzy matching, slot extraction) that dispatches commands either locally to the vehicle via DiLink/Android intents or to Home Assistant via WebSocket/REST. An in-app updater polls a separate APK-only distribution repository for new versions.

**Tech Stack:** Android (Java/Gradle), JUnit 4, Levenshtein Distance algorithms, WebSockets, HttpURLConnection, FileProvider, Home Assistant REST/WebSocket API.

**Spec:** `docs/superpowers/specs/2026-10-06-offline-car-voice-assistant-design.md`

## Global Constraints
- Target Android SDK: 28+ (Automotive & DiLink compatibility)
- Offline-First: Vehicle controls, NLU, and local audio must execute without internet
- No Source Code in Release Repo: Only binary APKs and `version.json` must exist in the distribution repo
- Latency Target: <300ms from voice transcript to vehicle command dispatch
- Memory & CPU: Lightweight algorithms (Rule matrix, Levenshtein, FSM) without heavyweight runtime dependencies

## Review Focus
1. Malformed or noisy Arabic speech with heavy dialect variations or background noise must fuzzy match intended actions or safely prompt for clarification.
2. Loss of network connectivity during external API calls (e.g., OpenMeteo) must fail gracefully without hanging the speech engine.
3. Rapid concurrent voice inputs must be serialized by the FSM dialogue manager without race conditions.
4. In-app APK updater must verify file integrity and handle lack of storage space or cancelled installations.
5. Home Assistant connection drops must automatically fall back to direct local vehicle intents with retry timers.

---

### Task 1: Baseline Restoration & Levenshtein Fuzzy Matching Engine

**Files:**
- Restore/Modify: `app/src/main/java/com/byd/assistant/nlp/ArabicNormalizer.java`
- Create: `app/src/main/java/com/byd/assistant/nlp/LevenshteinMatcher.java`
- Test: `app/src/test/java/com/byd/assistant/nlp/LevenshteinMatcherTest.java`

**Interfaces:**
- Consumes: Raw text strings from speech recognition
- Produces: `LevenshteinMatcher.findBestMatch(String input, List<String> candidates, double threshold)` returning matched candidate or null

- [ ] **Step 1: Write failing unit tests for LevenshteinMatcher**

Create `app/src/test/java/com/byd/assistant/nlp/LevenshteinMatcherTest.java`:
```java
package com.byd.assistant.nlp;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class LevenshteinMatcherTest {

    @Test
    public void testExactMatch() {
        List<String> dictionary = Arrays.asList("افتح النافذة", "شغل المكيف", "اغلق النوافذ");
        String match = LevenshteinMatcher.findBestMatch("شغل المكيف", dictionary, 0.8);
        assertEquals("شغل المكيف", match);
    }

    @Test
    public void testFuzzyMatchWithTypo() {
        List<String> dictionary = Arrays.asList("افتح النافذة", "شغل المكيف", "اغلق النوافذ");
        // Typo: "شغل المكف" instead of "شغل المكيف"
        String match = LevenshteinMatcher.findBestMatch("شغل المكف", dictionary, 0.75);
        assertEquals("شغل المكيف", match);
    }

    @Test
    public void testNoMatchBelowThreshold() {
        List<String> dictionary = Arrays.asList("افتح النافذة", "شغل المكيف");
        String match = LevenshteinMatcher.findBestMatch("مساء الخير يا صديقي", dictionary, 0.8);
        assertNull(match);
    }

    @Test
    public void testSimilarityCalculation() {
        double sim = LevenshteinMatcher.similarity("مكيف", "مكيف");
        assertEquals(1.0, sim, 0.001);

        double partial = LevenshteinMatcher.similarity("مكيف", "مكف");
        assertTrue(partial >= 0.75);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `Test via Gradle or Java test runner`
Expected: FAIL (class `LevenshteinMatcher` not found)

- [ ] **Step 3: Implement LevenshteinMatcher**

Create `app/src/main/java/com/byd/assistant/nlp/LevenshteinMatcher.java`:
```java
package com.byd.assistant.nlp;

import java.util.List;

public class LevenshteinMatcher {

    public static int distance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int dist = distance(s1, s2);
        return 1.0 - ((double) dist / (double) maxLen);
    }

    public static String findBestMatch(String input, List<String> candidates, double threshold) {
        if (input == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String bestMatch = null;
        double highestSim = -1.0;

        for (String candidate : candidates) {
            double sim = similarity(input.trim(), candidate.trim());
            if (sim > highestSim && sim >= threshold) {
                highestSim = sim;
                bestMatch = candidate;
            }
        }
        return bestMatch;
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

Run unit tests and verify 100% pass rate.

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/byd/assistant/nlp/LevenshteinMatcher.java app/src/test/java/com/byd/assistant/nlp/LevenshteinMatcherTest.java
git commit -m "feat(nlp): add Levenshtein distance and fuzzy matcher"
```

---

### Task 2: Advanced Slot Extraction (NER) & FSM Dialogue Manager

**Files:**
- Create: `app/src/main/java/com/byd/assistant/nlp/SlotExtractor.java`
- Create: `app/src/main/java/com/byd/assistant/nlp/DialogueFsm.java`
- Modify: `app/src/main/java/com/byd/assistant/nlp/ArabicIntentResolver.java`
- Test: `app/src/test/java/com/byd/assistant/nlp/DialogueFsmTest.java`

**Interfaces:**
- Consumes: Normalized speech input
- Produces: `DialogueFsm.processInput(String input)` updating state and emitting resolved vehicle/API actions

- [ ] **Step 1: Write failing tests for SlotExtractor and DialogueFsm**

Create `app/src/test/java/com/byd/assistant/nlp/DialogueFsmTest.java`:
```java
package com.byd.assistant.nlp;

import org.junit.Test;
import static org.junit.Assert.*;

public class DialogueFsmTest {

    @Test
    public void testSingleTurnIntent() {
        DialogueFsm fsm = new DialogueFsm();
        DialogueFsm.FsmResult result = fsm.processInput("شغل المكيف على 22");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("CLIMATE_SET_TEMP", result.intent);
        assertEquals(22, result.numericValue);
    }

    @Test
    public void testMultiTurnContextMemory() {
        DialogueFsm fsm = new DialogueFsm();
        // Turn 1: ambiguous window command
        DialogueFsm.FsmResult res1 = fsm.processInput("افتح النافذة");
        assertEquals(DialogueFsm.State.AWAITING_CONFIRMATION, fsm.getCurrentState());
        assertTrue(res1.prompt.contains("اي نافذة"));

        // Turn 2: answer context
        DialogueFsm.FsmResult res2 = fsm.processInput("السائق");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("WINDOW_OPEN_DRIVER", res2.intent);
    }

    @Test
    public void testSlotExtractorNumbers() {
        int temp = SlotExtractor.extractInteger("اضبط درجة الحرارة على اربعة وعشرين", 20);
        assertEquals(24, temp);

        int directNum = SlotExtractor.extractInteger("خلها 19", 20);
        assertEquals(19, directNum);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: JUnit test runner
Expected: FAIL (classes missing)

- [ ] **Step 3: Implement SlotExtractor and DialogueFsm**

Create `app/src/main/java/com/byd/assistant/nlp/SlotExtractor.java` with number parsing (Arabic written numbers like "واحد وعشرين", "خمسة وعشرين" and digits) and target entities (السائق, الراكب, فتحة السقف, الكل).

Create `app/src/main/java/com/byd/assistant/nlp/DialogueFsm.java`:
```java
package com.byd.assistant.nlp;

import java.util.HashMap;
import java.util.Map;

public class DialogueFsm {
    public enum State { IDLE, LISTENING, THINKING, AWAITING_CONFIRMATION, SPEAKING, ERROR }

    public static class FsmResult {
        public String intent;
        public int numericValue = -1;
        public String targetEntity;
        public String prompt;
        public boolean requiresConfirmation = false;
    }

    private State currentState = State.IDLE;
    private String pendingAction = null;
    private final Map<String, Object> sessionMemory = new HashMap<>();

    public State getCurrentState() {
        return currentState;
    }

    public synchronized FsmResult processInput(String rawInput) {
        String input = ArabicNormalizer.normalize(rawInput);
        FsmResult result = new FsmResult();

        if (currentState == State.AWAITING_CONFIRMATION && pendingAction != null) {
            // Context resolution
            if (pendingAction.equals("WINDOW_OPEN_AMBIGUOUS")) {
                if (input.contains("سائق")) {
                    result.intent = "WINDOW_OPEN_DRIVER";
                    result.prompt = "جاري فتح نافذة السائق";
                } else if (input.contains("راكب")) {
                    result.intent = "WINDOW_OPEN_PASSENGER";
                    result.prompt = "جاري فتح نافذة الراكب";
                } else {
                    result.intent = "WINDOW_OPEN_ALL";
                    result.prompt = "جاري فتح جميع النوافذ";
                }
                currentState = State.SPEAKING;
                pendingAction = null;
                return result;
            }
        }

        // Direct matching
        if (input.contains("مكيف") || input.contains("تبريد")) {
            int temp = SlotExtractor.extractInteger(input, 22);
            result.intent = "CLIMATE_SET_TEMP";
            result.numericValue = temp;
            result.prompt = "تم ضبط المكيف على " + temp + " درجة";
            currentState = State.SPEAKING;
            return result;
        }

        if (input.equals("افتح النافذه") || input.equals("افتح الدريشه")) {
            currentState = State.AWAITING_CONFIRMATION;
            pendingAction = "WINDOW_OPEN_AMBIGUOUS";
            result.prompt = "اي نافذة ترغب بفتحها؟ السائق، الراكب، ام الكل؟";
            result.requiresConfirmation = true;
            return result;
        }

        result.intent = "UNKNOWN";
        result.prompt = "عذراً، لم افهم الامر بدقة";
        currentState = State.IDLE;
        return result;
    }

    public void reset() {
        currentState = State.IDLE;
        pendingAction = null;
        sessionMemory.clear();
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

Run: `DialogueFsmTest`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/byd/assistant/nlp/SlotExtractor.java app/src/main/java/com/byd/assistant/nlp/DialogueFsm.java app/src/test/java/com/byd/assistant/nlp/DialogueFsmTest.java
git commit -m "feat(nlp): add slot extractor and multi-turn dialogue FSM"
```

---

### Task 3: Comprehensive Vehicle Controller (AC, Windows, Ambient Lighting, Telemetry)

**Files:**
- Create: `app/src/main/java/com/byd/assistant/model/LightingIntent.java`
- Create: `app/src/main/java/com/byd/assistant/model/TelemetryData.java`
- Modify: `app/src/main/java/com/byd/assistant/vehicle/BydVehicleController.java`
- Modify: `app/src/main/java/com/byd/assistant/vehicle/DiLinkConstants.java`
- Test: `app/src/test/java/com/byd/assistant/vehicle/BydVehicleControllerTest.java`

**Interfaces:**
- Consumes: Intent strings and parameters from `DialogueFsm`
- Produces: Execution of DiLink broadcast intents and telemetry retrieval

- [ ] **Step 1: Write failing tests for lighting and telemetry control**

Update `app/src/test/java/com/byd/assistant/vehicle/BydVehicleControllerTest.java`:
```java
package com.byd.assistant.vehicle;

import org.junit.Test;
import static org.junit.Assert.*;

public class BydVehicleControllerTest {

    @Test
    public void testAmbientLightingIntent() {
        BydVehicleController controller = new BydVehicleController(null);
        boolean handled = controller.setAmbientLightColor("أزرق", 80);
        assertTrue(handled);
    }

    @Test
    public void testTelemetryQuery() {
        BydVehicleController controller = new BydVehicleController(null);
        TelemetryData telemetry = controller.getVehicleTelemetry();
        assertNotNull(telemetry);
        assertTrue(telemetry.batterySoc >= 0 && telemetry.batterySoc <= 100);
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Expected: Compilation error / FAIL (LightingIntent / TelemetryData missing)

- [ ] **Step 3: Implement TelemetryData and extended BydVehicleController methods**

Implement `TelemetryData` model holding `batterySoc`, `rangeKm`, `fuelLevel`, and `hasDtcFault`.
Add ambient lighting broadcast constant and handlers in `DiLinkConstants` and `BydVehicleController`.

- [ ] **Step 4: Run tests and verify they pass**

Run: `BydVehicleControllerTest`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/byd/assistant/model/TelemetryData.java app/src/main/java/com/byd/assistant/vehicle/BydVehicleController.java app/src/test/java/com/byd/assistant/vehicle/BydVehicleControllerTest.java
git commit -m "feat(vehicle): add ambient lighting and vehicle telemetry controller"
```

---

### Task 4: Home Assistant WebSocket & REST Integration Bridge

**Files:**
- Create: `app/src/main/java/com/byd/assistant/ha/HomeAssistantBridge.java`
- Create: `app/src/main/java/com/byd/assistant/ha/HaConfig.java`
- Test: `app/src/test/java/com/byd/assistant/ha/HomeAssistantBridgeTest.java`

**Interfaces:**
- Consumes: `HaConfig` (Host, Port, Token) and service call requests
- Produces: JSON RPC messages over WebSocket with sensor event subscription

- [ ] **Step 1: Write failing test for HomeAssistantBridge**

Create `app/src/test/java/com/byd/assistant/ha/HomeAssistantBridgeTest.java`:
```java
package com.byd.assistant.ha;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeAssistantBridgeTest {

    @Test
    public void testServiceCallJsonPayload() {
        String payload = HomeAssistantBridge.buildServicePayload("climate", "set_temperature", "{\"temperature\": 22}");
        assertTrue(payload.contains("\"type\": \"call_service\""));
        assertTrue(payload.contains("\"domain\": \"climate\""));
        assertTrue(payload.contains("\"service\": \"set_temperature\""));
    }

    @Test
    public void testConfigValidation() {
        HaConfig config = new HaConfig("http://192.168.1.100:8123", "secret_token");
        assertTrue(config.isValid());
        assertEquals("ws://192.168.1.100:8123/api/websocket", config.getWebSocketUrl());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL (classes not found)

- [ ] **Step 3: Implement HaConfig and HomeAssistantBridge**

Implement JSON construction, HTTP fallback, and WebSocket endpoint transformation.

- [ ] **Step 4: Run tests and verify they pass**

Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/byd/assistant/ha/HomeAssistantBridge.java app/src/main/java/com/byd/assistant/ha/HaConfig.java app/src/test/java/com/byd/assistant/ha/HomeAssistantBridgeTest.java
git commit -m "feat(ha): add Home Assistant WebSocket and REST bridge"
```

---

### Task 5: Lightweight External APIs Dispatcher

**Files:**
- Create: `app/src/main/java/com/byd/assistant/api/ExternalApiDispatcher.java`
- Create: `app/src/main/java/com/byd/assistant/api/WeatherClient.java`
- Create: `app/src/main/java/com/byd/assistant/api/InstantAnswerClient.java`
- Test: `app/src/test/java/com/byd/assistant/api/ExternalApiDispatcherTest.java`

**Interfaces:**
- Consumes: Intent query string when offline vehicle match does not apply
- Produces: Concise plain text Arabic answer for TTS speech synthesis

- [ ] **Step 1: Write failing test for ExternalApiDispatcher**

Create `app/src/test/java/com/byd/assistant/api/ExternalApiDispatcherTest.java`:
```java
package com.byd.assistant.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExternalApiDispatcherTest {

    @Test
    public void testDuckDuckGoUrlBuilding() {
        String url = InstantAnswerClient.buildQueryUrl("عاصمة العراق");
        assertTrue(url.contains("duckduckgo.com"));
        assertTrue(url.contains("format=json"));
    }

    @Test
    public void testOpenMeteoUrlBuilding() {
        String url = WeatherClient.buildForecastUrl(33.3152, 44.3661);
        assertTrue(url.contains("open-meteo.com"));
        assertTrue(url.contains("latitude=33.3152"));
        assertTrue(url.contains("current_weather=true"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL

- [ ] **Step 3: Implement API clients and dispatcher**

Implement URL builders and response parsers with strict timeouts (<2.5s) to guarantee high responsiveness.

- [ ] **Step 4: Run tests and verify they pass**

Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/byd/assistant/api/ app/src/test/java/com/byd/assistant/api/
git commit -m "feat(api): add lightweight online API dispatcher for weather and search"
```

---

### Task 6: In-App Auto-Updater & UI Controls

**Files:**
- Modify: `app/src/main/java/com/byd/assistant/updater/AppUpdateManager.java`
- Modify: `app/src/main/java/com/byd/assistant/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Test: `app/src/test/java/com/byd/assistant/updater/AppUpdateManagerTest.java`

**Interfaces:**
- Consumes: Target release repository raw URL for `version.json`
- Produces: In-app download progress events and system installation Intent

- [ ] **Step 1: Write failing test for update version check and download progress**

Update `app/src/test/java/com/byd/assistant/updater/AppUpdateManagerTest.java`:
```java
package com.byd.assistant.updater;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppUpdateManagerTest {

    @Test
    public void testVersionComparison() {
        AppUpdateManager.UpdateInfo info = new AppUpdateManager.UpdateInfo();
        info.versionCode = 200;
        info.versionName = "2.0.0";
        info.apkUrl = "https://example.com/app.apk";

        assertTrue(AppUpdateManager.isUpdateAvailable(100, info));
        assertFalse(AppUpdateManager.isUpdateAvailable(200, info));
        assertFalse(AppUpdateManager.isUpdateAvailable(201, info));
    }

    @Test
    public void testParseVersionJson() {
        String json = "{\"versionCode\": 210, \"versionName\": \"2.1.0\", \"apkUrl\": \"https://github.com/org/repo/release.apk\", \"changelog\": \"New features\"}";
        AppUpdateManager.UpdateInfo parsed = AppUpdateManager.parseVersionJson(json);
        assertNotNull(parsed);
        assertEquals(210, parsed.versionCode);
        assertEquals("2.1.0", parsed.versionName);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL if new parsing logic or fields differ

- [ ] **Step 3: Implement In-App Update UI & Manager**
  - Add explicit "التحقق من التحديثات" button and status text in `activity_main.xml`.
  - Wire button in `MainActivity` with callback for progress dialog and install trigger.

- [ ] **Step 4: Run tests and verify they pass**

Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/byd/assistant/updater/ app/src/main/java/com/byd/assistant/MainActivity.java app/src/main/res/layout/activity_main.xml
git commit -m "feat(updater): add in-app update check button and direct APK installer"
```

---

### Task 7: Dedicated Release Repository Scaffold & Automation

**Files:**
- Create: `distribution/version.json`
- Create: `distribution/README.md`
- Create: `.github/workflows/publish-release-apk.yml`

**Interfaces:**
- Consumes: Built release APK from Gradle
- Produces: GitHub Action publishing APK binary and updating `version.json` in the dedicated release repository without pushing source code

- [ ] **Step 1: Write `version.json` schema and metadata**

Create `distribution/version.json`:
```json
{
  "versionCode": 200,
  "versionName": "2.0.0",
  "apkUrl": "https://github.com/Ahmed69450/byd-voice-assistant-releases/releases/latest/download/app-release.apk",
  "changelog": "إصدار 2.0.0: مساعد صوتي محلي بالكامل للسيارات، تحكم بالتكييف والنوافذ والإضاءة، ودعم Home Assistant.",
  "minAppVersion": 100
}
```

- [ ] **Step 2: Create release workflow publishing exclusively to the release repository**

Create `.github/workflows/publish-release-apk.yml` that builds the APK and pushes **only** `version.json` and the `.apk` asset to the separate releases repo.

- [ ] **Step 3: Verify configuration and structure**

Verify JSON validity using parser/tool.

- [ ] **Step 4: Commit changes**

```bash
git add distribution/ .github/workflows/publish-release-apk.yml
git commit -m "ci: add APK-only distribution repository automation and version.json"
```
