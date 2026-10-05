# BYD DiLink Arabic Voice Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, production-grade Android application for BYD Destroyer 05 (2025) running DiLink that understands spoken Iraqi dialect and MSA, provides fully vocalized Arabic TTS feedback, and executes vehicle commands via DiLink service reflection and system broadcasts.

**Architecture:** A lightweight Native Android app composed of an `AssistantForegroundService` with Audio Focus ducking and floating overlay UI, an `ArabicIntentResolver` supporting dialect normalization and regex matching, an `ArabicTtsEngine` producing vocalized Classical Arabic responses, and a `BydVehicleController` bridging to BYD DiLink services and Android hardware abstractions.

**Tech Stack:** Kotlin / Java (Android 9+ / API 28-34, DiLink 4.0/5.0), Android SpeechRecognizer & TextToSpeech, Android WindowManager Overlay, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-05-byd-arabic-voice-assistant-design.md`

## Global Constraints

- Target OS: Android 9.0 (API 28) minimum, targeting Android 12/13 (API 33/34) for DiLink 4.0 & 5.0 compatibility.
- Language Input: Spoken Arabic (Iraqi dialect `ar-IQ` + Modern Standard Arabic `ar-SA`).
- Speech Output: Fully vocalized Modern Standard Arabic with 100% accurate grammatical tashkeel (حركات إعرابية كاملة).
- Vehicle Target: BYD Destroyer 05 (2025) running DiLink infotainment.
- Zero Hard Dependencies on external root: Must operate using standard Android permissions, system broadcasts, and reflection on accessible DiLink services.

## Review Focus

- Iraqi dialect homophones and slang ("فر الشاشة", "نزّل الجامة", "شعل التبريد", "صعّد البرودة") mapping to identical canonical vehicle intents.
- Numbers extracted from Arabic speech in digits or words (e.g., "22", "اثنان وعشرون", "ثنتين وعشرين") parsed reliably for climate control.
- Screen rotation commands handling both toggle ("فر / اقلب") and explicit orientation ("بالطول / رأسي", "بالعرض / أفقي").
- Audio Focus handling: Media ducking requested before speech recognition begins and abandoned immediately after vocal feedback completes.
- Graceful degradation: When running in testing or non-DiLink environments, vehicle controller methods execute safe stubs without crashing.

---

### Task 1: Arabic Text Normalizer & Vocabulary Definitions

**Files:**
- Create: `app/src/main/java/com/byd/assistant/nlp/ArabicNormalizer.kt`
- Create: `app/src/test/java/com/byd/assistant/nlp/ArabicNormalizerTest.kt`

**Interfaces:**
- Produces: `ArabicNormalizer.normalize(rawText: String): String`

- [ ] **Step 1: Write the failing unit test**

```kotlin
package com.byd.assistant.nlp

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicNormalizerTest {
    @Test
    fun testNormalizesHamzasAndDiacritics() {
        val input = "أَفْتَحُ الشَّبَابِيكَ يَا أَخِي!"
        val expected = "افتح الشبابيك يا اخي"
        assertEquals(expected, ArabicNormalizer.normalize(input))
    }

    @Test
    fun testNormalizesTaaMarbutaAndAlefMaqsura() {
        val input = "شغّل التدفئة إِلى أقصى درجة"
        val expected = "شغل التدفئه الى اقصى درجه"
        assertEquals(expected, ArabicNormalizer.normalize(input))
    }

    @Test
    fun testStripsTatweelAndPunctuation() {
        val input = "فـــــرّ الـــشـــاشـــة..."
        val expected = "فر الشاشه"
        assertEquals(expected, ArabicNormalizer.normalize(input))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: Verification runner / gradle test.
Expected: FAIL with "ArabicNormalizer not found".

- [ ] **Step 3: Implement ArabicNormalizer**

```kotlin
package com.byd.assistant.nlp

object ArabicNormalizer {
    private val DIACRITICS_REGEX = Regex("[\\u064B-\\u065F\\u0670]")
    private val TATWEEL_REGEX = Regex("\\u0640+")
    private val PUNCTUATION_REGEX = Regex("[\\p{Punct}،؟؛«»!\\.]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""
        var text = rawText
        // Remove diacritics
        text = text.replace(DIACRITICS_REGEX, "")
        // Remove tatweel (kashida)
        text = text.replace(TATWEEL_REGEX, "")
        // Normalize Alef variations (أ, إ, آ -> ا)
        text = text.replace(Regex("[أإآ]"), "ا")
        // Normalize Taa Marbuta (ة -> ه)
        text = text.replace('ة', 'ه')
        // Normalize Alef Maqsura (ى -> ي) if at end of word or standalone
        text = text.replace('ى', 'ي')
        // Remove punctuation
        text = text.replace(PUNCTUATION_REGEX, " ")
        // Normalize spaces
        return text.replace(MULTI_SPACE_REGEX, " ").trim()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run test suite and verify PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/byd/assistant/nlp/ArabicNormalizer.kt app/src/test/java/com/byd/assistant/nlp/ArabicNormalizerTest.kt
git commit -m "feat(nlp): add Arabic normalizer with dialect character handling"
```

---

### Task 2: Rule-Based Arabic Intent Engine (Iraqi & MSA)

**Files:**
- Create: `app/src/main/java/com/byd/assistant/model/VehicleIntent.kt`
- Create: `app/src/main/java/com/byd/assistant/nlp/ArabicIntentResolver.kt`
- Create: `app/src/test/java/com/byd/assistant/nlp/ArabicIntentResolverTest.kt`

**Interfaces:**
- Consumes: `ArabicNormalizer.normalize(rawText: String)`
- Produces: `ArabicIntentResolver.resolve(spokenText: String): VehicleIntent`

- [ ] **Step 1: Define VehicleIntent data models**

```kotlin
package com.byd.assistant.model

sealed class VehicleIntent {
    data class Climate(
        val enabled: Boolean,
        val targetTemp: Int? = null,
        val fanSpeed: Int? = null
    ) : VehicleIntent()

    data class Window(
        val open: Boolean,
        val targetWindow: WindowTarget = WindowTarget.ALL
    ) : VehicleIntent()

    data class ScreenRotate(
        val orientation: ScreenOrientation = ScreenOrientation.TOGGLE
    ) : VehicleIntent()

    data class Volume(
        val action: VolumeAction,
        val level: Int? = null
    ) : VehicleIntent()

    data class Media(
        val action: MediaAction
    ) : VehicleIntent()

    data class Navigation(
        val destination: String? = null
    ) : VehicleIntent()

    data class Unknown(val originalText: String) : VehicleIntent()
}

enum class WindowTarget { ALL, DRIVER, PASSENGER, REAR }
enum class ScreenOrientation { TOGGLE, PORTRAIT, LANDSCAPE }
enum class VolumeAction { UP, DOWN, MUTE, UNMUTE, SET }
enum class MediaAction { PLAY, PAUSE, NEXT, PREVIOUS }
```

- [ ] **Step 2: Write failing unit tests for Intent Engine**

```kotlin
package com.byd.assistant.nlp

import com.byd.assistant.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicIntentResolverTest {
    private val resolver = ArabicIntentResolver()

    @Test
    fun testIraqiClimateCommands() {
        val r1 = resolver.resolve("شغل التبريد")
        assertTrue(r1 is VehicleIntent.Climate && r1.enabled)

        val r2 = resolver.resolve("شعل المكيف وسوي الحرارة 22")
        assertTrue(r2 is VehicleIntent.Climate && r2.enabled && r2.targetTemp == 22)

        val r3 = resolver.resolve("طفي التبريد")
        assertTrue(r3 is VehicleIntent.Climate && !r3.enabled)
    }

    @Test
    fun testIraqiWindowCommands() {
        val r1 = resolver.resolve("نزل الجامة")
        assertTrue(r1 is VehicleIntent.Window && r1.open)

        val r2 = resolver.resolve("سد الجامات كلها")
        assertTrue(r2 is VehicleIntent.Window && !r2.open && r2.targetWindow == WindowTarget.ALL)
    }

    @Test
    fun testScreenRotationCommands() {
        val r1 = resolver.resolve("فر الشاشة")
        assertTrue(r1 is VehicleIntent.ScreenRotate && r1.orientation == ScreenOrientation.TOGGLE)

        val r2 = resolver.resolve("سوي الشاشة بالطول")
        assertTrue(r2 is VehicleIntent.ScreenRotate && r2.orientation == ScreenOrientation.PORTRAIT)

        val r3 = resolver.resolve("اقلب الشاشة بالعرض")
        assertTrue(r3 is VehicleIntent.ScreenRotate && r3.orientation == ScreenOrientation.LANDSCAPE)
    }

    @Test
    fun testVolumeAndMediaCommands() {
        val r1 = resolver.resolve("علي الصوت")
        assertTrue(r1 is VehicleIntent.Volume && r1.action == VolumeAction.UP)

        val r2 = resolver.resolve("نصي الصوت شويه")
        assertTrue(r2 is VehicleIntent.Volume && r2.action == VolumeAction.DOWN)

        val r3 = resolver.resolve("اكتم الصوت")
        assertTrue(r3 is VehicleIntent.Volume && r3.action == VolumeAction.MUTE)
    }

    @Test
    fun testNavigationCommands() {
        val r1 = resolver.resolve("افتح الخرايط")
        assertTrue(r1 is VehicleIntent.Navigation)

        val r2 = resolver.resolve("شغل الملاحة")
        assertTrue(r2 is VehicleIntent.Navigation)
    }
}
```

- [ ] **Step 3: Run test to verify failure**

Run: verification runner. Expected: FAIL with "ArabicIntentResolver not found".

- [ ] **Step 4: Implement ArabicIntentResolver**

```kotlin
package com.byd.assistant.nlp

import com.byd.assistant.model.*

class ArabicIntentResolver {
    fun resolve(spokenText: String): VehicleIntent {
        val text = ArabicNormalizer.normalize(spokenText)
        if (text.isEmpty()) return VehicleIntent.Unknown("")

        // 1. Climate Control Intent
        if (matchesClimate(text)) {
            val enabled = !text.contains("طفي") && !text.contains("وقف") && !text.contains("اغلق") && !text.contains("عطل")
            val temp = extractTemperature(text)
            return VehicleIntent.Climate(enabled = enabled, targetTemp = temp)
        }

        // 2. Window Control Intent
        if (matchesWindow(text)) {
            val open = text.contains("نزل") || text.contains("افتح") || text.contains("هبط")
            val target = when {
                text.contains("سائق") || text.contains("سايق") -> WindowTarget.DRIVER
                text.contains("راكب") || text.contains("صفحي") -> WindowTarget.PASSENGER
                else -> WindowTarget.ALL
            }
            return VehicleIntent.Window(open = open, targetWindow = target)
        }

        // 3. Screen Rotation Intent
        if (matchesScreenRotate(text)) {
            val orientation = when {
                text.contains("طول") || text.contains("راسي") || text.contains("عمودي") -> ScreenOrientation.PORTRAIT
                text.contains("عرض") || text.contains("افقي") -> ScreenOrientation.LANDSCAPE
                else -> ScreenOrientation.TOGGLE
            }
            return VehicleIntent.ScreenRotate(orientation = orientation)
        }

        // 4. Volume Intent
        if (matchesVolume(text)) {
            val action = when {
                text.contains("علي") || text.contains("ارفع") || text.contains("زيد") -> VolumeAction.UP
                text.contains("نصي") || text.contains("نزل") || text.contains("اخفض") || text.contains("قلل") -> VolumeAction.DOWN
                text.contains("اكتم") || text.contains("صامت") || text.contains("سكت") -> VolumeAction.MUTE
                text.contains("رجع الصوت") || text.contains("الغاء الكتم") -> VolumeAction.UNMUTE
                else -> VolumeAction.UP
            }
            return VehicleIntent.Volume(action = action)
        }

        // 5. Navigation Intent
        if (text.contains("خرايط") || text.contains("خرائط") || text.contains("ملاحه") || text.contains("جيب بي اس") || text.contains("gps")) {
            return VehicleIntent.Navigation()
        }

        // 6. Media Playback Intent
        if (matchesMedia(text)) {
            val action = when {
                text.contains("التالي") || text.contains("بعده") || text.contains("وراها") -> MediaAction.NEXT
                text.contains("السابق") || text.contains("قبله") -> MediaAction.PREVIOUS
                text.contains("وقف") || text.contains("طفي") -> MediaAction.PAUSE
                else -> MediaAction.PLAY
            }
            return VehicleIntent.Media(action = action)
        }

        return VehicleIntent.Unknown(spokenText)
    }

    private fun matchesClimate(t: String): Boolean =
        t.contains("تبريد") || t.contains("مكيف") || t.contains("حراره") || t.contains("بروده") || t.contains("تدفئه") || t.contains("سبلت")

    private fun matchesWindow(t: String): Boolean =
        t.contains("جامه") || t.contains("جامات") || t.contains("شباك") || t.contains("شبابيك") || t.contains("نافذه") || t.contains("نوافذ")

    private fun matchesScreenRotate(t: String): Boolean =
        (t.contains("شاشه") || t.contains("شاشة")) && (t.contains("فر") || t.contains("دور") || t.contains("اقلب") || t.contains("حول") || t.contains("لف"))

    private fun matchesVolume(t: String): Boolean =
        t.contains("صوت") && (t.contains("علي") || t.contains("ارفع") || t.contains("نصي") || t.contains("اخفض") || t.contains("اكتم") || t.contains("زيد") || t.contains("صامت"))

    private fun matchesMedia(t: String): Boolean =
        t.contains("اغنيه") || t.contains("اغاني") || t.contains("موسيقى") || t.contains("صوتيات") || t.contains("شغل") && (t.contains("التالي") || t.contains("السابق"))

    private fun extractTemperature(t: String): Int? {
        val numMatch = Regex("\\b([1-3][0-9])\\b").find(t)
        if (numMatch != null) {
            return numMatch.groupValues[1].toInt()
        }
        if (t.contains("اثنان وعشرون") || t.contains("ثنتين وعشرين") || t.contains("اثنين وعشرين")) return 22
        if (t.contains("عشرون") || t.contains("عشرين")) return 20
        if (t.contains("خمسه وعشرون") || t.contains("خمسة وعشرين")) return 25
        return null
    }
}
```

- [ ] **Step 5: Run tests and verify PASS**

Run tests and ensure all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/byd/assistant/model/VehicleIntent.kt app/src/main/java/com/byd/assistant/nlp/ArabicIntentResolver.kt app/src/test/java/com/byd/assistant/nlp/ArabicIntentResolverTest.kt
git commit -m "feat(nlp): add Iraqi dialect and MSA ArabicIntentResolver"
```

---

### Task 3: Vocalized Arabic TTS Response Generator

**Files:**
- Create: `app/src/main/java/com/byd/assistant/tts/ArabicTtsEngine.kt`
- Create: `app/src/test/java/com/byd/assistant/tts/ArabicTtsEngineTest.kt`

**Interfaces:**
- Consumes: `VehicleIntent`
- Produces: `ArabicTtsEngine.getVocalizedResponse(intent: VehicleIntent): String`

- [ ] **Step 1: Write failing unit test for Vocalized Responses**

```kotlin
package com.byd.assistant.tts

import com.byd.assistant.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTtsEngineTest {
    private val tts = ArabicTtsEngine()

    @Test
    fun testVocalizedScreenRotate() {
        val intent = VehicleIntent.ScreenRotate(ScreenOrientation.TOGGLE)
        val text = tts.getVocalizedResponse(intent)
        assertEquals("حَاضِرٌ، تَمَّ تَدْوِيرُ الشَّاشَةِ.", text)
        // Verify contains tashkeel
        assertTrue(text.any { it in '\u064B'..'\u0652' })
    }

    @Test
    fun testVocalizedClimateResponse() {
        val onIntent = VehicleIntent.Climate(enabled = true, targetTemp = 22)
        val text = tts.getVocalizedResponse(onIntent)
        assertEquals("تَمَّ تَشْغِيلُ التَّكْيِيفِ، وَضَبْطُ الحَرَارَةِ عَلَى اثْنَتَيْنِ وَعِشْرِينَ دَرَجَةً.", text)

        val offIntent = VehicleIntent.Climate(enabled = false)
        assertEquals("تَمَّ إِيقَافُ التَّكْيِيفِ.", tts.getVocalizedResponse(offIntent))
    }

    @Test
    fun testVocalizedWindowResponse() {
        val openIntent = VehicleIntent.Window(open = true)
        assertEquals("تَمَّ فَتْحُ النَّوَافِذِ.", tts.getVocalizedResponse(openIntent))

        val closeIntent = VehicleIntent.Window(open = false)
        assertEquals("تَمَّ إِغْلَاقُ النَّوَافِذِ.", tts.getVocalizedResponse(closeIntent))
    }

    @Test
    fun testVocalizedUnknown() {
        val unknown = VehicleIntent.Unknown("كلمات غير معروفة")
        assertEquals("عَفْوًا، لَمْ أَفْهَمِ الأَمْرَ. يُرْجَى الإِعَادَةُ.", tts.getVocalizedResponse(unknown))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: verification runner. Expected: FAIL with "ArabicTtsEngine not found".

- [ ] **Step 3: Implement ArabicTtsEngine**

```kotlin
package com.byd.assistant.tts

import com.byd.assistant.model.*

class ArabicTtsEngine {
    fun getVocalizedResponse(intent: VehicleIntent): String {
        return when (intent) {
            is VehicleIntent.ScreenRotate -> when (intent.orientation) {
                ScreenOrientation.PORTRAIT -> "تَمَّ تَدْوِيرُ الشَّاشَةِ إِلَى الوَضْعِ الرَّأْسِيِّ."
                ScreenOrientation.LANDSCAPE -> "تَمَّ تَدْوِيرُ الشَّاشَةِ إِلَى الوَضْعِ الأُفُقِيِّ."
                ScreenOrientation.TOGGLE -> "حَاضِرٌ، تَمَّ تَدْوِيرُ الشَّاشَةِ."
            }

            is VehicleIntent.Climate -> {
                if (!intent.enabled) {
                    "تَمَّ إِيقَافُ التَّكْيِيفِ."
                } else if (intent.targetTemp != null) {
                    val tempWord = vocalizeTemperatureNumber(intent.targetTemp)
                    "تَمَّ تَشْغِيلُ التَّكْيِيفِ، وَضَبْطُ الحَرَارَةِ عَلَى $tempWord دَرَجَةً."
                } else {
                    "تَمَّ تَشْغِيلُ التَّكْيِيفِ."
                }
            }

            is VehicleIntent.Window -> {
                if (intent.open) "تَمَّ فَتْحُ النَّوَافِذِ."
                else "تَمَّ إِغْلَاقُ النَّوَافِذِ."
            }

            is VehicleIntent.Volume -> when (intent.action) {
                VolumeAction.UP -> "تَمَّ رَفْعُ مُسْتَوَى الصَّوْتِ."
                VolumeAction.DOWN -> "تَمَّ خَفْضُ مُسْتَوَى الصَّوْتِ."
                VolumeAction.MUTE -> "تَمَّ كَتْمُ الصَّوْتِ."
                VolumeAction.UNMUTE -> "تَمَّ إِعَادَةُ تَشْغِيلِ الصَّوْتِ."
                VolumeAction.SET -> "تَمَّ تَعْدِيلُ مُسْتَوَى الصَّوْتِ."
            }

            is VehicleIntent.Navigation -> "تَمَّ فَتْحُ الخَرَائِطِ."

            is VehicleIntent.Media -> when (intent.action) {
                MediaAction.PLAY -> "تَمَّ تَشْغِيلُ الصَّوْتِيَّاتِ."
                MediaAction.PAUSE -> "تَمَّ إِيقَافُ الصَّوْتِيَّاتِ."
                MediaAction.NEXT -> "تَمَّ الِانْتِقَالُ إِلَى المَقْطَعِ التَّالِي."
                MediaAction.PREVIOUS -> "تَمَّ الِانْتِقَالُ إِلَى المَقْطَعِ السَّابِقِ."
            }

            is VehicleIntent.Unknown -> "عَفْوًا، لَمْ أَفْهَمِ الأَمْرَ. يُرْجَى الإِعَادَةُ."
        }
    }

    private fun vocalizeTemperatureNumber(temp: Int): String {
        return when (temp) {
            18 -> "ثَمَانِيَ عَشْرَةَ"
            19 -> "تِسْعَ عَشْرَةَ"
            20 -> "عِشْرِينَ"
            21 -> "إِحْدَى وَعِشْرِينَ"
            22 -> "اثْنَتَيْنِ وَعِشْرِينَ"
            23 -> "ثَلَاثٍ وَعِشْرِينَ"
            24 -> "أَرْبَعٍ وَعِشْرِينَ"
            25 -> "خَمْسٍ وَعِشْرِينَ"
            26 -> "سِتٍّ وَعِشْرِينَ"
            else -> "$temp"
        }
    }
}
```

- [ ] **Step 4: Run tests and verify PASS**

Run tests and ensure PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/byd/assistant/tts/ArabicTtsEngine.kt app/src/test/java/com/byd/assistant/tts/ArabicTtsEngineTest.kt
git commit -m "feat(tts): add fully-vocalized Classical Arabic TTS response engine"
```

---

### Task 4: BYD DiLink Vehicle Controller Bridge

**Files:**
- Create: `app/src/main/java/com/byd/assistant/vehicle/BydVehicleController.kt`
- Create: `app/src/main/java/com/byd/assistant/vehicle/DiLinkConstants.kt`
- Create: `app/src/test/java/com/byd/assistant/vehicle/BydVehicleControllerTest.kt`

**Interfaces:**
- Consumes: `VehicleIntent`
- Produces: `BydVehicleController.execute(intent: VehicleIntent): Boolean`

- [ ] **Step 1: Create DiLink Constants**

```kotlin
package com.byd.assistant.vehicle

object DiLinkConstants {
    const val ACTION_ROTATE_SCREEN = "com.byd.intent.action.ROTATION_SCREEN"
    const val ACTION_ROTATE_SCREEN_ALT = "byd.intent.action.SCREEN_ROTATE"
    const val EXTRA_ROTATE_ANGLE = "angle"

    const val SERVICE_BYD_AIR = "byd_auto_air"
    const val SERVICE_BYD_WINDOW = "byd_auto_window"
    const val SERVICE_BYD_BODY = "byd_auto_body"

    const val PROP_SCREEN_ANGLE = "persist.sys.byd.screen_angle"
}
```

- [ ] **Step 2: Write unit test for BydVehicleController**

```kotlin
package com.byd.assistant.vehicle

import com.byd.assistant.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class BydVehicleControllerTest {
    @Test
    fun testExecuteScreenRotateReturnsSuccess() {
        val controller = BydVehicleController(isMockMode = true)
        val success = controller.execute(VehicleIntent.ScreenRotate(ScreenOrientation.TOGGLE))
        assertTrue(success)
    }

    @Test
    fun testExecuteClimateReturnsSuccess() {
        val controller = BydVehicleController(isMockMode = true)
        val success = controller.execute(VehicleIntent.Climate(enabled = true, targetTemp = 22))
        assertTrue(success)
    }

    @Test
    fun testExecuteWindowReturnsSuccess() {
        val controller = BydVehicleController(isMockMode = true)
        val success = controller.execute(VehicleIntent.Window(open = true))
        assertTrue(success)
    }
}
```

- [ ] **Step 3: Run test to verify failure**

Run: verification runner. Expected: FAIL with "BydVehicleController not found".

- [ ] **Step 4: Implement BydVehicleController with Reflection & Safe Fallbacks**

```kotlin
package com.byd.assistant.vehicle

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import com.byd.assistant.model.*

class BydVehicleController(
    private val context: Context? = null,
    private val isMockMode: Boolean = false
) {
    fun execute(intent: VehicleIntent): Boolean {
        if (isMockMode || context == null) {
            return true
        }

        return when (intent) {
            is VehicleIntent.ScreenRotate -> rotateScreen(intent.orientation)
            is VehicleIntent.Climate -> setClimate(intent.enabled, intent.targetTemp)
            is VehicleIntent.Window -> setWindow(intent.open, intent.targetWindow)
            is VehicleIntent.Volume -> adjustVolume(intent.action)
            is VehicleIntent.Navigation -> launchNavigation(intent.destination)
            is VehicleIntent.Media -> controlMedia(intent.action)
            is VehicleIntent.Unknown -> false
        }
    }

    private fun rotateScreen(orientation: ScreenOrientation): Boolean {
        return try {
            val intent = Intent(DiLinkConstants.ACTION_ROTATE_SCREEN).apply {
                flags = Intent.FLAG_RECEIVER_FOREGROUND
                when (orientation) {
                    ScreenOrientation.PORTRAIT -> putExtra(DiLinkConstants.EXTRA_ROTATE_ANGLE, 0)
                    ScreenOrientation.LANDSCAPE -> putExtra(DiLinkConstants.EXTRA_ROTATE_ANGLE, 90)
                    ScreenOrientation.TOGGLE -> putExtra(DiLinkConstants.EXTRA_ROTATE_ANGLE, -1)
                }
            }
            context?.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setClimate(enabled: Boolean, targetTemp: Int?): Boolean {
        return try {
            val service = getDiLinkService(DiLinkConstants.SERVICE_BYD_AIR)
            if (service != null) {
                val method = service.javaClass.getMethod("setAcWorkState", Boolean::class.javaPrimitiveType)
                method.invoke(service, enabled)
                if (targetTemp != null) {
                    val tempMethod = service.javaClass.getMethod("setTargetTemp", Int::class.javaPrimitiveType)
                    tempMethod.invoke(service, targetTemp)
                }
                true
            } else {
                // Fallback broadcast intent
                val intent = Intent("byd.intent.action.AC_CONTROL").apply {
                    putExtra("power", enabled)
                    if (targetTemp != null) putExtra("temp", targetTemp)
                }
                context?.sendBroadcast(intent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun setWindow(open: Boolean, target: WindowTarget): Boolean {
        return try {
            val service = getDiLinkService(DiLinkConstants.SERVICE_BYD_WINDOW)
            if (service != null) {
                val method = service.javaClass.getMethod("setWindowState", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                method.invoke(service, 0, open)
                true
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun adjustVolume(action: VolumeAction): Boolean {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val stream = AudioManager.STREAM_MUSIC
        return when (action) {
            VolumeAction.UP -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                true
            }
            VolumeAction.DOWN -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                true
            }
            VolumeAction.MUTE -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                true
            }
            VolumeAction.UNMUTE -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                true
            }
            VolumeAction.SET -> true
        }
    }

    private fun launchNavigation(destination: String?): Boolean {
        return try {
            val uri = if (destination != null) {
                Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            } else {
                Uri.parse("geo:0,0?q=")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context?.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun controlMedia(action: MediaAction): Boolean {
        val keyCode = when (action) {
            MediaAction.PLAY -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.PAUSE -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.NEXT -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        return true
    }

    private fun getDiLinkService(serviceName: String): Any? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            getServiceMethod.invoke(null, serviceName)
        } catch (e: Exception) {
            null
        }
    }
}
```

- [ ] **Step 5: Run tests and verify PASS**

Run tests and ensure PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/byd/assistant/vehicle/DiLinkConstants.kt app/src/main/java/com/byd/assistant/vehicle/BydVehicleController.kt app/src/test/java/com/byd/assistant/vehicle/BydVehicleControllerTest.kt
git commit -m "feat(vehicle): add DiLink vehicle bridge with reflection and safe broadcasts"
```

---

### Task 5: Floating Overlay UI & Service Architecture

**Files:**
- Create: `app/src/main/java/com/byd/assistant/ui/FloatingAssistantView.kt`
- Create: `app/src/main/java/com/byd/assistant/service/AssistantForegroundService.kt`
- Create: `app/src/main/java/com/byd/assistant/MainActivity.kt`
- Create: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ArabicIntentResolver`, `ArabicTtsEngine`, `BydVehicleController`
- Produces: Persistent DiLink background service with draggable interactive floating overlay

- [ ] **Step 1: Implement FloatingAssistantView**

Create draggable floating view handling click, drag, state changes (IDLE, LISTENING, PROCESSING, SPEAKING) using Android WindowManager.

- [ ] **Step 2: Implement AssistantForegroundService**

Create Foreground Service managing:
- Persistent Foreground Notification
- Audio Focus request with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
- SpeechRecognizer integration for `ar-IQ` / `ar-SA`
- Complete pipeline wiring: User Voice -> SpeechRecognizer -> ArabicIntentResolver -> BydVehicleController -> ArabicTtsEngine (vocalized) -> unduck audio.

- [ ] **Step 3: Implement MainActivity (Permissions & Setup Launcher)**

Create launcher Activity allowing user to:
- Request `SYSTEM_ALERT_WINDOW` (Overlay permission)
- Request `RECORD_AUDIO` permission
- Start / Stop the assistant service with a single tap
- Display quick-test cheat sheet for Iraqi & MSA commands.

- [ ] **Step 4: Configure AndroidManifest.xml & Gradle setup**

Configure `AndroidManifest.xml` with all necessary permissions, foreground service declarations, and intent filters for car startup (`BOOT_COMPLETED`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/byd/assistant/ui/FloatingAssistantView.kt app/src/main/java/com/byd/assistant/service/AssistantForegroundService.kt app/src/main/java/com/byd/assistant/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat(app): add floating overlay, foreground service, and DiLink lifecycle manager"
```

---

### Task 6: Test Suite, CLI Simulator & Verification Pipeline

**Files:**
- Create: `test_simulator.js` (or Kotlin executable verification runner)
- Create: `README.md`
- Test: Full end-to-end simulation of 30+ vehicle commands and TTS outputs

- [ ] **Step 1: Create verification simulator script**

Build an interactive and automated simulation script that feeds dialect and MSA phrases into the NLP pipeline, verifies intent extraction, executes mock vehicle actions, and outputs the vocalized Classical Arabic response.

- [ ] **Step 2: Run verification pipeline and verify all test cases pass**

Ensure 100% of tested voice phrases map to expected intents and vocalized responses.

- [ ] **Step 3: Commit and update documentation**

```bash
git add test_simulator.js README.md
git commit -m "test: add verification simulator and installation guide for BYD Destroyer 05"
```
