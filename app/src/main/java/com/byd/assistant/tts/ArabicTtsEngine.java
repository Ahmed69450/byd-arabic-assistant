package com.byd.assistant.tts;

import com.byd.assistant.model.*;

public class ArabicTtsEngine {

    public String getVocalizedResponse(VehicleIntent intent) {
        if (intent instanceof VehicleIntent.ScreenRotate) {
            VehicleIntent.ScreenRotate sr = (VehicleIntent.ScreenRotate) intent;
            switch (sr.orientation) {
                case PORTRAIT:
                    return "تَمَّ تَدْوِيرُ الشَّاشَةِ إِلَى الوَضْعِ الرَّأْسِيِّ.";
                case LANDSCAPE:
                    return "تَمَّ تَدْوِيرُ الشَّاشَةِ إِلَى الوَضْعِ الأُفُقِيِّ.";
                case TOGGLE:
                default:
                    return "حَاضِرٌ، تَمَّ تَدْوِيرُ الشَّاشَةِ.";
            }
        }

        if (intent instanceof VehicleIntent.Climate) {
            VehicleIntent.Climate c = (VehicleIntent.Climate) intent;
            if (!c.enabled) {
                return "تَمَّ إِيقَافُ التَّكْيِيفِ.";
            } else if (c.targetTemp != null) {
                String tempText = vocalizeTemperatureNumber(c.targetTemp);
                return "تَمَّ تَشْغِيلُ التَّكْيِيفِ، وَضَبْطُ الحَرَارَةِ عَلَى " + tempText + " دَرَجَةً.";
            } else {
                return "تَمَّ تَشْغِيلُ التَّكْيِيفِ.";
            }
        }

        if (intent instanceof VehicleIntent.Window) {
            VehicleIntent.Window w = (VehicleIntent.Window) intent;
            if (w.open) {
                return "تَمَّ فَتْحُ النَّوَافِذِ.";
            } else {
                return "تَمَّ إِغْلَاقُ النَّوَافِذِ.";
            }
        }

        if (intent instanceof VehicleIntent.Volume) {
            VehicleIntent.Volume v = (VehicleIntent.Volume) intent;
            switch (v.action) {
                case UP:
                    return "تَمَّ رَفْعُ مُسْتَوَى الصَّوْتِ.";
                case DOWN:
                    return "تَمَّ خَفْضُ مُسْتَوَى الصَّوْتِ.";
                case MUTE:
                    return "تَمَّ كَتْمُ الصَّوْتِ.";
                case UNMUTE:
                    return "تَمَّ إِعَادَةُ تَشْغِيلِ الصَّوْتِ.";
                case SET:
                default:
                    return "تَمَّ تَعْدِيلُ مُسْتَوَى الصَّوْتِ.";
            }
        }

        if (intent instanceof VehicleIntent.Navigation) {
            return "تَمَّ فَتْحُ الخَرَائِطِ.";
        }

        if (intent instanceof VehicleIntent.Media) {
            VehicleIntent.Media m = (VehicleIntent.Media) intent;
            switch (m.action) {
                case PLAY:
                    return "تَمَّ تَشْغِيلُ الصَّوْتِيَّاتِ.";
                case PAUSE:
                    return "تَمَّ إِيقَافُ الصَّوْتِيَّاتِ.";
                case NEXT:
                    return "تَمَّ الِانْتِقَالُ إِلَى المَقْطَعِ التَّالِي.";
                case PREVIOUS:
                    return "تَمَّ الِانْتِقَالُ إِلَى المَقْطَعِ السَّابِقِ.";
            }
        }

        return "عَفْوًا، لَمْ أَفْهَمِ الأَمْرَ. يُرْجَى الإِعَادَةُ.";
    }

    private String vocalizeTemperatureNumber(int temp) {
        switch (temp) {
            case 16: return "سِتَّ عَشْرَةَ";
            case 17: return "سَبْعَ عَشْرَةَ";
            case 18: return "ثَمَانِيَ عَشْرَةَ";
            case 19: return "تِسْعَ عَشْرَةَ";
            case 20: return "عِشْرِينَ";
            case 21: return "إِحْدَى وَعِشْرِينَ";
            case 22: return "اثْنَتَيْنِ وَعِشْرِينَ";
            case 23: return "ثَلَاثٍ وَعِشْرِينَ";
            case 24: return "أَرْبَعٍ وَعِشْرِينَ";
            case 25: return "خَمْسٍ وَعِشْرِينَ";
            case 26: return "سِتٍّ وَعِشْرِينَ";
            case 27: return "سَبْعٍ وَعِشْرِينَ";
            case 28: return "ثَمَانٍ وَعِشْرِينَ";
            case 29: return "تِسْعٍ وَعِشْرِينَ";
            case 30: return "ثَلَاثِينَ";
            default: return String.valueOf(temp);
        }
    }
}
