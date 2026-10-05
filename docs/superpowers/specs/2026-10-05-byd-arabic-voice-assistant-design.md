# Design Specification: BYD DiLink Arabic Voice Assistant (المساعد الصوتي العربي لسيارة BYD)

**Date:** 2026-10-05  
**Target Vehicle:** BYD Destroyer 05 (2025) / BYD DiLink (Android-based Infotainment)  
**Author:** opencode & antigravity  

---

## 1. Executive Summary & Goals

This project designs and implements an Android-native Arabic Voice Assistant application (`BYD Arabic Assistant`) tailored for BYD DiLink infotainment systems (specifically verified for the BYD Destroyer 05 / 2025 model). 

### Primary Goals
1. **Dialect & Language Understanding:** Seamlessly understand both Iraqi spoken dialect (اللهجة العراقية) and Modern Standard Arabic (الفصحى) for car command execution.
2. **Grammatically Correct Vocalized Output:** Provide speech feedback (TTS) using fully vocalized Classical/Modern Standard Arabic (فصحى مشكولة بالكامل) to ensure crisp, error-free acoustic pronunciation.
3. **Vehicle Control Integration:** Control core vehicle and infotainment functions (Climate Control, Windows, Screen Rotation, Volume, Navigation, and Media) utilizing reflection on BYD DiLink internal services inspired by open-source community benchmarks (`wheregoes/byd-apps` and `AndyShaman/BYDMate`) with safe system fallbacks.
4. **Persistent Overlay User Experience:** Provide a non-intrusive floating microphone widget (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`) that remains accessible over maps, media players, and system menus.
5. **Future-Proof Extensibility:** Decouple intent resolution into an extensible interface (`IntentResolver`), allowing direct replacement or augmentation with an LLM (conversational AI) in future phases without rewriting vehicle drivers or the audio pipeline.

---

## 2. Architecture Overview

The system is constructed as a modular Native Android application (Kotlin) divided into five core layers:

```
┌─────────────────────────────────────────────────────────────┐
│                       Floating UI                           │
│  (FloatingMicButton + SoundWaveform + Dynamic Status Badge) │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                AssistantForegroundService                   │
│      (Lifecycle, Audio Focus / Ducking, State Machine)       │
└───────┬──────────────────────┬──────────────────────┬───────┘
        │                      │                      │
┌───────▼────────┐    ┌────────▼────────┐    ┌────────▼───────┐
│ SpeechManager  │    │  IntentEngine   │    │ ArabicTtsEngine│
│ (STT / Record) │    │(Regex / Mapping)│    │(Vocalized MSA) │
└────────────────┘    └────────┬────────┘    └────────────────┘
                               │
                      ┌────────▼──────────────┐
                      │ BydVehicleController  │
                      │  (DiLink Reflection & │
                      │   Android Hardware)   │
                      └───────────────────────┘
```

---

## 3. Detailed Component Specifications

### 3.1. Floating UI & Lifecycle (`FloatingAssistantView` & `AssistantForegroundService`)
* **Foreground Service:** Runs with a persistent notification to prevent Android's low-memory killer (OOM) from destroying the assistant during heavy multitasking (e.g., GPS Navigation + Video/Music).
* **Audio Focus Management:** Requests transient audio focus ducking (`AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) when listening begins, reducing media volume automatically and restoring it after response completion.
* **Floating Widget:**
  * Displays a draggable floating button on the screen edge.
  * Tapping triggers voice listening immediately.
  * Animated pulsing ring/waveform indicates active listening and command processing.

### 3.2. Speech Input Pipeline (`SpeechManager`)
* Leverages Android's `SpeechRecognizer` configured for Arabic (`ar-IQ` primary with `ar-SA` fallback).
* Implements robust error handling (silence timeouts, background road noise suppression).

