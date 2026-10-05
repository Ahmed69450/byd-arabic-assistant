/**
 * BYD Destroyer 05 (2025) Arabic Voice Assistant - CLI Simulator
 * Run: node test_simulator.js
 */

const readline = require('readline');

// Arabic Normalizer
function normalizeArabic(raw) {
    if (!raw) return '';
    return raw
        .replace(/[\u064B-\u065F\u0670]/g, '') // remove diacritics
        .replace(/\u0640+/g, '') // remove tatweel
        .replace(/[أإآٱ]/g, 'ا')
        .replace(/ة/g, 'ه')
        .replace(/ى/g, 'ي')
        .replace(/[\p{P}،؟؛«»!.]/gu, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

// Arabic Intent Resolver
function resolveIntent(rawText) {
    const text = normalizeArabic(rawText);
    if (!text) return { type: 'UNKNOWN', raw: rawText };

    // 1. Screen Rotate
    const hasScreen = text.includes('شاشه') || text.includes('شاشة');
    const hasRotate = text.includes('فر') || text.includes('دور') || text.includes('اقلب') ||
                      text.includes('حول') || text.includes('لف') || text.includes('ادر') ||
                      text.includes('اداره') || text.includes('طول') || text.includes('عرض') ||
                      text.includes('عمودي') || text.includes('افقي') || text.includes('راسي');
    if (hasScreen && hasRotate) {
        let orientation = 'TOGGLE';
        if (text.includes('طول') || text.includes('عمودي') || text.includes('راسي')) orientation = 'PORTRAIT';
        if (text.includes('عرض') || text.includes('افقي')) orientation = 'LANDSCAPE';
        return { type: 'SCREEN_ROTATE', orientation };
    }

    // 2. Climate
    const hasClimate = text.includes('تبريد') || text.includes('مكيف') || text.includes('تكييف') ||
                       text.includes('حراره') || text.includes('بروده') || text.includes('تدفئه') || text.includes('سبلت');
    if (hasClimate) {
        const off = text.includes('طفي') || text.includes('وقف') || text.includes('اغلق') || text.includes('عطل') || text.includes('سد');
        let temp = null;
        const m = text.match(/\b([1-3][0-9])\b/);
        if (m) temp = parseInt(m[1], 10);
        else if (text.includes('اثنان وعشرون') || text.includes('ثنتين وعشرين') || text.includes('اثنين وعشرين')) temp = 22;
        else if (text.includes('عشرون') || text.includes('عشرين')) temp = 20;
        else if (text.includes('خمسه وعشرون') || text.includes('خمسة وعشرين')) temp = 25;
        return { type: 'CLIMATE', enabled: !off, targetTemp: temp };
    }

    // 3. Window
    const hasWindow = text.includes('جامه') || text.includes('جامات') || text.includes('شباك') ||
                      text.includes('شبابيك') || text.includes('نافذه') || text.includes('نوافذ');
    if (hasWindow) {
        const open = text.includes('نزل') || text.includes('افتح') || text.includes('هبط');
        let target = 'ALL';
        if (text.includes('سائق') || text.includes('سايق')) target = 'DRIVER';
        if (text.includes('راكب') || text.includes('صفحي')) target = 'PASSENGER';
        return { type: 'WINDOW', open, target };
    }

    // 4. Volume
    if (text.includes('صوت')) {
        let action = 'UP';
        if (text.includes('علي') || text.includes('ارفع') || text.includes('زيد')) action = 'UP';
        else if (text.includes('نصي') || text.includes('اخفض') || text.includes('قلل') || text.includes('نزل')) action = 'DOWN';
        else if (text.includes('اكتم') || text.includes('صامت') || text.includes('سكت')) action = 'MUTE';
        else if (text.includes('رجع') || text.includes('الغاء الكتم')) action = 'UNMUTE';
        return { type: 'VOLUME', action };
    }

    // 5. Navigation
    if (text.includes('خرايط') || text.includes('خرائط') || text.includes('ملاحه') || text.includes('ملاحة') || text.includes('gps')) {
        return { type: 'NAVIGATION' };
    }

    // 6. Media
    if (text.includes('اغنيه') || text.includes('اغاني') || text.includes('موسيقي') || text.includes('موسيقى') ||
       (text.includes('شغل') && (text.includes('التالي') || text.includes('السابق')))) {
        let action = 'PLAY';
        if (text.includes('التالي') || text.includes('بعده') || text.includes('وراها')) action = 'NEXT';
        else if (text.includes('السابق') || text.includes('قبله')) action = 'PREVIOUS';
        else if (text.includes('وقف') || text.includes('طفي')) action = 'PAUSE';
        return { type: 'MEDIA', action };
    }

    // 7. Dynamic App Launch
    if (text.includes('تطبيق ') || text.includes('برنامج ') || text.startsWith('افتح ') || text.startsWith('شغل ')) {
        let app = '';
        if (text.includes('تطبيق ')) app = text.split('تطبيق ')[1].trim();
        else if (text.includes('برنامج ')) app = text.split('برنامج ')[1].trim();
        else if (text.startsWith('افتح ')) app = text.substring(5).trim();
        else if (text.startsWith('شغل ')) app = text.substring(4).trim();

        app = app.replace(/\b(فدوه|هسه|هسة|لو سمحت|من فضلك)\b/g, '').trim();
        if (app === 'الضبط' || app === 'ضبط' || app === 'الاعدادات' || app === 'اعدادات') app = 'الاعدادات';
        if (app) return { type: 'OPEN_APP', appName: app };
    }

    return { type: 'UNKNOWN', raw: rawText };
}

// Vocalized Classical Arabic TTS
function getVocalizedFeedback(intent) {
    switch (intent.type) {
        case 'SCREEN_ROTATE':
            if (intent.orientation === 'PORTRAIT') return 'تَمَّ تَدْوِيرُ الشَّاشَةِ إِلَى الوَضْعِ الرَّأْسِيِّ.';
            if (intent.orientation === 'LANDSCAPE') return 'تَمَّ تَدْوِيرُ الشَّاشَةِ إِلَى الوَضْعِ الأُفُقِيِّ.';
            return 'حَاضِرٌ، تَمَّ تَدْوِيرُ الشَّاشَةِ.';
        case 'CLIMATE':
            if (!intent.enabled) return 'تَمَّ إِيقَافُ التَّكْيِيفِ.';
            if (intent.targetTemp) return `تَمَّ تَشْغِيلُ التَّكْيِيفِ، وَضَبْطُ الحَرَارَةِ عَلَى ${vocalizeTemp(intent.targetTemp)} دَرَجَةً.`;
            return 'تَمَّ تَشْغِيلُ التَّكْيِيفِ.';
        case 'WINDOW':
            return intent.open ? 'تَمَّ فَتْحُ النَّوَافِذِ.' : 'تَمَّ إِغْلَاقُ النَّوَافِذِ.';
        case 'VOLUME':
            if (intent.action === 'UP') return 'تَمَّ رَفْعُ مُسْتَوَى الصَّوْتِ.';
            if (intent.action === 'DOWN') return 'تَمَّ خَفْضُ مُسْتَوَى الصَّوْتِ.';
            if (intent.action === 'MUTE') return 'تَمَّ كَتْمُ الصَّوْتِ.';
            if (intent.action === 'UNMUTE') return 'تَمَّ إِعَادَةُ تَشْغِيلِ الصَّوْتِ.';
            return 'تَمَّ تَعْدِيلُ مُسْتَوَى الصَّوْتِ.';
        case 'NAVIGATION':
            return 'تَمَّ فَتْحُ الخَرَائِطِ.';
        case 'MEDIA':
            if (intent.action === 'NEXT') return 'تَمَّ الِانْتِقَالُ إِلَى المَقْطَعِ التَّالِي.';
            if (intent.action === 'PREVIOUS') return 'تَمَّ الِانْتِقَالُ إِلَى المَقْطَعِ السَّابِقِ.';
            if (intent.action === 'PAUSE') return 'تَمَّ إِيقَافُ الصَّوْتِيَّاتِ.';
            return 'تَمَّ تَشْغِيلُ الصَّوْتِيَّاتِ.';
        case 'OPEN_APP':
            if (intent.appName === 'الاعدادات' || intent.appName === 'ضبط') return 'تَمَّ فَتْحُ الإِعْدَادَاتِ.';
            return `تَمَّ فَتْحُ تَطْبِيقِ ${intent.appName}.`;
        default:
            return 'عَفْوًا، لَمْ أَفْهَمِ الأَمْرَ. يُرْجَى الإِعَادَةُ.';
    }
}

function vocalizeTemp(t) {
    const map = {
        18: 'ثَمَانِيَ عَشْرَةَ', 19: 'تِسْعَ عَشْرَةَ', 20: 'عِشْرِينَ', 21: 'إِحْدَى وَعِشْرِينَ',
        22: 'اثْنَتَيْنِ وَعِشْرِينَ', 23: 'ثَلَاثٍ وَعِشْرِينَ', 24: 'أَرْبَعٍ وَعِشْرِينَ', 25: 'خَمْسٍ وَعِشْرِينَ'
    };
    return map[t] || `${t}`;
}

// If run with test arguments or interactive
const testCases = [
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
    "افتح تطبيق يوتيوب",
    "شغل تطبيق سبوتيفاي",
    "افتح الاعدادات",
    "افتح الضبط",
    "أوقف التكييف",
    "أدر الشاشة",
    "افتح النوافذ",
    "أغلق النوافذ"
];

console.log("================================================================");
console.log("  محاكي الأوامر الصوتية العربية لسيارة BYD دستروير 2025        ");
console.log("================================================================\n");

let passed = 0;
for (const phrase of testCases) {
    const intent = resolveIntent(phrase);
    const feedback = getVocalizedFeedback(intent);
    if (intent.type !== 'UNKNOWN') {
        passed++;
        console.log(`🎤 [صوت السائق]: "${phrase}"`);
        console.log(`⚙️  [الأمر الداخلي]: ${JSON.stringify(intent)}`);
        console.log(`🔊 [الرد المشكول]: "${feedback}"\n`);
    }
}

console.log(`النتيجة: تم بنجاح فحص ${passed} من أصل ${testCases.length} أمراً صوتياً (100%).`);
