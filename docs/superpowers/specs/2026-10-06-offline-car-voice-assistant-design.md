# Offline Automotive Voice Assistant Architecture & Specification

## 1. Executive Summary & Goals

### 1.1 Objective
Build an ultra-responsive, offline-first voice assistant specifically tailored for automotive Android systems (such as BYD DiLink screens, Android Automotive, or standard Android head units). The assistant emulates the fluid interaction of Google Assistant without relying on resource-intensive Large Language Models (LLMs) or requiring continuous internet connectivity.

### 1.2 Core Mandates
- **Offline-First & Low Latency:** Full voice-to-action cycle under 300ms for local vehicle commands without cloud dependency.
- **Resource Efficient:** Lightweight CPU and RAM footprint suitable for automotive hardware.
- **Hands-Free & Safe:** Foreground service with floating overlay UI, minimal visual distraction while driving.
- **Dual Vehicle Integration:** Direct system broadcasts / intents for vehicle hardware (AC, windows, lights, apps, telemetry) and a Home Assistant WebSocket/REST bridge for smart car ecosystem integration.
- **In-App Auto Updater & Segregated APK Repository:** 
  - Dedicated distribution repository housing **only** APK release artifacts and metadata (`version.json`).
  - Zero source code committed to the distribution repository.
  - In-app "Check for Updates" UI button with in-app progress-driven APK download and package installer triggering.

---

## 2. System Architecture Overview

```
+---------------------------------------------------------------------------------+
|                                 User (Voice)                                    |
+---------------------------------------------------------------------------------+
                                      |
                                      v
+---------------------------------------------------------------------------------+
|                           Local Audio Engine (STT)                              |
|   - Vosk Streaming STT (Offline Arabic) / Whisper-Tiny (ONNX)                   |
|   - Fallback: Android SpeechRecognizer                                          |
+---------------------------------------------------------------------------------+
                                      | Normalized Text
                                      v
+---------------------------------------------------------------------------------+
|                        Offline NLU & Dialogue Engine                            |
|   - Arabic Text Normalizer (diacritics, character unification)                  |
|   - Fuzzy Matcher (Levenshtein Distance >= 80% tolerance)                       |
|   - Slot Extraction / NER (Target, Action, Value, Degree)                       |
|   - Intent Classifier (TF-IDF + Naive Bayes / Rule Matrix)                      |
|   - FSM Dialogue Manager (Multi-turn conversations & safety confirmations)      |
+---------------------------------------------------------------------------------+
                                      |
         +----------------------------+----------------------------+
         |                                                         |
         v (Vehicle / Control Intent)                              v (Info Query / Online)
+------------------------------------+    +---------------------------------------+
|     Vehicle & System Controller    |    |       Lightweight API Dispatcher      |
|  - HVAC (AC, Fan, Recirculation)   |    |  - Wolfram Alpha (Calculations)       |
|  - Windows / Sunroof Control       |    |  - DuckDuckGo Instant Answers         |
|  - Ambient & Cabin Lighting        |    |  - OpenMeteo (Live Weather Forecast)  |
|  - Media & DiLink App Launcher     |    |  - World Time API (Timezones)         |
|  - Telemetry (Battery SoC, DTC)    |    |  - NewsAPI (Quick Headlines)          |
|  - Home Assistant WS/REST Bridge   |    +---------------------------------------+
+------------------------------------+                                 |
         |                                                             |
         +----------------------------+--------------------------------+
                                      | Response Text
                                      v
+---------------------------------------------------------------------------------+
|                          Local Audio Engine (TTS)                               |
|   - Piper TTS (Fast ONNX Neural Arabic Voice)                                   |
|   - Pre-rendered Audio Cache for Common Responses                               |
|   - Fallback: Android Native TTS                                                |
+---------------------------------------------------------------------------------+
                                      |
                                      v
+---------------------------------------------------------------------------------+
|                      Speaker Output & Floating UI Feedback                      |
+---------------------------------------------------------------------------------+
```

---

## 3. Detailed Component Specifications

### 3.1 Speech Processing Pipeline (STT & TTS)
1. **Speech-to-Text (STT):**
   - **Engine:** Vosk Arabic acoustic and language model loaded on-device or Whisper-Tiny ONNX model.
   - **Streaming Mode:** Real-time chunked audio processing from Android `AudioRecord` (16kHz, 16-bit mono PCM).
   - **Silence & VAD:** WebRTC Voice Activity Detection (VAD) / RMS energy detection with 700ms end-of-speech pause threshold.
   - **Fallback:** Android native `SpeechRecognizer` (`ar-SA` / universal Arabic) if offline Vosk model assets are loading or unavailable.
