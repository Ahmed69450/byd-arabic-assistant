package com.byd.assistant.nlp;

public class DialogueFsmTest {

    public static void main(String[] args) {
        testSingleTurnIntent();
        testMultiTurnContextMemory();
        testMultiTurnPassengerAndAll();
        testMultiTurnWindowClose();
        testMultiTurnSunroof();
        testMultiTurnCancellation();
        testSlotExtractorNumbers();
        testSlotExtractorTarget();
        testDirectWindowCommands();
        testVolumeAndScreenCommands();
        testFuzzyToleranceWithLevenshtein();
        testFsmReset();

        System.out.println("ALL_DIALOGUE_FSM_TESTS_PASSED");
    }

    public static void testSingleTurnIntent() {
        DialogueFsm fsm = new DialogueFsm();

        // Single-turn climate set temperature
        DialogueFsm.FsmResult res1 = fsm.processInput("شغل المكيف على 22");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("CLIMATE_SET_TEMP", res1.intent);
        assertEquals(22, res1.numericValue);
        assertFalse(res1.requiresConfirmation);

        // Single-turn climate on
        fsm.reset();
        DialogueFsm.FsmResult res2 = fsm.processInput("شغل التبريد");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("CLIMATE_ON", res2.intent);

        // Single-turn climate off
        fsm.reset();
        DialogueFsm.FsmResult res3 = fsm.processInput("طفي المكيف");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("CLIMATE_OFF", res3.intent);
    }

    public static void testMultiTurnContextMemory() {
        DialogueFsm fsm = new DialogueFsm();

        // Turn 1: ambiguous window command without target
        DialogueFsm.FsmResult res1 = fsm.processInput("افتح النافذة");
        assertEquals(DialogueFsm.State.AWAITING_CONFIRMATION, fsm.getCurrentState());
        assertTrue(res1.requiresConfirmation);
        assertTrue(res1.prompt.contains("اي نافذة"));

        // Turn 2: answer specifying driver
        DialogueFsm.FsmResult res2 = fsm.processInput("السائق");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("WINDOW_OPEN_DRIVER", res2.intent);
        assertEquals("DRIVER", res2.targetEntity);
        assertFalse(res2.requiresConfirmation);
    }

    public static void testMultiTurnPassengerAndAll() {
        DialogueFsm fsm = new DialogueFsm();

        // Turn 1 -> Turn 2: Passenger
        fsm.processInput("افتح النافذة");
        assertEquals(DialogueFsm.State.AWAITING_CONFIRMATION, fsm.getCurrentState());
        DialogueFsm.FsmResult resPass = fsm.processInput("الراكب");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("WINDOW_OPEN_PASSENGER", resPass.intent);
        assertEquals("PASSENGER", resPass.targetEntity);

        // Reset and test All
        fsm.reset();
        fsm.processInput("افتح النافذة");
        assertEquals(DialogueFsm.State.AWAITING_CONFIRMATION, fsm.getCurrentState());
        DialogueFsm.FsmResult resAll = fsm.processInput("الكل");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("WINDOW_OPEN_ALL", resAll.intent);
        assertEquals("ALL", resAll.targetEntity);
    }

    public static void testMultiTurnWindowClose() {
        DialogueFsm fsm = new DialogueFsm();

        DialogueFsm.FsmResult res1 = fsm.processInput("اغلق النافذة");
        assertEquals(DialogueFsm.State.AWAITING_CONFIRMATION, fsm.getCurrentState());
        assertTrue(res1.requiresConfirmation);
        assertTrue(res1.prompt.contains("اي نافذة"));

        DialogueFsm.FsmResult res2 = fsm.processInput("السائق");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("WINDOW_CLOSE_DRIVER", res2.intent);
        assertEquals("DRIVER", res2.targetEntity);
    }

    public static void testMultiTurnSunroof() {
        DialogueFsm fsm = new DialogueFsm();

        fsm.processInput("افتح النافذة");
        DialogueFsm.FsmResult res = fsm.processInput("فتحة السقف");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("SUNROOF_OPEN", res.intent);
        assertEquals("SUNROOF", res.targetEntity);
    }

    public static void testMultiTurnCancellation() {
        DialogueFsm fsm = new DialogueFsm();

        fsm.processInput("افتح النافذة");
        assertEquals(DialogueFsm.State.AWAITING_CONFIRMATION, fsm.getCurrentState());

        DialogueFsm.FsmResult resCancel = fsm.processInput("الغاء");
        assertEquals(DialogueFsm.State.IDLE, fsm.getCurrentState());
        assertEquals("CANCEL", resCancel.intent);
    }

