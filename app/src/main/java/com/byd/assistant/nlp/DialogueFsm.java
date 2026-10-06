package com.byd.assistant.nlp;

import java.util.HashMap;
import java.util.Map;

/**
 * Finite State Machine (FSM) dialogue manager supporting multi-turn conversational context,
 * intent resolution, entity mapping, and action confirmation.
 */
public class DialogueFsm {

    public enum State {
        IDLE,
        LISTENING,
        THINKING,
        AWAITING_CONFIRMATION,
        SPEAKING,
        ERROR
    }

    public static class FsmResult {
        public String intent;
        public int numericValue = -1;
        public String targetEntity;
        public String prompt;
        public boolean requiresConfirmation = false;

        public FsmResult() {}

        public FsmResult(String intent, int numericValue, String targetEntity, String prompt, boolean requiresConfirmation) {
            this.intent = intent;
            this.numericValue = numericValue;
            this.targetEntity = targetEntity;
            this.prompt = prompt;
            this.requiresConfirmation = requiresConfirmation;
        }

        @Override
        public String toString() {
            return "FsmResult{" +
                    "intent='" + intent + '\'' +
                    ", numericValue=" + numericValue +
                    ", targetEntity='" + targetEntity + '\'' +
                    ", prompt='" + prompt + '\'' +
                    ", requiresConfirmation=" + requiresConfirmation +
                    '}';
        }
    }

    private State currentState = State.IDLE;
    private String pendingAction = null;
    private final Map<String, Object> sessionMemory = new HashMap<>();

    public State getCurrentState() {
        return currentState;
    }

    public void setState(State state) {
        this.currentState = state;
    }

    public String getPendingAction() {
        return pendingAction;
    }

    public Map<String, Object> getSessionMemory() {
        return sessionMemory;
    }

    /**
     * Resets dialogue state to IDLE and clears context memory.
     */
    public synchronized void reset() {
        currentState = State.IDLE;
        pendingAction = null;
        sessionMemory.clear();
    }