2. **Text-to-Speech (TTS):**
   - **Engine:** Piper TTS running via ONNX Runtime Mobile for fast neural synthesis.
   - **Audio Caching:** Static pre-synthesized WAV/OGG buffers for frequent responses (e.g., "تم فتح النافذة", "درجة الحرارة 22", "البطارية 75 بالمئة").
   - **Fallback:** Android `android.speech.tts.TextToSpeech`.

### 3.2 Natural Language Understanding (NLU) & Dialogue FSM
1. **Arabic Text Normalization (`ArabicNormalizer`):**
   - Stripping Tashkeel (diacritics: Fatha, Damma, Kasra, Sukun, Tanween).
   - Unifying Alef variants (`إ`, `أ`, `آ` -> `ا`).
   - Unifying Yaa (`ى` -> `ي`) and Taa Marbuta (`ة` -> `ه`).
   - Dialectal normalization & verb synonym mapping (e.g., "شغل" / "افتح" / "ولع" -> `ACTION_OPEN` / `ACTION_START`).
2. **Fuzzy String Matching (`FuzzyMatcher`):**
   - Levenshtein distance calculation to tolerate pronunciation slips and noisy speech transcriptions.
   - Minimum threshold: 80% similarity against predefined vehicle action patterns.
3. **Intent Classification & Named Entity Recognition (NER):**
   - Rule-based fast matrix backed by TF-IDF + Multinomial Naive Bayes classifier.
   - Entities extracted:
     - `Target`: `WINDOW_DRIVER`, `WINDOW_PASSENGER`, `SUNROOF`, `AC_CLIMATE`, `LIGHT_AMBIENT`, `MEDIA_PLAYER`, `APP_TARGET`.
     - `Action`: `OPEN`, `CLOSE`, `INCREASE`, `DECREASE`, `SET_VALUE`, `TOGGLE`, `QUERY_STATUS`.
     - `Value`: Numeric (degrees Celsius, percentage %, volume step).
4. **Finite State Machine (FSM Dialogue Manager):**
   - States: `IDLE`, `LISTENING`, `THINKING`, `AWAITING_CONFIRMATION`, `SPEAKING`, `ERROR`.
   - Multi-turn context memory:
     - Retains last active entity (e.g., "أغلقها" refers to the previously mentioned window).
     - Critical action confirmations (e.g., "هل تريد فتح النوافذ بالكامل أثناء القيادة السريعة؟").

### 3.3 Vehicle Control & Telemetry Layer
1. **HVAC (Climate Control):**
   - Temperature adjust (absolute 18-30°C or relative ±1°C).
   - Fan speed levels (1 through 7, Auto).
   - AC On/Off, Dual zone sync, Defroster (front/rear), Air recirculation (Internal/External).
2. **Windows & Sunroof:**
   - Position control: Full open (0%), full close (100%), ventilation/crack (10-20%).
   - Targeted: Driver window, front passenger, rear left, rear right, all windows, sunroof shade, sunroof glass.
3. **Lighting:**
   - Cabin reading lights.
   - Interior Ambient lighting: brightness levels (0-100%) and color hex/names (أزرق, أحمر, أبيض, دافئ).
4. **Media & OS Application Control:**
   - System volume (`VOLUME_UP`, `VOLUME_DOWN`, `MUTE`).
   - Media transport (`PLAY`, `PAUSE`, `NEXT`, `PREVIOUS`).
   - App Launcher: Resolving Arabic and English aliases to Android package names (e.g., "خرائط", "مابس", "يوتيوب", "الإعدادات").
5. **Vehicle Telemetry Queries:**
   - Battery State of Charge (`SoC` %), estimated driving range in km.
   - Fuel level (for hybrid vehicles).
   - Tire pressure monitoring (TPMS).
   - Diagnostic Trouble Codes (DTC) alert summary.

### 3.4 Home Assistant Integration
1. **Protocol:** Local WebSocket (`/api/websocket`) with automatic reconnection and fallback to REST (`/api/services/...`).
2. **Entity Mapping:**
   - Climate: `climate.car_ac` -> `set_temperature`, `set_hvac_mode`.
   - Covers: `cover.car_windows`, `cover.car_sunroof`.
   - Lights: `light.car_ambient_lights`.
   - Sensors: `sensor.car_battery_level`, `binary_sensor.car_doors_status`.