    public static void testSlotExtractorNumbers() {
        // Arabic written compound numbers
        int num24 = SlotExtractor.extractInteger("اضبط درجة الحرارة على اربعة وعشرين", 20);
        assertEquals(24, num24);

        int num21 = SlotExtractor.extractInteger("شغل المكيف على واحد وعشرين", 20);
        assertEquals(21, num21);

        int num22 = SlotExtractor.extractInteger("درجة الحرارة اثنان وعشرون", 20);
        assertEquals(22, num22);

        int num23 = SlotExtractor.extractInteger("ثلاثة وعشرون", 20);
        assertEquals(23, num23);

        int num25 = SlotExtractor.extractInteger("خمسة وعشرون", 20);
        assertEquals(25, num25);

        // Tens & teens
        int num20 = SlotExtractor.extractInteger("عشرون", 15);
        assertEquals(20, num20);

        int num30 = SlotExtractor.extractInteger("ثلاثين", 15);
        assertEquals(30, num30);

        int num16 = SlotExtractor.extractInteger("ستة عشر", 20);
        assertEquals(16, num16);

        // ASCII digits
        int directNum = SlotExtractor.extractInteger("خلها 19", 20);
        assertEquals(19, directNum);

        // Eastern Arabic numerals (٢٢)
        int arabicIndicNum = SlotExtractor.extractInteger("الحرارة ٢٢", 20);
        assertEquals(22, arabicIndicNum);

        // Default fallback when no number present
        int fallback = SlotExtractor.extractInteger("بدون رقم", 20);
        assertEquals(20, fallback);
    }

    public static void testSlotExtractorTarget() {
        assertEquals("DRIVER", SlotExtractor.extractTarget("نافذة السائق"));
        assertEquals("DRIVER", SlotExtractor.extractTarget("السايق"));
        assertEquals("PASSENGER", SlotExtractor.extractTarget("الراكب"));
        assertEquals("PASSENGER", SlotExtractor.extractTarget("المرافق"));
        assertEquals("SUNROOF", SlotExtractor.extractTarget("فتحة السقف"));
        assertEquals("SUNROOF", SlotExtractor.extractTarget("بانوراما"));
        assertEquals("ALL", SlotExtractor.extractTarget("الكل"));
        assertEquals("ALL", SlotExtractor.extractTarget("سد الجامات كلها"));
        assertEquals("ALL", SlotExtractor.extractTarget("جميع النوافذ"));
        assertNull(SlotExtractor.extractTarget("افتح النافذة"));
    }

    public static void testDirectWindowCommands() {
        DialogueFsm fsm = new DialogueFsm();

        // Direct driver window
        DialogueFsm.FsmResult r1 = fsm.processInput("افتح نافذة السائق");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("WINDOW_OPEN_DRIVER", r1.intent);
        assertEquals("DRIVER", r1.targetEntity);

        // Direct close all windows
        fsm.reset();
        DialogueFsm.FsmResult r2 = fsm.processInput("اغلق جميع النوافذ");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("WINDOW_CLOSE_ALL", r2.intent);
        assertEquals("ALL", r2.targetEntity);

        // Direct sunroof
        fsm.reset();
        DialogueFsm.FsmResult r3 = fsm.processInput("افتح فتحة السقف");
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
        assertEquals("SUNROOF_OPEN", r3.intent);
        assertEquals("SUNROOF", r3.targetEntity);
    }

    public static void testVolumeAndScreenCommands() {
        DialogueFsm fsm = new DialogueFsm();

        DialogueFsm.FsmResult rVolUp = fsm.processInput("علي الصوت");
        assertEquals("VOLUME_UP", rVolUp.intent);

        DialogueFsm.FsmResult rVolDown = fsm.processInput("نصي الصوت");
        assertEquals("VOLUME_DOWN", rVolDown.intent);

        DialogueFsm.FsmResult rVolMute = fsm.processInput("اكتم الصوت");
        assertEquals("VOLUME_MUTE", rVolMute.intent);

        DialogueFsm.FsmResult rScreen = fsm.processInput("فر الشاشة");
        assertEquals("SCREEN_ROTATE", rScreen.intent);

        DialogueFsm.FsmResult rPortrait = fsm.processInput("سوي الشاشة بالطول");
        assertEquals("SCREEN_ROTATE_PORTRAIT", rPortrait.intent);
    }

    public static void testFuzzyToleranceWithLevenshtein() {
        DialogueFsm fsm = new DialogueFsm();

        // Typo: "شغل المكف على 22" -> matches climate set temp
        DialogueFsm.FsmResult res = fsm.processInput("شغل المكف على 22");
        assertEquals("CLIMATE_SET_TEMP", res.intent);
        assertEquals(22, res.numericValue);
        assertEquals(DialogueFsm.State.SPEAKING, fsm.getCurrentState());
    }

    public static void testFsmReset() {
        DialogueFsm fsm = new DialogueFsm();
        fsm.processInput("افتح النافذة");
        assertEquals(DialogueFsm.State.AWAITING_CONFIRMATION, fsm.getCurrentState());

        fsm.reset();
        assertEquals(DialogueFsm.State.IDLE, fsm.getCurrentState());
        assertNull(fsm.getPendingAction());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected: " + expected + ", but got: " + actual);
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition expected to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Condition expected to be false");
        }
    }

    private static void assertNull(Object object) {
        if (object != null) {
            throw new AssertionError("Expected null, but got: " + object);
        }
    }
}