    /**
     * Processes raw spoken input, updates FSM state, retains context memory,
     * and returns the resulting dialogue state and action.
     *
     * @param rawInput Raw speech transcribed string
     * @return FsmResult containing intent, slots, and TTS vocal prompt
     */
    public synchronized FsmResult processInput(String rawInput) {
        FsmResult result = new FsmResult();
        if (rawInput == null || rawInput.trim().isEmpty()) {
            result.intent = "UNKNOWN";
            result.prompt = "عذراً، لم اسمعك جيداً";
            currentState = State.IDLE;
            return result;
        }

        String input = ArabicNormalizer.normalize(rawInput);
        if (input.isEmpty()) {
            result.intent = "UNKNOWN";
            result.prompt = "عذراً، لم اسمعك جيداً";
            currentState = State.IDLE;
            return result;
        }

        // 1. Multi-turn context resolution when waiting for clarification
        if (currentState == State.AWAITING_CONFIRMATION && pendingAction != null) {
            // Cancellation check
            if (isCancellation(input)) {
                result.intent = "CANCEL";
                result.prompt = "تم الغاء الامر";
                currentState = State.IDLE;
                pendingAction = null;
                return result;
            }

            if ("WINDOW_OPEN_AMBIGUOUS".equals(pendingAction) || "WINDOW_CLOSE_AMBIGUOUS".equals(pendingAction)) {
                boolean isOpen = "WINDOW_OPEN_AMBIGUOUS".equals(pendingAction);
                String target = SlotExtractor.extractTarget(input);
                if (target != null) {
                    result.targetEntity = target;
                    if ("DRIVER".equals(target)) {
                        result.intent = isOpen ? "WINDOW_OPEN_DRIVER" : "WINDOW_CLOSE_DRIVER";
                        result.prompt = isOpen ? "جاري فتح نافذة السائق" : "جاري اغلاق نافذة السائق";
                    } else if ("PASSENGER".equals(target)) {
                        result.intent = isOpen ? "WINDOW_OPEN_PASSENGER" : "WINDOW_CLOSE_PASSENGER";
                        result.prompt = isOpen ? "جاري فتح نافذة الراكب" : "جاري اغلاق نافذة الراكب";
                    } else if ("SUNROOF".equals(target)) {
                        result.intent = isOpen ? "SUNROOF_OPEN" : "SUNROOF_CLOSE";
                        result.prompt = isOpen ? "جاري فتح فتحة السقف" : "جاري اغلاق فتحة السقف";
                    } else {
                        result.intent = isOpen ? "WINDOW_OPEN_ALL" : "WINDOW_CLOSE_ALL";
                        result.prompt = isOpen ? "جاري فتح جميع النوافذ" : "جاري اغلاق جميع النوافذ";
                    }

                    currentState = State.SPEAKING;
                    pendingAction = null;
                    sessionMemory.put("lastTarget", target);
                    sessionMemory.put("lastAction", result.intent);
                    return result;
                }
            }
        }

        // 2. Climate Control Commands
        if (matchesClimate(input)) {
            if (isOffAction(input)) {
                result.intent = "CLIMATE_OFF";
                result.prompt = "تم ايقاف التكييف";
                currentState = State.SPEAKING;
                sessionMemory.put("lastAction", result.intent);
                return result;
            }

            int temp = SlotExtractor.extractInteger(input, -1);
            if (temp != -1) {
                result.intent = "CLIMATE_SET_TEMP";
                result.numericValue = temp;
                result.prompt = "تم ضبط المكيف على " + temp + " درجة";
                currentState = State.SPEAKING;
                sessionMemory.put("lastAction", result.intent);
                sessionMemory.put("lastTemperature", temp);
                return result;
            }

            result.intent = "CLIMATE_ON";
            result.prompt = "تم تشغيل التكييف";
            currentState = State.SPEAKING;
            sessionMemory.put("lastAction", result.intent);
            return result;
        }

        // 3. Window & Sunroof Commands
        if (matchesWindow(input)) {
            boolean isClose = isOffAction(input) || input.contains("سد") || input.contains("صعد") || input.contains("سكر") || input.contains("قفل");
            boolean isOpen = input.contains("افتح") || input.contains("نزل") || input.contains("هبط") || input.contains("بطل") || !isClose;

            String target = SlotExtractor.extractTarget(input);
            if (target == null) {
                // Ambiguous command -> Enter multi-turn confirmation state
                currentState = State.AWAITING_CONFIRMATION;
                pendingAction = isClose ? "WINDOW_CLOSE_AMBIGUOUS" : "WINDOW_OPEN_AMBIGUOUS";
                result.prompt = isClose
                        ? "اي نافذة ترغب باغلاقها؟ السائق، الراكب، ام الكل؟"
                        : "اي نافذة ترغب بفتحها؟ السائق، الراكب، ام الكل؟";
                result.requiresConfirmation = true;
                return result;
            }

            // Direct unambiguous command
            result.targetEntity = target;
            if ("DRIVER".equals(target)) {
                result.intent = isClose ? "WINDOW_CLOSE_DRIVER" : "WINDOW_OPEN_DRIVER";
                result.prompt = isClose ? "جاري اغلاق نافذة السائق" : "جاري فتح نافذة السائق";
            } else if ("PASSENGER".equals(target)) {
                result.intent = isClose ? "WINDOW_CLOSE_PASSENGER" : "WINDOW_OPEN_PASSENGER";
                result.prompt = isClose ? "جاري اغلاق نافذة الراكب" : "جاري فتح نافذة الراكب";
            } else if ("SUNROOF".equals(target)) {
                result.intent = isClose ? "SUNROOF_CLOSE" : "SUNROOF_OPEN";
                result.prompt = isClose ? "جاري اغلاق فتحة السقف" : "جاري فتح فتحة السقف";
            } else {
                result.intent = isClose ? "WINDOW_CLOSE_ALL" : "WINDOW_OPEN_ALL";
                result.prompt = isClose ? "جاري اغلاق جميع النوافذ" : "جاري فتح جميع النوافذ";
            }

            currentState = State.SPEAKING;
            sessionMemory.put("lastTarget", target);
            sessionMemory.put("lastAction", result.intent);
            return result;
        }

        // 4. Volume Commands
        if (matchesVolume(input)) {
            if (input.contains("علي") || input.contains("ارفع") || input.contains("زيد")) {
                result.intent = "VOLUME_UP";
                result.prompt = "تم رفع مستوى الصوت";
            } else if (input.contains("نصي") || input.contains("اخفض") || input.contains("قلل") || input.contains("نزل")) {
                result.intent = "VOLUME_DOWN";
                result.prompt = "تم خفض مستوى الصوت";
            } else if (input.contains("اكتم") || input.contains("صامت") || input.contains("سكت")) {
                result.intent = "VOLUME_MUTE";
                result.prompt = "تم كتم الصوت";
            } else if (input.contains("رجع") || input.contains("الغاء الكتم")) {
                result.intent = "VOLUME_UNMUTE";
                result.prompt = "تم اعادة تشغيل الصوت";
            } else {
                int vol = SlotExtractor.extractInteger(input, -1);
                if (vol != -1) {
                    result.intent = "VOLUME_SET";
                    result.numericValue = vol;
                    result.prompt = "تم ضبط مستوى الصوت على " + vol;
                } else {
                    result.intent = "VOLUME_UP";
                    result.prompt = "تم رفع مستوى الصوت";
                }
            }

            currentState = State.SPEAKING;
            sessionMemory.put("lastAction", result.intent);
            return result;
        }

        // 5. Screen Rotation Commands
        if (matchesScreen(input)) {
            if (input.contains("طول") || input.contains("راسي") || input.contains("عمودي")) {
                result.intent = "SCREEN_ROTATE_PORTRAIT";
                result.prompt = "تم تدوير الشاشة الى الوضع الرأسي";
            } else if (input.contains("عرض") || input.contains("افقي")) {
                result.intent = "SCREEN_ROTATE_LANDSCAPE";
                result.prompt = "تم تدوير الشاشة الى الوضع الافقي";
            } else {
                result.intent = "SCREEN_ROTATE";
                result.prompt = "تم تدوير الشاشة";
            }

            currentState = State.SPEAKING;
            sessionMemory.put("lastAction", result.intent);
            return result;
        }

        // 6. Navigation Commands
        if (matchesNavigation(input)) {
            result.intent = "NAVIGATION_OPEN";
            result.prompt = "تم فتح الخرائط";
            currentState = State.SPEAKING;
            sessionMemory.put("lastAction", result.intent);
            return result;
        }

        // 7. Media Commands
        if (matchesMedia(input)) {
            if (input.contains("التالي") || input.contains("بعده") || input.contains("بعدها") || input.contains("وراها")) {
                result.intent = "MEDIA_NEXT";
                result.prompt = "تم الانتقال الى المقطع التالي";
            } else if (input.contains("السابق") || input.contains("قبله") || input.contains("قبلها")) {
                result.intent = "MEDIA_PREVIOUS";
                result.prompt = "تم الانتقال الى المقطع السابق";
            } else if (isOffAction(input) || input.contains("اسكت")) {
                result.intent = "MEDIA_PAUSE";
                result.prompt = "تم ايقاف الصوتيات";
            } else {
                result.intent = "MEDIA_PLAY";
                result.prompt = "تم تشغيل الصوتيات";
            }

            currentState = State.SPEAKING;
            sessionMemory.put("lastAction", result.intent);
            return result;
        }

        // 8. Dynamic App Launch Commands
        if (matchesApp(input)) {
            result.intent = "APP_OPEN";
            result.prompt = "تم فتح التطبيق";
            currentState = State.SPEAKING;
            sessionMemory.put("lastAction", result.intent);
            return result;
        }

        // 9. Unknown Fallback
        result.intent = "UNKNOWN";
        result.prompt = "عذراً، لم افهم الامر بدقة";
        currentState = State.IDLE;
        return result;
    }

