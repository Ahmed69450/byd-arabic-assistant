package com.byd.assistant.nlp;

import com.byd.assistant.model.*;

public class ArabicIntentResolverTest {
    public static void main(String[] args) {
        ArabicIntentResolver resolver = new ArabicIntentResolver();

        testClimateCommands(resolver);
        testWindowCommands(resolver);
        testScreenRotationCommands(resolver);
        testVolumeCommands(resolver);
        testNavigationCommands(resolver);
        testMediaCommands(resolver);
        testOpenAppCommands(resolver);
        testUnknownCommands(resolver);

        System.out.println("ALL_INTENT_TESTS_PASSED");
    }

    private static void testClimateCommands(ArabicIntentResolver resolver) {
        // Iraqi & MSA phrases
        VehicleIntent r1 = resolver.resolve("شغل التبريد");
        if (!(r1 instanceof VehicleIntent.Climate && ((VehicleIntent.Climate) r1).enabled)) {
            throw new AssertionError("Failed: شغل التبريد -> " + r1);
        }

        VehicleIntent r2 = resolver.resolve("شعل المكيف وسوي الحرارة 22");
        if (!(r2 instanceof VehicleIntent.Climate)) {
            throw new AssertionError("Failed: شعل المكيف -> " + r2);
        }
        VehicleIntent.Climate c2 = (VehicleIntent.Climate) r2;
        if (!c2.enabled || c2.targetTemp == null || c2.targetTemp != 22) {
            throw new AssertionError("Failed temp parsing: expected 22, got " + c2.targetTemp);
        }

        VehicleIntent r3 = resolver.resolve("طفي التبريد فدوه");
        if (!(r3 instanceof VehicleIntent.Climate && !((VehicleIntent.Climate) r3).enabled)) {
            throw new AssertionError("Failed: طفي التبريد -> " + r3);
        }
    }

    private static void testWindowCommands(ArabicIntentResolver resolver) {
        VehicleIntent r1 = resolver.resolve("نزل الجامة");
        if (!(r1 instanceof VehicleIntent.Window && ((VehicleIntent.Window) r1).open)) {
            throw new AssertionError("Failed: نزل الجامة -> " + r1);
        }

        VehicleIntent r2 = resolver.resolve("سد الجامات كلها");
        if (!(r2 instanceof VehicleIntent.Window && !((VehicleIntent.Window) r2).open)) {
            throw new AssertionError("Failed: سد الجامات كلها -> " + r2);
        }

        VehicleIntent r3 = resolver.resolve("صعد الجامة مال السايق");
        if (!(r3 instanceof VehicleIntent.Window && !((VehicleIntent.Window) r3).open && ((VehicleIntent.Window) r3).targetWindow == WindowTarget.DRIVER)) {
            throw new AssertionError("Failed: صعد الجامة مال السايق -> " + r3);
        }
    }

    private static void testScreenRotationCommands(ArabicIntentResolver resolver) {
        VehicleIntent r1 = resolver.resolve("فر الشاشة");
        if (!(r1 instanceof VehicleIntent.ScreenRotate && ((VehicleIntent.ScreenRotate) r1).orientation == ScreenOrientation.TOGGLE)) {
            throw new AssertionError("Failed: فر الشاشة -> " + r1);
        }

        VehicleIntent r2 = resolver.resolve("سوي الشاشة بالطول");
        if (!(r2 instanceof VehicleIntent.ScreenRotate && ((VehicleIntent.ScreenRotate) r2).orientation == ScreenOrientation.PORTRAIT)) {
            throw new AssertionError("Failed: سوي الشاشة بالطول -> " + r2);
        }

        VehicleIntent r3 = resolver.resolve("اقلب الشاشة بالعرض");
        if (!(r3 instanceof VehicleIntent.ScreenRotate && ((VehicleIntent.ScreenRotate) r3).orientation == ScreenOrientation.LANDSCAPE)) {
            throw new AssertionError("Failed: اقلب الشاشة بالعرض -> " + r3);
        }
    }

    private static void testVolumeCommands(ArabicIntentResolver resolver) {
        VehicleIntent r1 = resolver.resolve("علي الصوت");
        if (!(r1 instanceof VehicleIntent.Volume && ((VehicleIntent.Volume) r1).action == VolumeAction.UP)) {
            throw new AssertionError("Failed: علي الصوت -> " + r1);
        }

        VehicleIntent r2 = resolver.resolve("نصي الصوت شويه");
        if (!(r2 instanceof VehicleIntent.Volume && ((VehicleIntent.Volume) r2).action == VolumeAction.DOWN)) {
            throw new AssertionError("Failed: نصي الصوت -> " + r2);
        }

        VehicleIntent r3 = resolver.resolve("اكتم الصوت");
        if (!(r3 instanceof VehicleIntent.Volume && ((VehicleIntent.Volume) r3).action == VolumeAction.MUTE)) {
            throw new AssertionError("Failed: اكتم الصوت -> " + r3);
        }
    }

    private static void testNavigationCommands(ArabicIntentResolver resolver) {
        VehicleIntent r1 = resolver.resolve("افتح الخرايط");
        if (!(r1 instanceof VehicleIntent.Navigation)) {
            throw new AssertionError("Failed: افتح الخرايط -> " + r1);
        }

        VehicleIntent r2 = resolver.resolve("شغل الملاحة");
        if (!(r2 instanceof VehicleIntent.Navigation)) {
            throw new AssertionError("Failed: شغل الملاحة -> " + r2);
        }
    }

    private static void testMediaCommands(ArabicIntentResolver resolver) {
        VehicleIntent r1 = resolver.resolve("شغل الاغنية التالية");
        if (!(r1 instanceof VehicleIntent.Media && ((VehicleIntent.Media) r1).action == MediaAction.NEXT)) {
            throw new AssertionError("Failed: شغل الاغنية التالية -> " + r1);
        }
    }

    private static void testOpenAppCommands(ArabicIntentResolver resolver) {
        VehicleIntent r1 = resolver.resolve("افتح تطبيق يوتيوب");
        if (!(r1 instanceof VehicleIntent.OpenApp && ((VehicleIntent.OpenApp) r1).appName.contains("يوتيوب"))) {
            throw new AssertionError("Failed: افتح تطبيق يوتيوب -> " + r1);
        }

        VehicleIntent r2 = resolver.resolve("شغل تطبيق سبوتيفاي فدوه");
        if (!(r2 instanceof VehicleIntent.OpenApp && ((VehicleIntent.OpenApp) r2).appName.contains("سبوتيفاي"))) {
            throw new AssertionError("Failed: شغل تطبيق سبوتيفاي -> " + r2);
        }

        VehicleIntent r3 = resolver.resolve("افتح الاعدادات");
        if (!(r3 instanceof VehicleIntent.OpenApp && ((VehicleIntent.OpenApp) r3).appName.equals("الاعدادات"))) {
            throw new AssertionError("Failed: افتح الاعدادات -> " + r3);
        }

        VehicleIntent r4 = resolver.resolve("افتح الضبط");
        if (!(r4 instanceof VehicleIntent.OpenApp && ((VehicleIntent.OpenApp) r4).appName.equals("الاعدادات"))) {
            throw new AssertionError("Failed: افتح الضبط -> " + r4);
        }
    }

    private static void testUnknownCommands(ArabicIntentResolver resolver) {
        VehicleIntent r1 = resolver.resolve("شنو لون السماء اليوم؟");
        if (!(r1 instanceof VehicleIntent.Unknown)) {
            throw new AssertionError("Failed: Unknown test -> " + r1);
        }
    }
}
