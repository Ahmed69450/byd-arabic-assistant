package com.byd.assistant.nlp;

import com.byd.assistant.model.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArabicIntentResolver {
    private static final Pattern TEMP_DIGIT_PATTERN = Pattern.compile("\\b([1-3][0-9])\\b");

    public VehicleIntent resolve(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) {
            return new VehicleIntent.Unknown("");
        }

        String text = ArabicNormalizer.normalize(spokenText);
        if (text.isEmpty()) {
            return new VehicleIntent.Unknown(spokenText);
        }

        // 1. Screen Rotation Intent
        if (matchesScreenRotate(text)) {
            ScreenOrientation orientation = ScreenOrientation.TOGGLE;
            if (text.contains("طول") || text.contains("راسي") || text.contains("عمودي")) {
                orientation = ScreenOrientation.PORTRAIT;
            } else if (text.contains("عرض") || text.contains("افقي")) {
                orientation = ScreenOrientation.LANDSCAPE;
            }
            return new VehicleIntent.ScreenRotate(orientation);
        }

        // 2. Climate Control Intent
        if (matchesClimate(text)) {
            boolean enabled = !isNegativeOrOff(text);
            Integer temp = extractTemperature(text);
            return new VehicleIntent.Climate(enabled, temp);
        }

        // 3. Window Control Intent
        if (matchesWindow(text)) {
            boolean open = text.contains("نزل") || text.contains("افتح") || text.contains("هبط");
            WindowTarget target = WindowTarget.ALL;
            if (text.contains("سائق") || text.contains("سايق")) {
                target = WindowTarget.DRIVER;
            } else if (text.contains("راكب") || text.contains("صفحي")) {
                target = WindowTarget.PASSENGER;
            }
            return new VehicleIntent.Window(open, target);
        }

        // 4. Volume Intent
        if (matchesVolume(text)) {
            VolumeAction action = VolumeAction.UP;
            if (text.contains("علي") || text.contains("ارفع") || text.contains("زيد")) {
                action = VolumeAction.UP;
            } else if (text.contains("نصي") || text.contains("نزل") || text.contains("اخفض") || text.contains("قلل")) {
                action = VolumeAction.DOWN;
            } else if (text.contains("اكتم") || text.contains("صامت") || text.contains("سكت")) {
                action = VolumeAction.MUTE;
            } else if (text.contains("رجع") || text.contains("الغاء الكتم")) {
                action = VolumeAction.UNMUTE;
            }
            return new VehicleIntent.Volume(action);
        }

        // 5. Navigation Intent
        if (matchesNavigation(text)) {
            return new VehicleIntent.Navigation();
        }

        // 6. Media Intent
        if (matchesMedia(text)) {
            MediaAction action = MediaAction.PLAY;
            if (text.contains("التالي") || text.contains("بعده") || text.contains("وراها") || text.contains("بعدها")) {
                action = MediaAction.NEXT;
            } else if (text.contains("السابق") || text.contains("قبله") || text.contains("قبلها")) {
                action = MediaAction.PREVIOUS;
            } else if (text.contains("وقف") || text.contains("طفي") || text.contains("اسكت")) {
                action = MediaAction.PAUSE;
            }
            return new VehicleIntent.Media(action);
        }

        return new VehicleIntent.Unknown(spokenText);
    }

    private boolean isNegativeOrOff(String t) {
        return t.contains("طفي") || t.contains("وقف") || t.contains("اغلق") || t.contains("عطل") || t.contains("سد");
    }

    private boolean matchesClimate(String t) {
        return t.contains("تبريد") || t.contains("مكيف") || t.contains("حراره") || t.contains("بروده") || t.contains("تدفئه") || t.contains("سبلت");
    }

    private boolean matchesWindow(String t) {
        return t.contains("جامه") || t.contains("جامات") || t.contains("شباك") || t.contains("شبابيك") || t.contains("نافذه") || t.contains("نوافذ");
    }

    private boolean matchesScreenRotate(String t) {
        boolean hasScreenWord = t.contains("شاشه") || t.contains("شاشة");
        boolean hasRotateOrOrientation = t.contains("فر") || t.contains("دور") || t.contains("اقلب")
                || t.contains("حول") || t.contains("لف") || t.contains("طول") || t.contains("عرض")
                || t.contains("راسي") || t.contains("عمودي") || t.contains("افقي");
        return hasScreenWord && hasRotateOrOrientation;
    }

    private boolean matchesVolume(String t) {
        boolean hasVolumeWord = t.contains("صوت");
        boolean hasActionWord = t.contains("علي") || t.contains("ارفع") || t.contains("نصي") || t.contains("اخفض") || t.contains("اكتم") || t.contains("زيد") || t.contains("صامت") || t.contains("سكت");
        return hasVolumeWord && hasActionWord;
    }

    private boolean matchesNavigation(String t) {
        return t.contains("خرايط") || t.contains("خرائط") || t.contains("ملاحه") || t.contains("ملاحة") || t.contains("جي بي اس") || t.contains("gps");
    }

    private boolean matchesMedia(String t) {
        return t.contains("اغنيه") || t.contains("اغاني") || t.contains("موسيقي") || t.contains("موسيقى") || t.contains("صوتيات")
                || (t.contains("شغل") && (t.contains("التالي") || t.contains("السابق")));
    }

    private Integer extractTemperature(String t) {
        Matcher matcher = TEMP_DIGIT_PATTERN.matcher(t);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        if (t.contains("اثنان وعشرون") || t.contains("ثنتين وعشرين") || t.contains("اثنين وعشرين")) return 22;
        if (t.contains("عشرون") || t.contains("عشرين")) return 20;
        if (t.contains("خمسه وعشرون") || t.contains("خمسة وعشرين")) return 25;
        if (t.contains("واحد وعشرون") || t.contains("واحد وعشرين")) return 21;
        if (t.contains("ثلاثه وعشرون") || t.contains("ثلاثة وعشرين")) return 23;
        if (t.contains("اربعه وعشرون") || t.contains("اربعة وعشرين")) return 24;
        return null;
    }
}