    private boolean isCancellation(String text) {
        return text.contains("الغاء") || text.contains("الغي") || text.contains("كنسل")
                || text.equals("لا") || text.contains("بطلت") || text.contains("تراجع");
    }

    private boolean isOffAction(String text) {
        return text.contains("طفي") || text.contains("وقف") || text.contains("اغلق")
                || text.contains("عطل") || text.contains("اطفئ") || text.contains("اوقف");
    }

    private boolean matchesClimate(String text) {
        if (text.contains("مكيف") || text.contains("تبريد") || text.contains("تكييف")
                || text.contains("حراره") || text.contains("حرارة") || text.contains("بروده")
                || text.contains("تدفئه") || text.contains("سبلت")) {
            return true;
        }

        // Fuzzy match tokens against climate words
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (LevenshteinMatcher.similarity(token, "المكيف") >= 0.75
                    || LevenshteinMatcher.similarity(token, "مكيف") >= 0.75
                    || LevenshteinMatcher.similarity(token, "التبريد") >= 0.75
                    || LevenshteinMatcher.similarity(token, "تبريد") >= 0.75) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesWindow(String text) {
        if (text.contains("نافذه") || text.contains("نافذة") || text.contains("نوافذ")
                || text.contains("جامه") || text.contains("جامات") || text.contains("شباك")
                || text.contains("شبابيك") || text.contains("دريشه") || text.contains("دريشة")
                || text.contains("سقف") || text.contains("بانوراما")) {
            return true;
        }

        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (LevenshteinMatcher.similarity(token, "النافذة") >= 0.75
                    || LevenshteinMatcher.similarity(token, "نافذة") >= 0.75
                    || LevenshteinMatcher.similarity(token, "النوافذ") >= 0.75
                    || LevenshteinMatcher.similarity(token, "الجامة") >= 0.75) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesVolume(String text) {
        if (text.contains("صوت")) {
            return true;
        }
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (LevenshteinMatcher.similarity(token, "الصوت") >= 0.75
                    || LevenshteinMatcher.similarity(token, "صوت") >= 0.75) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesScreen(String text) {
        boolean hasScreenWord = text.contains("شاشه") || text.contains("شاشة");
        if (!hasScreenWord) {
            String[] tokens = text.split("\\s+");
            for (String token : tokens) {
                if (LevenshteinMatcher.similarity(token, "الشاشة") >= 0.75
                        || LevenshteinMatcher.similarity(token, "شاشة") >= 0.75) {
                    hasScreenWord = true;
                    break;
                }
            }
        }

        boolean hasRotateWord = text.contains("فر") || text.contains("دور") || text.contains("اقلب")
                || text.contains("حول") || text.contains("لف") || text.contains("ادر")
                || text.contains("طول") || text.contains("عرض")
                || text.contains("عمودي") || text.contains("افقي") || text.contains("راسي");

        return hasScreenWord && hasRotateWord;
    }

    private boolean matchesNavigation(String text) {
        return text.contains("خرايط") || text.contains("خرائط")
                || text.contains("ملاحه") || text.contains("ملاحة")
                || text.contains("جي بي اس") || text.contains("gps");
    }

    private boolean matchesMedia(String text) {
        return text.contains("اغنيه") || text.contains("اغنية") || text.contains("اغاني")
                || text.contains("موسيقي") || text.contains("موسيقى") || text.contains("صوتيات")
                || (text.contains("شغل") && (text.contains("التالي") || text.contains("السابق")));
    }

    private boolean matchesApp(String text) {
        return text.contains("تطبيق") || text.contains("برنامج")
                || text.contains("الاعدادات") || text.contains("اعدادات")
                || text.contains("الضبط") || text.contains("ضبط")
                || text.contains("يوتيوب") || text.contains("سبوتيفاي");
    }
}