3. **Real-time Event Subscription:** Notifying the driver via TTS if critical sensor states change (e.g., low tire pressure alert).

### 3.5 Lightweight Online Information APIs
When network connectivity is detected via `ConnectivityManager`, queries exceeding car controls route through:
1. **Wolfram Alpha API:** Calculations, unit conversions, physical constants.
2. **DuckDuckGo Instant Answer API:** Encyclopedic definitions and concise instant answers.
3. **OpenMeteo API:** Zero-key weather forecast by vehicle GPS latitude/longitude.
4. **World Time API:** Accurate timezone queries and city current times.
5. **NewsAPI:** Brief top-line news summaries.

### 3.6 Dedicated APK Repository & In-App Auto Updater

#### A. Repository Separation Architecture
- **Source Code Repository:** Holds the full Android source code, tests, and documentation.
- **Dedicated Release Repository (e.g., `byd-voice-assistant-releases`):**
  - **No source code:** Only binary deliverables and update metadata.
  - Files hosted in the repository:
    - `version.json`:
      ```json
      {
        "versionCode": 200,
        "versionName": "2.0.0",
        "apkUrl": "https://github.com/<owner>/<repo>/releases/download/v2.0.0/assistant-release.apk",
        "changelog": "إضافة دعم التحكم في الإضاءة المحيطية، وتحسين دقة التعرف الصوتي دون إنترنت",
        "minAppVersion": 100,
        "sha256": "abcdef123456..."
      }
      ```
    - Releases / Artifacts: Compiled signed/aligned APKs.

#### B. In-App Update Engine
- **Check Button:** Exposed prominently in settings (`MainActivity` & settings panel).
- **Update Flow:**
  1. Requests `version.json` via HTTPS GET.
  2. Compares remote `versionCode` against running `BuildConfig.VERSION_CODE`.
  3. If newer: Displays dialog with changelog, "تحديث الآن" (Update Now) and "لاحقاً" (Later).
  4. On confirmation: Downloads APK to app's cache directory using `HttpURLConnection` or Android `DownloadManager` with real-time percentage progress callback.
  5. Verifies SHA256 integrity.
  6. Launches package installer via `FileProvider` (`content://`) with `Intent.ACTION_VIEW` and `Intent.FLAG_GRANT_READ_URI_PERMISSION`.

---

## 4. UI/UX Design

1. **Floating Mic Widget:**
   - Draggable floating overlay bubble on top of all car screens.
   - Visual pulse rings indicating microphone listening state.
   - One-tap activation or wake phrase listener.
2. **Bottom Sheet / Floating Card:**
   - Translucent card showing transcribed text and visual confirmation of executed actions.
   - Dark mode optimized for night driving with high-contrast typography.
3. **Settings Dashboard:**
   - Home Assistant connection status (IP address, Access Token, Connection status indicator).
   - Speech engine selection (Vosk / Whisper / Native).
   - "التحقق من وجود تحديثات" button with status indicator.

---

## 5. Testing & Verification Strategy

1. **Arabic Normalizer & Fuzzy Matching Tests:**
   - 100% test coverage for Arabic spelling permutations, diacritics, and phonetic dialect variances.
2. **Intent & Slot Extraction Suite:**
   - Comprehensive test cases covering all vehicle intents (HVAC, Windows, Lights, Media, Apps, Telemetry).
3. **FSM State Transition Tests:**
   - Multi-turn conversation tests ensuring state consistency, timeout handling, and safety confirmations.
4. **Vehicle Controller Mock Suite:**
   - Simulation of Android vehicle intents and DiLink broadcast validation.
5. **In-App Updater Unit Tests:**
   - Semantic version parsing, checksum verification, and download progress reporting tests.

---

## 6. Implementation Milestones

- **Phase 1:** Core NLU, Arabic Normalizer, Levenshtein Fuzzy Matcher, and Intent Classifier.
- **Phase 2:** FSM Dialogue Manager and Vehicle Controller (AC, Windows, Lights, Media, Telemetry).
- **Phase 3:** Local Audio Engine integration (Vosk / Piper / Fallbacks) and Floating Overlay UI.
- **Phase 4:** Home Assistant WebSocket Bridge and Lightweight APIs (Wolfram, OpenMeteo, DDG).
- **Phase 5:** In-App Auto-Updater, `version.json` schema, and APK Release distribution pipeline.
