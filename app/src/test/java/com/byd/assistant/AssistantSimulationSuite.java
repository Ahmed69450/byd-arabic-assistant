package com.byd.assistant;

import com.byd.assistant.model.*;
import com.byd.assistant.nlp.ArabicIntentResolver;
import com.byd.assistant.nlp.ArabicNormalizer;
import com.byd.assistant.tts.ArabicTtsEngine;
import com.byd.assistant.vehicle.BydVehicleController;

public class AssistantSimulationSuite {
    public static void main(String[] args) {
        ArabicIntentResolver resolver = new ArabicIntentResolver();
        ArabicTtsEngine ttsEngine = new ArabicTtsEngine();
        BydVehicleController vehicleController = new BydVehicleController(null, true);

        String[] testPhrases = {
            // Iraqi Dialect Commands
            "شغل التبريد فدوه",
            "شعل المكيف وسوي الحرارة 22",
            "طفي التبريد هسة",
            "نزل الجامة",
            "صعد الجامات كلها",
            "فر الشاشة",
            "سوي الشاشة بالطول",
            "اقلب الشاشة بالعرض",
            "علي الصوت شويه",
            "نصي الصوت",
            "اكتم الصوت",
            "افتح الخرايط",
            "شغل الاغنية التالية",

            // Classical / MSA Commands
            "شغل المكيف واضبط الحرارة على 24",
            "أوقف التكييف",
            "افتح النوافذ",
            "أغلق النوافذ",
            "أدر الشاشة",
            "ارفع مستوى الصوت",
            "اخفض الصوت",
            "افتح الخرائط",
            "شغل الملاحة"
        };

        System.out.println("==================================================================");
        System.out.println("  BYD Destroyer 05 (2025) Arabic Assistant Simulation Test Suite  ");
        System.out.println("==================================================================");

        int passedCount = 0;

        for (String phrase : testPhrases) {
            String normalized = ArabicNormalizer.normalize(phrase);
            VehicleIntent intent = resolver.resolve(phrase);
            boolean executed = vehicleController.execute(intent);
            String response = ttsEngine.getVocalizedResponse(intent);

            boolean isUnknown = (intent instanceof VehicleIntent.Unknown);
            if (!isUnknown && executed && response != null && !response.isEmpty()) {
                passedCount++;
                System.out.println("✓ [صوت المستخدم]: " + phrase);
                System.out.println("  [الأمر المستخرج]: " + intent);
                System.out.println("  [الرد الصوتي المشكول]: " + response);
                System.out.println("------------------------------------------------------------------");
            } else {
                System.err.println("✗ Failed for phrase: " + phrase + " -> " + intent);
            }
        }

        System.out.println("Simulation Finished: " + passedCount + " / " + testPhrases.length + " passed successfully.");
        if (passedCount == testPhrases.length) {
            System.out.println("STATUS: ALL_SIMULATION_TESTS_PASSED_100%");
        } else {
            throw new AssertionError("Some test cases failed!");
        }
    }
}
