package com.byd.assistant.tts;

import com.byd.assistant.model.*;

public class ArabicTtsEngineTest {
    public static void main(String[] args) {
        ArabicTtsEngine engine = new ArabicTtsEngine();

        testVocalizedScreenRotate(engine);
        testVocalizedClimate(engine);
        testVocalizedWindow(engine);
        testVocalizedVolumeAndMedia(engine);
        testVocalizedNavigation(engine);
        testVocalizedUnknown(engine);

        System.out.println("ALL_TTS_TESTS_PASSED");
    }

    private static void assertHasTashkeel(String text) {
        boolean hasTashkeel = false;
        for (char c : text.toCharArray()) {
            if (c >= '\u064B' && c <= '\u0652') { // Fathah, Dammah, Kasrah, Sukun, Shaddah, Tanween
                hasTashkeel = true;
                break;
            }
        }
        if (!hasTashkeel) {
            throw new AssertionError("Text lacks tashkeel: " + text);
        }
    }

    private static void testVocalizedScreenRotate(ArabicTtsEngine engine) {
        String resToggle = engine.getVocalizedResponse(new VehicleIntent.ScreenRotate(ScreenOrientation.TOGGLE));
        if (!resToggle.equals("حَاضِرٌ، تَمَّ تَدْوِيرُ الشَّاشَةِ.")) {
            throw new AssertionError("Failed ScreenRotate TOGGLE: " + resToggle);
        }
        assertHasTashkeel(resToggle);

        String resPortrait = engine.getVocalizedResponse(new VehicleIntent.ScreenRotate(ScreenOrientation.PORTRAIT));
        if (!resPortrait.equals("تَمَّ تَدْوِيرُ الشَّاشَةِ إِلَى الوَضْعِ الرَّأْسِيِّ.")) {
            throw new AssertionError("Failed ScreenRotate PORTRAIT: " + resPortrait);
        }
        assertHasTashkeel(resPortrait);
    }

    private static void testVocalizedClimate(ArabicTtsEngine engine) {
        String resOn = engine.getVocalizedResponse(new VehicleIntent.Climate(true, 22));
        if (!resOn.equals("تَمَّ تَشْغِيلُ التَّكْيِيفِ، وَضَبْطُ الحَرَارَةِ عَلَى اثْنَتَيْنِ وَعِشْرِينَ دَرَجَةً.")) {
            throw new AssertionError("Failed Climate ON 22: " + resOn);
        }
        assertHasTashkeel(resOn);

        String resOff = engine.getVocalizedResponse(new VehicleIntent.Climate(false));
        if (!resOff.equals("تَمَّ إِيقَافُ التَّكْيِيفِ.")) {
            throw new AssertionError("Failed Climate OFF: " + resOff);
        }
        assertHasTashkeel(resOff);
    }

    private static void testVocalizedWindow(ArabicTtsEngine engine) {
        String resOpen = engine.getVocalizedResponse(new VehicleIntent.Window(true));
        if (!resOpen.equals("تَمَّ فَتْحُ النَّوَافِذِ.")) {
            throw new AssertionError("Failed Window OPEN: " + resOpen);
        }
        assertHasTashkeel(resOpen);

        String resClose = engine.getVocalizedResponse(new VehicleIntent.Window(false));
        if (!resClose.equals("تَمَّ إِغْلَاقُ النَّوَافِذِ.")) {
            throw new AssertionError("Failed Window CLOSE: " + resClose);
        }
        assertHasTashkeel(resClose);
    }

    private static void testVocalizedVolumeAndMedia(ArabicTtsEngine engine) {
        String resVolUp = engine.getVocalizedResponse(new VehicleIntent.Volume(VolumeAction.UP));
        if (!resVolUp.equals("تَمَّ رَفْعُ مُسْتَوَى الصَّوْتِ.")) {
            throw new AssertionError("Failed Volume UP: " + resVolUp);
        }
        assertHasTashkeel(resVolUp);

        String resMediaNext = engine.getVocalizedResponse(new VehicleIntent.Media(MediaAction.NEXT));
        if (!resMediaNext.equals("تَمَّ الِانْتِقَالُ إِلَى المَقْطَعِ التَّالِي.")) {
            throw new AssertionError("Failed Media NEXT: " + resMediaNext);
        }
        assertHasTashkeel(resMediaNext);
    }

    private static void testVocalizedNavigation(ArabicTtsEngine engine) {
        String resNav = engine.getVocalizedResponse(new VehicleIntent.Navigation());
        if (!resNav.equals("تَمَّ فَتْحُ الخَرَائِطِ.")) {
            throw new AssertionError("Failed Navigation: " + resNav);
        }
        assertHasTashkeel(resNav);
    }

    private static void testVocalizedUnknown(ArabicTtsEngine engine) {
        String resUnknown = engine.getVocalizedResponse(new VehicleIntent.Unknown("???"));
        if (!resUnknown.equals("عَفْوًا، لَمْ أَفْهَمِ الأَمْرَ. يُرْجَى الإِعَادَةُ.")) {
            throw new AssertionError("Failed Unknown: " + resUnknown);
        }
        assertHasTashkeel(resUnknown);
    }
}
