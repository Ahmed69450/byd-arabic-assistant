package com.byd.assistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.widget.Toast;
import com.byd.assistant.MainActivity;
import com.byd.assistant.model.VehicleIntent;
import com.byd.assistant.nlp.ArabicIntentResolver;
import com.byd.assistant.tts.ArabicTtsEngine;
import com.byd.assistant.ui.FloatingAssistantView;
import com.byd.assistant.vehicle.BydVehicleController;
import java.util.ArrayList;
import java.util.Locale;

public class AssistantForegroundService extends Service implements FloatingAssistantView.OnAssistantClickListener {
    private static final String CHANNEL_ID = "byd_assistant_channel";
    private static final int NOTIFICATION_ID = 1001;

    private FloatingAssistantView floatingView;
    private ArabicIntentResolver intentResolver;
    private ArabicTtsEngine ttsEngine;
    private BydVehicleController vehicleController;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    private boolean isListening = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        intentResolver = new ArabicIntentResolver();
        ttsEngine = new ArabicTtsEngine();
        vehicleController = new BydVehicleController(this);

        initNotificationChannel();
        Notification notification = buildForegroundNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        initTts();
        initSpeechRecognizer();

        floatingView = new FloatingAssistantView(this);
        floatingView.setOnAssistantClickListener(this);
        floatingView.attachToWindow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onMicClicked() {
        if (!isListening) {
            startListening();
        } else {
            stopListening();
        }
    }

    private void startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "يرجى منح إذن الميكروفون أولاً من إعدادات التطبيق", Toast.LENGTH_LONG).show();
                if (floatingView != null) floatingView.setState(FloatingAssistantView.State.IDLE);
                return;
            }
        }

        if (speechRecognizer == null) {
            initSpeechRecognizer();
        }

        requestAudioFocusDucking();
        floatingView.setState(FloatingAssistantView.State.LISTENING);
        isListening = true;

        Toast.makeText(this, "أستمع إليك الآن... تَحَدَّث", Toast.LENGTH_SHORT).show();

        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        mainHandler.post(() -> {
            try {
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                floatingView.setState(FloatingAssistantView.State.IDLE);
                isListening = false;
                abandonAudioFocus();
            }
        });
    }

    private void stopListening() {
        isListening = false;
        mainHandler.post(() -> {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
        });
    }

    private void handleRecognizedSpeech(String spokenText) {
        floatingView.setState(FloatingAssistantView.State.PROCESSING);

        // 1. Resolve Intent
        VehicleIntent intent = intentResolver.resolve(spokenText);

        // 2. Execute Action on Vehicle Bridge
        vehicleController.execute(intent);

        // 3. Generate Vocalized Arabic Feedback
        String responseText = ttsEngine.getVocalizedResponse(intent);

        // 4. Speak response
        speakResponse(responseText);
    }

    private void speakResponse(String text) {
        floatingView.setState(FloatingAssistantView.State.SPEAKING);

        if (textToSpeech != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "BYD_RESPONSE");
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "BYD_RESPONSE");
        } else {
            floatingView.setState(FloatingAssistantView.State.IDLE);
            abandonAudioFocus();
        }
    }

    private void initSpeechRecognizer() {
        mainHandler.post(() -> {
            try {
                if (speechRecognizer != null) {
                    try { speechRecognizer.destroy(); } catch (Exception ignored) {}
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {
                        isListening = false;
                    }
                    @Override public void onError(int error) {
                        isListening = false;
                        if (floatingView != null) floatingView.setState(FloatingAssistantView.State.IDLE);
                        abandonAudioFocus();

                        String errorMsg;
                        switch (error) {
                            case SpeechRecognizer.ERROR_NO_MATCH:
                                errorMsg = "لم أسمع أي أمر، يرجى المحاولة ثانية";
                                break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                                errorMsg = "انتهى الوقت دون تحدث";
                                break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                errorMsg = "يرجى منح إذن الميكروفون للتطبيق";
                                break;
                            case SpeechRecognizer.ERROR_AUDIO:
                                errorMsg = "خطأ في التقاط الصوت من الميكروفون";
                                break;
                            case SpeechRecognizer.ERROR_NETWORK:
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                errorMsg = "يرجى التأكد من اتصال الإنترنت للتعرف على الصوت";
                                break;
                            case 12: // ERROR_LANGUAGE_NOT_SUPPORTED
                            case 13: // ERROR_LANGUAGE_UNAVAILABLE
                                errorMsg = "حزمة لغة الصوت غير مثبتة محلياً، جاري التبديل للمحرك الافتراضي...";
                                retryDefaultLocale();
                                return;
                            default:
                                errorMsg = "تعذر التعرف على الصوت (رمز: " + error + ")";
                                break;
                        }
                        Toast.makeText(AssistantForegroundService.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onResults(Bundle results) {
                        isListening = false;
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            handleRecognizedSpeech(matches.get(0));
                        } else {
                            if (floatingView != null) floatingView.setState(FloatingAssistantView.State.IDLE);
                            abandonAudioFocus();
                        }
                    }
                    @Override public void onPartialResults(Bundle partialResults) {}
                    @Override public void onEvent(int eventType, Bundle params) {}
                });
            } catch (Exception e) {
                Toast.makeText(AssistantForegroundService.this, "خدمة التعرف على الصوت غير مفعلة في النظام", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void retryDefaultLocale() {
        mainHandler.post(() -> {
            try {
                if (speechRecognizer == null) return;
                Toast.makeText(AssistantForegroundService.this, "أستمع باللغة الافتراضية...", Toast.LENGTH_SHORT).show();
                if (floatingView != null) floatingView.setState(FloatingAssistantView.State.LISTENING);
                isListening = true;

                Intent fallbackIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                fallbackIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                fallbackIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                speechRecognizer.startListening(fallbackIntent);
            } catch (Exception e) {
                if (floatingView != null) floatingView.setState(FloatingAssistantView.State.IDLE);
                isListening = false;
                abandonAudioFocus();
            }
        });
    }

    private void initTts() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale arabicLocale = new Locale("ar");
                textToSpeech.setLanguage(arabicLocale);
                textToSpeech.setPitch(1.0f);
                textToSpeech.setSpeechRate(0.95f);

                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) {
                        floatingView.setState(FloatingAssistantView.State.IDLE);
                        abandonAudioFocus();
                    }
                    @Override public void onError(String utteranceId) {
                        floatingView.setState(FloatingAssistantView.State.IDLE);
                        abandonAudioFocus();
                    }
                });
            }
        });
    }

    private void requestAudioFocusDucking() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "BYD Voice Assistant Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Running in background to listen for BYD voice commands");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder.setContentTitle("مساعد BYD الصوتي نشط")
                .setContentText("المساعد الصوتي يستمع لأوامرك باللغة العربية")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            floatingView.detachFromWindow();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        abandonAudioFocus();
    }
}