### 3.3. Arabic Intent Resolution (`ArabicIntentResolver`)
* **Normalization Engine:** Strips diacritics, punctuation, elongation (tatweel), and normalizes Arabic characters (`أ/إ/آ -> ا`, `ة -> ه`, `ى -> ي`).
* **Multi-Dialect Matching Table:**
  * **Climate Control (`INTENT_AC`):**
    * Dialect: *"شغل التبريد"*, *"شعل المكيف"*, *"برد السيارة"*, *"طفي التبريد"*, *"سوي التبريد 22"*, *"نزل الحرارة"*
    * MSA: *"شغل المكيف"*, *"أوقف التكييف"*, *"اضبط درجة الحرارة على..."*
  * **Windows & Glass (`INTENT_WINDOW`):**
    * Dialect: *"نزل الجامة"*, *"صعد الجامة"*, *"افتح الشبابيك"*, *"سد الجامات"*
    * MSA: *"افتح النوافذ"*, *"أغلق النوافذ"*
  * **Screen Rotation (`INTENT_ROTATE_SCREEN`):**
    * Dialect: *"فر الشاشة"*, *"دور الشاشة"*, *"اقلب الشاشة"*, *"سوي الشاشة بالطول / بالعرض"*
    * MSA: *"أدر الشاشة"*, *"حول الشاشة إلى الوضع الرأسي / الأفقي"*
  * **Media & Audio (`INTENT_VOLUME` / `INTENT_MEDIA`):**
    * Dialect: *"علي الصوت"*, *"نصي الصوت"*, *"اكتم الصوت"*
    * MSA: *"ارفع مستوى الصوت"*, *"اخفض الصوت"*, *"كتم الصوت"*
  * **Navigation (`INTENT_NAV`):**
    * Dialect/MSA: *"افتح الخرايط"*, *"افتح الخرائط"*, *"شغل الملاحة"*

### 3.4. Vocalized TTS Response Engine (`ArabicTtsEngine`)
To guarantee accurate Arabic phonetics without robotic pronunciation errors, all generated spoken feedback strings use exact vocalization (تشكيل كامل):
* Screen rotation: `«حَاضِرٌ، تَمَّ تَدْوِيرُ الشَّاشَةِ.»`
* AC activated: `«تَمَّ تَشْغِيلُ التَّكْيِيفِ.»`
* AC deactivated: `«تَمَّ إِيقَافُ التَّكْيِيفِ.»`
* AC temperature: `«تَمَّ ضَبْطُ دَرَجَةِ الحَرَارَةِ عَلَى {temp} دَرَجَةٍ مِئَوِيَّةٍ.»`
* Windows opened: `«تَمَّ فَتْحُ النَّوَافِذِ.»`
* Windows closed: `«تَمَّ إِغْلَاقُ النَّوَافِذِ.»`
* Volume adjusted: `«تَمَّ تَعْدِيلُ مُسْتَوَى الصَّوْتِ.»`
* Navigation opened: `«تَمَّ فَتْحُ الخَرَائِطِ.»`
* Unknown command: `«عَفْوًا، لَمْ أَفْهَمِ الأَمْرَ. يُرْجَى الإِعَادَةُ.»`

### 3.5. BYD Vehicle Control Bridge (`BydVehicleController`)
Synthesizing reverse-engineered methods from `wheregoes/byd-apps` and `AndyShaman/BYDMate`:
1. **Screen Rotation Mechanism:**
   * DiLink Broadcast action: `com.byd.intent.action.ROTATION_SCREEN` or `byd.intent.action.SCREEN_ROTATE`.
   * Secondary reflection: `android.os.SystemProperties.set("persist.sys.byd.screen_angle", angle)`.
2. **HVAC & Windows Control:**
   * Uses Java reflection on `android.os.ServiceManager.getService("byd_auto_air")` and `getService("byd_auto_window")`.
   * Dispatches command parameters (`setAcWorkState(true)`, `setTargetTemp(temp)`).
   * Safe Fallback: If vendor API is blocked on a locked firmware, routes command through a localized notification or accessible UI tap intent without crashing the app.
3. **Volume and Media:**
   * Directly drives Android `AudioManager.adjustStreamVolume` and `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`.

---

## 4. Security, Permissions, and Deployment

### Required Android Permissions
* `android.permission.SYSTEM_ALERT_WINDOW` (for floating overlay over DiLink launcher)
* `android.permission.RECORD_AUDIO` (microphone access)
* `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MICROPHONE`
* `android.permission.MODIFY_AUDIO_SETTINGS` (volume & ducking)
* `android.permission.ACCESS_NETWORK_STATE` & `INTERNET` (speech recognition service)

### Installation on BYD Destroyer 05 (2025)
* Target architecture: `arm64-v8a` / `armeabi-v7a`.
* Installation vector: Sideload APK via USB drive into DiLink third-party application installer or via wireless ADB.

---

## 5. Verification & Testing Strategy
* **Unit Tests for Intent Recognition:** Test 50+ variations of Iraqi and MSA expressions against expected `IntentResult` actions.
* **Normalization Tests:** Verify correct handling of hamzas, diacritics, and noise words.
* **Action Mocking Tests:** Verify `BydVehicleController` translates intents into the correct system broadcasts and service calls.
* **TTS String Validation:** Ensure every response contains valid Arabic harakat/tashkeel.
